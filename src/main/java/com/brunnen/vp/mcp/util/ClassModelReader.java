package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IClassDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IEnumerationLiteral;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IRealization;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the classes and relationships shown on a class diagram into the plain models of {@link
 * CodeGenerator} and {@link OrmErdPlanner}. A generalization and a realization are stored by Visual
 * Paradigm from the general (supplier) element to the specific one; the from end of an association
 * belongs to the from class.
 */
public final class ClassModelReader {

  private ClassModelReader() {}

  /** Classes, associations, generalizations and realizations shown on a diagram. */
  public static final class Shown {
    public final List<IClass> classes = new ArrayList<>();
    public final List<IAssociation> associations = new ArrayList<>();
    public final List<IGeneralization> generalizations = new ArrayList<>();
    public final List<IRealization> realizations = new ArrayList<>();
  }

  /**
   * The model elements shown on one or more class diagrams (an element shown twice counts once).
   *
   * @param diagrams the diagrams
   * @return the shown elements
   */
  public static Shown shown(IClassDiagramUIModel... diagrams) {
    Shown s = new Shown();
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (IClassDiagramUIModel diagram : diagrams) {
      Iterator<?> it = diagram.diagramElementIterator();
      while (it.hasNext()) {
        Object o = it.next();
        if (!(o instanceof IDiagramElement)) {
          continue;
        }
        IModelElement m = ((IDiagramElement) o).getModelElement();
        if (m == null || !seen.add(m.getId())) {
          continue;
        }
        if (m instanceof IClass) {
          s.classes.add((IClass) m);
        } else if (m instanceof IAssociation) {
          s.associations.add((IAssociation) m);
        } else if (m instanceof IGeneralization) {
          s.generalizations.add((IGeneralization) m);
        } else if (m instanceof IRealization) {
          s.realizations.add((IRealization) m);
        }
      }
    }
    return s;
  }

