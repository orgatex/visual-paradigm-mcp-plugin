package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.brunnen.vp.mcp.util.StateLayoutProfile;
import com.brunnen.vp.mcp.util.StateMachineUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IStateDiagramUIModel;
import com.vp.plugin.model.IActivity;
import com.vp.plugin.model.IFinalState2;
import com.vp.plugin.model.IInitialPseudoState;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IState2;
import com.vp.plugin.model.ITransition2;

/**
 * MCP tools for Visual Paradigm UML state machine diagrams.
 *
 * <p>Models the UML 2 state machine elements: {@link IState2} for named states (entry / do / exit
 * behaviours as {@link IActivity} models, which VP draws in the state's internal activity
 * compartment), {@link IInitialPseudoState} and {@link IFinalState2} for the start/end pseudo
 * states, and {@link ITransition2} for the arrows. Transition labels follow the UML text form
 * {@code event [guard] / effect} and are stored as the transition name so Visual Paradigm renders
 * them on the connector.
 *
 * <p>The diagram is laid out by the shared layered layout ({@link StateLayoutProfile}): every
 * state, transition and label gets its place whenever something is added, every transition is a
 * horizontal or vertical line (right-angle elbows, rectangular loops).
 */
public class StateMachineDiagramMcpTools extends AbstractDiagramMcpTools {

  /** Smallest state box: short names still get a readable rounded rectangle. */
  private static final int STATE_MIN_WIDTH = 100;

  private static final int STATE_MIN_HEIGHT = 30;

  /** Height of a state that shows only its name. */
  private static final int NAME_ONLY_HEIGHT = 30;

