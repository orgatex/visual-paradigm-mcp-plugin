package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Source code skeletons of a class model (an instant generator): one file per class, interface or
 * enumeration, with fields for the attributes and the navigable association ends, method stubs for
 * the operations, the superclass and the realized interfaces. Java can carry JPA annotations for
 * the classes with the {@code ORM Persistable} stereotype. Pure Java: the tool reads the Visual
 * Paradigm model into {@link CgClass}es.
 */
public final class CodeGenerator {

  /** Languages {@link #generate} writes. */
  public static final List<String> LANGUAGES =
      java.util.Arrays.asList("java", "csharp", "python", "typescript", "php");

  private CodeGenerator() {}

  /** A class, interface or enumeration. */
  public static final class CgClass {
    public final String name;
    public String kind = "class"; // class, interface, enum
    public boolean isAbstract;
    public boolean persistable;
    public String parent;
    public final List<String> interfaces = new ArrayList<>();
    public final List<CgField> fields = new ArrayList<>();
    public final List<CgOp> operations = new ArrayList<>();
    public final List<String> literals = new ArrayList<>();

    /**
     * A class.
     *
     * @param name class name
     */
    public CgClass(String name) {
      this.name = name;
    }
  }

  /** A field: an attribute or a navigable association end. */
  public static final class CgField {
    public final String name;
    public final String type;
    public String visibility = "private";
    public boolean isStatic;
    public boolean isFinal;
    public String initial = "";
    public boolean many;
    public boolean key;

    /** JPA relationship of an association end (OneToOne, OneToMany, ManyToOne, ManyToMany). */
    public String relation;

    /** The opposite end's field name, for mappedBy, or null. */
    public String mappedBy;

    /** Join table of the owning side of a many-to-many association, or null. */
    public String joinTable;

    /**
     * A field.
     *
     * @param name field name
     * @param type element type (without collection)
     */
    public CgField(String name, String type) {
      this.name = name;
      this.type = type == null ? "" : type.trim();
    }
  }

  /** An operation. */
  public static final class CgOp {
    public final String name;
    public final String returnType;
    public String visibility = "public";
    public boolean isStatic;
    public boolean isAbstract;
    public final List<String[]> params = new ArrayList<>();

    /**
     * An operation.
     *
     * @param name operation name
     * @param returnType return type ("" = void)
     */
    public CgOp(String name, String returnType) {
      this.name = name;
      this.returnType = returnType == null ? "" : returnType.trim();
    }
  }

