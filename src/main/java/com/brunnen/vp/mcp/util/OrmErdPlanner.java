package com.brunnen.vp.mcp.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Plans the entity relationship model of a class model, the way Visual Paradigm's "Synchronize to
 * Entity Relationship Diagram" does: only classes with the {@code ORM Persistable} stereotype
 * become tables. Attributes become columns, an attribute named {@code id} (or {@code <class>Id}, or
 * one with a {@code PK}/{@code id} stereotype) is the primary key, otherwise an {@code ID} column
 * is added. A one-to-many association puts a foreign key into the many side, a one-to-one one into
 * the optional (or the target) side, a many-to-many one becomes a join table, and a generalization
 * between persistable classes makes the subclass table share the key of its superclass (table per
 * subclass). Everything that cannot be mapped is reported as a warning instead of guessed silently.
 *
 * <p>Pure Java: the tool reads the Visual Paradigm model into {@link ClassSpec}s and applies the
 * returned {@link Plan}.
 */
public final class OrmErdPlanner {

  /** The stereotype that marks a class as persistable. */
  public static final String PERSISTABLE = "ORM Persistable";

  private OrmErdPlanner() {}

  /** A class of the class diagram. */
  public static final class ClassSpec {
    final String name;
    final boolean persistable;
    final List<AttrSpec> attributes = new ArrayList<>();
    String parent;

    /**
     * A class.
     *
     * @param name class name
     * @param persistable whether it carries the ORM Persistable stereotype
     */
    public ClassSpec(String name, boolean persistable) {
      this.name = name;
      this.persistable = persistable;
    }

    /**
     * Add an attribute.
     *
     * @param attrName name
     * @param type UML type ("" when untyped)
     * @param multiplicity UML multiplicity ("" = 1)
     * @param stereotypes stereotypes of the attribute
     * @return this
     */
    public ClassSpec attr(
        String attrName, String type, String multiplicity, String... stereotypes) {
      attributes.add(new AttrSpec(attrName, type, multiplicity, stereotypes));
      return this;
    }

    /**
     * Set the superclass (generalization parent).
     *
     * @param parentName parent class name
     * @return this
     */
    public ClassSpec parent(String parentName) {
      this.parent = parentName;
      return this;
    }
  }

  /** An attribute of a class. */
  public static final class AttrSpec {
    final String name;
    final String type;
    final String multiplicity;
    final Set<String> stereotypes = new HashSet<>();

    AttrSpec(String name, String type, String multiplicity, String... stereotypes) {
      this.name = name;
      this.type = type == null ? "" : type.trim();
      this.multiplicity = multiplicity == null ? "" : multiplicity.trim();
      for (String s : stereotypes) {
        this.stereotypes.add(s.toLowerCase(Locale.ROOT));
      }
    }
  }

  /** An association (aggregation and composition included) between two classes. */
  public static final class AssocSpec {
    final String fromClass;
    final String toClass;
    final String fromMult;
    final String toMult;
    final String name;

    /**
     * An association.
     *
     * @param fromClass class at the from end
     * @param fromMult multiplicity of the from end ("" = unspecified)
     * @param toClass class at the to end
     * @param toMult multiplicity of the to end ("" = unspecified)
     * @param name association name ("" when unnamed)
     */
    public AssocSpec(
        String fromClass, String fromMult, String toClass, String toMult, String name) {
      this.fromClass = fromClass;
      this.toClass = toClass;
      this.fromMult = fromMult == null ? "" : fromMult.trim();
      this.toMult = toMult == null ? "" : toMult.trim();
      this.name = name == null ? "" : name.trim();
    }
  }

  /** A planned column. */
  public static final class Column {
    public final String name;
    public final String type;
    public final int length;
    public final int scale;
    public final boolean primaryKey;
    public final boolean nullable;

    /** Attribute the column comes from, or null for a generated one. */
    public final String attribute;

    Column(String name, String type, int length, int scale, boolean pk, String attribute) {
      this.name = name;
      this.type = type;
      this.length = length;
      this.scale = scale;
      this.primaryKey = pk;
      this.nullable = !pk;
      this.attribute = attribute;
    }

    @Override
    public String toString() {
      return name
          + " "
          + type
          + (length > 0 ? "(" + length + (scale > 0 ? "," + scale : "") + ")" : "")
          + (primaryKey ? " PK" : "");
    }
  }

  /** A planned table. */
  public static final class Table {
    public final String name;

    /** Class the table comes from, or null for a join table. */
    public final String className;