  /**
   * Whether a class carries the ORM Persistable stereotype.
   *
   * @param cls the class
   * @return true when persistable
   */
  public static boolean isPersistable(IClass cls) {
    for (String st : cls.toStereotypeArray()) {
      if (OrmErdPlanner.PERSISTABLE.equalsIgnoreCase(st.trim())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasStereotype(IClass cls, String name) {
    for (String st : cls.toStereotypeArray()) {
      if (name.equalsIgnoreCase(st.trim())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The ORM planner's view of the diagram.
   *
   * @param s shown elements
   * @param classesOut receives the class specs
   * @param assocsOut receives the association specs
   */
  public static void ormSpecs(
      Shown s, List<OrmErdPlanner.ClassSpec> classesOut, List<OrmErdPlanner.AssocSpec> assocsOut) {
    Map<String, OrmErdPlanner.ClassSpec> byId = new LinkedHashMap<>();
    for (IClass cls : s.classes) {
      if (hasStereotype(cls, "Interface") || hasStereotype(cls, "enumeration")) {
        continue;
      }
      OrmErdPlanner.ClassSpec spec = new OrmErdPlanner.ClassSpec(cls.getName(), isPersistable(cls));
      for (IAttribute a : cls.toAttributeArray()) {
        if (IAttribute.SCOPE_CLASSIFIER.equals(a.getScope())) {
          continue; // a static attribute is no column
        }
        spec.attr(
            a.getName(),
            typeOf(a.getTypeAsString()),
            mult(a.getMultiplicity()),
            a.toStereotypeArray());
      }
      byId.put(cls.getId(), spec);
      classesOut.add(spec);
    }
    for (IGeneralization g : s.generalizations) {
      if (g.getFrom() != null && g.getTo() != null && byId.containsKey(g.getTo().getId())) {
        byId.get(g.getTo().getId()).parent(g.getFrom().getName());
      }
    }
    for (IAssociation as : s.associations) {
      IAssociationEnd from = (IAssociationEnd) as.getFromEnd();
      IAssociationEnd to = (IAssociationEnd) as.getToEnd();
      if (as.getFrom() == null || as.getTo() == null || from == null || to == null) {
        continue;
      }
      assocsOut.add(
          new OrmErdPlanner.AssocSpec(
              as.getFrom().getName(),
              mult(from.getMultiplicity()),
              as.getTo().getName(),
              mult(to.getMultiplicity()),
              nz(as.getName())));
    }
  }

  /**
   * The code generator's view of the diagram.
   *
   * @param s shown elements
   * @param keys chosen key attributes per class name (empty: keys by name, see {@link
   *     OrmErdPlanner#isKeyName})
   * @return one class per class, interface and enumeration
   */
  public static List<CodeGenerator.CgClass> codeClasses(Shown s, Map<String, List<String>> keys) {
    Map<String, CodeGenerator.CgClass> byId = new LinkedHashMap<>();
    for (IClass cls : s.classes) {
      CodeGenerator.CgClass c = new CodeGenerator.CgClass(codeName(cls.getName()));
      if (hasStereotype(cls, "Interface")) {
        c.kind = "interface";
      } else if (hasStereotype(cls, "enumeration") || cls.enumerationLiteralCount() > 0) {
        c.kind = "enum";
      }
      c.isAbstract = cls.isAbstract();
      c.persistable = isPersistable(cls);
      if (c.kind.equals("enum")) {
        for (IEnumerationLiteral l : cls.toEnumerationLiteralArray()) {
          c.literals.add(codeName(l.getName()));
        }
      }
      for (IAttribute a : cls.toAttributeArray()) {
        if (c.kind.equals("enum") && isBlank(a.getTypeAsString())) {
          c.literals.add(codeName(a.getName())); // literals drawn as untyped attributes
          continue;
        }
        CodeGenerator.CgField f =
            new CodeGenerator.CgField(codeName(a.getName()), typeOf(a.getTypeAsString()));
        f.visibility = nz(a.getVisibility());
        f.isStatic = IAttribute.SCOPE_CLASSIFIER.equals(a.getScope());
        f.isFinal = a.isReadOnly();
        f.initial = nz(a.getInitialValueAsString());
        f.many = OrmErdPlanner.isMany(a.getMultiplicity());
        List<String> chosen = keys.get(cls.getName());
        f.key =
            chosen != null
                ? chosen.contains(a.getName())
                : OrmErdPlanner.isKeyName(cls.getName(), a.getName(), a.toStereotypeArray());
        c.fields.add(f);
      }
      for (IOperation op : cls.toOperationArray()) {
        CodeGenerator.CgOp o =
            new CodeGenerator.CgOp(codeName(op.getName()), typeOf(op.getReturnTypeAsString()));
        o.visibility = nz(op.getVisibility());
        o.isStatic = IOperation.SCOPE_CLASSIFIER.equals(op.getScope());
        o.isAbstract = op.isAbstract();
        for (IParameter p : op.toParameterArray()) {
          String type = typeOf(p.getTypeAsString());
          if (OrmErdPlanner.isMany(p.getMultiplicity())) {
            type = "List<" + type + ">";
          }
          o.params.add(new String[] {codeName(p.getName()), type});
        }
        c.operations.add(o);
      }
      byId.put(cls.getId(), c);
    }
    for (IGeneralization g : s.generalizations) {
      CodeGenerator.CgClass child = g.getTo() == null ? null : byId.get(g.getTo().getId());
      if (child != null && g.getFrom() != null) {
        String parent = codeName(g.getFrom().getName());
        CodeGenerator.CgClass p = byId.get(g.getFrom().getId());
        if (p != null && p.kind.equals("interface") && !child.kind.equals("interface")) {
          child.interfaces.add(parent);
        } else if (child.kind.equals("interface")) {
          child.interfaces.add(parent);
        } else if (child.parent == null) {
          child.parent = parent;
        }
      }
    }
    for (IRealization r : s.realizations) {
      CodeGenerator.CgClass client = r.getTo() == null ? null : byId.get(r.getTo().getId());
      if (client != null && r.getFrom() != null) {
        client.interfaces.add(codeName(r.getFrom().getName()));
      }
    }
    for (IAssociation as : s.associations) {
      IAssociationEnd fromEnd = (IAssociationEnd) as.getFromEnd();
      IAssociationEnd toEnd = (IAssociationEnd) as.getToEnd();
      if (as.getFrom() == null || as.getTo() == null || fromEnd == null || toEnd == null) {
        continue;
      }
      CodeGenerator.CgClass a = byId.get(as.getFrom().getId());
      CodeGenerator.CgClass b = byId.get(as.getTo().getId());
      CodeGenerator.CgField ab = endField(a, b, toEnd, fromEnd, as.getFrom().getName());
      CodeGenerator.CgField ba = endField(b, a, fromEnd, toEnd, as.getTo().getName());
      if (ab != null && ba != null && ab.relation != null) {
        // bidirectional: the one side of a one-to-many, else the from side, is the inverse one
        if ("OneToMany".equals(ba.relation)) {
          ba.mappedBy = ab.name;
        } else {
          ab.mappedBy = ba.name;
        }
      }
      // the owning side of a many-to-many names the join table syncClassesToErd creates
      String join =
          isBlank(as.getName())
              ? OrmErdPlanner.identifier(as.getFrom().getName())
                  + "_"
                  + OrmErdPlanner.identifier(as.getTo().getName())
              : OrmErdPlanner.identifier(as.getName());
      for (CodeGenerator.CgField f : new CodeGenerator.CgField[] {ab, ba}) {
        if (f != null && "ManyToMany".equals(f.relation) && f.mappedBy == null) {
          f.joinTable = join;
        }
      }
    }
    return new ArrayList<>(byId.values());
  }

  /**
   * The field of {@code owner} for the association end at {@code target}, added to the owner, or
   * null when the end is not navigable.
   */
  private static CodeGenerator.CgField endField(
      CodeGenerator.CgClass owner,
      CodeGenerator.CgClass target,
      IAssociationEnd targetEnd,
      IAssociationEnd ownerEnd,
      String ownerName) {
    if (owner == null
        || target == null
        || targetEnd.getNavigable() == IAssociationEnd.NAVIGABLE_NON_NAVIGABLE) {
      return null;
    }
    boolean many = OrmErdPlanner.isMany(targetEnd.getMultiplicity());
    boolean ownerMany = OrmErdPlanner.isMany(ownerEnd.getMultiplicity());
    String role = targetEnd.getName();
    if (isBlank(role)) {
      role = lowerFirst(target.name) + (many ? "s" : "");
    }
    String fieldName = codeName(role);
    for (CodeGenerator.CgField existing : owner.fields) {
      if (existing.name.equals(fieldName)) {
        return null; // already an attribute of that name
      }
    }
    CodeGenerator.CgField f = new CodeGenerator.CgField(fieldName, target.name);
    f.many = many;
    if (owner.persistable && target.persistable) {
      f.relation =
          many ? (ownerMany ? "ManyToMany" : "OneToMany") : (ownerMany ? "ManyToOne" : "OneToOne");
    }
    owner.fields.add(f);
    return f;
  }

  /** A UML type text, with VP's "void" and null cleaned up. */
  private static String typeOf(String type) {
    return type == null ? "" : type.trim();
  }

  /**
   * An identifier for code: diacritics removed, other characters dropped, spaces joining words in
   * camel case.
   *
   * @param name model name
   * @return identifier
   */
  public static String codeName(String name) {
    String n = name == null ? "" : name.trim().replace('đ', 'd').replace('Đ', 'D');
    n = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    StringBuilder b = new StringBuilder();
    boolean up = false;
    for (char ch : n.toCharArray()) {
      if (Character.isLetterOrDigit(ch) || ch == '_') {
        b.append(up ? Character.toUpperCase(ch) : ch);
        up = false;
      } else if (b.length() > 0) {
        up = true;
      }
    }
    if (b.length() == 0) {
      return "unnamed";
    }
    return Character.isDigit(b.charAt(0)) ? "_" + b : b.toString();
  }

  private static String lowerFirst(String s) {
    return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static String nz(String s) {
    return s == null ? "" : s.trim();
  }

  /** A multiplicity, "" when Visual Paradigm reports it unspecified. */
  private static String mult(String m) {
    String v = nz(m);
    return v.equalsIgnoreCase("Unspecified") ? "" : v;
  }
}
