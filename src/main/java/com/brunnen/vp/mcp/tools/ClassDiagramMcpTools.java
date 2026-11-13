package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ClassDiagramUtils;
import com.brunnen.vp.mcp.util.ClassLayoutProfile;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.brunnen.vp.mcp.util.VpGraphLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.ExportDiagramAsImageOption;
import com.vp.plugin.ExportDiagramAsImageWatermark;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IClassDiagramUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.connector.IHasRoleConnectorUIModel;
import com.vp.plugin.diagram.format.IShapeUIModelFillColor;
import com.vp.plugin.diagram.shape.IClassUIModel;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRealization;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IUsage;
import java.awt.Color;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** MCP tools for Visual Paradigm Class diagram operations. */
public class ClassDiagramMcpTools extends AbstractDiagramMcpTools {

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return diagram instanceof IClassDiagramUIModel ? new ClassLayoutProfile() : null;
  }

  @Tool(name = "createClassDiagram", description = "Create a new class diagram in Visual Paradigm")
  public String createClassDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            dm.openDiagram(diagram);
            return "Created class diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating class diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addClass",
      description =
          "Add a class to a class diagram. x/y (>0) place the shape explicitly: it stays there and"
              + " the connectors are routed around it; otherwise the layered layout places every"
              + " class again as things are added (generalizations decide the layers, lines are"
              + " horizontal or vertical, nothing overlaps). packageName draws the class inside that"
              + " package frame (a package of that name already in the project is reused, the"
              + " frame grows around its classes) and, for a new class, makes the package its"
              + " owner. modelPackage puts the class inside a model package (no package shape"
              + " drawn) so it cannot collide with same-named classes elsewhere in the project. A"
              + " class of that name already in the project (root, or in modelPackage or the"
              + " package) is reused: a new view of it is drawn")
  public String addClass(
      String diagramName,
      String className,
      String packageName,
      String packageColor,
      String stereotype,
      boolean isAbstract,
      String extendsClass,
      String implementsInterfaces,
      int x,
      int y,
      String modelPackage) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            if (className == null || className.trim().isEmpty()) {
              return "Class name must not be empty";
            }
            if (findClassOnDiagram(diagram, className) != null) {
              return "Class already on diagram: " + className;
            }
            if (isSet(packageColor) && parseColor(packageColor) == null) {
              return "Error adding class: packageColor must be #RRGGBB, got '" + packageColor + "'";
            }
            // the generalization and realizations are drawn to classes that are on this diagram:
            // refuse before anything is created rather than leave a class without its relations
            List<String> supers = new ArrayList<>();
            if (isSet(extendsClass)) {
              supers.add(extendsClass.trim());
            }
            if (isSet(implementsInterfaces)) {
              for (String name : implementsInterfaces.split(",")) {
                if (!name.trim().isEmpty()) {
                  supers.add(name.trim());
                }
              }
            }
            for (String name : supers) {
              if (findClassOnDiagram(diagram, name) == null) {
                return "Error adding class: '"
                    + name
                    + "' (extends/implements) is not on diagram '"
                    + diagramName
                    + "'; add it first or leave it out";
              }
            }
            // Visual Paradigm keeps names unique per owner: a class of that name in the same
            // owner is the same class, so it is reused (a new view) instead of being created again
            // and silently renamed "Class2".
            IPackage owner =
                isSet(modelPackage) ? findOrCreateModelPackage(modelPackage.trim()) : null;
            IShapeUIModel frame =
                isSet(packageName) ? packageFrame(diagram, packageName.trim(), packageColor) : null;
            if (owner == null && frame != null) {
              owner = (IPackage) frame.getModelElement();
            }
            IClass cls =
                owner != null
                    ? ownedClass(owner, className)
                    : findRootElement(className, IClass.class);
            boolean reused = cls != null;
            if (cls == null) {
              cls = getModelElementFactory().createClass();
              nameInOwner(cls, owner, className);
            }
            if (isAbstract) {
              cls.setAbstract(true);
            }
            if (stereotype != null
                && !stereotype.trim().isEmpty()
                && !cls.hasStereotype(stereotype.trim())) {
              cls.addStereotype(stereotype.trim());
            }

            IDiagramElement classDe = addToDiagram(diagram, cls, className);
            if (reused && classDe instanceof IClassUIModel && !isSet(stereotype)) {
              // a class stereotyped elsewhere (e.g. <<entity>>) stays a class box here
              ((IClassUIModel) classDe).setDisplayAsRobustnessAnalysisIcon(false);
            }
            fitShape(classDe);
            if (frame != null && classDe instanceof IShapeUIModel) {
              nestChild(frame, (IShapeUIModel) classDe, x, y);
            } else {
              placeNew(diagram, classDe, x, y);
            }

            // Create generalization (extends) if provided
            if (isSet(extendsClass)) {
              IClass parent = findClassOnDiagram(diagram, extendsClass.trim());
              IDiagramElement parentDe = findDiagramElementByName(diagram, extendsClass.trim());
              // VP stores a generalization as from = general (parent), to = specific
              IGeneralization gen = getModelElementFactory().createGeneralization();
              gen.setFrom(parent);
              gen.setTo(cls);
              connectAndLayout(diagram, gen, parentDe, classDe);
            }

            // Create realization (implements) if provided
            for (String ifaceName : supers) {
              if (isSet(extendsClass) && ifaceName.equals(extendsClass.trim())) {
                continue;
              }
              IClass iface = findClassOnDiagram(diagram, ifaceName);
              IDiagramElement ifaceDe = findDiagramElementByName(diagram, ifaceName);
              IRealization real = getModelElementFactory().createRealization();
              real.setFrom(iface); // VP: supplier -> client
              real.setTo(cls);
              connectAndLayout(diagram, real, ifaceDe, classDe);
            }

            relayout(diagram);
            StringBuilder result = new StringBuilder();
            result
                .append("Added class '")
                .append(cls.getName())
                .append("' to diagram '")
                .append(diagramName)
                .append("'");
            if (reused) {
              result.append(" (existing class of the project, drawn again)");
            }
            if (stereotype != null && !stereotype.trim().isEmpty()) {
              result.append(" with stereotype <<").append(stereotype.trim()).append(">>");
            }
            if (isAbstract) {
              result.append(" (abstract)");
            }
            if (packageName != null && !packageName.trim().isEmpty()) {
              result.append(" in package '").append(packageName.trim()).append("'");
            }
            result.append(" at ").append(classDe.getX()).append(",").append(classDe.getY());
            result.append(" ").append(classDe.getWidth()).append("x").append(classDe.getHeight());
            return result.toString();
          });
    } catch (Exception e) {
      return "Error adding class: " + e.getMessage();
    }
  }

  @Tool(
      name = "addAttribute",
      description =
          "Add an attribute to a class. Type can be empty for analysis phase. When diagramName is"
              + " given, only the class shown on that diagram is used (no project-wide fallback)")
  public String addAttribute(
      String className,
      String attributeName,
      String attributeType,
      String visibility,
      String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClass cls;
            if (diagramName != null && !diagramName.trim().isEmpty()) {
              IClassDiagramUIModel diagram =
                  (IClassDiagramUIModel)
                      DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
              if (diagram == null) {
                return diagramNotFound(diagramName);
              }
              cls = findClassOnDiagram(diagram, className);
              if (cls == null) {
                return "Class not on diagram: " + className;
              }
            } else {
              cls = findModelElement(className, IClass.class, null);
            }
            if (cls == null) {
              return "Class not found: " + className;
            }

            if (!isSet(attributeName)) {
              return "Attribute name must not be empty";
            }
            // Duplicate attribute guard
            Iterator<?> existingAttrs = cls.attributeIterator();
            while (existingAttrs.hasNext()) {
              Object obj = existingAttrs.next();
              if (obj instanceof IAttribute && attributeName.equals(((IAttribute) obj).getName())) {
                return "Attribute '"
                    + attributeName
                    + "' already exists in class '"
                    + className
                    + "'";
              }
            }

            String attrVisibility = null;
            if (visibility != null && !visibility.trim().isEmpty()) {
              attrVisibility = visibilityKeyword(visibility);
              if (attrVisibility == null) {
                return "Unknown visibility '"
                    + visibility
                    + "': use public, private, protected or package";
              }
            }
            IAttribute attr = getModelElementFactory().createAttribute();
            attr.setName(attributeName);
            if (attributeType != null && !attributeType.trim().isEmpty()) {
              attr.setType(attributeType.trim());
            }
            if (attrVisibility != null) {
              attr.setVisibility(attrVisibility);
            }
            cls.addAttribute(attr);
            refitClass(cls);

            return "Added attribute '" + attributeName + "' to class '" + className + "'";
          });
    } catch (Exception e) {
      return "Error adding attribute: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeAttribute",
      description =
          "Remove an attribute from a class shown on a class diagram (by name; every attribute of"
              + " that name). The class box is fitted again. A wrong attribute is fixed with"
              + " removeAttribute and addAttribute")
  public String removeAttribute(String diagramName, String className, String attributeName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass cls = findClassOnDiagram(diagram, className);
            if (cls == null) {
              return "Class not on diagram: " + className;
            }
            if (!isSet(attributeName)) {
              return "Attribute name must not be empty";
            }
            int removed = 0;
            for (IAttribute attr : cls.toAttributeArray()) {
              if (attributeName.trim().equals(attr.getName())) {
                attr.delete();
                removed++;
              }
            }
            if (removed == 0) {
              return "Class '" + className + "' has no attribute '" + attributeName.trim() + "'";
            }
            refitClass(cls);
            return "Removed attribute '"
                + attributeName.trim()
                + "' from class '"
                + className
                + "'";
          });
    } catch (Exception e) {
      return "Error removing attribute: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeOperation",
      description =
          "Remove an operation from a class shown on a class diagram (by name, whatever its"
              + " parameters: every operation of that name). The class box is fitted again")
  public String removeOperation(String diagramName, String className, String operationName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass cls = findClassOnDiagram(diagram, className);
            if (cls == null) {
              return "Class not on diagram: " + className;
            }
            if (!isSet(operationName)) {
              return "Operation name must not be empty";
            }
            int removed = 0;
            for (IOperation op : cls.toOperationArray()) {
              if (operationName.trim().equals(op.getName())) {
                op.delete();
                removed++;
              }
            }
            if (removed == 0) {
              return "Class '" + className + "' has no operation '" + operationName.trim() + "'";
            }
            refitClass(cls);
            return "Removed "
                + removed
                + " operation(s) '"
                + operationName.trim()
                + "' from class '"
                + className
                + "'";
          });
    } catch (Exception e) {
      return "Error removing operation: " + e.getMessage();
    }
  }

  @Tool(
      name = "addOperation",
      description =
          "Add an operation/method to a class. params is 'name: Type, ...'. visibility is"
              + " public/private/protected/package (empty = VP default). When diagramName is"
              + " given, only the class shown on that diagram is used (no project-wide fallback)")
  public String addOperation(
      String className,
      String operationName,
      String returnType,
      String params,
      String visibility,
      boolean isAbstract,
      String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClass cls;
            if (isSet(diagramName)) {
              IClassDiagramUIModel diagram =
                  (IClassDiagramUIModel)
                      DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
              if (diagram == null) {
                return diagramNotFound(diagramName);
              }
              cls = findClassOnDiagram(diagram, className);
              if (cls == null) {
                return "Class not on diagram: " + className;
              }
            } else {
              cls = findModelElement(className, IClass.class, null);
            }
            if (cls == null) {
              return "Class not found: " + className;
            }

            if (!isSet(operationName)) {
              return "Operation name must not be empty";
            }
            String badParam = firstBadParameter(params);
            if (badParam != null) {
              return "Bad parameter '"
                  + badParam
                  + "': write params as 'name: Type, name2: Type2' (a name may be used alone)";
            }
            // Duplicate operation guard
            Iterator<?> existingOps = cls.operationIterator();
            while (existingOps.hasNext()) {
              Object obj = existingOps.next();
              if (obj instanceof IOperation && operationName.equals(((IOperation) obj).getName())) {
                return "Operation '"
                    + operationName
                    + "' already exists in class '"
                    + className
                    + "'";
              }
            }

            String opVisibility = null;
            if (isSet(visibility)) {
              opVisibility = visibilityKeyword(visibility);
              if (opVisibility == null) {
                return "Unknown visibility '"
                    + visibility
                    + "': use public, private, protected or package";
              }
            }
            IOperation op = getModelElementFactory().createOperation();
            op.setName(operationName);
            if (returnType != null && !returnType.trim().isEmpty()) {
              op.setReturnType(returnType.trim());
            }
            if (params != null && !params.trim().isEmpty()) {
              for (String param : params.split(",")) {
                String trimmed = param.trim();
                if (!trimmed.isEmpty()) {
                  IParameter paramElem = getModelElementFactory().createParameter();
                  if (trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    paramElem.setName(parts[0].trim());
                    paramElem.setType(parts[1].trim());
                  } else {
                    paramElem.setName(trimmed);
                  }
                  op.addParameter(paramElem);
                }
              }
            }
            if (opVisibility != null) {
              op.setVisibility(opVisibility);
            }
            if (isAbstract) {
              op.setAbstract(true);
            }
            cls.addOperation(op);
            refitClass(cls);

            return "Added operation '" + operationName + "' to class '" + className + "'";
          });
    } catch (Exception e) {
      return "Error adding operation: " + e.getMessage();
    }
  }

  @Tool(
      name = "addAssociation",
      description = "Add an association between two classes in a class diagram")
  public String addAssociation(
      String diagramName,
      String fromClass,
      String toClass,
      String fromMultiplicity,
      String toMultiplicity,
      String name) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            String badMultiplicity = firstBadMultiplicity(fromMultiplicity, toMultiplicity);
            if (badMultiplicity != null) {
              return badMultiplicity;
            }
            IAssociation assoc = getModelElementFactory().createAssociation();
            assoc.setFrom(source);
            assoc.setTo(target);
            if (name != null && !name.trim().isEmpty()) {
              assoc.setName(name.trim());
            }
            IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
            IAssociationEnd toEnd = (IAssociationEnd) assoc.getToEnd();
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              toEnd.setMultiplicity(toMultiplicity.trim());
            }
            connectAndLayout(diagram, assoc, fromElement, toElement);

            return "Added association from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding association: " + e.getMessage();
    }
  }

  @Tool(
      name = "addGeneralization",
      description =
          "Add a generalization (inheritance): fromClass is the subclass (child), toClass the"
              + " superclass (parent); the hollow triangle is drawn at toClass")
  public String addGeneralization(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            if (source.getId().equals(target.getId())) {
              return "A class cannot generalize itself: " + fromClass;
            }
            if (isDescendant(target, source)) {
              return "Generalization cycle: '"
                  + toClass
                  + "' already inherits from '"
                  + fromClass
                  + "'";
            }
            // VP stores a generalization as from = general (parent), to = specific (child)
            IGeneralization gen = getModelElementFactory().createGeneralization();
            gen.setFrom(target);
            gen.setTo(source);
            connectAndLayout(diagram, gen, toElement, fromElement);

            return "Added generalization from '" + fromClass + "' extends '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding generalization: " + e.getMessage();
    }
  }

  @Tool(
      name = "addAggregation",
      description = "Add an aggregation relationship between classes (diamond open)")
  public String addAggregation(
      String diagramName,
      String fromClass,
      String toClass,
      String fromMultiplicity,
      String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            String badMultiplicity = firstBadMultiplicity(fromMultiplicity, toMultiplicity);
            if (badMultiplicity != null) {
              return badMultiplicity;
            }
            IAssociation assoc = getModelElementFactory().createAssociation();
            assoc.setFrom(source);
            assoc.setTo(target);
            IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
            fromEnd.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_AGGREGATION);
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              ((IAssociationEnd) assoc.getToEnd()).setMultiplicity(toMultiplicity.trim());
            }
            connectAndLayout(diagram, assoc, fromElement, toElement);

            return "Added aggregation from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding aggregation: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComposition",
      description = "Add a composition relationship between classes (diamond filled)")
  public String addComposition(
      String diagramName,
      String fromClass,
      String toClass,
      String fromMultiplicity,
      String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            String badMultiplicity = firstBadMultiplicity(fromMultiplicity, toMultiplicity);
            if (badMultiplicity != null) {
              return badMultiplicity;
            }
            IAssociation assoc = getModelElementFactory().createAssociation();
            assoc.setFrom(source);
            assoc.setTo(target);
            IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
            fromEnd.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_COMPOSITED);
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              ((IAssociationEnd) assoc.getToEnd()).setMultiplicity(toMultiplicity.trim());
            }
            connectAndLayout(diagram, assoc, fromElement, toElement);

            return "Added composition from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding composition: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDependency",
      description = "Add a dependency relationship between classes (dashed arrow)")
  public String addDependency(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IDependency dep = getModelElementFactory().createDependency();
            dep.setFrom(source);
            dep.setTo(target);
            connectAndLayout(diagram, dep, fromElement, toElement);

            return "Added dependency from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding dependency: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRealization",
      description =
          "Add a realization (implements) relationship: fromClass implements the interface"
              + " toClass (triangle on toClass)")
  public String addRealization(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IRealization real = getModelElementFactory().createRealization();
            // VP stores a realization supplier -> client (like a generalization); from = client
            // would draw the triangle on the implementing class.
            real.setFrom(target);
            real.setTo(source);
            connectAndLayout(diagram, real, toElement, fromElement);

            return "Added realization from '" + fromClass + "' implements '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding realization: " + e.getMessage();
    }
  }

  @Tool(
      name = "addInterface",
      description =
          "Add an interface to a class diagram (placed by the layered layout, like a class)")
  public String addInterface(String diagramName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            if (!isSet(interfaceName)) {
              return "Interface name must not be empty";
            }
            IClass iface = getModelElementFactory().createClass();
            iface.addStereotype("Interface");
            IDiagramElement ifaceDe = addToDiagram(diagram, iface, interfaceName.trim());
            fitShape(ifaceDe);
            placeNew(diagram, ifaceDe, 0, 0);
            relayout(diagram);

            return "Added interface '" + interfaceName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding interface: " + e.getMessage();
    }
  }

  @Tool(
      name = "addPackage",
      description =
          "Add a package frame with an optional background color (#RRGGBB) to a class diagram. A"
              + " package of that name already in the project is reused (a new view of it is"
              + " drawn); a name already on the diagram is refused")
  public String addPackage(String diagramName, String packageName, String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!isSet(packageName)) {
              return "packageName is required";
            }
            if (isSet(backgroundColor) && parseColor(backgroundColor) == null) {
              return "Error adding package: backgroundColor must be #RRGGBB, got '"
                  + backgroundColor
                  + "'";
            }
            if (findPackageShape(diagram, packageName.trim()) != null) {
              return "Package already on diagram: " + packageName.trim();
            }
            packageFrame(diagram, packageName.trim(), backgroundColor);
            relayout(diagram);

            return "Added package '"
                + packageName
                + "' to diagram '"
                + diagramName
                + "'"
                + (backgroundColor != null && !backgroundColor.trim().isEmpty()
                    ? " with color " + backgroundColor.trim()
                    : "");
          });
    } catch (Exception e) {
      return "Error adding package: " + e.getMessage();
    }
  }

  @Tool(
      name = "addPackageShape",
      description =
          "Add a package frame at the given bounds. parentPackage (a package already on the"
              + " diagram) nests it in that package, in the model and on the diagram; otherwise"
              + " modelPackage (optional) is the model package that owns it. backgroundColor is"
              + " '#RRGGBB' or empty")
  public String addPackageShape(
      String diagramName,
      String packageName,
      String parentPackage,
      String modelPackage,
      int x,
      int y,
      int width,
      int height,
      String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!isSet(packageName)) {
              return "packageName is required";
            }
            if (findPackageShape(diagram, packageName.trim()) != null) {
              return "Package already on diagram: " + packageName;
            }
            if (isSet(backgroundColor) && parseColor(backgroundColor) == null) {
              return "Error adding package: backgroundColor must be #RRGGBB, got '"
                  + backgroundColor
                  + "'";
            }
            IShapeUIModel parentShape = null;
            if (isSet(parentPackage)) {
              parentShape = findPackageShape(diagram, parentPackage.trim());
              if (parentShape == null) {
                return "Parent package not on diagram: " + parentPackage;
              }
            }
            // a package of that name in the same owner is the same package: a new view of it
            IModelElement owner =
                parentShape != null
                    ? parentShape.getModelElement()
                    : isSet(modelPackage) ? findOrCreateModelPackage(modelPackage.trim()) : null;
            IPackage pkg =
                owner != null
                    ? findOwnedPackage((IPackage) owner, packageName.trim())
                    : findRootElement(packageName.trim(), IPackage.class);
            if (pkg == null) {
              pkg = getModelElementFactory().createPackage();
              nameInOwner(pkg, owner, packageName.trim());
            }
            IDiagramElement de = addToDiagram(diagram, pkg, packageName.trim());
            if (parentShape != null && de instanceof IShapeUIModel) {
              parentShape.addChild((IShapeUIModel) de);
            }
            if (width > 0 && height > 0) {
              de.setBounds(x, y, width, height);
            }
            if (isSet(backgroundColor) && de instanceof IShapeUIModel) {
              ((IShapeUIModel) de).getFillColor().setColor1(parseColor(backgroundColor), true);
            }
            return "Added package '"
                + packageName.trim()
                + "'"
                + (parentShape != null ? " in '" + parentPackage.trim() + "'" : "")
                + " at "
                + de.getX()
                + ","
                + de.getY()
                + " "
                + de.getWidth()
                + "x"
                + de.getHeight();
          });
    } catch (Exception e) {
      return "Error adding package: " + e.getMessage();
    }
  }

  @Tool(
      name = "moveIntoPackage",
      description =
          "Put a class (or package) shown on a diagram into a package frame on the same diagram:"
              + " the package becomes its owner in the model and its container on the diagram."
              + " Bounds are kept")
  public String moveIntoPackage(String diagramName, String elementName, String packageName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel pkgShape = findPackageShape(diagram, packageName);
            if (pkgShape == null) {
              return "Package not on diagram: " + packageName;
            }
            IDiagramElement de = findDiagramElementByName(diagram, elementName);
            if (!(de instanceof IShapeUIModel) || de.getModelElement() == null) {
              return "Element not on diagram: " + elementName;
            }
            int x = de.getX();
            int y = de.getY();
            int w = de.getWidth();
            int h = de.getHeight();
            ((IPackage) pkgShape.getModelElement()).addChild(de.getModelElement());
            pkgShape.addChild((IShapeUIModel) de);
            de.setBounds(x, y, w, h);
            ((IShapeUIModel) de).bringToFront(); // a frame created later would hide it
            return "Moved '" + elementName + "' into package '" + packageName + "'";
          });
    } catch (Exception e) {
      return "Error moving into package: " + e.getMessage();
    }
  }

  @Tool(
      name = "addUsage",
      description =
          "Add a usage dependency (dashed arrow labelled <<use>>) from one class or package to"
              + " another on a class diagram")
  public String addUsage(String diagramName, String fromName, String toName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IDiagramElement from = findDiagramElementByName(diagram, fromName);
            IDiagramElement to = findDiagramElementByName(diagram, toName);
            if (from == null || to == null) {
              return "Element not on diagram: " + (from == null ? fromName : toName);
            }
            IUsage usage = getModelElementFactory().createUsage();
            usage.setFrom(from.getModelElement());
            usage.setTo(to.getModelElement());
            usage.addStereotype("use"); // VP draws a usage as a bare dashed arrow otherwise
            if (connectAndLayout(diagram, usage, from, to) == null) {
              return "Visual Paradigm refused a usage from '" + fromName + "' to '" + toName + "'";
            }
            return "Added usage from '" + fromName + "' to '" + toName + "'";
          });
    } catch (Exception e) {
      return "Error adding usage: " + e.getMessage();
    }
  }

  @Tool(
      name = "setClassColor",
      description = "Set background color of a class shape on a class diagram")
  public String setClassColor(String diagramName, String className, String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            IDiagramElement de = findDiagramElementByName(diagram, className);
            if (de == null) {
              return "Class not on diagram: " + className;
            }
            if (!(de instanceof IShapeUIModel)) {
              return "Element is not a shape: " + className;
            }

            Color color;
            try {
              color = Color.decode(backgroundColor == null ? "" : backgroundColor.trim());
            } catch (NumberFormatException e) {
              return "Unknown color '" + backgroundColor + "': use #RRGGBB, e.g. #7AD2FF";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            IShapeUIModelFillColor fill = shape.getFillColor();
            fill.setColor1(color, true);

            return "Set color of '" + className + "' to " + backgroundColor.trim();
          });
    } catch (Exception e) {
      return "Error setting class color: " + e.getMessage();
    }
  }

  @Tool(name = "generateClassReport", description = "Generate a class diagram analysis report")
  public String generateClassReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            List<IClass> classes = ClassDiagramUtils.getClassesInDiagram(diagram);

            // Build model -> caption name map
            java.util.Map<IModelElement, String> nameMap = new java.util.LinkedHashMap<>();
            Iterator<?> deIter = diagram.diagramElementIterator();
            while (deIter.hasNext()) {
              Object obj = deIter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model instanceof IClass) {
                  String displayName = model.getName();
                  if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
                    String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
                    if (caption != null && !caption.isEmpty()) {
                      displayName = caption;
                    }
                  }
                  nameMap.put(model, displayName);
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("CLASS DIAGRAM REPORT: ").append(diagramName).append("\n");
            report.append("=====================================\n");

            // Classes with attributes and operations
            report.append("Classes (").append(classes.size()).append("):\n");
            for (IClass cls : classes) {
              String className = nameMap.getOrDefault(cls, cls.getName());
              // Check for Interface stereotype
              boolean isInterface = false;
              Iterator<?> stereotypes = cls.stereotypeIterator();
              while (stereotypes.hasNext()) {
                if ("Interface".equals(stereotypes.next())) {
                  isInterface = true;
                  break;
                }
              }
              StringBuilder header = new StringBuilder("  - ");
              if (isInterface) {
                header.append("Interface: ");
              }
              header.append(className);
              appendStereotypes(header, cls, "Interface");
              if (cls.isAbstract()) {
                header.append(" (abstract)");
              }
              report.append(header).append("\n");

              // Attributes
              List<String> attrs = new ArrayList<>();
              Iterator<?> attrIter = cls.attributeIterator();
              while (attrIter.hasNext()) {
                Object attrObj = attrIter.next();
                if (attrObj instanceof IAttribute) {
                  IAttribute attr = (IAttribute) attrObj;
                  String attrStr =
                      visibilitySymbol(attr.getVisibility())
                          + attr.getName()
                          + (attr.getType() != null ? " : " + attr.getType() : "");
                  attrs.add(attrStr);
                }
              }
              if (!attrs.isEmpty()) {
                report.append("    Attributes: ").append(String.join(", ", attrs)).append("\n");
              }

              // Operations
              List<String> ops = new ArrayList<>();
              Iterator<?> opIter = cls.operationIterator();
              while (opIter.hasNext()) {
                Object opObj = opIter.next();
                if (opObj instanceof IOperation) {
                  IOperation op = (IOperation) opObj;
                  StringBuilder opStr = new StringBuilder();
                  opStr.append(visibilitySymbol(op.getVisibility()));
                  opStr.append(op.getName()).append("(");
                  List<String> params = new ArrayList<>();
                  Iterator<?> paramIter = op.parameterIterator();
                  while (paramIter.hasNext()) {
                    Object paramObj = paramIter.next();
                    if (paramObj instanceof IParameter) {
                      IParameter p = (IParameter) paramObj;
                      String paramText = p.getName();
                      if (p.getType() != null) {
                        paramText += " : " + p.getType();
                      }
                      params.add(paramText);
                    }
                  }
                  opStr.append(String.join(", ", params)).append(")");
                  if (op.getReturnType() != null) {
                    opStr.append(" : ").append(op.getReturnType());
                  }
                  ops.add(opStr.toString());
                }
              }
              if (!ops.isEmpty()) {
                report.append("    Operations: ").append(String.join(", ", ops)).append("\n");
              }
            }

            // Relationships. Visual Paradigm stores a generalization general -> specific and a
            // realization interface -> implementation: the report names the subclass and the
            // implementing class first, the way the arrow is drawn.
            List<String> relationships = new ArrayList<>();
            Iterator<?> elemIter = diagram.diagramElementIterator();
            while (elemIter.hasNext()) {
              Object obj = elemIter.next();
              if (obj instanceof IDiagramElement) {
                IModelElement model = ((IDiagramElement) obj).getModelElement();
                if (model instanceof IGeneralization) {
                  IGeneralization gen = (IGeneralization) model;
                  relationships.add(
                      "Generalization: "
                          + reportName(nameMap, gen.getTo())
                          + " extends "
                          + reportName(nameMap, gen.getFrom()));
                } else if (model instanceof IAssociation) {
                  relationships.add(describeAssociation((IAssociation) model, nameMap));
                } else if (model instanceof IDependency) {
                  IDependency dep = (IDependency) model;
                  relationships.add(
                      "Dependency: "
                          + reportName(nameMap, dep.getFrom())
                          + " -> "
                          + reportName(nameMap, dep.getTo()));
                } else if (model instanceof IRealization) {
                  IRealization real = (IRealization) model;
                  relationships.add(
                      "Realization: "
                          + reportName(nameMap, real.getTo())
                          + " implements "
                          + reportName(nameMap, real.getFrom()));
                }
              }
            }
            if (!relationships.isEmpty()) {
              report.append("Relationships (").append(relationships.size()).append("):\n");
              for (String rel : relationships) {
                report.append("  - ").append(rel).append("\n");
              }
            }

            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  /** A multiplicity VP understands: {@code n}, {@code *}, {@code n..m}, {@code n..*}. */
  private static final java.util.regex.Pattern MULTIPLICITY =
      java.util.regex.Pattern.compile("(\\d+|\\*|[nm])(\\.\\.(\\d+|\\*|[nm]))?");

  /** An error text for the first multiplicity that is not {@code n}, {@code *} or {@code n..m}. */
  private static String firstBadMultiplicity(String... multiplicities) {
    for (String m : multiplicities) {
      if (isSet(m) && !MULTIPLICITY.matcher(m.trim()).matches()) {
        return "Bad multiplicity '"
            + m.trim()
            + "': use a number, *, n, m or a range such as 0..1, 1..*, 2..n";
      }
    }
    return null;
  }

  /** The first entry of a {@code name: Type, ...} list that is not a plain identifier. */
  private static String firstBadParameter(String params) {
    if (!isSet(params)) {
      return null;
    }
    for (String param : params.split(",")) {
      String trimmed = param.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String name =
          trimmed.contains(":") ? trimmed.substring(0, trimmed.indexOf(':')).trim() : trimmed;
      if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")) {
        return trimmed;
      }
    }
    return null;
  }

  private static String reportName(
      java.util.Map<IModelElement, String> nameMap, IModelElement element) {
    return element == null ? "?" : nameMap.getOrDefault(element, element.getName());
  }

  /**
   * One report line for an association: its kind (association, aggregation or composition with the
   * whole named), the ends, the multiplicity at both ends, role names and the name.
   */
  private static String describeAssociation(
      IAssociation assoc, java.util.Map<IModelElement, String> nameMap) {
    IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
    IAssociationEnd toEnd = (IAssociationEnd) assoc.getToEnd();
    String kind = "Association";
    String whole = null;
    for (IAssociationEnd end : new IAssociationEnd[] {fromEnd, toEnd}) {
      String aggregation = aggregationOf(end);
      if (aggregation != null) {
        kind = "composite".equals(aggregation) ? "Composition" : "Aggregation";
        whole = reportName(nameMap, end.getModelElement());
      }
    }
    StringBuilder line = new StringBuilder(kind);
    line.append(": ")
        .append(reportName(nameMap, assoc.getFrom()))
        .append(" -> ")
        .append(reportName(nameMap, assoc.getTo()));
    if (whole != null) {
      line.append(" (whole: ").append(whole).append(")");
    }
    String multFrom = fromEnd != null ? multiplicityText(fromEnd.getMultiplicity()) : "";
    String multTo = toEnd != null ? multiplicityText(toEnd.getMultiplicity()) : "";
    if (!multFrom.isEmpty() || !multTo.isEmpty()) {
      line.append(" [").append(multFrom.isEmpty() ? "-" : multFrom);
      line.append(" .. ").append(multTo.isEmpty() ? "-" : multTo).append("]");
    }
    String fromRole = fromEnd != null ? fromEnd.getName() : null;
    String toRole = toEnd != null ? toEnd.getName() : null;
    if (isSet(fromRole) || isSet(toRole)) {
      line.append(" roles ")
          .append(isSet(fromRole) ? fromRole : "-")
          .append(" / ")
          .append(isSet(toRole) ? toRole : "-");
    }
    if (isSet(assoc.getName())) {
      line.append(" (name: ").append(assoc.getName()).append(")");
    }
    return line.toString();
  }

  @Tool(
      name = "setElementBounds",
      description =
          "Move/resize a shape on a diagram. x or y <= 0 keeps the shape's current coordinate (a"
              + " resize-only call is setElementBounds(name, 0, 0, w, h)); width or height <= 0"
              + " fits the shape to its content"
              + " (a container such as a package, node or component keeps its size, and always"
              + " stays big enough for what it holds). A container takes the shapes nested in it"
              + " along (ports, artifacts, classes...); a lifeline of a sequence diagram takes its"
              + " messages and activation bar along; control nodes, pseudo-states and"
              + " BPMN/flowchart/DFD symbols keep their notation sizes. The labels of the"
              + " connectors of a moved shape are routed again and their labels put beside the"
              + " lines. A shape placed this way is pinned: the layered layout (class, use case,"
              + " state, activity, flowchart, communication diagrams) leaves it where it is and"
              + " routes the lines around it; a node of an activity swimlane stays in its lane"
              + " (autoLayoutDiagram releases every pin)")
  public String setElementBounds(
      String diagramName, String elementName, int x, int y, int width, int height) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IDiagramElement de = findDiagramElementByName(diagram, elementName);
            if (de == null) {
              return "Element not on diagram: " + elementName;
            }
            // 0 is "not given": a resize-only call must not send the shape to the origin
            int left = x > 0 ? x : de.getX();
            int top = y > 0 ? y : de.getY();
            // a family that lays out its own diagram type knows what a moved shape drags along
            for (AbstractDiagramMcpTools family : families()) {
              String done = family.moveOwnShape(diagram, de, elementName, left, top, width, height);
              if (done != null) {
                return done;
              }
            }
            String note = applyBounds(de, left, top, width, height);
            if (de.getModelElement() instanceof com.vp.plugin.model.IActor) {
              // An actor's name is a detached label that stays behind when the figure moves.
              de.resetCaption();
            }
            if (profileOf(diagram) != null) {
              // explicitly placed: the layered layout leaves it here, connectors go around it
              note += avoidOverlap(diagram, de);
              pin(de);
              relayout(diagram);
            }
            return boundsResult(elementName, de) + note;
          });
    } catch (Exception e) {
      return "Error setting bounds: " + e.getMessage();
    }
  }

  @Tool(
      name = "addStereotypeToClasses",
      description =
          "Apply a stereotype (e.g. 'ORM Persistable') to classes on a diagram. classNames is a"
              + " comma-separated list, or '*' for every class on the diagram. robustnessIcon"
              + " true/false sets whether boundary/control/entity classes are drawn as robustness"
              + " icons on this diagram (false keeps the class box with its members); empty leaves"
              + " it unchanged")
  public String addStereotypeToClasses(
      String diagramName, String classNames, String stereotype, String robustnessIcon) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (stereotype == null || stereotype.trim().isEmpty()) {
              return "Stereotype is required";
            }
            boolean all = classNames == null || "*".equals(classNames.trim());
            Set<String> wanted = new LinkedHashSet<>();
            if (!all) {
              for (String n : classNames.split(",")) {
                if (!n.trim().isEmpty()) {
                  wanted.add(n.trim());
                }
              }
            }
            if (isSet(robustnessIcon)
                && !"true".equalsIgnoreCase(robustnessIcon.trim())
                && !"false".equalsIgnoreCase(robustnessIcon.trim())) {
              return "robustnessIcon must be true or false: " + robustnessIcon;
            }
            List<String> applied = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (IClass cls : ClassDiagramUtils.getClassesInDiagram(diagram)) {
              if (!all && !wanted.contains(cls.getName())) {
                continue;
              }
              seen.add(cls.getName());
              if (!cls.hasStereotype(stereotype.trim())) {
                cls.addStereotype(stereotype.trim());
              }
              refitClass(cls);
              applied.add(cls.getName());
            }
            if (isSet(robustnessIcon)) {
              boolean icon = Boolean.parseBoolean(robustnessIcon.trim());
              for (IDiagramElement de : getDiagramElementsList(diagram)) {
                if (de instanceof IClassUIModel
                    && de.getModelElement() instanceof IClass
                    && seen.contains(de.getModelElement().getName())) {
                  ((IClassUIModel) de).setDisplayAsRobustnessAnalysisIcon(icon);
                  fitShape(de);
                }
              }
            }
            wanted.removeAll(seen);
            String result =
                "Stereotype <<"
                    + stereotype.trim()
                    + ">> on "
                    + applied.size()
                    + " class(es): "
                    + String.join(", ", applied);
            if (!wanted.isEmpty()) {
              result += "; not on diagram: " + String.join(", ", wanted);
            }
            return result;
          });
    } catch (Exception e) {
      return "Error applying stereotype: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeRelationship",
      description =
          "Delete relationships between two elements on a diagram from the MODEL (connector and"
              + " model element). Class diagrams: relationshipType Generalization (fromClass ="
              + " child, toClass = parent), Association (any kind), Aggregation, Composition,"
              + " Dependency or Realization; associations match either direction. Every other"
              + " diagram type (use case, activity, state, flowchart, DFD, BPMN, component,"
              + " deployment, package...): fromClass and toClass are the names of the two shapes,"
              + " relationshipType (\"\" or * = any) narrows by the kind of connector (Include,"
              + " Extend, ControlFlow, Transition, BPSequenceFlow...), its stereotype or its"
              + " label, and every connector between the two shapes in either direction that"
              + " matches is removed (this is how an unnamed connector is deleted)")
  public String removeRelationship(
      String diagramName, String fromClass, String toClass, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel any = DiagramUtils.findDiagramByName(diagramName);
            if (any == null) {
              return diagramNotFound(diagramName);
            }
            if (!(any instanceof IClassDiagramUIModel)) {
              return removeConnectorsBetween(any, fromClass, toClass, relationshipType);
            }
            IClassDiagramUIModel diagram = (IClassDiagramUIModel) any;
            List<IRelationship> matches =
                findRelationships(diagram, fromClass, toClass, relationshipType);
            if (matches.isEmpty()) {
              return "No "
                  + relationshipType
                  + " found between '"
                  + fromClass
                  + "' and '"
                  + toClass
                  + "'";
            }
            for (IRelationship rel : matches) {
              rel.delete();
            }
            relayout(diagram); // the lines it took away free their lanes
            return "Removed "
                + matches.size()
                + " "
                + relationshipType
                + " between '"
                + fromClass
                + "' and '"
                + toClass
                + "'";
          });
    } catch (Exception e) {
      return "Error removing relationship: " + e.getMessage();
    }
  }

  @Tool(
      name = "setAssociationProperties",
      description =
          "Update an existing association/aggregation/composition between two classes (either"
              + " direction). fromMultiplicity/fromRole/fromNavigable apply to the end attached to"
              + " fromClass. Empty values leave the property unchanged; role '-' clears the role"
              + " name; navigable is true, false or unspecified")
  public String setAssociationProperties(
      String diagramName,
      String fromClass,
      String toClass,
      String name,
      String fromMultiplicity,
      String toMultiplicity,
      String fromRole,
      String toRole,
      String fromNavigable,
      String toNavigable) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            List<IRelationship> matches =
                findRelationships(diagram, fromClass, toClass, "Association");
            if (matches.isEmpty()) {
              return "No association found between '" + fromClass + "' and '" + toClass + "'";
            }
            if (matches.size() > 1) {
              return "Ambiguous: "
                  + matches.size()
                  + " associations between '"
                  + fromClass
                  + "' and '"
                  + toClass
                  + "'";
            }
            IAssociation assoc = (IAssociation) matches.get(0);
            IAssociationEnd endA = (IAssociationEnd) assoc.getFromEnd();
            IAssociationEnd endB = (IAssociationEnd) assoc.getToEnd();
            boolean reversed = !fromClass.equals(nameOf(assoc.getFrom()));
            IAssociationEnd fromEnd = reversed ? endB : endA;
            IAssociationEnd toEnd = reversed ? endA : endB;
            if (isSet(name)) {
              assoc.setName(name.trim());
            }
            if (isSet(fromMultiplicity)) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (isSet(toMultiplicity)) {
              toEnd.setMultiplicity(toMultiplicity.trim());
            }
            if (isSet(fromRole)) {
              fromEnd.setName("-".equals(fromRole.trim()) ? "" : fromRole.trim());
            }
            if (isSet(toRole)) {
              toEnd.setName("-".equals(toRole.trim()) ? "" : toRole.trim());
            }
            for (String nav : new String[] {fromNavigable, toNavigable}) {
              if (isSet(nav) && navigableCode(nav) == null) {
                return "navigable must be true, false or unspecified: " + nav;
              }
            }
            if (isSet(fromNavigable)) {
              fromEnd.setNavigable(navigableCode(fromNavigable));
            }
            if (isSet(toNavigable)) {
              toEnd.setNavigable(navigableCode(toNavigable));
            }
            return "Updated association '"
                + fromClass
                + "' ["
                + fromEnd.getMultiplicity()
                + "] -- ["
                + toEnd.getMultiplicity()
                + "] '"
                + toClass
                + "' (name: "
                + assoc.getName()
                + ")";
          });
    } catch (Exception e) {
      return "Error updating association: " + e.getMessage();
    }
  }

  @Tool(
      name = "getRelationshipDetails",
      description =
          "Audit dump (JSON) of a class diagram: classes (abstract, stereotypes, owner, attributes,"
              + " bounds) and every relationship with both ends (multiplicity, aggregation kind,"
              + " role) and name")
  public String getRelationshipDetails(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            ObjectNode root = JSON.createObjectNode();
            root.put("diagram", diagramName);
            ArrayNode classes = root.putArray("classes");
            ArrayNode relationships = root.putArray("relationships");
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              IModelElement model = de.getModelElement();
              if (model instanceof IClass) {
                IClass cls = (IClass) model;
                ObjectNode c = classes.addObject();
                c.put("name", cls.getName());
                c.put("abstract", cls.isAbstract());
                c.put("owner", nameOf(cls.getParent()));
                ArrayNode st = c.putArray("stereotypes");
                for (String s : cls.toStereotypeArray()) {
                  st.add(s);
                }
                ArrayNode attrs = c.putArray("attributes");
                Iterator<?> it = cls.attributeIterator();
                while (it.hasNext()) {
                  Object o = it.next();
                  if (o instanceof IAttribute) {
                    IAttribute a = (IAttribute) o;
                    ObjectNode an = attrs.addObject();
                    an.put("name", a.getName());
                    an.put("type", a.getTypeAsString());
                    an.put("visibility", a.getVisibility());
                  }
                }
                ArrayNode ops = c.putArray("operations");
                Iterator<?> oit = cls.operationIterator();
                while (oit.hasNext()) {
                  Object o = oit.next();
                  if (o instanceof IOperation) {
                    IOperation op = (IOperation) o;
                    ObjectNode on = ops.addObject();
                    on.put("name", op.getName());
                    on.put("returnType", op.getReturnTypeAsString());
                    on.put("visibility", op.getVisibility());
                    on.put("abstract", op.isAbstract());
                    ArrayNode ps = on.putArray("params");
                    for (IParameter p : op.toParameterArray()) {
                      ps.add(p.getName() + ": " + p.getTypeAsString());
                    }
                  }
                }
                if (de instanceof IClassUIModel) {
                  c.put("robustnessIcon", ((IClassUIModel) de).isDisplayAsRobustnessAnalysisIcon());
                }
                c.putArray("bounds")
                    .add(de.getX())
                    .add(de.getY())
                    .add(de.getWidth())
                    .add(de.getHeight());
              } else if (model instanceof IRelationship) {
                IRelationship rel = (IRelationship) model;
                ObjectNode r = relationships.addObject();
                r.put("type", model.getModelType());
                r.put("from", nameOf(rel.getFrom()));
                r.put("to", nameOf(rel.getTo()));
                r.put("name", model.getName());
                if (model instanceof IGeneralization) {
                  r.put("parent", nameOf(rel.getFrom()));
                  r.put("child", nameOf(rel.getTo()));
                }
                if (model instanceof IAssociation) {
                  IAssociation assoc = (IAssociation) model;
                  putEnd(r.putObject("fromEnd"), (IAssociationEnd) assoc.getFromEnd());
                  putEnd(r.putObject("toEnd"), (IAssociationEnd) assoc.getToEnd());
                }
                if (de instanceof IConnectorUIModel) {
                  IConnectorUIModel conn = (IConnectorUIModel) de;
                  ArrayNode pts = r.putArray("points");
                  Point[] points = conn.getPoints();
                  if (points != null) {
                    for (Point pt : points) {
                      pts.addArray().add(pt.x).add(pt.y);
                    }
                  }
                  r.putArray("fromDiff")
                      .add(conn.getFromShapeXDiff())
                      .add(conn.getFromShapeYDiff());
                  r.putArray("toDiff").add(conn.getToShapeXDiff()).add(conn.getToShapeYDiff());
                  r.put(
                      "shapeFrom",
                      conn.getFromShape() == null
                          ? null
                          : nameOf(conn.getFromShape().getModelElement()));
                  r.put(
                      "shapeTo",
                      conn.getToShape() == null
                          ? null
                          : nameOf(conn.getToShape().getModelElement()));
                  r.putArray("connectorBounds")
                      .add(conn.getX())
                      .add(conn.getY())
                      .add(conn.getWidth())
                      .add(conn.getHeight());
                  if (conn instanceof IHasRoleConnectorUIModel) {
                    IHasRoleConnectorUIModel hr = (IHasRoleConnectorUIModel) conn;
                    putRect(r, "multA", hr.getMultiplicityARectangle());
                    putRect(r, "multB", hr.getMultiplicityBRectangle());
                    putRect(r, "roleA", hr.getRoleARectangle());
                    putRect(r, "roleB", hr.getRoleBRectangle());
                  }
                  ICaptionUIModel cap = conn.getCaptionUIModel();
                  if (cap != null) {
                    r.putArray("caption")
                        .add(cap.getX())
                        .add(cap.getY())
                        .add(cap.getWidth())
                        .add(cap.getHeight());
                  }
                }
              }
            }
            return JSON.writeValueAsString(root);
          });
    } catch (Exception e) {
      return "Error reading relationship details: " + e.getMessage();
    }
  }

  @Tool(
      name = "exportDiagramImage",
      description =
          "Open a diagram and export it as an image to an absolute file path ending in .png, .jpg"
              + " or .jpeg (a relative path would land inside Visual Paradigm's own folder)")
  public String exportDiagramImage(String diagramName, String filePath) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            File file = new File(filePath.trim());
            if (!file.isAbsolute()) {
              return "filePath must be absolute: " + filePath.trim();
            }
            String lower = file.getName().toLowerCase(Locale.ROOT);
            boolean jpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            if (!jpg && !lower.endsWith(".png")) {
              return "filePath must end in .png, .jpg or .jpeg: " + filePath.trim();
            }
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
              return "Cannot create directory: " + dir;
            }
            getDiagramManager().openDiagram(diagram);
            IDiagramUIModel active = getDiagramManager().getActiveDiagram();
            if (active == null || !active.getId().equals(diagram.getId())) {
              return "Diagram could not be activated: " + diagramName;
            }
            ExportDiagramAsImageOption exportOption =
                new ExportDiagramAsImageOption(
                    jpg
                        ? ExportDiagramAsImageOption.IMAGE_TYPE_JPG
                        : ExportDiagramAsImageOption.IMAGE_TYPE_PNG);
            ExportDiagramAsImageWatermark emptyWatermark = (graphics, width, height) -> {};
            exportOption.setWatermark(emptyWatermark);
            ApplicationManager.instance()
                .getModelConvertionManager()
                .exportActiveDiagramAsImage(file, exportOption);
            return file.isFile()
                ? "Exported '" + diagramName + "' to " + file + " (" + file.length() + " bytes)"
                : "Export did not produce a file: " + file;
          });
    } catch (Exception e) {
      return "Error exporting diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "newProject",
      description =
          "VP File > New Project: close the open project and start a new empty one (unsaved"
              + " changes of the current project are discarded); save it with saveProjectAs")
  public String newProject() {
    try {
      return runOnEdt(
          () -> {
            boolean ok =
                answeringDeletePrompts(
                    () -> ApplicationManager.instance().getProjectManager().newProject(),
                    new ArrayList<>());
            IProject project = DiagramUtils.getProject();
            return ok && project != null
                ? "Created new project: " + project.getName()
                : "New project failed";
          });
    } catch (Exception e) {
      return "Error creating project: " + e.getMessage();
    }
  }

  @Tool(
      name = "openProject",
      description =
          "VP File > Open: open an existing .vpp file, replacing the open project (save it first;"
              + " VP may ask about unsaved changes)")
  public String openProject(String filePath) {
    try {
      return runOnEdt(
          () -> {
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            File file = new File(filePath.trim());
            if (!file.isFile()) {
              return "File does not exist: " + file;
            }
            boolean ok = ApplicationManager.instance().getProjectManager().openProject(file);
            IProject project = DiagramUtils.getProject();
            return ok && project != null
                ? "Opened project " + project.getProjectFile()
                : "Open failed for " + file;
          });
    } catch (Exception e) {
      return "Error opening project: " + e.getMessage();
    }
  }

  @Tool(
      name = "saveProjectAs",
      description =
          "VP File > Save As: save the open project to a new .vpp path (parent folder must exist,"
              + " existing files are not overwritten); the new file becomes the open project")
  public String saveProjectAs(String filePath) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            String path = filePath.trim();
            File file =
                new File(path.toLowerCase(Locale.ROOT).endsWith(".vpp") ? path : path + ".vpp");
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir == null || !dir.isDirectory()) {
              return "Folder does not exist: " + dir;
            }
            if (file.exists()) {
              return "File already exists (not overwritten): " + file;
            }
            boolean ok = ApplicationManager.instance().getProjectManager().saveProjectAs(file);
            IProject project = DiagramUtils.getProject();
            return ok
                ? "Saved project as " + project.getProjectFile()
                : "Save As failed for " + file;
          });
    } catch (Exception e) {
      return "Error in Save As: " + e.getMessage();
    }
  }

  @Tool(name = "saveProject", description = "Save the currently open project to its file")
  public String saveProject() {
    try {
      return runOnEdt(
          () -> {
            IProject project = requireProject();
            boolean ok = ApplicationManager.instance().getProjectManager().saveProject();
            return (ok ? "Saved project to " : "Save failed for ") + project.getProjectFile();
          });
    } catch (Exception e) {
      return "Error saving project: " + e.getMessage();
    }
  }

  @Tool(
      name = "getProjectInfo",
      description = "Name and file path of the project currently open in Visual Paradigm")
  public String getProjectInfo() {
    try {
      return runOnEdt(
          () -> {
            IProject project = DiagramUtils.getProject();
            if (project == null) {
              return "No project is open";
            }
            ObjectNode root = JSON.createObjectNode();
            root.put("name", project.getName());
            File file = project.getProjectFile();
            root.put("file", file != null ? file.getAbsolutePath() : null);
            root.put("diagramCount", DiagramUtils.findAllDiagrams(IDiagramUIModel.class).size());
            return JSON.writeValueAsString(root);
          });
    } catch (Exception e) {
      return "Error reading project info: " + e.getMessage();
    }
  }

  @Tool(
      name = "rerouteConnectors",
      description =
          "Route every connector of a diagram again around the shapes where they stand: every"
              + " line is horizontal or vertical (right-angle elbows), clear of the other shapes,"
              + " ends spread over the sides, labels beside the lines. Run after moving shapes;"
              + " autoLayoutDiagram also moves the shapes")
  public String rerouteConnectors(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            // a family with its own connector routing (BPMN, activity...) re-routes its diagram
            for (AbstractDiagramMcpTools family : families()) {
              int done = family.rerouteOwnConnectors(diagram);
              if (done >= 0) {
                return "Rerouted " + done + " connector(s) on '" + diagramName + "'";
              }
            }
            LayoutProfile profile = profileOf(diagram);
            if (profile != null) {
              activate(diagram);
              VpGraphLayout.Summary summary = VpGraphLayout.route(diagram, profile);
              settleReopen(diagram, profile);
              return "Rerouted " + summary.connectors + " connector(s) on '" + diagramName + "'";
            }
            return "Diagram '" + diagramName + "' has no connectors to route again";
          });
    } catch (Exception e) {
      return "Error rerouting connectors: " + e.getMessage();
    }
  }

  @Tool(
      name = "layoutConnectorLabels",
      description =
          "Put the labels of a diagram's connectors (multiplicities at the ends, names beside the"
              + " longest leg) clear of shapes, lines and each other. The connectors are routed"
              + " again around the shapes, which stay where they are")
  public String layoutConnectorLabels(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            LayoutProfile profile = profileOf(diagram);
            if (profile == null) {
              return "Diagram '" + diagramName + "' has no connector labels to lay out";
            }
            activate(diagram);
            VpGraphLayout.Summary summary = VpGraphLayout.route(diagram, profile);
            settleReopen(diagram, profile);
            return "Laid out " + summary.labels + " label(s) on '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error laying out labels: " + e.getMessage();
    }
  }

  /**
   * Refit every view of a class after its content changed and lay the diagram out again (the boxes
   * grew, so the places and routes change).
   */
  private void refitClass(IClass cls) {
    IDiagramElement[] views = cls.getDiagramElements();
    if (views == null) {
      return;
    }
    java.util.Set<String> laidOut = new java.util.HashSet<>();
    for (IDiagramElement view : views) {
      fitShape(view);
      IDiagramUIModel diagram = view.getDiagramUIModel();
      if (diagram != null && laidOut.add(diagram.getId())) {
        relayout(diagram);
      }
    }
  }

  private static void putRect(ObjectNode node, String key, java.awt.Rectangle rect) {
    if (rect != null) {
      node.putArray(key).add(rect.x).add(rect.y).add(rect.width).add(rect.height);
    }
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private static boolean isSet(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String nameOf(IModelElement element) {
    return element != null ? element.getName() : null;
  }

  private static void putEnd(ObjectNode node, IAssociationEnd end) {
    if (end == null) {
      return;
    }
    node.put("class", nameOf(end.getTypeAsElement()));
    node.put("multiplicity", end.getMultiplicity());
    node.put("aggregation", end.getAggregationKind());
    node.put("role", end.getName());
    int nav = end.getNavigable();
    node.put(
        "navigable",
        nav == IAssociationEnd.NAVIGABLE_NAVIGABLE
            ? "true"
            : nav == IAssociationEnd.NAVIGABLE_NON_NAVIGABLE ? "false" : "unspecified");
  }

  private static Integer navigableCode(String value) {
    switch (value.trim().toLowerCase()) {
      case "true":
        return IAssociationEnd.NAVIGABLE_NAVIGABLE;
      case "false":
        return IAssociationEnd.NAVIGABLE_NON_NAVIGABLE;
      case "unspecified":
        return IAssociationEnd.NAVIGABLE_UNSPECIFIED;
      default:
        return null;
    }
  }

  private IClass findClassOnDiagram(IDiagramUIModel diagram, String className) {
    IDiagramElement de = findDiagramElementByName(diagram, className);
    if (de != null && de.getModelElement() instanceof IClass) {
      return (IClass) de.getModelElement();
    }
    return null;
  }

  /**
   * Relationships drawn on the diagram between two named classes. Generalization, dependency and
   * realization match the given direction; association kinds match either direction.
   */
  private List<IRelationship> findRelationships(
      IDiagramUIModel diagram, String fromClass, String toClass, String relationshipType) {
    String type = relationshipType == null ? "" : relationshipType.trim().toLowerCase(Locale.ROOT);
    List<IRelationship> result = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (!(model instanceof IRelationship) || result.contains(model)) {
        continue;
      }
      IRelationship rel = (IRelationship) model;
      String from = nameOf(rel.getFrom());
      String to = nameOf(rel.getTo());
      boolean forward = fromClass.equals(from) && toClass.equals(to);
      boolean backward = fromClass.equals(to) && toClass.equals(from);
      boolean typeMatch;
      boolean directionMatch = forward;
      switch (type) {
        case "generalization":
          // fromClass is the child; VP stores the parent as the model's "from" end
          typeMatch = model instanceof IGeneralization;
          directionMatch = backward;
          break;
        case "dependency":
          typeMatch = model instanceof IDependency;
          break;
        case "realization":
          typeMatch = model instanceof IRealization;
          break;
        case "association":
          typeMatch = model instanceof IAssociation;
          directionMatch = forward || backward;
          break;
        case "aggregation":
        case "composition":
          typeMatch =
              model instanceof IAssociation
                  && hasAggregationKind(
                      (IAssociation) model, "aggregation".equals(type) ? "shared" : "composite");
          directionMatch = forward || backward;
          break;
        default:
          typeMatch = false;
      }
      if (typeMatch && directionMatch) {
        result.add(rel);
      }
    }
    return result;
  }

  /** Whether either end of the association is "shared" or "composite" ({@code kind}). */
  private static boolean hasAggregationKind(IAssociation assoc, String kind) {
    return kind.equals(aggregationOf((IAssociationEnd) assoc.getFromEnd()))
        || kind.equals(aggregationOf((IAssociationEnd) assoc.getToEnd()));
  }

  private IShapeUIModel findPackageShape(IDiagramUIModel diagram, String packageName) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel
          && de.getModelElement() instanceof IPackage
          && packageName.equals(de.getModelElement().getName())) {
        return (IShapeUIModel) de;
      }
    }
    return null;
  }

  /** Whether {@code candidate} inherits (directly or not) from {@code ancestor}. */
  private static boolean isDescendant(IModelElement candidate, IModelElement ancestor) {
    java.util.Deque<IModelElement> pending = new java.util.ArrayDeque<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    pending.add(ancestor);
    while (!pending.isEmpty()) {
      IModelElement node = pending.poll();
      if (!seen.add(node.getId())) {
        continue;
      }
      // VP stores a generalization from the general class to the specific one
      for (com.vp.plugin.model.ISimpleRelationship rel : node.toFromRelationshipArray()) {
        if (rel instanceof IGeneralization && rel.getTo() != null) {
          if (rel.getTo().getId().equals(candidate.getId())) {
            return true;
          }
          pending.add(rel.getTo());
        }
      }
    }
    return false;
  }

  /** VP's visibility keyword for public/private/protected/package (or + - # ~), else null. */
  private static String visibilityKeyword(String visibility) {
    switch (visibility.trim().toLowerCase(Locale.ROOT)) {
      case "public":
      case "+":
        return "public";
      case "private":
      case "-":
        return "private";
      case "protected":
      case "#":
        return "protected";
      case "package":
      case "~":
        return "package";
      default:
        return null;
    }
  }

  /** The class called {@code name} directly owned by {@code owner}, or null. */
  private static IClass ownedClass(IPackage owner, String name) {
    IModelElement[] children = owner.toChildArray();
    if (children != null) {
      for (IModelElement child : children) {
        if (child instanceof IClass && name.equals(child.getName())) {
          return (IClass) child;
        }
      }
    }
    return null;
  }

  private IPackage findOrCreateModelPackage(String packageName) {
    IPackage existing = DiagramUtils.findModelElementByName(packageName, IPackage.class);
    if (existing != null) {
      return existing;
    }
    IPackage pkg = getModelElementFactory().createPackage();
    pkg.setName(packageName);
    return pkg;
  }

  /**
   * The frame of a package on the diagram: the one already drawn, else a new view of the package of
   * that name in the project (a second package of the name would be renamed "Package2" by Visual
   * Paradigm), else a new package. The fill is set when a color is given.
   */
  private IShapeUIModel packageFrame(IClassDiagramUIModel diagram, String name, String color) {
    IShapeUIModel frame = findPackageShape(diagram, name);
    if (frame == null) {
      IPackage pkg = findRootElement(name, IPackage.class);
      if (pkg == null) {
        pkg = getModelElementFactory().createPackage();
        nameInOwner(pkg, null, name);
      }
      IDiagramElement de = addToDiagram(diagram, pkg, name);
      if (!(de instanceof IShapeUIModel)) {
        throw new IllegalStateException("Visual Paradigm did not draw a package shape for " + name);
      }
      frame = (IShapeUIModel) de;
    }
    if (isSet(color)) {
      frame.getFillColor().setColor1(parseColor(color), true);
    }
    return frame;
  }

  /** A color from "#RRGGBB" (or 0xRRGGBB), null when the text is none. */
  private static Color parseColor(String text) {
    try {
      return Color.decode(text.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