    public final List<Column> columns = new ArrayList<>();

    Table(String name, String className) {
      this.name = name;
      this.className = className;
    }

    /**
     * Whether the table has a primary key column.
     *
     * @return true when one column is the key
     */
    public boolean hasPrimaryKey() {
      return columns.stream().anyMatch(c -> c.primaryKey);
    }
  }

  /** A planned foreign key: {@code child} gets a column referencing the key of {@code parent}. */
  public static final class ForeignKey {
    public final String parent;
    public final String child;
    public final String fromMultiplicity;
    public final String toMultiplicity;
    public final boolean identifying;

    /** Name for the FK column, or null to keep Visual Paradigm's default. */
    public final String columnName;

    /** What the key maps: association, generalization or many-to-many. */
    public final String source;

    ForeignKey(
        String parent,
        String child,
        String fromMultiplicity,
        String toMultiplicity,
        boolean identifying,
        String columnName,
        String source) {
      this.parent = parent;
      this.child = child;
      this.fromMultiplicity = fromMultiplicity;
      this.toMultiplicity = toMultiplicity;
      this.identifying = identifying;
      this.columnName = columnName;
      this.source = source;
    }

    @Override
    public String toString() {
      return parent + " -> " + child + (identifying ? " (identifying)" : "") + " [" + source + "]";
    }
  }

  /** The planned model: tables in creation order (referenced first), foreign keys, warnings. */
  public static final class Plan {
    public final List<Table> tables = new ArrayList<>();
    public final List<ForeignKey> foreignKeys = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    /**
     * The planned table of a class or join table name.
     *
     * @param name table name
     * @return the table or null
     */
    public Table table(String name) {
      for (Table t : tables) {
        if (t.name.equals(name)) {
          return t;
        }
      }
      return null;
    }
  }

  /**
   * Plan the tables of the persistable classes.
   *
   * @param classes the classes of the diagram
   * @param associations the associations between them
   * @return the plan
   */
  public static Plan plan(List<ClassSpec> classes, List<AssocSpec> associations) {
    return plan(classes, associations, java.util.Collections.emptyMap());
  }

