package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.util.ClassDiagramUtils;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IClassDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IRealization;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.Iterator;
import java.util.List;
import com.brunnen.vp.mcp.tool.Tool;

/** MCP tools for Visual Paradigm Class diagram operations. */
public class ClassDiagramMcpTools extends AbstractDiagramMcpTools {

  @Tool(name = "createClassDiagram", description = "Create a new class diagram in Visual Paradigm")
  public String createClassDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created class diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating class diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addClass", description = "Add a class to a class diagram")
  public String addClass(String diagramName, String className) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IClass cls = getModelElementFactory().createClass();
            cls.setName(className);
            addToDiagram(diagram, cls);

            return "Added class '" + className + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding class: " + e.getMessage();
    }
  }

  @Tool(name = "addAttribute", description = "Add an attribute to a class. Type can be empty for analysis phase.")
  public String addAttribute(String className, String attributeName, String attributeType, String visibility) {
    try {
      return runOnEdt(
          () -> {
            IClass cls = ClassDiagramUtils.findClassByName(className);
            if (cls == null) {
              return "Class not found: " + className;
            }

            IAttribute attr = getModelElementFactory().createAttribute();
            attr.setName(attributeName);
            if (attributeType != null && !attributeType.trim().isEmpty()) {
              attr.setType(attributeType.trim());
            }
            if (visibility != null && !visibility.trim().isEmpty()) {
              attr.setVisibility(visibility.trim());
            }
            cls.addAttribute(attr);

            return "Added attribute '" + attributeName + "' to class '" + className + "'";
          });
    } catch (Exception e) {
      return "Error adding attribute: " + e.getMessage();
    }
  }

  @Tool(name = "addOperation", description = "Add an operation/method to a class")
  public String addOperation(String className, String operationName, String returnType, String params) {
    try {
      return runOnEdt(
          () -> {
            IClass cls = ClassDiagramUtils.findClassByName(className);
            if (cls == null) {
              return "Class not found: " + className;
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
            cls.addOperation(op);

            return "Added operation '" + operationName + "' to class '" + className + "'";
          });
    } catch (Exception e) {
      return "Error adding operation: " + e.getMessage();
    }
  }

  @Tool(name = "addAssociation", description = "Add an association between two classes in a class diagram")
  public String addAssociation(
      String diagramName, String fromClass, String toClass, String fromMultiplicity, String toMultiplicity, String name) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = ClassDiagramUtils.findClassByName(fromClass);
            IClass target = ClassDiagramUtils.findClassByName(toClass);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: "
                  + (fromElement == null ? fromClass : toClass);
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
            getDiagramManager().createConnector(diagram, assoc, fromElement, toElement, null);

            return "Added association from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding association: " + e.getMessage();
    }
  }

  @Tool(name = "addGeneralization", description = "Add a generalization (inheritance) relationship between classes")
  public String addGeneralization(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = ClassDiagramUtils.findClassByName(fromClass);
            IClass target = ClassDiagramUtils.findClassByName(toClass);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: "
                  + (fromElement == null ? fromClass : toClass);
            }

            IGeneralization gen = getModelElementFactory().createGeneralization();
            gen.setFrom(source);
            gen.setTo(target);
            getDiagramManager().createConnector(diagram, gen, fromElement, toElement, null);

            return "Added generalization from '" + fromClass + "' extends '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding generalization: " + e.getMessage();
    }
  }

  @Tool(name = "addAggregation", description = "Add an aggregation relationship between classes (diamond open)")
  public String addAggregation(
      String diagramName, String fromClass, String toClass, String fromMultiplicity, String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = ClassDiagramUtils.findClassByName(fromClass);
            IClass target = ClassDiagramUtils.findClassByName(toClass);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: "
                  + (fromElement == null ? fromClass : toClass);
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
            getDiagramManager().createConnector(diagram, assoc, fromElement, toElement, null);

            return "Added aggregation from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding aggregation: " + e.getMessage();
    }
  }

  @Tool(name = "addComposition", description = "Add a composition relationship between classes (diamond filled)")
  public String addComposition(
      String diagramName, String fromClass, String toClass, String fromMultiplicity, String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = ClassDiagramUtils.findClassByName(fromClass);
            IClass target = ClassDiagramUtils.findClassByName(toClass);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: "
                  + (fromElement == null ? fromClass : toClass);
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
            getDiagramManager().createConnector(diagram, assoc, fromElement, toElement, null);

            return "Added composition from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding composition: " + e.getMessage();
    }
  }

  @Tool(name = "addDependency", description = "Add a dependency relationship between classes (dashed arrow)")
  public String addDependency(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = ClassDiagramUtils.findClassByName(fromClass);
            IClass target = ClassDiagramUtils.findClassByName(toClass);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: "
                  + (fromElement == null ? fromClass : toClass);
            }

            IDependency dep = getModelElementFactory().createDependency();
            dep.setFrom(source);
            dep.setTo(target);
            getDiagramManager().createConnector(diagram, dep, fromElement, toElement, null);

            return "Added dependency from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding dependency: " + e.getMessage();
    }
  }

  @Tool(name = "addRealization", description = "Add a realization (implements) relationship between classes")
  public String addRealization(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = ClassDiagramUtils.findClassByName(fromClass);
            IClass target = ClassDiagramUtils.findClassByName(toClass);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: "
                  + (fromElement == null ? fromClass : toClass);
            }

            IRealization real = getModelElementFactory().createRealization();
            real.setFrom(source);
            real.setTo(target);
            getDiagramManager().createConnector(diagram, real, fromElement, toElement, null);

            return "Added realization from '" + fromClass + "' implements '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding realization: " + e.getMessage();
    }
  }

  @Tool(name = "addInterface", description = "Add an interface to a class diagram")
  public String addInterface(String diagramName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IClass iface = getModelElementFactory().createClass();
            iface.setName(interfaceName);
            iface.addStereotype("Interface");
            addToDiagram(diagram, iface);

            return "Added interface '" + interfaceName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding interface: " + e.getMessage();
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
              return "Diagram not found: " + diagramName;
            }

            List<IClass> classes = ClassDiagramUtils.getClassesInDiagram(diagram);
            List<IAssociation> associations = ClassDiagramUtils.getAssociationsInDiagram(diagram);

            int totalAttributes = 0;
            int totalOperations = 0;
            for (IClass cls : classes) {
              Iterator<?> attrIter = cls.attributeIterator();
              while (attrIter.hasNext()) {
                attrIter.next();
                totalAttributes++;
              }
              Iterator<?> opIter = cls.operationIterator();
              while (opIter.hasNext()) {
                opIter.next();
                totalOperations++;
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("CLASS DIAGRAM REPORT: ").append(diagramName).append("\n");
            report.append("=====================================\n");
            report.append("Classes: ").append(classes.size()).append("\n");
            report.append("Attributes: ").append(totalAttributes).append("\n");
            report.append("Operations: ").append(totalOperations).append("\n");
            report.append("Associations: ").append(associations.size()).append("\n");
            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

}