  /** Default size of the initial/final pseudo state circles. */
  private static final int PSEUDO_SIZE = 30;

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return diagram instanceof IStateDiagramUIModel ? new StateLayoutProfile() : null;
  }

  @Tool(
      name = "createStateDiagram",
      description =
          "Create a new UML state machine diagram in Visual Paradigm. States auto-fit their text")
  public String createStateDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_STATE_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created state machine diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating state machine diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addState",
      description =
          "Add a named state to a state machine diagram. entryAction/doActivity/exitAction are "
              + "optional behaviours shown in the state's compartment as 'entry / ...', "
              + "'do / ...', 'exit / ...' (pass \"\" to omit). The box is fitted to its text. "
              + "x/y (>0) place the state explicitly (the transitions are routed around it), "
              + "otherwise the layout puts it on the row its transitions lead to (the book's "
              + "top-to-bottom screen flow, branches side by side)")
  public String addState(
      String diagramName,
      String stateName,
      String entryAction,
      String doActivity,
      String exitAction,
      int x,
      int y) {
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram =
                findDiagram(diagramName, IStateDiagramUIModel.class, "state machine");
            if (!StateMachineUtils.isValidStateName(stateName)) {
              return "State name must not be empty";
            }
            String name = stateName.trim();
            if (findDiagramElementByName(diagram, name) != null) {
              return "State already on diagram: " + name;
            }

            IState2 state = getModelElementFactory().createState2();
            state.setName(name);
            setBehaviour(state, entryAction, Behaviour.ENTRY);
            setBehaviour(state, doActivity, Behaviour.DO);
            setBehaviour(state, exitAction, Behaviour.EXIT);
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, state);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding state: Visual Paradigm did not create a shape for " + name;
            }
            // Fresh shapes render blank until the caption is reset; fit afterwards so the box
            // grows to show the internal activity compartment.
            de.resetCaption();
            fitShape(de);
            int width = Math.max(de.getWidth(), STATE_MIN_WIDTH);
            int height = Math.max(de.getHeight(), STATE_MIN_HEIGHT);
            boolean nameOnly =
                state.getEntry() == null
                    && state.getDoActivity() == null
                    && state.getExit() == null;
            if (nameOnly) {
              // the name sits at the top of the box: a box hardly taller than the name reads as
              // centered, as in the book (Visual Paradigm's fit leaves an empty lower half)
              height = NAME_ONLY_HEIGHT;
            }
            de.setBounds(de.getX(), de.getY(), width, height);
            if (nameOnly && de instanceof IShapeUIModel) {
              keepSize((IShapeUIModel) de);
            }
            placeNew(diagram, de, x, y);
            // the name is a detached label laid out where the caption was last reset
            de.resetCaption();
            relayout(diagram);
            return "Added state '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + " "
                + de.getWidth()
                + "x"
                + de.getHeight();
          });
    } catch (Exception e) {
      return "Error adding state: " + e.getMessage();
    }
  }

  @Tool(
      name = "addInitialState",
      description =
          "Add the initial pseudo state (filled circle) to a state machine diagram. stateName is "
              + "the handle used by addTransition AND the label Visual Paradigm draws under the "
              + "circle, so use a display-friendly name like \"Start\". x/y (>0) place it "
              + "explicitly")
  public String addInitialState(String diagramName, String stateName, int x, int y) {
    return addPseudoState(diagramName, stateName, x, y, true);
  }

  @Tool(
      name = "addFinalState",
      description =
          "Add a final state (bullseye) to a state machine diagram. stateName is the handle used "
              + "by addTransition AND the label Visual Paradigm draws under the shape, so use a "
              + "display-friendly name like \"End\". x/y (>0) place it explicitly")
  public String addFinalState(String diagramName, String stateName, int x, int y) {
    return addPseudoState(diagramName, stateName, x, y, false);
  }

  @Tool(
      name = "addTransition",
      description =
          "Add a transition between two states of a state machine diagram. label is the UML text "
              + "form 'event [guard] / effect' (pass \"\" for an unlabelled transition). Use the "
              + "same state for fromState and toState to draw a self-transition (a rectangular "
              + "loop on a free side). Every transition is a horizontal or vertical line; two "
              + "opposite transitions between a pair of states run side by side. The diagram is "
              + "laid out again after each transition (autoLayoutDiagram does it on demand)")
  public String addTransition(String diagramName, String fromState, String toState, String label) {
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram =
                findDiagram(diagramName, IStateDiagramUIModel.class, "state machine");
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromState);
            if (fromElement == null) {
              return "State not on diagram: " + fromState;
            }
            IDiagramElement toElement = findDiagramElementByName(diagram, toState);
            if (toElement == null) {
              return "State not on diagram: " + toState;
            }

            IModelElement source = fromElement.getModelElement();
            IModelElement target = toElement.getModelElement();
            if (source == null || target == null) {
              return "State has no model element: " + (source == null ? fromState : toState);
            }

            if (target instanceof IInitialPseudoState) {
              return "An initial state cannot have incoming transitions: " + toState;
            }
            if (source instanceof IFinalState2) {
              return "A final state cannot have outgoing transitions: " + fromState;
            }
            String caption = StateMachineUtils.normalizeTransitionLabel(label);
            if (transitionExists(fromElement, toElement, caption)) {
              return "A transition '"
                  + fromState
                  + "' -> '"
                  + toState
                  + "'"
                  + (caption == null ? "" : " " + caption)
                  + " already exists on diagram '"
                  + diagramName
                  + "'";
            }
            ITransition2 transition = getModelElementFactory().createTransition2();
            IDiagramElement connector = null;
            try {
              transition.setFrom(source);
              transition.setTo(target);
              if (caption != null) {
                transition.setName(caption);
              }
              connector = connectShapes(diagram, transition, fromElement, toElement);
            } finally {
              if (connector == null) {
                transition.delete(); // no orphan transition model when VP refuses the arrow
              }
            }
            if (connector == null) {
              return "Error adding transition: Visual Paradigm refused the connector";
            }
            // every shape, line and label is laid out again around the new transition
            relayout(diagram);

            String suffix = caption == null ? "" : " " + caption;
            return "Added transition '" + fromState + "' -> '" + toState + "'" + suffix;
          });
    } catch (Exception e) {
      return "Error adding transition: " + e.getMessage();
    }
  }

  // --- Helpers ---

  private enum Behaviour {
    ENTRY,
    DO,
    EXIT
  }

  /** Model an entry/do/exit behaviour as an activity of the state; blank text means none. */
  private void setBehaviour(IState2 state, String text, Behaviour kind) {
    if (text == null || text.trim().isEmpty()) {
      return;
    }
    IActivity activity = getModelElementFactory().createActivity();
    activity.setName(text.trim());
    switch (kind) {
      case ENTRY:
        state.setEntry(activity);
        break;
      case DO:
        state.setDoActivity(activity);
        break;
      default:
        state.setExit(activity);
        break;
    }
  }

  private String addPseudoState(
      String diagramName, String stateName, int x, int y, boolean initial) {
    String kind = initial ? "initial state" : "final state";
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram =
                findDiagram(diagramName, IStateDiagramUIModel.class, "state machine");
            if (!StateMachineUtils.isValidStateName(stateName)) {
              return "State name must not be empty";
            }
            String name = stateName.trim();
            if (findDiagramElementByName(diagram, name) != null) {
              return "State already on diagram: " + name;
            }

            IModelElement state =
                initial
                    ? getModelElementFactory().createInitialPseudoState()
                    : getModelElementFactory().createFinalState2();
            state.setName(name);
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, state);
            if (de == null) {
              return "Error adding " + kind + ": Visual Paradigm did not create a shape";
            }
            de.setBounds(de.getX(), de.getY(), PSEUDO_SIZE, PSEUDO_SIZE);
            placeNew(diagram, de, x, y);
            // Visual Paradigm always draws the model name beside the circle, so pick a stateName
            // that reads well on the diagram (e.g. "Start"/"End"); it is also the handle used by
            // addTransition. The label is detached and laid out where the caption is reset.
            de.resetCaption();
            relayout(diagram);
            String another = "";
            if (initial) {
              for (IShapeUIModel other : diagram.toShapeUIModelArray()) {
                if (other.getModelElement() instanceof IInitialPseudoState
                    && !other.getId().equals(de.getId())) {
                  another =
                      "; WARNING: the diagram already has an initial state ('"
                          + other.getModelElement().getName()
                          + "'), a state machine has one";
                  break;
                }
              }
            }

            return "Added "
                + kind
                + " '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + another;
          });
    } catch (Exception e) {
      return "Error adding " + kind + ": " + e.getMessage();
    }
  }

  /** Whether {@code from} already has a transition to {@code to} with the same label. */
  private static boolean transitionExists(
      IDiagramElement from, IDiagramElement to, String caption) {
    IConnectorUIModel[] outgoing = from.toFromConnectorArray();
    for (IConnectorUIModel c : outgoing == null ? new IConnectorUIModel[0] : outgoing) {
      IShapeUIModel end = c.getToShape();
      IModelElement model = c.getModelElement();
      if (end != null
          && end.getId().equals(to.getId())
          && model instanceof ITransition2
          && StateMachineUtils.sameLabel(model.getName(), caption)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected String moveOwnShape(
      IDiagramUIModel diagram,
      IDiagramElement de,
      String name,
      int x,
      int y,
      int width,
      int height) {
    if (!(diagram instanceof IStateDiagramUIModel) || !(de instanceof IShapeUIModel)) {
      return null;
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    IModelElement model = shape.getModelElement();
    String note = "";
    if (model instanceof IInitialPseudoState || model instanceof IFinalState2) {
      // the circles keep their notation size (fitting would shrink them to the name's size)
      shape.setBounds(
          x, y, width > 0 ? width : shape.getWidth(), height > 0 ? height : shape.getHeight());
    } else {
      note = applyBounds(shape, x, y, width, height);
      if (width <= 0 || height <= 0) {
        shape.setBounds(
            shape.getX(),
            shape.getY(),
            Math.max(shape.getWidth(), STATE_MIN_WIDTH),
            Math.max(shape.getHeight(), STATE_MIN_HEIGHT));
      }
    }
    // explicitly placed: the layered layout leaves it here, the transitions go around it
    note += avoidOverlap(diagram, shape);
    pin(shape);
    relayout(diagram);
    return boundsResult(name, shape) + note;
  }
}