  /**
   * Plan the tables of the persistable classes with chosen primary keys.
   *
   * @param classes the classes of the diagram
   * @param associations the associations between them
   * @param keys chosen key attributes per class name (see {@link #parseKeys}); a class without an
   *     entry gets its key by name (id, {@code <class>Id}, {@code ma<Class>}, {@code <<PK>>})
   * @return the plan
   */
  public static Plan plan(
      List<ClassSpec> classes, List<AssocSpec> associations, Map<String, List<String>> keys) {
    Plan plan = new Plan();
    for (String cls : keys.keySet()) {
      ClassSpec spec = null;
      for (ClassSpec c : classes) {
        if (c.name.equals(cls)) {
          spec = c;
        }
      }
      if (spec == null) {
        plan.warnings.add("primaryKeys names the class '" + cls + "' that is not on the diagram");
        continue;
      }
      for (String attr : keys.get(cls)) {
        boolean found = false;
        for (AttrSpec a : spec.attributes) {
          found |= a.name.equals(attr);
        }
        if (!found) {
          plan.warnings.add(
              "primaryKeys: class '" + cls + "' has no attribute '" + attr + "': not a key");
        }
      }
    }
    Map<String, String> tableOf = new HashMap<>();
    for (ClassSpec c : classes) {
      if (c.persistable) {
        tableOf.put(c.name, uniqueName(identifier(c.name), new HashSet<>(tableOf.values())));
      } else {
        plan.warnings.add(
            "Class '"
                + c.name
                + "' has no <<"
                + PERSISTABLE
                + ">> stereotype: no table (add it with addStereotypeToClasses)");
      }
    }
    if (tableOf.isEmpty()) {
      return plan;
    }

    // superclasses before subclasses: the identifying key of a subclass needs the parent's key
    for (ClassSpec c : inheritanceOrder(classes)) {
      if (!c.persistable) {
        continue;
      }
      Table table = new Table(tableOf.get(c.name), c.name);
      boolean inheritsKey = c.parent != null && tableOf.containsKey(c.parent);
      if (inheritsKey && keys.containsKey(c.name)) {
        plan.warnings.add(
            "primaryKeys for '"
                + c.name
                + "' ignored: a subclass table shares the key of its superclass '"
                + c.parent
                + "'");
      }
      if (c.parent != null && !inheritsKey) {
        plan.warnings.add(
            "Superclass '"
                + c.parent
                + "' of '"
                + c.name
                + "' is not persistable: its attributes are not mapped");
      }
      Set<String> used = new HashSet<>();
      for (AttrSpec a : c.attributes) {
        if (tableOf.containsKey(stripCollection(a.type))) {
          plan.warnings.add(
              "Attribute "
                  + c.name
                  + "."
                  + a.name
                  + " has the persistable class type '"
                  + a.type
                  + "': model it as an association to get a foreign key; not mapped as a column");
          continue;
        }
        if (isMany(a.multiplicity) || a.type.endsWith("[]") || a.type.contains("<")) {
          plan.warnings.add(
              "Attribute "
                  + c.name
                  + "."
                  + a.name
                  + " is multi-valued ("
                  + (a.multiplicity.isEmpty() ? a.type : a.multiplicity)
                  + "): a column holds one value, model it as a class with an association");
          continue;
        }
        String colName = uniqueName(identifier(a.name), used);
        used.add(colName);
        String[] sql = sqlType(a.type);
        if (sql[3] != null) {
          plan.warnings.add(
              "Attribute "
                  + c.name
                  + "."
                  + a.name
                  + ": "
                  + sql[3]
                  + ", mapped to "
                  + sql[0]
                  + (Integer.parseInt(sql[1]) > 0 ? "(" + sql[1] + ")" : ""));
        }
        List<String> chosen = keys.get(c.name);
        boolean pk =
            !inheritsKey
                && (chosen != null && chosenExists(c, chosen)
                    ? chosen.contains(a.name)
                    : isKeyAttribute(c.name, a));
        table.columns.add(
            new Column(
                colName, sql[0], Integer.parseInt(sql[1]), Integer.parseInt(sql[2]), pk, a.name));
      }
      if (!inheritsKey && !table.hasPrimaryKey()) {
        String id = uniqueName("ID", used);
        table.columns.add(0, new Column(id, "integer", 0, 0, true, null));
        plan.warnings.add(
            "Class '"
                + c.name
                + "' has no key attribute (choose one with primaryKeys, or name it id, "
                + lowerFirst(c.name)
                + "Id or <<PK>>): added primary key column "
                + table.name
                + "."
                + id);
      }
      plan.tables.add(table);
      if (inheritsKey) {
        plan.foreignKeys.add(
            new ForeignKey(
                tableOf.get(c.parent), table.name, "1", "0..1", true, null, "generalization"));
      }
    }

    for (AssocSpec as : associations) {
      String label = as.fromClass + " -- " + as.toClass;
      boolean fromP = tableOf.containsKey(as.fromClass);
      boolean toP = tableOf.containsKey(as.toClass);
      if (!fromP || !toP) {
        if (fromP || toP) {
          plan.warnings.add(
              "Association "
                  + label
                  + " reaches the non-persistable class '"
                  + (fromP ? as.toClass : as.fromClass)
                  + "': no foreign key");
        }
        continue;
      }
      if (as.fromMult.isEmpty() || as.toMult.isEmpty()) {
        plan.warnings.add(
            "Association "
                + label
                + " has no multiplicity at "
                + (as.fromMult.isEmpty() && as.toMult.isEmpty()
                    ? "either end"
                    : (as.fromMult.isEmpty() ? as.fromClass : as.toClass))
                + ": taken as 1");
      }
      boolean fromMany = isMany(as.fromMult);
      boolean toMany = isMany(as.toMult);
      String fromTable = tableOf.get(as.fromClass);
      String toTable = tableOf.get(as.toClass);
      if (fromMany && toMany) {
        Set<String> names = new HashSet<>(tableOf.values());
        for (Table t : plan.tables) {
          names.add(t.name);
        }
        String join =
            uniqueName(as.name.isEmpty() ? fromTable + "_" + toTable : identifier(as.name), names);
        plan.tables.add(new Table(join, null));
        plan.foreignKeys.add(
            new ForeignKey(fromTable, join, "1", "0..*", true, null, "many-to-many"));
        plan.foreignKeys.add(
            new ForeignKey(toTable, join, "1", "0..*", true, null, "many-to-many"));
      } else if (fromMany || toMany) {
        // the many side holds the key of the one side
        String parent = fromMany ? toTable : fromTable;
        String child = fromMany ? fromTable : toTable;
        String oneMult = fromMany ? as.toMult : as.fromMult;
        String manyMult = fromMany ? as.fromMult : as.toMult;
        plan.foreignKeys.add(
            new ForeignKey(
                parent,
                child,
                oneMult.startsWith("0") ? "0..1" : "1",
                manyMult.startsWith("1") ? "1..*" : "0..*",
                false,
                null,
                "association"));
      } else {
        // one-to-one: the optional side (else the to side) holds the key
        boolean fromOptional = as.fromMult.startsWith("0") && !as.toMult.startsWith("0");
        String parent = fromOptional ? toTable : fromTable;
        String child = fromOptional ? fromTable : toTable;
        String parentMult = fromOptional ? as.toMult : as.fromMult;
        String childMult = fromOptional ? as.fromMult : as.toMult;
        plan.foreignKeys.add(
            new ForeignKey(
                parent,
                child,
                parentMult.startsWith("0") ? "0..1" : "1",
                childMult.startsWith("0") ? "0..1" : "1",
                false,
                null,
                "association"));
      }
    }
    return plan;
  }