  /**
   * Generate the source files.
   *
   * @param language one of {@link #LANGUAGES}
   * @param packageName package/namespace ("" for none)
   * @param classes the classes
   * @param jpa Java only: JPA annotations on persistable classes
   * @param accessors getters/setters (Java, PHP) or properties (C#)
   * @return file path (relative, with package folders for Java) to file content
   */
  public static Map<String, String> generate(
      String language, String packageName, List<CgClass> classes, boolean jpa, boolean accessors) {
    String lang = normalizeLanguage(language);
    if (lang == null) {
      throw new IllegalArgumentException(
          "Unknown language '" + language + "': use one of " + LANGUAGES);
    }
    String pkg = packageName == null ? "" : packageName.trim();
    Map<String, String> files = new LinkedHashMap<>();
    Set<String> parents = new LinkedHashSet<>();
    for (CgClass c : classes) {
      if (c.parent != null) {
        parents.add(c.parent);
      }
    }
    for (CgClass c : classes) {
      switch (lang) {
        case "java":
          files.put(
              (pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/") + c.name + ".java",
              java(pkg, c, jpa && c.persistable, parents.contains(c.name), accessors, classes));
          break;
        case "csharp":
          files.put(c.name + ".cs", csharp(pkg, c, accessors));
          break;
        case "python":
          files.put(snake(c.name) + ".py", python(c, classes));
          break;
        case "typescript":
          files.put(c.name + ".ts", typescript(c, classes));
          break;
        default:
          files.put(c.name + ".php", php(pkg, c, accessors));
          break;
      }
    }
    return files;
  }

  /**
   * The language key for a user spelling (Java, C#, cs, py, ts, ...), or null.
   *
   * @param language spelling
   * @return key of {@link #LANGUAGES} or null
   */
  public static String normalizeLanguage(String language) {
    String l = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
    switch (l) {
      case "":
      case "java":
        return "java";
      case "c#":
      case "cs":
      case "csharp":
        return "csharp";
      case "py":
      case "python":
      case "python3":
        return "python";
      case "ts":
      case "typescript":
        return "typescript";
      case "php":
        return "php";
      default:
        return null;
    }
  }

  // --- Java ---

  private static String java(
      String pkg,
      CgClass c,
      boolean jpa,
      boolean hasChildren,
      boolean accessors,
      List<CgClass> all) {
    StringBuilder body = new StringBuilder();
    Set<String> imports = new LinkedHashSet<>();
    String kind =
        c.kind.equals("interface") ? "interface" : c.kind.equals("enum") ? "enum" : "class";
    if (jpa) {
      imports.add("jakarta.persistence.*");
      body.append("@Entity\n@Table(name = \"").append(c.name).append("\")\n");
      if (hasChildren && c.parent == null) {
        body.append("@Inheritance(strategy = InheritanceType.JOINED)\n");
      }
      if (keyCount(c) > 1) {
        body.append("@IdClass(").append(c.name).append(".Key.class)\n");
      }
    }
    body.append("public ");
    if (c.isAbstract && kind.equals("class")) {
      body.append("abstract ");
    }
    body.append(kind).append(' ').append(c.name);
    if (c.parent != null && !kind.equals("enum")) {
      body.append(" extends ").append(c.parent);
    }
    if (!c.interfaces.isEmpty()) {
      body.append(kind.equals("interface") ? " extends " : " implements ")
          .append(String.join(", ", c.interfaces));
    }
    body.append(" {\n");
    if (kind.equals("enum")) {
      body.append("  ").append(String.join(",\n  ", c.literals)).append(";\n");
    }
    boolean hasKey = c.fields.stream().anyMatch(f -> f.key);
    if (jpa && !hasKey && c.parent == null) {
      body.append("\n  @Id\n  @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
      body.append("  private Long id;\n");
    }
    for (CgField f : c.fields) {
      String type = javaType(f.type, f.many, imports);
      body.append('\n');
      if (jpa && !f.isStatic) {
        if (f.key) {
          body.append("  @Id\n");
          if (keyCount(c) == 1
              && (type.equals("Long")
                  || type.equals("long")
                  || type.equals("Integer")
                  || type.equals("int"))) {
            body.append("  @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
          }
        } else if (f.relation != null) {
          body.append("  @").append(f.relation);
          if (f.mappedBy != null
              && (f.relation.equals("OneToMany")
                  || f.relation.equals("ManyToMany")
                  || f.relation.equals("OneToOne"))) {
            body.append("(mappedBy = \"").append(f.mappedBy).append("\")");
          }
          body.append('\n');
          if (f.relation.equals("ManyToOne")
              || (f.relation.equals("OneToOne") && f.mappedBy == null)) {
            body.append("  @JoinColumn(name = \"").append(joinColumn(f.type, all)).append("\")\n");
          }
          if (f.relation.equals("ManyToMany") && f.mappedBy == null && f.joinTable != null) {
            body.append("  @JoinTable(\n      name = \"")
                .append(f.joinTable)
                .append("\",\n      joinColumns = @JoinColumn(name = \"")
                .append(joinColumn(c.name, all))
                .append("\"),\n      inverseJoinColumns = @JoinColumn(name = \"")
                .append(joinColumn(f.type, all))
                .append("\"))\n");
          }
        }
      }
      body.append("  ");
      if (!kind.equals("interface")) {
        body.append(f.visibility.isEmpty() ? "private" : f.visibility).append(' ');
      }
      if (f.isStatic || kind.equals("interface")) {
        body.append("static ");
      }
      if (f.isFinal || kind.equals("interface")) {
        body.append("final ");
      }
      body.append(type).append(' ').append(f.name);
      if (!f.initial.isEmpty()) {
        body.append(" = ").append(f.initial);
      } else if (f.many) {
        body.append(" = new ArrayList<>()");
        imports.add("java.util.ArrayList");
      } else if (kind.equals("interface")) {
        body.append(" = ").append(javaDefault(type));
      }
      body.append(";\n");
    }
    if (jpa && kind.equals("class")) {
      body.append("\n  public ").append(c.name).append("() {}\n");
    }
    if (accessors && kind.equals("class")) {
      if (jpa && !hasKey && c.parent == null) {
        appendJavaAccessors(body, "Long", "id", false);
      }
      for (CgField f : c.fields) {
        if (!f.isStatic) {
          appendJavaAccessors(body, javaType(f.type, f.many, imports), f.name, f.isFinal);
        }
      }
    }
    for (CgOp op : c.operations) {
      String ret = op.returnType.isEmpty() ? "void" : javaType(op.returnType, false, imports);
      body.append('\n');
      boolean noBody = kind.equals("interface") || op.isAbstract;
      body.append("  ");
      if (!kind.equals("interface")) {
        body.append(op.visibility.isEmpty() ? "public" : op.visibility).append(' ');
      }
      if (op.isStatic) {
        body.append("static ");
      }
      if (op.isAbstract && !kind.equals("interface")) {
        body.append("abstract ");
      }
      body.append(ret).append(' ').append(op.name).append('(');
      List<String> ps = new ArrayList<>();
      for (String[] p : op.params) {
        ps.add(javaType(p[1], false, imports) + " " + p[0]);
      }
      body.append(String.join(", ", ps)).append(')');
      if (noBody) {
        body.append(";\n");
      } else {
        body.append(" {\n    // TODO implement\n");
        if (!ret.equals("void")) {
          body.append("    return ").append(javaDefault(ret)).append(";\n");
        }
        body.append("  }\n");
      }
    }
    if (jpa && keyCount(c) > 1) {
      appendIdClass(body, c, imports);
    }
    body.append("}\n");
    StringBuilder out = new StringBuilder();
    if (!pkg.isEmpty()) {
      out.append("package ").append(pkg).append(";\n\n");
    }
    for (String imp : imports) {
      out.append("import ").append(imp).append(";\n");
    }
    if (!imports.isEmpty()) {
      out.append('\n');
    }
    return out.append(body).toString();
  }

  /**
   * The foreign key column of a reference to {@code target}, named like syncClassesToErd names it:
   * the target's key (maKhachHang), or target + Id when the key is id or missing (phongHatId).
   */
  static String joinColumn(String target, List<CgClass> all) {
    for (CgClass c : all) {
      if (c.name.equals(target)) {
        for (CgField f : c.fields) {
          if (f.key && !f.name.equalsIgnoreCase("id")) {
            return f.name;
          }
        }
      }
    }
    return target.isEmpty()
        ? "id"
        : Character.toLowerCase(target.charAt(0)) + target.substring(1) + "Id";
  }

  private static int keyCount(CgClass c) {
    int n = 0;
    for (CgField f : c.fields) {
      if (f.key && !f.isStatic) {
        n++;
      }
    }
    return n;
  }

  /** The id class of a composite key: the key fields, equals and hashCode. */
  private static void appendIdClass(StringBuilder b, CgClass c, Set<String> imports) {
    imports.add("java.io.Serializable");
    imports.add("java.util.Objects");
    List<String> names = new ArrayList<>();
    b.append("\n  /** Composite primary key of ").append(c.name).append(". */\n");
    b.append("  public static class Key implements Serializable {\n");
    for (CgField f : c.fields) {
      if (f.key && !f.isStatic) {
        b.append("    private ")
            .append(javaType(f.type, false, imports))
            .append(' ')
            .append(f.name)
            .append(";\n");
        names.add(f.name);
      }
    }
    b.append("\n    public Key() {}\n\n    @Override\n    public boolean equals(Object o) {\n");
    b.append("      if (this == o) {\n        return true;\n      }\n");
    b.append("      if (!(o instanceof Key)) {\n        return false;\n      }\n");
    b.append("      Key k = (Key) o;\n      return ");
    List<String> eq = new ArrayList<>();
    for (String n : names) {
      eq.add("Objects.equals(" + n + ", k." + n + ")");
    }
    b.append(String.join("\n          && ", eq)).append(";\n    }\n\n");
    b.append("    @Override\n    public int hashCode() {\n      return Objects.hash(")
        .append(String.join(", ", names))
        .append(");\n    }\n  }\n");
  }

  private static void appendJavaAccessors(StringBuilder b, String type, String name, boolean ro) {
    String cap = capitalize(name);
    String get = (type.equals("boolean") ? "is" : "get") + cap;
    b.append("\n  public ")
        .append(type)
        .append(' ')
        .append(get)
        .append("() {\n    return ")
        .append(name)
        .append(";\n  }\n");
    if (!ro) {
      b.append("\n  public void set")
          .append(cap)
          .append('(')
          .append(type)
          .append(' ')
          .append(name)
          .append(") {\n    this.")
          .append(name)
          .append(" = ")
          .append(name)
          .append(";\n  }\n");
    }
  }

  private static String javaType(String uml, boolean many, Set<String> imports) {
    String t = baseType(uml);
    String mapped;
    switch (t.toLowerCase(Locale.ROOT)) {
      case "":
      case "string":
      case "str":
      case "text":
        mapped = "String";
        break;
      case "int":
      case "integer":
        mapped = many ? "Integer" : "int";
        break;
      case "long":
        mapped = many ? "Long" : "long";
        break;
      case "float":
        mapped = many ? "Float" : "float";
        break;
      case "double":
        mapped = many ? "Double" : "double";
        break;
      case "boolean":
      case "bool":
        mapped = many ? "Boolean" : "boolean";
        break;
      case "char":
        mapped = many ? "Character" : "char";
        break;
      case "decimal":
      case "bigdecimal":
      case "money":
        mapped = "BigDecimal";
        imports.add("java.math.BigDecimal");
        break;
      case "date":
      case "localdate":
        mapped = "LocalDate";
        imports.add("java.time.LocalDate");
        break;
      case "datetime":
      case "localdatetime":
      case "timestamp":
        mapped = "LocalDateTime";
        imports.add("java.time.LocalDateTime");
        break;
      case "time":
      case "localtime":
        mapped = "LocalTime";
        imports.add("java.time.LocalTime");
        break;
      case "void":
        mapped = "void";
        break;
      default:
        mapped = t;
        break;
    }
    if (many || isCollection(uml)) {
      imports.add("java.util.List");
      return "List<" + boxed(mapped) + ">";
    }
    return mapped;
  }

  private static String boxed(String t) {
    switch (t) {
      case "int":
        return "Integer";
      case "char":
        return "Character";
      case "long":
      case "float":
      case "double":
      case "boolean":
        return capitalize(t);
      default:
        return t;
    }
  }

  private static String javaDefault(String type) {
    switch (type) {
      case "int":
      case "long":
      case "char":
        return "0";
      case "float":
        return "0f";
      case "double":
        return "0.0";
      case "boolean":
        return "false";
      default:
        return "null";
    }
  }

  // --- C# ---

  private static String csharp(String ns, CgClass c, boolean properties) {
    StringBuilder b = new StringBuilder();
    Set<String> usings = new LinkedHashSet<>();
    String ind = ns.isEmpty() ? "" : "    ";
    String kind =
        c.kind.equals("interface") ? "interface" : c.kind.equals("enum") ? "enum" : "class";
    b.append(ind).append("public ");
    if (c.isAbstract && kind.equals("class")) {
      b.append("abstract ");
    }
    b.append(kind).append(' ').append(c.name);
    List<String> bases = new ArrayList<>();
    if (c.parent != null && !kind.equals("enum")) {
      bases.add(c.parent);
    }
    bases.addAll(c.interfaces);
    if (!bases.isEmpty()) {
      b.append(" : ").append(String.join(", ", bases));
    }
    b.append('\n').append(ind).append("{\n");
    String in = ind + "    ";
    if (kind.equals("enum")) {
      b.append(in).append(String.join(",\n" + in, c.literals)).append('\n');
    }
    for (CgField f : c.fields) {
      String type = csType(f.type, f.many, usings);
      String name = properties || kind.equals("interface") ? capitalize(f.name) : f.name;
      b.append(in);
      if (kind.equals("interface")) {
        b.append(type).append(' ').append(name).append(" { get; set; }\n");
        continue;
      }
      b.append(properties ? "public" : csVisibility(f.visibility)).append(' ');
      if (f.isStatic) {
        b.append("static ");
      }
      if (f.isFinal && !properties) {
        b.append("readonly ");
      }
      b.append(type).append(' ').append(name);
      if (properties) {
        b.append(f.isFinal ? " { get; }" : " { get; set; }");
      }
      if (!f.initial.isEmpty()) {
        b.append(" = ").append(f.initial).append(';');
      } else if (f.many) {
        b.append(" = new List<").append(csType(f.type, false, usings)).append(">();");
      } else if (!properties) {
        b.append(';');
      }
      b.append('\n');
    }
    for (CgOp op : c.operations) {
      String ret = op.returnType.isEmpty() ? "void" : csType(op.returnType, false, usings);
      List<String> ps = new ArrayList<>();
      for (String[] p : op.params) {
        ps.add(csType(p[1], false, usings) + " " + p[0]);
      }
      b.append('\n').append(in);
      if (!kind.equals("interface")) {
        b.append(csVisibility(op.visibility.isEmpty() ? "public" : op.visibility)).append(' ');
        if (op.isStatic) {
          b.append("static ");
        }
        if (op.isAbstract) {
          b.append("abstract ");
        }
      }
      b.append(ret)
          .append(' ')
          .append(capitalize(op.name))
          .append('(')
          .append(String.join(", ", ps))
          .append(')');
      if (kind.equals("interface") || op.isAbstract) {
        b.append(";\n");
      } else {
        b.append('\n')
            .append(in)
            .append("{\n")
            .append(in)
            .append("    throw new System.NotImplementedException();\n")
            .append(in)
            .append("}\n");
      }
    }
    b.append(ind).append("}\n");
    StringBuilder out = new StringBuilder();
    for (String u : usings) {
      out.append("using ").append(u).append(";\n");
    }
    if (!usings.isEmpty()) {
      out.append('\n');
    }
    if (!ns.isEmpty()) {
      out.append("namespace ").append(ns).append("\n{\n").append(b).append("}\n");
    } else {
      out.append(b);
    }
    return out.toString();
  }

  private static String csVisibility(String v) {
    return v.equals("package") ? "internal" : v.isEmpty() ? "private" : v;
  }

  private static String csType(String uml, boolean many, Set<String> usings) {
    String t = baseType(uml);
    String mapped;
    switch (t.toLowerCase(Locale.ROOT)) {
      case "":
      case "string":
      case "str":
      case "text":
        mapped = "string";
        break;
      case "int":
      case "integer":
        mapped = "int";
        break;
      case "long":
        mapped = "long";
        break;
      case "float":
        mapped = "float";
        break;
      case "double":
        mapped = "double";
        break;
      case "boolean":
      case "bool":
        mapped = "bool";
        break;
      case "char":
        mapped = "char";
        break;
      case "decimal":
      case "bigdecimal":
      case "money":
        mapped = "decimal";
        break;
      case "date":
      case "localdate":
      case "datetime":
      case "localdatetime":
      case "timestamp":
        mapped = "DateTime";
        usings.add("System");
        break;
      case "time":
      case "localtime":
        mapped = "TimeSpan";
        usings.add("System");
        break;
      case "void":
        mapped = "void";
        break;
      default:
        mapped = t;
        break;
    }
    if (many || isCollection(uml)) {
      usings.add("System.Collections.Generic");
      return "List<" + mapped + ">";
    }
    return mapped;
  }

  // --- Python ---

  private static String python(CgClass c, List<CgClass> all) {
    StringBuilder b = new StringBuilder();
    Set<String> imports = new LinkedHashSet<>();
    Set<String> local = new LinkedHashSet<>();
    if (c.kind.equals("enum")) {
      b.append("from enum import Enum\n\n\nclass ").append(c.name).append("(Enum):\n");
      int i = 1;
      for (String l : c.literals) {
        b.append("    ").append(l).append(" = ").append(i++).append('\n');
      }
      if (c.literals.isEmpty()) {
        b.append("    pass\n");
      }
      return b.toString();
    }
    List<String> bases = new ArrayList<>();
    if (c.parent != null) {
      bases.add(c.parent);
      local.add(c.parent);
    }
    bases.addAll(c.interfaces);
    local.addAll(c.interfaces);
    boolean abc = c.kind.equals("interface") || c.isAbstract;
    if (abc && bases.isEmpty()) {
      bases.add("ABC");
    }
    if (abc) {
      imports.add("from abc import ABC, abstractmethod");
    }
    b.append("class ").append(c.name);
    if (!bases.isEmpty()) {
      b.append('(').append(String.join(", ", bases)).append(')');
    }
    b.append(":\n");
    boolean empty = true;
    for (CgField f : c.fields) {
      if (f.isStatic) {
        b.append("    ")
            .append(f.name)
            .append(": ")
            .append(pyType(f.type, f.many, imports))
            .append(" = ")
            .append(f.initial.isEmpty() ? "None" : f.initial)
            .append('\n');
        empty = false;
      }
    }
    List<CgField> inst = new ArrayList<>();
    for (CgField f : c.fields) {
      if (!f.isStatic) {
        inst.add(f);
      }
    }
    if (!inst.isEmpty()) {
      b.append("\n    def __init__(self");
      for (CgField f : inst) {
        if (!f.many) {
          b.append(", ")
              .append(snake(f.name))
              .append(": ")
              .append(pyOptional(pyType(f.type, false, imports)))
              .append(" = ")
              .append(f.initial.isEmpty() ? "None" : f.initial);
        }
      }
      b.append(") -> None:\n");
      if (c.parent != null) {
        b.append("        super().__init__()\n");
      }
      for (CgField f : inst) {
        String prefix = f.visibility.equals("private") ? "_" : "";
        b.append("        self.")
            .append(prefix)
            .append(snake(f.name))
            .append(": ")
            .append(
                f.many ? pyType(f.type, true, imports) : pyOptional(pyType(f.type, false, imports)))
            .append(" = ")
            .append(f.many ? "[]" : snake(f.name))
            .append('\n');
      }
      empty = false;
    }
    for (CgOp op : c.operations) {
      b.append('\n');
      if (op.isStatic) {
        b.append("    @staticmethod\n");
      } else if (abc && (op.isAbstract || c.kind.equals("interface"))) {
        b.append("    @abstractmethod\n");
      }
      b.append("    def ").append(snake(op.name)).append('(');
      List<String> ps = new ArrayList<>();
      if (!op.isStatic) {
        ps.add("self");
      }
      for (String[] p : op.params) {
        ps.add(snake(p[0]) + ": " + pyType(p[1], false, imports));
      }
      b.append(String.join(", ", ps))
          .append(") -> ")
          .append(op.returnType.isEmpty() ? "None" : pyType(op.returnType, false, imports))
          .append(":\n        raise NotImplementedError\n");
      empty = false;
    }
    if (empty) {
      b.append("    pass\n");
    }
    for (CgField f : c.fields) {
      local.add(baseType(f.type));
    }
    StringBuilder out = new StringBuilder("from __future__ import annotations\n\n");
    for (String imp : imports) {
      out.append(imp).append('\n');
    }
    for (CgClass other : all) {
      if (!other.name.equals(c.name)
          && local.contains(other.name)
          && (other.name.equals(c.parent) || c.interfaces.contains(other.name))) {
        out.append("from .")
            .append(snake(other.name))
            .append(" import ")
            .append(other.name)
            .append('\n');
      }
    }
    return out.append("\n\n").append(b).toString();
  }

  private static String pyOptional(String t) {
    return t + " | None";
  }

  private static String pyType(String uml, boolean many, Set<String> imports) {
    String t = baseType(uml);
    String mapped;
    switch (t.toLowerCase(Locale.ROOT)) {
      case "":
      case "string":
      case "str":
      case "text":
      case "char":
        mapped = "str";
        break;
      case "int":
      case "integer":
      case "long":
        mapped = "int";
        break;
      case "float":
      case "double":
        mapped = "float";
        break;
      case "boolean":
      case "bool":
        mapped = "bool";
        break;
      case "decimal":
      case "bigdecimal":
      case "money":
        mapped = "Decimal";
        imports.add("from decimal import Decimal");
        break;
      case "date":
      case "localdate":
        mapped = "date";
        imports.add("from datetime import date");
        break;
      case "datetime":
      case "localdatetime":
      case "timestamp":
        mapped = "datetime";
        imports.add("from datetime import datetime");
        break;
      case "void":
        mapped = "None";
        break;
      default:
        mapped = t;
        break;
    }
    return many || isCollection(uml) ? "list[" + mapped + "]" : mapped;
  }

  // --- TypeScript ---

  private static String typescript(CgClass c, List<CgClass> all) {
    StringBuilder b = new StringBuilder();
    Set<String> refs = new LinkedHashSet<>();
    if (c.kind.equals("enum")) {
      b.append("export enum ").append(c.name).append(" {\n");
      for (String l : c.literals) {
        b.append("  ").append(l).append(",\n");
      }
      return b.append("}\n").toString();
    }
    boolean iface = c.kind.equals("interface");
    b.append("export ");
    if (c.isAbstract && !iface) {
      b.append("abstract ");
    }
    b.append(iface ? "interface " : "class ").append(c.name);
    if (c.parent != null) {
      b.append(" extends ").append(c.parent);
      refs.add(c.parent);
    }
    if (!c.interfaces.isEmpty()) {
      b.append(iface ? (c.parent == null ? " extends " : ", ") : " implements ")
          .append(String.join(", ", c.interfaces));
      refs.addAll(c.interfaces);
    }
    b.append(" {\n");
    for (CgField f : c.fields) {
      String type = tsType(f.type, f.many);
      refs.add(baseType(f.type));
      b.append("  ");
      if (!iface) {
        b.append(f.visibility.equals("package") || f.visibility.isEmpty() ? "public" : f.visibility)
            .append(' ');
        if (f.isStatic) {
          b.append("static ");
        }
      }
      if (f.isFinal) {
        b.append("readonly ");
      }
      b.append(f.name);
      if (iface) {
        b.append(": ").append(type).append(";\n");
      } else if (!f.initial.isEmpty()) {
        b.append(": ").append(type).append(" = ").append(f.initial).append(";\n");
      } else if (f.many) {
        b.append(": ").append(type).append(" = [];\n");
      } else {
        b.append("?: ").append(type).append(";\n");
      }
    }
    for (CgOp op : c.operations) {
      List<String> ps = new ArrayList<>();
      for (String[] p : op.params) {
        ps.add(p[0] + ": " + tsType(p[1], false));
        refs.add(baseType(p[1]));
      }
      String ret = op.returnType.isEmpty() ? "void" : tsType(op.returnType, false);
      refs.add(baseType(op.returnType));
      b.append("\n  ");
      if (!iface) {
        b.append(
                op.visibility.equals("package") || op.visibility.isEmpty()
                    ? "public"
                    : op.visibility)
            .append(' ');
        if (op.isStatic) {
          b.append("static ");
        }
        if (op.isAbstract) {
          b.append("abstract ");
        }
      }
      b.append(op.name).append('(').append(String.join(", ", ps)).append("): ").append(ret);
      if (iface || op.isAbstract) {
        b.append(";\n");
      } else {
        b.append(" {\n    throw new Error(\"Not implemented\");\n  }\n");
      }
    }
    b.append("}\n");
    StringBuilder out = new StringBuilder();
    for (CgClass other : all) {
      if (!other.name.equals(c.name) && refs.contains(other.name)) {
        out.append("import ")
            .append(other.kind.equals("interface") ? "type " : "")
            .append("{ ")
            .append(other.name)
            .append(" } from \"./")
            .append(other.name)
            .append("\";\n");
      }
    }
    if (out.length() > 0) {
      out.append('\n');
    }
    return out.append(b).toString();
  }

  private static String tsType(String uml, boolean many) {
    String t = baseType(uml);
    String mapped;
    switch (t.toLowerCase(Locale.ROOT)) {
      case "":
      case "string":
      case "str":
      case "text":
      case "char":
        mapped = "string";
        break;
      case "int":
      case "integer":
      case "long":
      case "float":
      case "double":
      case "decimal":
      case "bigdecimal":
      case "money":
        mapped = "number";
        break;
      case "boolean":
      case "bool":
        mapped = "boolean";
        break;
      case "date":
      case "localdate":
      case "datetime":
      case "localdatetime":
      case "timestamp":
        mapped = "Date";
        break;
      case "void":
        mapped = "void";
        break;
      default:
        mapped = t;
        break;
    }
    return many || isCollection(uml) ? mapped + "[]" : mapped;
  }

  // --- PHP ---

  private static String php(String ns, CgClass c, boolean accessors) {
    StringBuilder b = new StringBuilder("<?php\n\n");
    if (!ns.isEmpty()) {
      b.append("namespace ").append(ns.replace('.', '\\')).append(";\n\n");
    }
    if (c.kind.equals("enum")) {
      b.append("enum ").append(c.name).append("\n{\n");
      for (String l : c.literals) {
        b.append("    case ").append(l).append(";\n");
      }
      return b.append("}\n").toString();
    }
    boolean iface = c.kind.equals("interface");
    if (c.isAbstract && !iface) {
      b.append("abstract ");
    }
    b.append(iface ? "interface " : "class ").append(c.name);
    if (c.parent != null) {
      b.append(" extends ").append(c.parent);
    }
    if (!c.interfaces.isEmpty()) {
      b.append(iface ? " extends " : " implements ").append(String.join(", ", c.interfaces));
    }
    b.append("\n{\n");
    if (!iface) {
      for (CgField f : c.fields) {
        String type = phpType(f.type, f.many);
        b.append("    ")
            .append(
                f.visibility.equals("package") || f.visibility.isEmpty() ? "private" : f.visibility)
            .append(' ');
        if (f.isStatic) {
          b.append("static ");
        }
        b.append(f.many || type.equals("mixed") ? type : "?" + type)
            .append(" $")
            .append(f.name)
            .append(" = ")
            .append(f.initial.isEmpty() ? (f.many ? "[]" : "null") : f.initial)
            .append(";\n");
      }
      if (accessors) {
        for (CgField f : c.fields) {
          if (f.isStatic) {
            continue;
          }
          String type = phpType(f.type, f.many);
          String nt = f.many || type.equals("mixed") ? type : "?" + type;
          b.append("\n    public function get")
              .append(capitalize(f.name))
              .append("(): ")
              .append(nt)
              .append("\n    {\n        return $this->")
              .append(f.name)
              .append(";\n    }\n");
          if (!f.isFinal) {
            b.append("\n    public function set")
                .append(capitalize(f.name))
                .append('(')
                .append(nt)
                .append(" $")
                .append(f.name)
                .append("): void\n    {\n        $this->")
                .append(f.name)
                .append(" = $")
                .append(f.name)
                .append(";\n    }\n");
          }
        }
      }
    }
    for (CgOp op : c.operations) {
      List<String> ps = new ArrayList<>();
      for (String[] p : op.params) {
        ps.add(phpType(p[1], false) + " $" + p[0]);
      }
      String ret = op.returnType.isEmpty() ? "void" : phpType(op.returnType, false);
      b.append("\n    ");
      if (op.isAbstract && !iface) {
        b.append("abstract ");
      }
      b.append(
              op.visibility.equals("package") || op.visibility.isEmpty() || iface
                  ? "public"
                  : op.visibility)
          .append(' ');
      if (op.isStatic) {
        b.append("static ");
      }
      b.append("function ")
          .append(op.name)
          .append('(')
          .append(String.join(", ", ps))
          .append("): ")
          .append(ret);
      if (iface || op.isAbstract) {
        b.append(";\n");
      } else {
        b.append("\n    {\n        throw new \\LogicException('Not implemented');\n    }\n");
      }
    }
    return b.append("}\n").toString();
  }

  private static String phpType(String uml, boolean many) {
    if (many || isCollection(uml)) {
      return "array";
    }
    String t = baseType(uml);
    switch (t.toLowerCase(Locale.ROOT)) {
      case "":
      case "string":
      case "str":
      case "text":
      case "char":
      case "decimal":
      case "bigdecimal":
      case "money":
        return "string";
      case "int":
      case "integer":
      case "long":
        return "int";
      case "float":
      case "double":
        return "float";
      case "boolean":
      case "bool":
        return "bool";
      case "date":
      case "localdate":
      case "datetime":
      case "localdatetime":
      case "timestamp":
        return "\\DateTimeImmutable";
      case "void":
        return "void";
      default:
        return t;
    }
  }

  // --- helpers ---

  private static boolean isCollection(String uml) {
    String t = uml == null ? "" : uml.trim();
    return t.endsWith("[]") || t.matches("(?i)(list|set|collection|array)<.*>");
  }

  /**
   * The element type of a UML type: {@code List<Order>} and {@code Order[]} give {@code Order}.
   *
   * @param uml type text
   * @return element type
   */
  static String baseType(String uml) {
    String t = uml == null ? "" : uml.trim();
    if (t.endsWith("[]")) {
      return t.substring(0, t.length() - 2).trim();
    }
    if (t.matches("(?i)(list|set|collection|array)<.*>")) {
      return t.substring(t.indexOf('<') + 1, t.length() - 1).trim();
    }
    return t;
  }

  private static String capitalize(String s) {
    return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * snake_case of a name: {@code OrderLine} gives {@code order_line}.
   *
   * @param s name
   * @return snake case
   */
  static String snake(String s) {
    return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replaceAll("[^A-Za-z0-9_]", "_")
        .toLowerCase(Locale.ROOT);
  }
}
