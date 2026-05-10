package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.SequenceDiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IInteractionDiagramUIModel;
import com.vp.plugin.model.IActivation;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.ICombinedFragment;
import com.vp.plugin.model.IInteractionConstraint;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionOperand;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.List;
import com.brunnen.vp.mcp.tool.Tool;

/** MCP tools for Visual Paradigm Sequence diagram operations. */
public class SequenceDiagramMcpTools extends AbstractDiagramMcpTools {

  @Tool(name = "createSequenceDiagram", description = "Create a new sequence diagram in Visual Paradigm")
  public String createSequenceDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created sequence diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating sequence diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addLifeline", description = "Add a lifeline (participant) to a sequence diagram")
  public String addLifeline(String diagramName, String lifelineName, String className) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            // Create a class as the base classifier for the lifeline
            IClass baseClass = getModelElementFactory().createClass();
            baseClass.setName(className != null && !className.trim().isEmpty() ? className : lifelineName);

            IInteractionLifeLine lifeline = getModelElementFactory().createInteractionLifeLine();
            lifeline.setName(lifelineName);
            lifeline.setBaseClassifier(baseClass);

            // Add to diagram
            addToDiagram(diagram, lifeline);

            return "Added lifeline '" + lifelineName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding lifeline: " + e.getMessage();
    }
  }

  @Tool(name = "addActivation", description = "Add an activation bar to a lifeline in a sequence diagram")
  public String addActivation(String diagramName, String lifelineName) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IInteractionLifeLine lifeline =
                SequenceDiagramUtils.findLifelineByName(diagram, lifelineName);
            if (lifeline == null) {
              return "Lifeline not found: " + lifelineName;
            }

            IActivation activation = getModelElementFactory().createActivation();
            lifeline.addActivation(activation);
            getDiagramManager().createDiagramElement(diagram, activation);

            return "Added activation to lifeline '" + lifelineName + "'";
          });
    } catch (Exception e) {
      return "Error adding activation: " + e.getMessage();
    }
  }

  @Tool(name = "addMessage", description = "Add a message between two lifelines in a sequence diagram")
  public String addMessage(
      String diagramName,
      String fromLifeline,
      String toLifeline,
      String messageName,
      String sequenceNumber,
      String messageType) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IInteractionLifeLine from =
                SequenceDiagramUtils.findLifelineByName(diagram, fromLifeline);
            if (from == null) {
              return "From lifeline not found: " + fromLifeline;
            }

            IInteractionLifeLine to =
                SequenceDiagramUtils.findLifelineByName(diagram, toLifeline);
            if (to == null) {
              return "To lifeline not found: " + toLifeline;
            }

            IMessage message = getModelElementFactory().createMessage();
            message.setName(messageName);
            if (sequenceNumber != null && !sequenceNumber.trim().isEmpty()) {
              message.setSequenceNumber(sequenceNumber.trim());
            }

            // Set message type
            if ("asynch".equalsIgnoreCase(messageType) || "async".equalsIgnoreCase(messageType)) {
              message.setAsynchronous(true);
            } else {
              message.setAsynchronous(false);
            }

            // Find or create activations
            IActivation fromAct = findOrCreateActivation(from, diagram);
            IActivation toAct = findOrCreateActivation(to, diagram);
            message.setFromActivation(fromAct);
            message.setToActivation(toAct);

            getDiagramManager().createDiagramElement(diagram, message);

            return "Added message '" + messageName + "' from '" + fromLifeline + "' to '" + toLifeline + "'";
          });
    } catch (Exception e) {
      return "Error adding message: " + e.getMessage();
    }
  }

  @Tool(name = "addReturnMessage", description = "Add a return message between two lifelines in a sequence diagram")
  public String addReturnMessage(
      String diagramName, String fromLifeline, String toLifeline, String messageName, String sequenceNumber) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IInteractionLifeLine from =
                SequenceDiagramUtils.findLifelineByName(diagram, fromLifeline);
            if (from == null) {
              return "From lifeline not found: " + fromLifeline;
            }

            IInteractionLifeLine to =
                SequenceDiagramUtils.findLifelineByName(diagram, toLifeline);
            if (to == null) {
              return "To lifeline not found: " + toLifeline;
            }

            IMessage message = getModelElementFactory().createMessage();
            message.setName(messageName);
            if (sequenceNumber != null && !sequenceNumber.trim().isEmpty()) {
              message.setSequenceNumber(sequenceNumber.trim());
            }
            message.setAsynchronous(false);

            IActivation fromAct = findOrCreateActivation(from, diagram);
            IActivation toAct = findOrCreateActivation(to, diagram);
            message.setFromActivation(fromAct);
            message.setToActivation(toAct);

            getDiagramManager().createDiagramElement(diagram, message);

            return "Added return message '" + messageName + "' from '" + fromLifeline + "' to '" + toLifeline + "'";
          });
    } catch (Exception e) {
      return "Error adding return message: " + e.getMessage();
    }
  }

  @Tool(name = "addCombinedFragment", description = "Add a combined fragment (alt/opt/loop) to a sequence diagram")
  public String addCombinedFragment(
      String diagramName, String operator, String guard, String coveredLifelines) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            ICombinedFragment fragment = getModelElementFactory().createCombinedFragment();

            // Set operator type
            if ("alt".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_ALT);
            } else if ("opt".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_OPT);
            } else if ("loop".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_LOOP);
            } else if ("break".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_BREAK);
            } else if ("par".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_PAR);
            } else {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_ALT);
            }

            // Create operand with guard
            IInteractionOperand operand = getModelElementFactory().createInteractionOperand();
            if (guard != null && !guard.trim().isEmpty()) {
              IInteractionConstraint constraint = getModelElementFactory().createInteractionConstraint();
              constraint.setConstraint(guard.trim());
              operand.setGuard(constraint);
            }
            fragment.addOperand(operand);

            // Add covered lifelines
            if (coveredLifelines != null && !coveredLifelines.trim().isEmpty()) {
              for (String lifelineName : coveredLifelines.split(",")) {
                IInteractionLifeLine lifeline =
                    SequenceDiagramUtils.findLifelineByName(diagram, lifelineName.trim());
                if (lifeline != null) {
                  fragment.addCoveredLifeLine(lifeline);
                }
              }
            }

            getDiagramManager().createDiagramElement(diagram, fragment);

            return "Added " + operator + " fragment to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding combined fragment: " + e.getMessage();
    }
  }

  @Tool(name = "generateSequenceReport", description = "Generate a sequence diagram analysis report")
  public String generateSequenceReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IInteractionLifeLine> lifelines =
                SequenceDiagramUtils.getAllLifelines(diagram);
            List<IMessage> messages = SequenceDiagramUtils.getAllMessages(diagram);

            StringBuilder report = new StringBuilder();
            report.append("SEQUENCE DIAGRAM REPORT: ").append(diagramName).append("\n");
            report.append("=====================================\n");
            report.append("Lifelines: ").append(lifelines.size()).append("\n");
            report.append("Messages: ").append(messages.size()).append("\n");
            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  private IActivation findOrCreateActivation(IInteractionLifeLine lifeline, IInteractionDiagramUIModel diagram) {
    // Try to find existing activation
    java.util.Iterator<?> iter = lifeline.activationIterator();
    if (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IActivation) {
        return (IActivation) obj;
      }
    }

    // Create new activation if none exists
    IActivation activation = getModelElementFactory().createActivation();
    lifeline.addActivation(activation);
    getDiagramManager().createDiagramElement(diagram, activation);
    return activation;
  }

}