  /** Classes ordered so that every superclass comes before its subclasses. */
  private static List<ClassSpec> inheritanceOrder(List<ClassSpec> classes) {
    Map<String, ClassSpec> byName = new LinkedHashMap<>();
    for (ClassSpec c : classes) {
      byName.put(c.name, c);
    }
    List<ClassSpec> order = new ArrayList<>();
    Set<String> done = new HashSet<>();
    for (ClassSpec c : classes) {
      visit(c, byName, done, new HashSet<>(), order);
    }
    return order;
  }

  private static void visit(
      ClassSpec c,
      Map<String, ClassSpec> byName,
      Set<String> done,
      Set<String> path,
      List<ClassSpec> order) {
    if (done.contains(c.name) || !path.add(c.name)) {
      return; // visited, or a generalization cycle
    }
    ClassSpec parent = c.parent == null ? null : byName.get(c.parent);
    if (parent != null) {
      visit(parent, byName, done, path, order);
    }
    done.add(c.name);
    order.add(c);
  }

  /** Whether at least one chosen key attribute exists (else the class falls back to names). */
  private static boolean chosenExists(ClassSpec c, List<String> chosen) {
    for (AttrSpec a : c.attributes) {
      if (chosen.contains(a.name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Parse chosen primary keys: {@code "KhachHang=maKhachHang; ChiTiet=maDon,maMon"} or {@code
   * "KhachHang.maKhachHang, ChiTiet.maDon, ChiTiet.maMon"} (several attributes make a composite
   * key).
   *
   * @param text the choice ("" for none)
   * @return attributes per class, in the given order
   */
  public static Map<String, List<String>> parseKeys(String text) {
    Map<String, List<String>> keys = new LinkedHashMap<>();
    if (text == null || text.trim().isEmpty()) {
      return keys;
    }
    for (String group : text.split(";")) {
      String g = group.trim();
      if (g.isEmpty()) {
        continue;
      }
      int eq = g.indexOf('=');
      if (eq > 0) {
        String cls = g.substring(0, eq).trim();
        for (String attr : g.substring(eq + 1).split(",")) {
          if (!attr.trim().isEmpty()) {
            keys.computeIfAbsent(cls, k -> new ArrayList<>()).add(attr.trim());
          }
        }
        continue;
      }
      for (String pair : g.split(",")) {
        String p = pair.trim();
        int dot = p.lastIndexOf('.');
        if (dot <= 0 || dot == p.length() - 1) {
          throw new IllegalArgumentException(
              "primaryKeys entry '"
                  + p
                  + "' is not Class=attr1,attr2 or Class.attr (use ; between classes)");
        }
        keys.computeIfAbsent(p.substring(0, dot).trim(), k -> new ArrayList<>())
            .add(p.substring(dot + 1).trim());
      }
    }
    return keys;
  }

  private static boolean isKeyAttribute(String className, AttrSpec a) {
    return isKeyName(className, a.name, a.stereotypes.toArray(new String[0]));
  }

  /**
   * Whether an attribute is a class's key: a {@code PK} or {@code id} stereotype, or the name
   * {@code id}, {@code <class>Id}, {@code <class>_id} or {@code ma<Class>} (the Vietnamese "mã").
   *
   * @param className the class
   * @param attrName the attribute
   * @param stereotypes the attribute's stereotypes
   * @return true for the key
   */
  public static boolean isKeyName(String className, String attrName, String... stereotypes) {
    for (String st : stereotypes) {
      String v = st.trim().toLowerCase(Locale.ROOT);
      if (v.equals("pk") || v.equals("id")) {
        return true;
      }
    }
    String n = attrName == null ? "" : attrName.toLowerCase(Locale.ROOT);
    String c = className.toLowerCase(Locale.ROOT);
    return n.equals("id") || n.equals(c + "id") || n.equals(c + "_id") || n.equals("ma" + c);
  }

  /**
   * Whether a multiplicity allows more than one: {@code *}, {@code 0..*}, {@code 1..*}, {@code n},
   * {@code 2..5}.
   *
   * @param multiplicity UML multiplicity
   * @return true for a many end
   */
  public static boolean isMany(String multiplicity) {
    if (multiplicity == null || multiplicity.trim().isEmpty()) {
      return false;
    }
    String m = multiplicity.trim().toLowerCase(Locale.ROOT);
    String upper = m.contains("..") ? m.substring(m.indexOf("..") + 2) : m;
    if (upper.equals("*") || upper.equals("n") || upper.equals("m")) {
      return true;
    }
    try {
      return Integer.parseInt(upper) > 1;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static String stripCollection(String type) {
    String t = type.trim();
    if (t.endsWith("[]")) {
      t = t.substring(0, t.length() - 2);
    }
    int lt = t.indexOf('<');
    if (lt >= 0 && t.endsWith(">")) {
      t = t.substring(lt + 1, t.length() - 1);
    }
    return t.trim();
  }

  /**
   * The SQL type of a UML attribute type: {name, length, scale, note}. The note says why a type was
   * guessed (untyped or unknown), or is null.
   *
   * @param umlType UML/Java/C# type such as String, int, Date, decimal(10,2)
   * @return the SQL type
   */
  public static String[] sqlType(String umlType) {
    String t = umlType == null ? "" : umlType.trim();
    if (t.isEmpty()) {
      return new String[] {"varchar", "255", "0", "untyped"};
    }
    int open = t.indexOf('(');
    String base = (open > 0 ? t.substring(0, open) : t).trim().toLowerCase(Locale.ROOT);
    if (open > 0 && t.endsWith(")")) {
      String[] args = t.substring(open + 1, t.length() - 1).split(",");
      String l = args[0].trim();
      String s = args.length > 1 ? args[1].trim() : "0";
      if (l.matches("\\d+") && s.matches("\\d+")) {
        return new String[] {base, l, s, null};
      }
    }
    switch (base) {
      case "string":
      case "str":
      case "varchar":
      case "nvarchar":
      case "text":
        return new String[] {"varchar", "255", "0", null};
      case "char":
      case "character":
        return new String[] {"char", "1", "0", null};
      case "int":
      case "integer":
      case "short":
      case "byte":
      case "number":
        return new String[] {"integer", "0", "0", null};
      case "long":
      case "bigint":
        return new String[] {"bigint", "0", "0", null};
      case "float":
      case "real":
        return new String[] {"float", "0", "0", null};
      case "double":
        return new String[] {"double", "0", "0", null};
      case "decimal":
      case "bigdecimal":
      case "money":
      case "currency":
        return new String[] {"decimal", "19", "2", null};
      case "boolean":
      case "bool":
      case "bit":
        return new String[] {"boolean", "0", "0", null};
      case "date":
      case "localdate":
        return new String[] {"date", "0", "0", null};
      case "time":
      case "localtime":
        return new String[] {"time", "0", "0", null};
      case "datetime":
      case "localdatetime":
      case "timestamp":
      case "instant":
        return new String[] {"timestamp", "0", "0", null};
      case "byte[]":
      case "blob":
        return new String[] {"blob", "0", "0", null};
      default:
        return new String[] {"varchar", "255", "0", "unknown type '" + t + "'"};
    }
  }

  /**
   * A SQL identifier for a model name: diacritics removed (đ becomes d), anything else that is not
   * a letter, digit or underscore becomes an underscore, a leading digit gets an underscore.
   *
   * @param name model name
   * @return identifier
   */
  public static String identifier(String name) {
    String n = name == null ? "" : name.trim().replace('đ', 'd').replace('Đ', 'D');
    n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    n = n.replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    if (n.isEmpty()) {
      return "T";
    }
    return Character.isDigit(n.charAt(0)) ? "_" + n : n;
  }

  private static String uniqueName(String base, Set<String> used) {
    String name = base;
    int i = 2;
    while (used.contains(name)) {
      name = base + i++;
    }
    return name;
  }

  private static String lowerFirst(String s) {
    return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}
