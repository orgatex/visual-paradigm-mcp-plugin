package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.brunnen.vp.mcp.util.ObjectLayoutProfile;
import com.brunnen.vp.mcp.util.PackageLayoutProfile;
import com.brunnen.vp.mcp.util.PackageObjectDiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IObjectDiagramUIModel;
import com.vp.plugin.diagram.IShapeTypeConstants;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.connector.IArrowHead;
import com.vp.plugin.diagram.connector.IGenericConnectorUIModel;
import com.vp.plugin.diagram.shape.IClassUIModel;
import com.vp.plugin.diagram.shape.IInstanceSpecificationUIModel;
import com.vp.plugin.diagram.shape.IPackageUIModel;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.ICompositeValueSpecification;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInstanceSpecification;
import com.vp.plugin.model.ILink;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.ISimpleRelationship;
import com.vp.plugin.model.ISlot;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm UML package diagrams and object diagrams.
 *
 * <p>Package diagrams use real UML model elements: {@link IPackage} frames (nested in the model and
 * on the diagram when a parent package is given), {@link IClass} boxes owned by their package, and
 * stereotyped dependencies between packages — an {@code IUsage} for {@code <<use>>} and an {@code
 * IDependency} carrying the {@code <<import>>}, {@code <<access>>} or {@code <<merge>>} stereotype.
 *
 * <p>Object diagrams show {@link IInstanceSpecification} boxes classified by an {@link IClass}, so
 * Visual Paradigm renders the underlined {@code objectName : ClassName} caption itself; slot values
 * are {@link ISlot}s whose defining feature is the class attribute. Objects are joined by {@link
 * ILink} connectors.
 *
 * <p>Leaf shapes (class boxes, objects) are fitted to their content with Visual Paradigm's fit-size
 * API; package frames are containers sized around their children. Both diagram types are laid out
 * by the shared layered layout ({@link PackageLayoutProfile}, {@link ObjectLayoutProfile}): shapes
 * placed with x/y stay where they are, every connector is a horizontal or vertical line. All name
 * lookups are scoped to the given diagram.
 */
