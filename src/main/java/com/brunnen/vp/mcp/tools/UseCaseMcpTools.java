package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.Iterator;
import java.util.List;
import com.brunnen.vp.mcp.tool.Tool;

/** MCP tools for Visual Paradigm Use Case diagram operations. */
public class UseCaseMcpTools extends AbstractDiagramMcpTools {

  @Tool(name = "createUseCaseDiagram", description = "Create a new use case diagram in Visual Paradigm")
  public String createUseCaseDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created use case diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating use case diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addActor", description = "Add an actor to a use case diagram")
  public String addActor(String actorName, String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IActor actor = getModelElementFactory().createActor();
            actor.setName(actorName);
            addToDiagram(diagram, actor);

            return "Added actor '" + actorName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding actor: " + e.getMessage();
    }
  }

  @Tool(name = "addUseCase", description = "Add a use case to a use case diagram")
  public String addUseCase(String useCaseName, String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IUseCase useCase = getModelElementFactory().createUseCase();
            useCase.setName(useCaseName);
            addToDiagram(diagram, useCase);

            return "Added use case '" + useCaseName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding use case: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRelationship",
      description = "Add a relationship (Include/Extend/Association) between elements in a use case diagram")
  public String addRelationship(String sourceName, String targetName, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IUseCase source =
                DiagramUtils.findModelElementByName(sourceName, IUseCase.class);
            if (source == null) {
              source = findUseCaseInAnyDiagram(sourceName);
            }
            if (source == null) {
              return "Source use case not found: " + sourceName;
            }

            IUseCase target =
                DiagramUtils.findModelElementByName(targetName, IUseCase.class);
            if (target == null) {
              target = findUseCaseInAnyDiagram(targetName);
            }
            if (target == null) {
              return "Target use case not found: " + targetName;
            }

            IDiagramUIModel diagram = findDiagramContainingElement(source);
            if (diagram == null) {
              return "Source element not on any diagram: " + sourceName;
            }

            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Element not on diagram: "
                  + (fromElement == null ? sourceName : targetName);
            }

            DiagramManager dm = getDiagramManager();

            if ("Include".equalsIgnoreCase(relationshipType)) {
              IInclude include = getModelElementFactory().createInclude();
              include.setFrom(source);
              include.setTo(target);
              dm.createConnector(diagram, include, fromElement, toElement, null);
              return "Added Include relationship from '" + sourceName + "' to '" + targetName + "'";
            } else if ("Extend".equalsIgnoreCase(relationshipType)) {
              IExtend extend = getModelElementFactory().createExtend();
              extend.setFrom(source);
              extend.setTo(target);
              dm.createConnector(diagram, extend, fromElement, toElement, null);
              return "Added Extend relationship from '" + sourceName + "' to '" + targetName + "'";
            } else {
              return "Unknown relationship type: " + relationshipType + ". Use Include or Extend.";
            }
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  @Tool(name = "generateUseCaseReport", description = "Generate a use case analysis report for a diagram")
  public String generateReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            int useCaseCount = 0;
            int actorCount = 0;
            int relationshipCount = 0;

            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IModelElement model = ((IDiagramElement) obj).getModelElement();
                if (model instanceof IUseCase) {
                  useCaseCount++;
                } else if (model instanceof IActor) {
                  actorCount++;
                } else if (model instanceof IInclude || model instanceof IExtend) {
                  relationshipCount++;
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("USE CASE REPORT: ").append(diagramName).append("\n");
            report.append("================================\n");
            report.append("Actors: ").append(actorCount).append("\n");
            report.append("Use Cases: ").append(useCaseCount).append("\n");
            report.append("Relationships: ").append(relationshipCount).append("\n");
            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  private IUseCase findUseCaseInAnyDiagram(String name) {
    List<IDiagramUIModel> diagrams =
        DiagramUtils.findAllDiagrams(IUseCaseDiagramUIModel.class);
    for (IDiagramUIModel diagram : diagrams) {
      Iterator<?> iter = diagram.diagramElementIterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        if (obj instanceof IDiagramElement) {
          IModelElement model = ((IDiagramElement) obj).getModelElement();
          if (model instanceof IUseCase && name.equals(model.getName())) {
            return (IUseCase) model;
          }
        }
      }
    }
    return null;
  }

  private IDiagramUIModel findDiagramContainingElement(IModelElement element) {
    List<IDiagramUIModel> diagrams =
        DiagramUtils.findAllDiagrams(IUseCaseDiagramUIModel.class);
    for (IDiagramUIModel diagram : diagrams) {
      Iterator<?> iter = diagram.diagramElementIterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        if (obj instanceof IDiagramElement) {
          IModelElement model = ((IDiagramElement) obj).getModelElement();
          if (model == element) {
            return diagram;
          }
        }
      }
    }
    return null;
  }

}