public class PackageObjectDiagramMcpTools extends AbstractDiagramMcpTools {

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    if (IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM.equals(diagram.getType())) {
      return new PackageLayoutProfile();
    }
    return IDiagramTypeConstants.DIAGRAM_TYPE_OBJECT_DIAGRAM.equals(diagram.getType())
        ? new ObjectLayoutProfile()
        : null;
  }

  /** Minimum size of a package frame; larger when the fitted label needs more room. */
  private static final int PACKAGE_MIN_WIDTH = 100;

  private static final int PACKAGE_MIN_HEIGHT = 50;

  /** Nesting depth limit when walking package parents or class generalizations. */
  private static final int MAX_DEPTH = 32;

  // --- Package diagram ---

  @Tool(
      name = "createPackageDiagram",
      description = "Create a new UML package diagram in Visual Paradigm")
  public String createPackageDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!PackageObjectDiagramUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM, diagramName.trim());
            diagram.setAutoFitShapesSize(true);
            dm.openDiagram(diagram);
            return "Created package diagram: " + diagramName.trim();
          });
    } catch (Exception e) {
      return "Error creating package diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addPackageElement",
      description =
          "Add a package frame to a package diagram. parentPackage (a package already on the "
              + "diagram, \"\" for none) nests it in that package, in the model and on the "
              + "diagram; the parent is sized around it. x/y (>0, diagram coordinates) place it "
              + "explicitly and keep it there (the lines are routed around it), otherwise the "
              + "layout decides: packages in layers by their dependencies (a package above the "
              + "ones it depends on), children inside their frame. width/height (>0) set the size "
              + "of an explicitly placed frame; a frame is always at least 100x50, wide enough "
              + "for its name and big enough for what it holds. backgroundColor is '#RRGGBB' for a "
              + "tint of its own (\"\" = the house blue). A package of that "
              + "name already in the project (root, or inside parentPackage) is reused: a new "
              + "view of it is drawn")
  public String addPackageElement(
      String diagramName,
      String packageName,
      String parentPackage,
      int x,
      int y,
      int width,
      int height,
      String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                findDiagramOfType(diagramName, IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM);
            if (diagram == null) {
              return diagramNotFound(diagramName, "package");
            }
            if (!PackageObjectDiagramUtils.isValidName(packageName)) {
              return "Package name must not be empty";
            }
            String name = packageName.trim();
            if (findShape(diagram, name, IPackage.class) != null) {
              return "Package already on diagram: " + name;
            }
            if (backgroundColor != null && !backgroundColor.trim().isEmpty()) {
              try {
                java.awt.Color.decode(backgroundColor.trim());
              } catch (NumberFormatException e) {
                return "Error adding package: backgroundColor must be #RRGGBB, got '"
                    + backgroundColor
                    + "'";
              }
            }
            IShapeUIModel parentShape = null;
            if (PackageObjectDiagramUtils.isValidName(parentPackage)) {
              parentShape = findShape(diagram, parentPackage.trim(), IPackage.class);
              if (parentShape == null) {
                return "Parent package not on diagram: " + parentPackage.trim();
              }
            }

            // A package of that name in the same owner (the project root, or the parent package)
            // is the same package: a new view of it. A new nested one is created inside its
            // parent before it is named.
            IPackage pkg =
                parentShape == null
                    ? findRootElement(name, IPackage.class)
                    : findOwnedPackage((IPackage) parentShape.getModelElement(), name);
            boolean reused = pkg != null;
            if (!reused) {
              pkg = getModelElementFactory().createPackage();
              nameInOwner(pkg, parentShape != null ? parentShape.getModelElement() : null, name);
            }
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, pkg);
            if (!(de instanceof IShapeUIModel)) {
              if (!reused) {
                pkg.delete();
              }
              return "Visual Paradigm did not create a package shape for: " + name;
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            resetCaption(shape);
            if (backgroundColor != null && !backgroundColor.trim().isEmpty()) {
              shape.getFillColor().setColor1(java.awt.Color.decode(backgroundColor.trim()), true);
            }
            if (parentShape != null && shape instanceof IPackageUIModel) {
              // The frame already sits inside its owner, so "(from Parent)" would be noise.
              ((IPackageUIModel) shape).setShowOwnerOption(IPackageUIModel.SHOW_OWNER_NONE);
            }

            // A frame is a container: fitSize() only measures the label, the frame itself is
            // kept at least the minimum size and sized around its children by the layout.
            fitShape(shape);
            int w = width > 0 ? width : Math.max(PACKAGE_MIN_WIDTH, shape.getWidth());
            int h = height > 0 ? height : Math.max(PACKAGE_MIN_HEIGHT, shape.getHeight());
            shape.setBounds(shape.getX(), shape.getY(), w, h);
            if (width > 0 || height > 0) {
              holdFit(diagram, shape); // the size asked for stays
            }
            if (parentShape != null) {
              nestChild(parentShape, shape, x, y);
            } else {
              placeNew(diagram, shape, x, y);
            }
            relayout(diagram);

            return "Added package '"
                + name
                + "'"
                + (parentShape != null ? " in '" + parentPackage.trim() + "'" : "")
                + " at "
                + shape.getX()
                + ","
                + shape.getY()
                + " "
                + shape.getWidth()
                + "x"
                + shape.getHeight();
          });
    } catch (Exception e) {
      return "Error adding package: " + e.getMessage();
    }
  }

  @Tool(
      name = "addPackageDependency",
      description =
          "Add a dependency between two packages on a package diagram. kind is"
              + " use|import|access|merge|line (\"\" = use): use draws a <<use>> usage (dashed"
              + " open arrow), import/access/merge a dependency stereotyped <<import>>,"
              + " <<access>> or <<merge>>, and line a plain solid line without arrowhead or"
              + " keyword (the book's view - dao - model diagram). A class box shown in a package"
              + " may also be used as an end")
  public String addPackageDependency(
      String diagramName, String fromPackage, String toPackage, String kind) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                findDiagramOfType(diagramName, IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM);
            if (diagram == null) {
              return diagramNotFound(diagramName, "package");
            }
            String stereotype = PackageObjectDiagramUtils.parseDependencyKind(kind);
            if (stereotype == null) {
              return "Unknown dependency kind '"
                  + kind
                  + "' (use one of use, import, access, merge, line)";
            }
            IShapeUIModel from = findPackageOrClassShape(diagram, fromPackage);
            if (from == null) {
              return "Package not on diagram: " + fromPackage;
            }
            IShapeUIModel to = findPackageOrClassShape(diagram, toPackage);
            if (to == null) {
              return "Package not on diagram: " + toPackage;
            }
            if (sameShape(from, to)) {
              return "A package cannot depend on itself: " + fromPackage;
            }

            if (PackageObjectDiagramUtils.KIND_LINE.equals(stereotype)) {
              return plainLine(diagram, from, to, fromPackage.trim(), toPackage.trim());
            }
            IRelationship dependency =
                PackageObjectDiagramUtils.KIND_USE.equals(stereotype)
                    ? getModelElementFactory().createUsage()
                    : getModelElementFactory().createDependency();
            dependency.setFrom(from.getModelElement());
            dependency.setTo(to.getModelElement());
            // Without the stereotype Visual Paradigm draws a bare dashed arrow.
            dependency.addStereotype(stereotype);
            IDiagramElement connector = connectAndLayout(diagram, dependency, from, to);
            if (connector == null) {
              dependency.delete();
              return "Visual Paradigm did not create the dependency connector";
            }

            return "Added "
                + PackageObjectDiagramUtils.stereotypeLabel(stereotype)
                + " dependency from '"
                + fromPackage.trim()
                + "' to '"
                + toPackage.trim()
                + "'";
          });
    } catch (Exception e) {
      return "Error adding package dependency: " + e.getMessage();
    }
  }

  @Tool(
      name = "addClassInPackage",
      description =
          "Show a small class box (name only, members hidden) inside a package frame of a "
              + "package diagram. The class is owned by that package in the model: an existing "
              + "class the package already owns is reused, otherwise a new class is created in "
              + "the package. The box is fitted to its name, placed inside the package by the layout "
              + "and the package is sized around it")
  public String addClassInPackage(String diagramName, String packageName, String className) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                findDiagramOfType(diagramName, IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM);
            if (diagram == null) {
              return diagramNotFound(diagramName, "package");
            }
            IShapeUIModel pkgShape = findShape(diagram, packageName, IPackage.class);
            if (pkgShape == null) {
              return "Package not on diagram: " + packageName;
            }
            if (!PackageObjectDiagramUtils.isValidName(className)) {
              return "Class name must not be empty";
            }
            String name = className.trim();
            String pkgName = packageName.trim();
            for (IShapeUIModel child : childShapes(pkgShape)) {
              IModelElement m = child.getModelElement();
              if (m instanceof IClass && name.equals(m.getName())) {
                return "Class '" + name + "' is already shown in package '" + pkgName + "'";
              }
            }

            IPackage pkg = (IPackage) pkgShape.getModelElement();
            IClass cls = findOwnedClass(pkg, name);
            boolean created = cls == null;
            if (created) {
              cls = getModelElementFactory().createClass();
              nameInOwner(cls, pkg, name);
            }
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, cls);
            if (!(de instanceof IShapeUIModel)) {
              if (created) {
                cls.delete();
              }
              return "Visual Paradigm did not create a class shape on package diagram '"
                  + diagram.getName()
                  + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            resetCaption(shape);
            if (shape instanceof IClassUIModel) {
              IClassUIModel classShape = (IClassUIModel) shape;
              // A package diagram only names the classes: hide the members of a reused class.
              classShape.setHiddenAttributeModels(cls.toAttributeArray());
              classShape.setHiddenOperationModels(cls.toOperationArray());
              classShape.setShowOwnerOption(IClassUIModel.SHOW_OWNER_NONE);
            }
            fitShape(shape);
            nestChild(pkgShape, shape, 0, 0);
            relayout(diagram);

            return "Added class '"
                + name
                + "' in package '"
                + pkgName
                + "'"
                + (created ? "" : " (existing class of the package)")
                + " at "
                + shape.getX()
                + ","
                + shape.getY()
                + " "
                + shape.getWidth()
                + "x"
                + shape.getHeight();
          });
    } catch (Exception e) {
      return "Error adding class in package: " + e.getMessage();
    }
  }

  /**
   * A plain solid line between two package frames: a package diagram offers no association, so it
   * is a generic connector with both arrowheads removed.
   */
  private String plainLine(
      IDiagramUIModel diagram,
      IShapeUIModel from,
      IShapeUIModel to,
      String fromName,
      String toName) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IGenericConnectorUIModel) {
        IGenericConnectorUIModel c = (IGenericConnectorUIModel) de;
        boolean same =
            c.getFromShape() != null
                && c.getToShape() != null
                && ((sameShape(c.getFromShape(), from) && sameShape(c.getToShape(), to))
                    || (sameShape(c.getFromShape(), to) && sameShape(c.getToShape(), from)));
        if (same) {
          return "A line between '" + fromName + "' and '" + toName + "' already exists";
        }
      }
    }
    activate(diagram);
    useCenterConnectionPoints(diagram);
    IDiagramElement created =
        getDiagramManager()
            .createConnector(
                diagram,
                IShapeTypeConstants.SHAPE_TYPE_GENERIC_CONNECTOR,
                from,
                to,
                new Point[] {center(from), center(to)});
    if (!(created instanceof IGenericConnectorUIModel)) {
      if (created != null) {
        created.deleteViewOnly();
      }
      return "Visual Paradigm did not create the line connector";
    }
    IGenericConnectorUIModel line = (IGenericConnectorUIModel) created;
    if (line.getBeginArrowHead() != null) {
      line.getBeginArrowHead().setType(IArrowHead.ARROW_NONE);
    }
    if (line.getEndArrowHead() != null) {
      line.getEndArrowHead().setType(IArrowHead.ARROW_NONE);
    }
    line.resetCaption();
    relayout(diagram);
    return "Added line between '" + fromName + "' and '" + toName + "'";
  }

  // --- Object diagram ---

  @Tool(
      name = "createObjectDiagram",
      description = "Create a new UML object diagram in Visual Paradigm (slots are shown)")
  public String createObjectDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!PackageObjectDiagramUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_OBJECT_DIAGRAM, diagramName.trim());
            diagram.setAutoFitShapesSize(true);
            if (diagram instanceof IObjectDiagramUIModel) {
              ((IObjectDiagramUIModel) diagram).setShowInstanceSpecificationSlots(true);
            }
            dm.openDiagram(diagram);
            return "Created object diagram: " + diagramName.trim();
          });
    } catch (Exception e) {
      return "Error creating object diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addObject",
      description =
          "Add an object (instance specification) to an object diagram, rendered underlined as "
              + "'objectName : ClassName' with one compartment line per slot; objectName \"\" "
              + "with a className draws an anonymous ': ClassName' object whose handle is the "
              + "class name. className is "
              + "looked up on this diagram, then in the project; if no class has that name a new "
              + "class is created (\"\" = untyped object, no slots allowed). slots is "
              + "'attr=value;attr2=value2' (\"\" for none); each attr must be an attribute of the "
              + "class or one of its superclasses (missing ones are added only to a class this "
              + "call created). When several classes of the project carry that name (one per "
              + "layer package), modelPackage names the package of the one to use; without it "
              + "the class shown on this diagram, else the one that has all the slot "
              + "attributes, is taken and the result names its owner. objectName is the handle "
              + "used by addObjectLink. x/y (>0) place it explicitly and keep it there, otherwise "
              + "the layout puts it in a layer by its links. The box is fitted to its content")
  public String addObject(
      String diagramName,
      String objectName,
      String className,
      String slots,
      int x,
      int y,
      String modelPackage) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                findDiagramOfType(diagramName, IDiagramTypeConstants.DIAGRAM_TYPE_OBJECT_DIAGRAM);
            if (diagram == null) {
              return diagramNotFound(diagramName, "object");
            }
            boolean typed = PackageObjectDiagramUtils.isValidName(className);
            // An anonymous object ": Class" has no name; its class name is its handle.
            boolean anonymous = !PackageObjectDiagramUtils.isValidName(objectName) && typed;
            if (!PackageObjectDiagramUtils.isValidName(objectName) && !anonymous) {
              return "Object name must not be empty (give only className for an anonymous"
                  + " ': Class' object)";
            }
            String name = anonymous ? "" : objectName.trim();
            String handle = anonymous ? className.trim() : name;
            if (findShape(diagram, handle, IInstanceSpecification.class) != null) {
              return "Object already on diagram: " + handle;
            }
            Map<String, String> slotValues;
            try {
              slotValues = PackageObjectDiagramUtils.parseSlots(slots);
            } catch (IllegalArgumentException e) {
              return "Invalid slots: " + e.getMessage();
            }
            if (!typed && !slotValues.isEmpty()) {
              return "className is required when slots are given (each slot needs a class "
                  + "attribute)";
            }

            // Resolve the classifier and every slot's attribute before creating anything.
            IClass cls = null;
            String classNote = "";
            boolean createdClass = false;
            List<IAttribute> features = new ArrayList<>();
            List<String> addedAttributes = new ArrayList<>();
            if (typed) {
              String clsName = className.trim();
              ClassChoice choice =
                  findClassForObject(diagram, clsName, modelPackage, slotValues.keySet());
              if (choice.error != null) {
                return choice.error;
              }
              cls = choice.cls;
              classNote = choice.note;
              if (cls == null) {
                cls = getModelElementFactory().createClass();
                cls.setName(clsName);
                createdClass = true;
              }
              List<String> missing = new ArrayList<>();
              for (String attrName : slotValues.keySet()) {
                IAttribute attr = findAttribute(cls, attrName);
                if (attr == null && createdClass) {
                  attr = getModelElementFactory().createAttribute();
                  attr.setName(attrName);
                  cls.addAttribute(attr);
                  addedAttributes.add(attrName);
                }
                if (attr == null) {
                  missing.add(attrName);
                } else {
                  features.add(attr);
                }
              }
              if (!missing.isEmpty()) {
                return "Class '"
                    + clsName
                    + "' has no attribute(s) "
                    + String.join(", ", missing)
                    + " (attributes: "
                    + attributeNames(cls)
                    + "); add them to the class first (addAttribute with diagramName empty)";
              }
            }

            IInstanceSpecification instance =
                getModelElementFactory().createInstanceSpecification();
            if (anonymous) {
              // VP names a fresh instance "Instance"; an anonymous object has no name at all
              instance.setName("");
            } else {
              instance.setName(name);
            }
            if (cls != null) {
              instance.addClassifier(cls);
            }
            int index = 0;
            for (String value : slotValues.values()) {
              ISlot slot = getModelElementFactory().createSlot();
              instance.addSlot(slot);
              slot.setFeature(features.get(index++));
              ICompositeValueSpecification spec =
                  getModelElementFactory().createCompositeValueSpecification();
              spec.setValue(value);
              slot.addValue(spec);
            }

            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, instance);
            if (!(de instanceof IShapeUIModel)) {
              instance.delete();
              if (createdClass) {
                cls.delete();
              }
              return "Visual Paradigm did not create an object shape for: " + name;
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            resetCaption(shape);
            if (shape instanceof IInstanceSpecificationUIModel) {
              ((IInstanceSpecificationUIModel) shape)
                  .setShowSlotsMode(IInstanceSpecificationUIModel.SHOW_SLOTS_MODE_SHOW_ALL);
            }
            fitShape(shape);
            placeNew(diagram, shape, x, y);
            relayout(diagram);

            StringBuilder result = new StringBuilder();
            result
                .append("Added object '")
                .append(
                    PackageObjectDiagramUtils.formatObjectLabel(
                        name, typed ? className.trim() : null))
                .append("' to diagram '")
                .append(diagram.getName())
                .append("'");
            if (!slotValues.isEmpty()) {
              List<String> lines = new ArrayList<>();
              for (Map.Entry<String, String> e : slotValues.entrySet()) {
                lines.add(PackageObjectDiagramUtils.formatSlot(e.getKey(), e.getValue()));
              }
              result.append(" with slots [").append(String.join("; ", lines)).append("]");
            }
            result.append(classNote);
            if (createdClass) {
              result.append("; created class '").append(cls.getName()).append("'");
              if (!addedAttributes.isEmpty()) {
                result.append(" with attribute(s) ").append(String.join(", ", addedAttributes));
              }
            }
            return result.toString();
          });
    } catch (Exception e) {
      return "Error adding object: " + e.getMessage();
    }
  }

  @Tool(
      name = "setObjectSlot",
      description =
          "Set, change or clear a slot of an object on an object diagram: attribute is an"
              + " attribute of the object's class or of a superclass; value \"\" removes the slot."
              + " The box is fitted again. A wrong slot value is fixed with setObjectSlot instead"
              + " of removing and re-adding the object")
  public String setObjectSlot(
      String diagramName, String objectName, String attribute, String value) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                findDiagramOfType(diagramName, IDiagramTypeConstants.DIAGRAM_TYPE_OBJECT_DIAGRAM);
            if (diagram == null) {
              return diagramNotFound(diagramName, "object");
            }
            IShapeUIModel shape = findShape(diagram, objectName, IInstanceSpecification.class);
            if (shape == null) {
              return "Object not on diagram: " + objectName;
            }
            if (!PackageObjectDiagramUtils.isValidName(attribute)) {
              return "Attribute name must not be empty";
            }
            String attr = attribute.trim();
            IInstanceSpecification instance = (IInstanceSpecification) shape.getModelElement();
            ISlot existing = null;
            for (ISlot slot : instance.toSlotArray()) {
              if (slot.getFeature() != null && attr.equals(slot.getFeature().getName())) {
                existing = slot;
              }
            }
            String text = value == null ? "" : value.trim();
            if (text.isEmpty()) {
              if (existing == null) {
                return "Object '" + objectName + "' has no slot '" + attr + "'";
              }
              existing.delete();
              fitShape(shape);
              relayout(diagram);
              return "Removed slot '" + attr + "' of object '" + objectName + "'";
            }
            if (existing == null) {
              IModelElement[] classifiers = instance.toClassifierArray();
              IAttribute feature =
                  classifiers != null && classifiers.length > 0 && classifiers[0] instanceof IClass
                      ? findAttribute((IClass) classifiers[0], attr)
                      : null;
              if (feature == null) {
                return "The class of object '"
                    + objectName
                    + "' has no attribute '"
                    + attr
                    + "' (attributes: "
                    + (classifiers != null
                            && classifiers.length > 0
                            && classifiers[0] instanceof IClass
                        ? attributeNames((IClass) classifiers[0])
                        : "none, the object is untyped")
                    + ")";
              }
              existing = getModelElementFactory().createSlot();
              instance.addSlot(existing);
              existing.setFeature(feature);
            }
            ICompositeValueSpecification[] specs = existing.toValueArray();
            if (specs != null && specs.length > 0) {
              specs[0].setValue(text);
            } else {
              ICompositeValueSpecification spec =
                  getModelElementFactory().createCompositeValueSpecification();
              spec.setValue(text);
              existing.addValue(spec);
            }
            shape.resetCaption();
            fitShape(shape);
            relayout(diagram);
            return "Set slot "
                + PackageObjectDiagramUtils.formatSlot(attr, text)
                + " of object '"
                + objectName
                + "'";
          });
    } catch (Exception e) {
      return "Error setting object slot: " + e.getMessage();
    }
  }

  @Tool(
      name = "addObjectLink",
      description =
          "Add a link (solid line) between two objects of an object diagram. label is shown on "
              + "the line (\"\" for none). fromObject/toObject are object names on this diagram")
  public String addObjectLink(
      String diagramName, String fromObject, String toObject, String label) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                findDiagramOfType(diagramName, IDiagramTypeConstants.DIAGRAM_TYPE_OBJECT_DIAGRAM);
            if (diagram == null) {
              return diagramNotFound(diagramName, "object");
            }
            IShapeUIModel from = findShape(diagram, fromObject, IInstanceSpecification.class);
            if (from == null) {
              return "Object not on diagram: " + fromObject;
            }
            IShapeUIModel to = findShape(diagram, toObject, IInstanceSpecification.class);
            if (to == null) {
              return "Object not on diagram: " + toObject;
            }
            if (sameShape(from, to)) {
              return "A link needs two different objects: " + fromObject;
            }

            ILink link = getModelElementFactory().createLink();
            link.setFrom(from.getModelElement());
            link.setTo(to.getModelElement());
            String caption = PackageObjectDiagramUtils.isValidName(label) ? label.trim() : null;
            if (caption != null) {
              link.setName(caption);
            }
            IDiagramElement connector = connectAndLayout(diagram, link, from, to);
            if (connector == null) {
              link.delete();
              return "Visual Paradigm did not create the link connector";
            }

            return "Added link '"
                + fromObject.trim()
                + "' -- '"
                + toObject.trim()
                + "'"
                + (caption != null ? " [" + caption + "]" : "");
          });
    } catch (Exception e) {
      return "Error adding object link: " + e.getMessage();
    }
  }

  // --- Diagram lookup ---

  /**
   * The most recently created diagram with this name and Visual Paradigm diagram type. The type
   * string is compared instead of a UI-model interface so no cast can fail.
   */
  private IDiagramUIModel findDiagramOfType(String diagramName, String diagramType) {
    if (diagramName == null) {
      return null;
    }
    String wanted = diagramName.trim();
    IDiagramUIModel result = null;
    for (IDiagramUIModel d : DiagramUtils.findAllDiagrams(IDiagramUIModel.class)) {
      if (wanted.equals(d.getName()) && diagramType.equals(d.getType())) {
        result = d;
      }
    }
    return result;
  }

  private String diagramNotFound(String diagramName, String kind) {
    IDiagramUIModel other =
        diagramName == null ? null : DiagramUtils.findDiagramByName(diagramName.trim());
    if (other != null) {
      return "Diagram '"
          + diagramName
          + "' is a "
          + other.getType()
          + ", not a "
          + kind
          + " diagram";
    }
    return diagramNotFound(diagramName);
  }

  // --- Shape lookup (diagram-scoped, includes shapes nested in packages) ---

  private IShapeUIModel findShape(
      IDiagramUIModel diagram, String name, Class<? extends IModelElement> type) {
    if (name == null || name.trim().isEmpty()) {
      return null;
    }
    String wanted = name.trim();
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement model = shape.getModelElement();
      if (type.isInstance(model) && wanted.equals(model.getName())) {
        return shape;
      }
    }
    // an anonymous object (": Class") is found by its class name, with or without the colon
    String classifierName = wanted.startsWith(":") ? wanted.substring(1).trim() : wanted;
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement model = shape.getModelElement();
      if (model instanceof IInstanceSpecification
          && type.isInstance(model)
          && (model.getName() == null || model.getName().isEmpty())) {
        IModelElement[] classifiers = ((IInstanceSpecification) model).toClassifierArray();
        if (classifiers != null
            && classifiers.length > 0
            && classifierName.equals(classifiers[0].getName())) {
          return shape;
        }
      }
    }
    return null;
  }

  private IShapeUIModel findPackageOrClassShape(IDiagramUIModel diagram, String name) {
    IShapeUIModel pkg = findShape(diagram, name, IPackage.class);
    return pkg != null ? pkg : findShape(diagram, name, IClass.class);
  }

  /** Every shape of the diagram, including shapes nested in containers, each once. */
  private List<IShapeUIModel> allShapes(IDiagramUIModel diagram) {
    List<IShapeUIModel> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel) {
        collectShapes((IShapeUIModel) de, result, seen, 0);
      }
    }
    return result;
  }

  private void collectShapes(
      IShapeUIModel shape, List<IShapeUIModel> out, Set<String> seen, int depth) {
    if (depth > MAX_DEPTH || !seen.add(shape.getId())) {
      return;
    }
    out.add(shape);
    for (IShapeUIModel child : childShapes(shape)) {
      collectShapes(child, out, seen, depth + 1);
    }
  }

  private static List<IShapeUIModel> childShapes(IShapeUIModel shape) {
    List<IShapeUIModel> result = new ArrayList<>();
    IShapeUIModel[] children = shape.toChildArray();
    if (children != null) {
      for (IShapeUIModel child : children) {
        if (child != null) {
          result.add(child);
        }
      }
    }
    return result;
  }

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IDiagramElement a, IDiagramElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  // --- Model lookup ---

  private static IClass findOwnedClass(IPackage pkg, String name) {
    Iterator<?> iter = pkg.childIterator();
    while (iter.hasNext()) {
      Object child = iter.next();
      if (child instanceof IClass && name.equals(((IClass) child).getName())) {
        return (IClass) child;
      }
    }
    return null;
  }

  /** The class an object is typed by, or why none could be chosen. */
  private static final class ClassChoice {
    private final IClass cls;
    private final String note;
    private final String error;

    private ClassChoice(IClass cls, String note, String error) {
      this.cls = cls;
      this.note = note;
      this.error = error;
    }
  }

  /**
   * The class called {@code className} an object is typed by. Several classes of one name may exist
   * (the layers of a design each own one): {@code modelPackage} picks by owner, otherwise the class
   * shown on this diagram, otherwise the first class that has every slot attribute.
   */
  private ClassChoice findClassForObject(
      IDiagramUIModel diagram, String className, String modelPackage, Set<String> attributes) {
    List<IClass> candidates = new ArrayList<>();
    IShapeUIModel shown = findShape(diagram, className, IClass.class);
    if (shown != null) {
      candidates.add((IClass) shown.getModelElement());
    }
    for (IClass c : DiagramUtils.findAllModelElements(IClass.class)) {
      if (className.equals(c.getName()) && candidates.stream().noneMatch(k -> sameModel(k, c))) {
        candidates.add(c);
      }
    }
    if (PackageObjectDiagramUtils.isValidName(modelPackage)) {
      for (IClass c : candidates) {
        if (modelPackage.trim().equals(ownerName(c))) {
          return new ClassChoice(c, " (class of package '" + modelPackage.trim() + "')", null);
        }
      }
      return new ClassChoice(
          null,
          "",
          candidates.isEmpty()
              ? null
              : "No class '"
                  + className
                  + "' in package '"
                  + modelPackage.trim()
                  + "' (owners: "
                  + owners(candidates)
                  + ")");
    }
    if (candidates.isEmpty()) {
      return new ClassChoice(null, "", null);
    }
    IClass chosen = null;
    for (IClass c : candidates) {
      boolean complete = true;
      for (String a : attributes) {
        complete &= findAttribute(c, a) != null;
      }
      if (complete) {
        chosen = c;
        break;
      }
    }
    if (chosen == null) {
      // keep the single/shown class: the caller reports its missing attributes
      if (candidates.size() == 1) {
        return new ClassChoice(candidates.get(0), "", null);
      }
      return new ClassChoice(
          null,
          "",
          "None of the "
              + candidates.size()
              + " classes named '"
              + className
              + "' (owners: "
              + owners(candidates)
              + ") has the slot attribute(s) "
              + String.join(", ", attributes)
              + "; add them to the class first (addAttribute with diagramName empty) or pick one"
              + " with modelPackage");
    }
    return new ClassChoice(
        chosen, candidates.size() > 1 ? " (class of '" + ownerName(chosen) + "')" : "", null);
  }

  private static boolean sameModel(IModelElement a, IModelElement b) {
    return a.getId().equals(b.getId());
  }

  private static String ownerName(IModelElement element) {
    IModelElement owner = element.getParent();
    return owner == null ? "(project)" : owner.getName();
  }

  private static String owners(List<IClass> classes) {
    List<String> names = new ArrayList<>();
    for (IClass c : classes) {
      names.add(ownerName(c));
    }
    return String.join(", ", names);
  }

  /** An attribute of the class or of one of its superclasses (VP: generalization from = parent). */
  private static IAttribute findAttribute(IClass cls, String name) {
    Set<String> visited = new HashSet<>();
    IClass current = cls;
    List<IClass> pending = new ArrayList<>();
    int steps = 0;
    while (current != null && steps++ < MAX_DEPTH) {
      if (visited.add(current.getId())) {
        IAttribute attr = current.getAttributeByName(name);
        if (attr != null) {
          return attr;
        }
        ISimpleRelationship[] incoming = current.toToRelationshipArray();
        if (incoming != null) {
          for (ISimpleRelationship rel : incoming) {
            if (rel instanceof IGeneralization && rel.getFrom() instanceof IClass) {
              pending.add((IClass) rel.getFrom());
            }
          }
        }
      }
      current = pending.isEmpty() ? null : pending.remove(0);
    }
    return null;
  }

  private static String attributeNames(IClass cls) {
    List<String> names = new ArrayList<>();
    IAttribute[] attrs = cls.toAttributeArray();
    if (attrs != null) {
      for (IAttribute a : attrs) {
        names.add(a.getName());
      }
    }
    return names.isEmpty() ? "none" : String.join(", ", names);
  }

  // --- Connectors and captions ---

  /**
   * Force Visual Paradigm to render the caption of a freshly created shape or connector; without
   * this new shapes and connectors come out blank.
   */
  private static void resetCaption(IDiagramElement de) {
    if (de != null) {
      de.resetCaption();
    }
  }
}
