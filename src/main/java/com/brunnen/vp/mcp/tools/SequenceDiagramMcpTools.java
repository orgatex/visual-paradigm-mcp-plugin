package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.SequenceDiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IInteractionDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivationUIModel;
import com.vp.plugin.model.IActivation;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.ICombinedFragment;
import com.vp.plugin.model.IInteractionConstraint;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionOperand;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** MCP tools for Visual Paradigm Sequence diagram operations. */
public class SequenceDiagramMcpTools extends AbstractDiagramMcpTools {

  // Per-execution activation bars follow the call stack (like the course sample image_12): a call
  // opens an activation on the callee, the matching return closes it. State is tracked by model id
  // because VP returns distinct proxies for the same element across calls.
  // diagramName -> lifelineId -> stack of OPEN activation ids (top = innermost execution).
  private final java.util.Map<String, java.util.Map<String, java.util.Deque<String>>> openStacks =
      new java.util.HashMap<>();
  // diagramName -> ids of every activation WE created (to delete VP's auto-created extras).
  private final java.util.Map<String, java.util.Set<String>> myActivations =
      new java.util.HashMap<>();
  // Messages anchor to lifelines, so VP creates one small activation per message end (fragmented).
  // We lay one continuous bar per lifeline over them (same blue, spanning first->last activity) so
  // the lifeline reads as a single execution bar. diagramName -> lifelineId -> continuous bar id.
  private final java.util.Map<String, java.util.Map<String, String>> continuousBarId =
      new java.util.HashMap<>();
  // Actor lifelines get their covered bar stretched the full diagram length (first message ->
  // last),
  // not just their own active span. diagramName -> set of actor lifeline ids.
  private final java.util.Map<String, java.util.Set<String>> actorLifelineIds =
      new java.util.HashMap<>();
  // Lifelines that got their destroy message: diagramName -> lifeline ids.
  private final java.util.Map<String, java.util.Set<String>> destroyedLifelines =
      new java.util.HashMap<>();

  @Tool(
      name = "createSequenceDiagram",
      description = "Create a new sequence diagram in Visual Paradigm")
  public String createSequenceDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM, diagramName);
            if (diagram instanceof IInteractionDiagramUIModel) {
              // Keep the numbers the tools give ("1", "1.1", "1.1.1"): with VP's automatic
              // handling every message is renumbered 1, 2, 3... by its position.
              ((IInteractionDiagramUIModel) diagram)
                  .setSequenceNumberHandling(IInteractionDiagramUIModel.MANUAL);
            }
            dm.openDiagram(diagram);
            openStacks.remove(diagramName);
            myActivations.remove(diagramName);
            continuousBarId.remove(diagramName);
            actorLifelineIds.remove(diagramName);
            destroyedLifelines.remove(diagramName);
            return "Created sequence diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating sequence diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addLifeline",
      description =
          "Add a lifeline (participant) to a sequence diagram. lifelineName is the head label and"
              + " the handle used by addMessage (unique on the diagram). className is the"
              + " classifier (\"\" = lifelineName; an existing class or actor of the project with"
              + " that name is reused, never modified). lifelineType: actor | boundary | control"
              + " | entity | participant or \"\" (plain box); boundary/control/entity draw the"
              + " robustness icon for this lifeline only. An actor lifeline is drawn with a box"
              + " head: Visual Paradigm does not draw a stick figure for a lifeline created"
              + " through the API (its stick-figure actor cannot anchor messages)")
  public String addLifeline(
      String diagramName,
      String lifelineName,
      String className,
      String lifelineType,
      String alias) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            String name = lifelineName == null ? "" : lifelineName.trim();
            if (name.isEmpty()) {
              return "Lifeline name must not be empty";
            }
            String type =
                lifelineType != null ? lifelineType.trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (!type.isEmpty() && !LIFELINE_TYPES.contains(type)) {
              return "Unknown lifelineType '"
                  + lifelineType
                  + "': use actor, boundary, control, entity, participant or \"\"";
            }
            if (SequenceDiagramUtils.findLifelineByName(diagram, name) != null) {
              return "Lifeline already on diagram: "
                  + name
                  + " (lifeline names are the handles used by addMessage)";
            }
            String classifierName =
                className != null && !className.trim().isEmpty() ? className.trim() : name;
            boolean isActor = "actor".equals(type);

            // Heads are as wide as their name (at least LIFELINE_W) and laid out left to right
            // after the widest head so far, keeping the usual gap between lifelines.
            int headWidth = Math.max(LIFELINE_W, estimateHeadWidth(name));
            int participantX = nextParticipantX(diagram);
            // Every participant is a real lifeline so its activation bars and message arrows anchor
            // correctly. An actor lifeline gets a real IActor as its base classifier.
            IInteractionLifeLine lifeline = getModelElementFactory().createInteractionLifeLine();
            // An actor/class of that name already in the project is the lifeline's classifier
            // (VP would rename a second root element of the same type and name).
            boolean reused;
            if (isActor) {
              IActor baseActor = findRootElement(classifierName, IActor.class);
              reused = baseActor != null;
              if (baseActor == null) {
                baseActor = getModelElementFactory().createActor();
                nameInOwner(baseActor, null, classifierName);
              }
              lifeline.setBaseClassifier(baseActor);
            } else {
              IClass baseClass = findRootElement(classifierName, IClass.class);
              reused = baseClass != null;
              if (baseClass == null) {
                baseClass = getModelElementFactory().createClass();
                nameInOwner(baseClass, null, classifierName);
              }
              lifeline.setBaseClassifier(baseClass);
            }
            if (ROBUSTNESS_TYPES.contains(type)) {
              // The robustness icon comes from the lifeline's own stereotype: the shared class is
              // left alone, so its boxes on class diagrams keep their notation.
              lifeline.setClassLevelStereotype(false);
              lifeline.addStereotype(type);
            }
            addToDiagram(diagram, lifeline, name);
            IShapeUIModel headShape = findLifelineShape(diagram, lifeline);
            if (headShape != null) {
              headShape.setBounds(participantX, LIFELINE_Y0, headWidth, LIFELINE_HEAD_H);
              if (headShape instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) {
                ((com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) headShape)
                    .setShowClassifier(false);
              }
            }
            if (alias != null && !alias.trim().isEmpty()) {
              lifeline.setNickname(alias.trim());
            }
            if (isActor) {
              actorLifelineIds
                  .computeIfAbsent(diagram.getName(), k -> new java.util.HashSet<>())
                  .add(lifeline.getId());
            }

            StringBuilder result = new StringBuilder();
            result
                .append("Added lifeline '")
                .append(name)
                .append("' to diagram '")
                .append(diagramName)
                .append("'");
            if (!type.isEmpty()) {
              result.append(" (type: ").append(type).append(")");
            }
            if (alias != null && !alias.trim().isEmpty()) {
              result.append(" (alias: ").append(alias.trim()).append(")");
            }
            if (headShape != null) {
              result
                  .append(" at ")
                  .append(headShape.getX())
                  .append(",")
                  .append(headShape.getY())
                  .append(" ")
                  .append(headShape.getWidth())
                  .append("x")
                  .append(headShape.getHeight());
            }
            if (reused) {
              result
                  .append(" (classifier: existing ")
                  .append(isActor ? "actor" : "class")
                  .append(" '")
                  .append(classifierName)
                  .append("', unchanged)");
            }
            return result.toString();
          });
    } catch (Exception e) {
      return "Error adding lifeline: " + e.getMessage();
    }
  }

  /** Accepted combined fragment operators. */
  private static final java.util.Set<String> FRAGMENT_OPERATORS =
      new java.util.HashSet<>(java.util.Arrays.asList("alt", "opt", "loop", "break", "par"));

  /** Accepted lifelineType values besides "". */
  private static final java.util.Set<String> LIFELINE_TYPES =
      new java.util.HashSet<>(
          java.util.Arrays.asList("actor", "boundary", "control", "entity", "participant"));

  /** Lifeline types drawn as a robustness analysis icon. */
  private static final java.util.Set<String> ROBUSTNESS_TYPES =
      new java.util.HashSet<>(java.util.Arrays.asList("boundary", "control", "entity"));

  @Tool(
      name = "addActivation",
      description = "Add an activation bar to a lifeline in a sequence diagram")
  public String addActivation(String diagramName, String lifelineName) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            IInteractionLifeLine lifeline =
                SequenceDiagramUtils.findLifelineByName(diagram, lifelineName);
            if (lifeline == null) {
              return "Lifeline not found: " + lifelineName;
            }

            IActivationUIModel shape = ensureOpenActivation(diagram, lifeline, MSG_TOP_Y);
            if (shape == null) {
              return "Could not create activation for lifeline '" + lifelineName + "'";
            }

            return "Added activation to lifeline '" + lifelineName + "'";
          });
    } catch (Exception e) {
      return "Error adding activation: " + e.getMessage();
    }
  }

  @Tool(
      name = "addMessage",
      description =
          "Add a message between two lifelines in a sequence diagram. messageType: synch (default,"
              + " solid line with a filled head), asynch (open head), create (the first message"
              + " to a lifeline: dashed arrow ending at its head, which is lowered to the arrow) or"
              + " destroy (the last message to a lifeline, which ends there with the X marker)."
              + " sequenceNumber (\"\" = the"
              + " next top-level number) is the number VP prints in front of messageName; a number"
              + " already used on the diagram is refused. A label wider than the arrow moves the"
              + " lifelines at its right apart")
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
              return diagramNotFound(diagramName);
            }

            String kind = messageType == null ? "" : messageType.trim().toLowerCase(Locale.ROOT);
            if (!kind.isEmpty()
                && !"synch".equals(kind)
                && !"sync".equals(kind)
                && !"asynch".equals(kind)
                && !"async".equals(kind)
                && !"create".equals(kind)
                && !"destroy".equals(kind)) {
              return "Unknown messageType '"
                  + messageType
                  + "': use synch (solid line, filled head), asynch (open head), create or"
                  + " destroy";
            }
            boolean async = "asynch".equals(kind) || "async".equals(kind);
            String special = "create".equals(kind) || "destroy".equals(kind) ? kind : "";
            return createMessageConnector(
                diagram,
                fromLifeline,
                toLifeline,
                messageName,
                sequenceNumber,
                async,
                false,
                special);
          });
    } catch (Exception e) {
      return "Error adding message: " + e.getMessage();
    }
  }

  @Tool(
      name = "addReturnMessage",
      description =
          "Add a return message (dashed line, open head) between two lifelines in a sequence"
              + " diagram; sequenceNumber as in addMessage")
  public String addReturnMessage(
      String diagramName,
      String fromLifeline,
      String toLifeline,
      String messageName,
      String sequenceNumber) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            return createMessageConnector(
                diagram, fromLifeline, toLifeline, messageName, sequenceNumber, false, true, "");
          });
    } catch (Exception e) {
      return "Error adding return message: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCombinedFragment",
      description =
          "Add a combined fragment frame (alt/opt/loop/break/par) to a sequence diagram."
              + " guard is the operand guard; separate several operands with '|' (e.g."
              + " \"còn phòng|else\" for alt/else). coveredLifelines is a comma list (empty = all"
              + " lifelines). fromSeq/toSeq are the first and last top-level message numbers the"
              + " frame encloses, nested ones (4.1, 4.2) included (0 = 1 / the last message);"
              + " add the messages first. operandStarts is a comma list with the first top-level"
              + " message number of operands 2..n (empty = share the messages out evenly)."
              + " Messages are moved down to make room for the operator tab and guards, and the"
              + " frame is widened to enclose the labels of the messages it covers")
  public String addCombinedFragment(
      String diagramName,
      String operator,
      String guard,
      String coveredLifelines,
      int fromSeq,
      int toSeq,
      String operandStarts) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                findDiagram(diagramName, IInteractionDiagramUIModel.class, "sequence");

            ICombinedFragment fragment = getModelElementFactory().createCombinedFragment();
            String op = operator == null ? "" : operator.trim().toLowerCase(java.util.Locale.ROOT);
            if (!op.isEmpty() && !FRAGMENT_OPERATORS.contains(op)) {
              return "Unknown operator '" + operator + "': use alt, opt, loop, break or par";
            }
            switch (op) {
              case "opt":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_OPT);
                break;
              case "loop":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_LOOP);
                break;
              case "break":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_BREAK);
                break;
              case "par":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_PAR);
                break;
              default:
                op = "alt";
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_ALT);
            }

            // One operand per '|'-separated guard (alt/else); a blank guard leaves it unguarded.
            String[] guards = guard == null ? new String[] {""} : guard.split("\\|", -1);
            List<IInteractionOperand> operands = new ArrayList<>();
            for (String g : guards) {
              IInteractionOperand operand = getModelElementFactory().createInteractionOperand();
              if (!g.trim().isEmpty()) {
                IInteractionConstraint constraint =
                    getModelElementFactory().createInteractionConstraint();
                constraint.setConstraint(g.trim());
                operand.setGuard(constraint);
              }
              fragment.addOperand(operand);
              operands.add(operand);
            }

            // Covered lifelines (all of them when none are named)
            List<String> notFound = new ArrayList<>();
            List<IShapeUIModel> covered = new ArrayList<>();
            if (coveredLifelines != null && !coveredLifelines.trim().isEmpty()) {
              for (String lifelineName : coveredLifelines.split(",")) {
                IInteractionLifeLine lifeline =
                    SequenceDiagramUtils.findLifelineByName(diagram, lifelineName.trim());
                IShapeUIModel shape =
                    lifeline == null ? null : findLifelineShape(diagram, lifeline);
                if (shape != null) {
                  fragment.addCoveredLifeLine(lifeline);
                  covered.add(shape);
                } else {
                  notFound.add(lifelineName.trim());
                }
              }
            } else {
              for (IInteractionLifeLine lifeline : SequenceDiagramUtils.getAllLifelines(diagram)) {
                IShapeUIModel shape = findLifelineShape(diagram, lifeline);
                if (shape != null) {
                  fragment.addCoveredLifeLine(lifeline);
                  covered.add(shape);
                }
              }
            }
            if (covered.isEmpty()) {
              fragment.delete();
              return "Error adding combined fragment: no covered lifeline on '"
                  + diagramName
                  + "'"
                  + (notFound.isEmpty() ? "" : " (not found: " + String.join(", ", notFound) + ")");
            }

            List<MessageRow> rows = messageRows(diagram);
            int maxTop = 0;
            for (MessageRow row : rows) {
              maxTop = Math.max(maxTop, SequenceDiagramUtils.topLevelNumber(row.seq));
            }
            int first = fromSeq > 0 ? fromSeq : 1;
            int last = toSeq > 0 ? toSeq : Math.max(first, maxTop);
            if (last < first) {
              fragment.delete();
              return "Error adding combined fragment: toSeq " + toSeq + " < fromSeq " + fromSeq;
            }
            int[] explicit = parseInts(operandStarts);
            int[] starts =
                SequenceDiagramUtils.operandStarts(first, last, operands.size(), explicit);
            int coveredLeft = Integer.MAX_VALUE;
            int coveredRight = Integer.MIN_VALUE;
            for (IShapeUIModel shape : covered) {
              coveredLeft = Math.min(coveredLeft, shape.getX() - FRAGMENT_MARGIN);
              coveredRight =
                  Math.max(coveredRight, shape.getX() + shape.getWidth() + FRAGMENT_MARGIN);
            }
            int rangeBottom = 0;
            for (MessageRow row : rows) {
              int top = SequenceDiagramUtils.topLevelNumber(row.seq);
              if (top >= first && top <= last) {
                rangeBottom = Math.max(rangeBottom, row.bottom());
              }
            }
            // Room above each operand's first message for the operator tab / separator and the
            // guard (the message label sits above its arrow), and below the last message: the
            // messages from there on move down when it is missing.
            for (int i = 0; i < starts.length; i++) {
              boolean guarded = !guards[Math.min(i, guards.length - 1)].trim().isEmpty();
              int need =
                  (i == 0 ? FRAGMENT_HEADER : 0) + (guarded ? GUARD_ROW : 0) + LABEL_ROW + GAP_ROW;
              rows = messageRows(diagram);
              MessageRow firstRow =
                  firstRowOf(rows, starts[i], i + 1 < starts.length ? starts[i + 1] - 1 : last);
              if (firstRow == null) {
                fragment.delete();
                return "Error adding combined fragment: no message numbered "
                    + starts[i]
                    + (i + 1 < starts.length ? ".." + (starts[i + 1] - 1) : ".." + last)
                    + " on '"
                    + diagramName
                    + "' (add the messages before the fragment)";
              }
              int above = previousBottom(rows, firstRow.arrowY);
              if (i == 0) {
                // a frame around this one has its own tab and guard above the first message: this
                // one starts below them
                for (IShapeUIModel outer :
                    enclosingFrames(
                        diagram, coveredLeft, coveredRight, firstRow.arrowY, rangeBottom)) {
                  above = Math.max(above, frameContentTopAt(outer, firstRow.arrowY));
                }
              }
              int missing = above + need - firstRow.arrowY;
              if (missing > 0) {
                shiftMessagesDown(diagram, firstRow.arrowY, missing);
              }
            }
            rows = messageRows(diagram);
            int[] firstYs = new int[starts.length];
            for (int i = 0; i < starts.length; i++) {
              firstYs[i] =
                  firstRowOf(rows, starts[i], i + 1 < starts.length ? starts[i + 1] - 1 : last)
                      .arrowY;
            }
            int lastBottom = 0;
            int labelLeft = Integer.MAX_VALUE;
            int labelRight = Integer.MIN_VALUE;
            for (MessageRow row : rows) {
              int top = SequenceDiagramUtils.topLevelNumber(row.seq);
              if (top >= first && top <= last) {
                lastBottom = Math.max(lastBottom, row.bottom());
                com.vp.plugin.diagram.ICaptionUIModel cap = row.connector.getCaptionUIModel();
                if (cap != null && cap.getWidth() > 0) {
                  labelLeft = Math.min(labelLeft, cap.getX());
                  labelRight = Math.max(labelRight, cap.getX() + cap.getWidth());
                }
              }
            }
            int nextTop = Integer.MAX_VALUE;
            for (MessageRow row : rows) {
              if (row.arrowY > lastBottom) {
                nextTop = Math.min(nextTop, row.arrowY);
              }
            }
            int bottomNeed = FRAGMENT_BOTTOM_PAD + GAP_ROW + LABEL_ROW;
            if (nextTop != Integer.MAX_VALUE && nextTop - lastBottom < bottomNeed) {
              shiftMessagesDown(diagram, nextTop, bottomNeed - (nextTop - lastBottom));
            }
            int frameTop =
                firstYs[0]
                    - (FRAGMENT_HEADER + (guards[0].trim().isEmpty() ? 0 : GUARD_ROW) + LABEL_ROW);
            int[] layout = new int[2 + starts.length];
            layout[0] = frameTop;
            layout[1] = lastBottom + FRAGMENT_BOTTOM_PAD;
            layout[2] = frameTop + FRAGMENT_HEADER;
            for (int i = 1; i < starts.length; i++) {
              boolean guarded = !guards[Math.min(i, guards.length - 1)].trim().isEmpty();
              layout[2 + i] = firstYs[i] - ((guarded ? GUARD_ROW : 0) + LABEL_ROW);
            }

            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (IShapeUIModel shape : covered) {
              left = Math.min(left, shape.getX());
              right = Math.max(right, shape.getX() + shape.getWidth());
            }
            left -= FRAGMENT_MARGIN;
            right += FRAGMENT_MARGIN;
            if (labelLeft != Integer.MAX_VALUE) {
              // self-message labels stick out right of their lifeline
              left = Math.min(left, labelLeft - 8);
              right = Math.max(right, labelRight + 8);
            }

            // inside the frame(s) around it, a little in from their edges
            for (IShapeUIModel outer :
                enclosingFrames(diagram, coveredLeft, coveredRight, frameTop, lastBottom)) {
              left = Math.max(left, outer.getX() + NEST_INSET);
              right = Math.min(right, outer.getX() + outer.getWidth() - NEST_INSET);
            }

            // a frame around this one ends a little below it, not on its bottom border
            for (IShapeUIModel outer :
                enclosingFrames(diagram, coveredLeft, coveredRight, frameTop, lastBottom)) {
              int wantedBottom = layout[1] + NEST_INSET;
              int growth = wantedBottom - (outer.getY() + outer.getHeight());
              if (growth > 0) {
                outer.setBounds(
                    outer.getX(), outer.getY(), outer.getWidth(), outer.getHeight() + growth);
                IShapeUIModel[] outerOperands = outer.toChildArray();
                IShapeUIModel lastOperand = null;
                for (IShapeUIModel candidate :
                    outerOperands == null ? new IShapeUIModel[0] : outerOperands) {
                  if (candidate.getModelElement() instanceof IInteractionOperand
                      && (lastOperand == null || candidate.getY() > lastOperand.getY())) {
                    lastOperand = candidate;
                  }
                }
                if (lastOperand != null) {
                  lastOperand.setBounds(
                      lastOperand.getX(),
                      lastOperand.getY(),
                      lastOperand.getWidth(),
                      lastOperand.getHeight() + growth);
                }
              }
            }

            IDiagramElement created = getDiagramManager().createDiagramElement(diagram, fragment);
            if (!(created instanceof IShapeUIModel)) {
              fragment.delete();
              return "Error adding combined fragment: Visual Paradigm did not create the frame";
            }
            IShapeUIModel frame = (IShapeUIModel) created;
            frame.setBounds(left, layout[0], right - left, layout[1] - layout[0]);
            frame.resetCaption();
            // Operands are shapes inside the frame: VP draws each [guard] at an operand's top-left
            // and a dashed separator between operands.
            for (int i = 0; i < operands.size(); i++) {
              IDiagramElement opDe =
                  getDiagramManager().createDiagramElement(diagram, operands.get(i));
              if (opDe instanceof IShapeUIModel) {
                IShapeUIModel opShape = (IShapeUIModel) opDe;
                frame.addChild(opShape);
                int top = layout[2 + i];
                int bottom = i + 1 < operands.size() ? layout[3 + i] : layout[1];
                opShape.setBounds(left, top, right - left, bottom - top);
                opShape.resetCaption();
              }
            }
            frame.sendToBack();
            // VP lays the operands out (separators, guard positions) when it opens the diagram.
            diagram.realCloseDiagram();
            getDiagramManager().openDiagram(diagram);

            String result =
                "Added "
                    + op
                    + " fragment with "
                    + operands.size()
                    + " operand(s) around messages "
                    + first
                    + ".."
                    + last
                    + " to diagram '"
                    + diagramName
                    + "' at "
                    + frame.getX()
                    + ","
                    + frame.getY()
                    + " "
                    + frame.getWidth()
                    + "x"
                    + frame.getHeight();
            if (!notFound.isEmpty()) {
              result += " WARNING: lifelines not found: " + String.join(", ", notFound);
            }
            return result;
          });
    } catch (Exception e) {
      return "Error adding combined fragment: " + e.getMessage();
    }
  }

  /**
   * The combined fragments already on the diagram that surround the lifelines from {@code left} to
   * {@code right} and the heights from {@code top} to {@code bottom}.
   */
  private List<IShapeUIModel> enclosingFrames(
      IInteractionDiagramUIModel diagram, int left, int right, int top, int bottom) {
    List<IShapeUIModel> frames = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel
          && de.getModelElement() instanceof ICombinedFragment
          && de.getX() <= left + 2
          && de.getX() + de.getWidth() >= right - 2
          && de.getY() < top
          && de.getY() + de.getHeight() > bottom) {
        frames.add((IShapeUIModel) de);
      }
    }
    return frames;
  }

  /**
   * The height below the operator tab or divider and the guard of the operand of {@code frame} that
   * holds the height {@code y}: where its messages start. A fragment nested in the second operand
   * of an alt must start below the divider and guard of that operand, not below the frame's tab.
   */
  private static int frameContentTopAt(IShapeUIModel frame, int y) {
    IShapeUIModel[] shapes = frame.toChildArray();
    IShapeUIModel operand = null;
    for (IShapeUIModel candidate : shapes == null ? new IShapeUIModel[0] : shapes) {
      if (candidate.getModelElement() instanceof IInteractionOperand
          && candidate.getY() <= y
          && (operand == null || candidate.getY() > operand.getY())) {
        operand = candidate;
      }
    }
    if (operand == null) {
      return frameContentTop(frame);
    }
    IInteractionOperand model = (IInteractionOperand) operand.getModelElement();
    boolean guarded =
        model.getGuard() != null
            && model.getGuard().getConstraint() != null
            && !model.getGuard().getConstraint().trim().isEmpty();
    return operand.getY() + (guarded ? GUARD_ROW : 0);
  }

  /** The height below a frame's operator tab and first guard, where its messages start. */
  private static int frameContentTop(IShapeUIModel frame) {
    int top = frame.getY() + FRAGMENT_HEADER;
    IInteractionOperand[] operands = ((ICombinedFragment) frame.getModelElement()).toOperandArray();
    if (operands != null
        && operands.length > 0
        && operands[0].getGuard() != null
        && operands[0].getGuard().getConstraint() != null
        && !operands[0].getGuard().getConstraint().trim().isEmpty()) {
      top += GUARD_ROW;
    }
    return top;
  }

  /** The topmost message whose top-level number is in [from, to], or null. */
  private static MessageRow firstRowOf(List<MessageRow> rows, int from, int to) {
    for (MessageRow row : rows) {
      int top = SequenceDiagramUtils.topLevelNumber(row.seq);
      if (top >= from && top <= to) {
        return row;
      }
    }
    return null;
  }

  /** Lowest point of the messages above y, or the bottom of the lifeline heads. */
  private static int previousBottom(List<MessageRow> rows, int y) {
    int bottom = LIFELINE_Y0 + LIFELINE_HEAD_H;
    for (MessageRow row : rows) {
      if (row.arrowY < y) {
        bottom = Math.max(bottom, row.bottom());
      }
    }
    return bottom;
  }

  /** Comma-separated integers, or null when the text is blank or not all numbers. */
  private static int[] parseInts(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    String[] parts = text.split(",");
    int[] values = new int[parts.length];
    try {
      for (int i = 0; i < parts.length; i++) {
        values[i] = Integer.parseInt(parts[i].trim());
      }
    } catch (NumberFormatException e) {
      return null;
    }
    return values;
  }

  @Tool(
      name = "generateSequenceReport",
      description = "Generate a sequence diagram analysis report")
  public String generateSequenceReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            List<IInteractionLifeLine> lifelines = SequenceDiagramUtils.getAllLifelines(diagram);
            List<IMessage> messages = SequenceDiagramUtils.getAllMessages(diagram);

            // Build activation -> lifeline name reverse map
            java.util.Map<IActivation, String> activationToLifeline =
                SequenceDiagramUtils.buildActivationToLifelineMap(diagram);

            StringBuilder report = new StringBuilder();
            report.append("SEQUENCE DIAGRAM REPORT: ").append(diagramName).append("\n");
            report.append("=====================================\n");

            // Lifelines with base classifier and type
            report.append("Lifelines (").append(lifelines.size()).append("):\n");
            for (IInteractionLifeLine ll : lifelines) {
              report.append("  - ").append(ll.getName());
              Object classifierObj = ll.getBaseClassifier();
              if (classifierObj instanceof IModelElement) {
                report.append(" [").append(((IModelElement) classifierObj).getName()).append("]");
                if (classifierObj instanceof IClass) {
                  IClass bc = (IClass) classifierObj;
                  java.util.Iterator<?> stIter = bc.stereotypeIterator();
                  if (stIter.hasNext()) {
                    report.append(" (").append(stIter.next()).append(")");
                  }
                }
              }
              report.append("\n");
            }

            // Messages with from -> to
            report.append("Messages (").append(messages.size()).append("):\n");
            for (int i = 0; i < messages.size(); i++) {
              IMessage msg = messages.get(i);
              String fromAct =
                  endName(msg.getFrom(), msg.getFromActivation(), activationToLifeline);
              String toAct = endName(msg.getTo(), msg.getToActivation(), activationToLifeline);

              report.append("  ").append(i + 1).append(". ");
              report.append(fromAct).append(" -> ").append(toAct);
              report.append(": ").append(msg.getName());

              // Message type hints
              if (!"?".equals(fromAct) && fromAct.equals(toAct)) {
                report.append(" (self)");
              } else if (msg.isAsynchronous()) {
                report.append(" (async)");
              }
              if (msg.getSequenceNumber() != null && !msg.getSequenceNumber().isEmpty()) {
                report.append(" [").append(msg.getSequenceNumber()).append("]");
              }
              report.append("\n");
            }

            // Combined Fragments
            List<String> fragments = new ArrayList<>();
            java.util.Iterator<?> fragIter = diagram.diagramElementIterator();
            while (fragIter.hasNext()) {
              Object obj = fragIter.next();
              if (obj instanceof com.vp.plugin.diagram.IDiagramElement) {
                IModelElement model =
                    ((com.vp.plugin.diagram.IDiagramElement) obj).getModelElement();
                if (model instanceof ICombinedFragment) {
                  ICombinedFragment cf = (ICombinedFragment) model;
                  StringBuilder fragStr = new StringBuilder();
                  // Operator
                  String op = cf.getInteractionOperator();
                  if (ICombinedFragment.INTERACTION_OPERATOR_ALT.equals(op)) {
                    fragStr.append("alt");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_OPT.equals(op)) {
                    fragStr.append("opt");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_LOOP.equals(op)) {
                    fragStr.append("loop");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_BREAK.equals(op)) {
                    fragStr.append("break");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_PAR.equals(op)) {
                    fragStr.append("par");
                  } else {
                    fragStr.append(op);
                  }

                  // Guards of every operand
                  java.util.Iterator<?> opIter = cf.operandIterator();
                  while (opIter.hasNext()) {
                    Object opObj = opIter.next();
                    if (opObj instanceof IInteractionOperand) {
                      IInteractionOperand operand = (IInteractionOperand) opObj;
                      IInteractionConstraint guard = operand.getGuard();
                      if (guard != null
                          && guard.getConstraint() != null
                          && !guard.getConstraint().isEmpty()) {
                        fragStr.append(" [").append(guard.getConstraint()).append("]");
                      }
                    }
                  }

                  // Covered lifelines
                  java.util.Iterator<?> llIter = cf.coveredLifeLineIterator();
                  List<String> coveredNames = new ArrayList<>();
                  while (llIter.hasNext()) {
                    Object llObj = llIter.next();
                    if (llObj instanceof IInteractionLifeLine) {
                      coveredNames.add(((IInteractionLifeLine) llObj).getName());
                    }
                  }
                  if (!coveredNames.isEmpty()) {
                    fragStr.append(" covering: ").append(String.join(", ", coveredNames));
                  }

                  fragments.add(fragStr.toString());
                }
              }
            }
            if (!fragments.isEmpty()) {
              report.append("Combined Fragments (").append(fragments.size()).append("):\n");
              for (String frag : fragments) {
                report.append("  - ").append(frag).append("\n");
              }
            }

            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  /**
   * Name of a message end: the lifeline (or actor) model the message is bound to, else the lifeline
   * owning its activation, else "?".
   */
  private static String endName(
      IModelElement end,
      IActivation activation,
      java.util.Map<IActivation, String> activationToLifeline) {
    if (end instanceof IInteractionLifeLine || end instanceof IActor) {
      return end.getName();
    }
    if (end instanceof IActivation && activationToLifeline.containsKey(end)) {
      return activationToLifeline.get(end);
    }
    if (activation != null && activationToLifeline.containsKey(activation)) {
      return activationToLifeline.get(activation);
    }
    return "?";
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
    if (!(diagram instanceof IInteractionDiagramUIModel)
        || !(de instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel)) {
      return null;
    }
    // A lifeline is the anchor of its messages and activation bars: they have to move with the
    // head by the shift of the head's center line (re-centring them on the head would pull every
    // arrow up to the head).
    IInteractionDiagramUIModel sequence = (IInteractionDiagramUIModel) diagram;
    IShapeUIModel head = (IShapeUIModel) de;
    int oldCx = head.getX() + head.getWidth() / 2;
    int oldHeight = head.getHeight();
    // Visual Paradigm drags the ends of attached messages along with the head in its own way (to
    // the head's corner): the arrow heights are noted first and every arrow is drawn again from
    // the lifeline centers afterwards.
    List<MessageRow> rows = messageRows(sequence);
    int newWidth = width;
    if (width <= 0 || height <= 0) {
      fitShape(head);
      newWidth = width > 0 ? width : head.getWidth();
    }
    int newHeight = height > 0 ? height : oldHeight;
    // a lifeline never stands on the head of another: it moves to the nearest free place beside it
    int left = Math.max(x, 0);
    String note = "";
    for (IDiagramElement other : getDiagramElementsList(sequence)) {
      if (other instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel
          && !other.getId().equals(head.getId())
          && left < other.getX() + other.getWidth() + LIFELINE_GAP
          && other.getX() < left + newWidth + LIFELINE_GAP) {
        boolean right = left + newWidth / 2 >= other.getX() + other.getWidth() / 2;
        int moved =
            right
                ? other.getX() + other.getWidth() + LIFELINE_GAP
                : other.getX() - newWidth - LIFELINE_GAP;
        if (moved >= 0) {
          note =
              " (moved from x="
                  + left
                  + " to x="
                  + moved
                  + ": the requested place overlaps the lifeline '"
                  + other.getModelElement().getName()
                  + "')";
          left = moved;
        }
      }
    }
    head.setBounds(left, Math.max(y, 0), newWidth, newHeight);
    int dx = head.getX() + head.getWidth() / 2 - oldCx;
    for (IDiagramElement other : getDiagramElementsList(sequence)) {
      if (other instanceof IActivationUIModel
          && Math.abs(other.getX() + other.getWidth() / 2 - oldCx) <= 14) {
        other.setBounds(other.getX() + dx, other.getY(), other.getWidth(), other.getHeight());
      }
    }
    refitFragments(sequence);
    if (head.getModelElement() instanceof IInteractionLifeLine) {
      int lowest = 0;
      for (MessageRow row : rows) {
        lowest = Math.max(lowest, row.bottom());
      }
      extendLifelineToY(sequence, (IInteractionLifeLine) head.getModelElement(), lowest);
    }
    // the arrows last: moving a bar or the head afterwards would put their ends back
    redrawMessages(sequence, rows, java.util.Collections.singleton(head.getId()));
    routeNoteAnchors(sequence);
    return boundsResult(name, head) + note;
  }

  @Override
  protected String layoutOwnDiagram(IDiagramUIModel diagram) {
    return diagram instanceof IInteractionDiagramUIModel
        ? laidOutWhileAdding(diagram, "sequence diagram")
        : null;
  }

  /** A note explaining a lifeline stands under the lifeline, on the line of its dashed part. */
  @Override
  protected int[] noteSpotOwn(
      IDiagramUIModel diagram, IShapeUIModel note, IShapeUIModel target, int w, int h) {
    if (!(diagram instanceof IInteractionDiagramUIModel)
        || !(target instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel)) {
      return null;
    }
    List<int[]> notes = new java.util.ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (!s.getId().equals(note.getId())
          && s.getModelElement() instanceof com.vp.plugin.model.INOTE) {
        notes.add(new int[] {s.getX(), s.getY(), s.getWidth(), s.getHeight()});
      }
    }
    return com.brunnen.vp.mcp.util.NoteAnchor.spotBelow(
        new int[] {target.getX(), target.getY(), target.getWidth(), target.getHeight()},
        target.getX() + target.getWidth() / 2,
        w,
        h,
        notes);
  }

  /** The anchor of a note ends on the dashed line of a lifeline, as a horizontal/vertical line. */
  @Override
  protected boolean routeAnchorOwn(
      IDiagramUIModel diagram,
      com.vp.plugin.diagram.IConnectorUIModel anchor,
      IShapeUIModel note,
      IShapeUIModel target) {
    if (!(diagram instanceof IInteractionDiagramUIModel)) {
      return false;
    }
    boolean lifeline = target instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel;
    List<int[]> route =
        com.brunnen.vp.mcp.util.NoteAnchor.route(
            new int[] {note.getX(), note.getY(), note.getWidth(), note.getHeight()},
            new int[] {target.getX(), target.getY(), target.getWidth(), target.getHeight()},
            lifeline ? target.getX() + target.getWidth() / 2 : null);
    com.brunnen.vp.mcp.util.VpGraphLayout.route(anchor, route);
    return true;
  }

  @Override
  protected int rerouteOwnConnectors(IDiagramUIModel diagram) {
    if (!(diagram instanceof IInteractionDiagramUIModel)) {
      return -1;
    }
    // A message is anchored to its lifelines, not to the center of a shape: the generic center
    // routing would turn the arrows diagonal. The arrows are drawn again from the lifeline centers
    // at their heights, which repairs a diagram whose lifelines or bars moved.
    IInteractionDiagramUIModel sequence = (IInteractionDiagramUIModel) diagram;
    List<MessageRow> rows = messageRows(sequence);
    java.util.Set<String> heads = new java.util.HashSet<>();
    for (IDiagramElement de : getDiagramElementsList(sequence)) {
      if (de instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) {
        heads.add(de.getId());
      }
    }
    redrawMessages(sequence, rows, heads);
    routeNoteAnchors(sequence);
    return rows.size();
  }

  /**
   * Draw the arrows of the given messages again from the current lifeline centers, at the heights
   * they had before the lifelines moved. Visual Paradigm re-anchors the ends of the messages
   * attached to a moved lifeline in its own way (to the head's old position or corner) when it lays
   * the diagram out again, so the diagram is reopened first and the arrows are drawn after.
   *
   * @param diagram the diagram
   * @param rows the messages, with their arrow heights from before the move
   * @param heads ids of the lifeline shapes that moved: only messages attached to them are redrawn
   */
  private void redrawMessages(
      IInteractionDiagramUIModel diagram, List<MessageRow> rows, java.util.Set<String> heads) {
    java.util.Map<String, Integer> heights = new java.util.HashMap<>();
    for (MessageRow row : rows) {
      if (row.connector.getModelElement() != null) {
        heights.put(row.connector.getModelElement().getId(), row.arrowY);
      }
    }
    diagram.realCloseDiagram();
    getDiagramManager().openDiagram(diagram);
    for (MessageRow row : messageRows(diagram)) {
      IShapeUIModel from = row.connector.getFromShape();
      IShapeUIModel to = row.connector.getToShape();
      Integer y =
          row.connector.getModelElement() == null
              ? null
              : heights.get(row.connector.getModelElement().getId());
      if (y == null
          || from == null
          || to == null
          || !(heads.contains(from.getId()) || heads.contains(to.getId()))) {
        continue;
      }
      row.connector.clearPoints();
      for (Point p : messagePoints(from, to, y, row.self)) {
        row.connector.addPoint(p);
      }
      row.connector.resetCaption();
      if (row.self) {
        moveSelfCaption(row.connector);
      }
    }
  }

  /**
   * Put the label of a message to itself right of its loop: Visual Paradigm puts it on the loop's
   * right edge, where its (opaque) box hides the vertical line that closes the loop.
   */
  private static void moveSelfCaption(com.vp.plugin.diagram.IConnectorUIModel connector) {
    com.vp.plugin.diagram.ICaptionUIModel cap = connector.getCaptionUIModel();
    Point[] points = connector.getPoints();
    if (cap == null || cap.getWidth() <= 0 || points == null || points.length < 2) {
      return;
    }
    int right = Integer.MIN_VALUE;
    int top = Integer.MAX_VALUE;
    int bottom = Integer.MIN_VALUE;
    for (Point p : points) {
      right = Math.max(right, p.x);
      top = Math.min(top, p.y);
      bottom = Math.max(bottom, p.y);
    }
    cap.setBounds(
        right + 6, (top + bottom) / 2 - cap.getHeight() / 2, cap.getWidth(), cap.getHeight());
  }

  /**
   * Move every lifeline whose head starts at or right of {@code fromX} to the right by {@code dx},
   * with its activation bars and the arrows attached to it, and stretch the combined fragments.
   * Makes room between two lifelines for a long message label.
   */
  private void shiftLifelinesFrom(IInteractionDiagramUIModel diagram, int fromX, int dx) {
    List<MessageRow> rows = messageRows(diagram);
    java.util.Set<String> moved = new java.util.HashSet<>();
    List<Integer> oldCenters = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel
          && de.getX() >= fromX) {
        oldCenters.add(de.getX() + de.getWidth() / 2);
        moved.add(de.getId());
        de.setBounds(de.getX() + dx, de.getY(), de.getWidth(), de.getHeight());
      }
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IActivationUIModel) {
        for (int center : oldCenters) {
          if (Math.abs(de.getX() + de.getWidth() / 2 - center) <= 14) {
            de.setBounds(de.getX() + dx, de.getY(), de.getWidth(), de.getHeight());
            break;
          }
        }
      }
    }
    refitFragments(diagram);
    redrawMessages(diagram, rows, moved);
  }

  /**
   * Stretch each combined fragment sideways so it encloses the lifelines it covers (plus the labels
   * of the messages inside it) after a lifeline moved.
   */
  private void refitFragments(IInteractionDiagramUIModel diagram) {
    List<MessageRow> rows = messageRows(diagram);
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IShapeUIModel) || !(de.getModelElement() instanceof ICombinedFragment)) {
        continue;
      }
      IShapeUIModel frame = (IShapeUIModel) de;
      int left = Integer.MAX_VALUE;
      int right = Integer.MIN_VALUE;
      IModelElement[] covered = ((ICombinedFragment) de.getModelElement()).toCoveredLifeLineArray();
      for (IModelElement lifeline : covered == null ? new IModelElement[0] : covered) {
        IShapeUIModel shape =
            lifeline instanceof IInteractionLifeLine
                ? findLifelineShape(diagram, (IInteractionLifeLine) lifeline)
                : null;
        if (shape != null) {
          left = Math.min(left, shape.getX() - FRAGMENT_MARGIN);
          right = Math.max(right, shape.getX() + shape.getWidth() + FRAGMENT_MARGIN);
        }
      }
      if (left == Integer.MAX_VALUE) {
        continue;
      }
      for (MessageRow row : rows) {
        com.vp.plugin.diagram.ICaptionUIModel cap = row.connector.getCaptionUIModel();
        if (cap != null
            && cap.getWidth() > 0
            && row.arrowY >= frame.getY()
            && row.arrowY <= frame.getY() + frame.getHeight()) {
          left = Math.min(left, cap.getX() - 8);
          right = Math.max(right, cap.getX() + cap.getWidth() + 8);
        }
      }
      // a fragment inside another one stands NEST_INSET inside it: the two borders do not merge
      IShapeUIModel outer = null;
      for (IDiagramElement other : getDiagramElementsList(diagram)) {
        if (other instanceof IShapeUIModel
            && other.getModelElement() instanceof ICombinedFragment
            && !other.getId().equals(frame.getId())
            && other.getY() <= frame.getY()
            && other.getY() + other.getHeight() >= frame.getY() + frame.getHeight()
            && other.getWidth() * other.getHeight() > frame.getWidth() * frame.getHeight()
            && (outer == null
                || other.getWidth() * other.getHeight() < outer.getWidth() * outer.getHeight())) {
          outer = (IShapeUIModel) other;
        }
      }
      if (outer != null) {
        left = Math.max(left, outer.getX() + NEST_INSET);
        right = Math.min(right, outer.getX() + outer.getWidth() - NEST_INSET);
      }
      frame.setBounds(
          Math.max(left, 0), frame.getY(), right - Math.max(left, 0), frame.getHeight());
      IShapeUIModel[] operands = frame.toChildArray();
      for (IShapeUIModel operand : operands == null ? new IShapeUIModel[0] : operands) {
        operand.setBounds(frame.getX(), operand.getY(), frame.getWidth(), operand.getHeight());
      }
    }
  }

  // --- Sequence geometry ---
  // Messages are placed top-to-bottom by sequence number. Activation bars follow the call stack:
  // Each lifeline gets one continuous activation bar (grown to cover its messages). Messages are
  // drawn as connectors anchored to the lifeline shapes at the message y (createDiagramElement on a
  // message alone is unanchored and never rendered).
  private static final int MSG_TOP_Y = 100;
  private static final int MSG_STEP_Y = 36;
  private static final int SELF_LOOP_W = 36;
  private static final int SELF_LOOP_H = 16;
  // Combined fragment frame: VP's operator tab row, the space below the last covered message, and
  // the side margin around the covered lifelines.
  private static final int FRAGMENT_HEADER = 17;
  private static final int FRAGMENT_BOTTOM_PAD = 16;
  // A guard line under the tab / separator, the label above a message arrow, and free space.
  private static final int GUARD_ROW = 18;
  private static final int LABEL_ROW = 18;
  private static final int GAP_ROW = 20;
  private static final int FRAGMENT_MARGIN = 24;
  // Space kept between a message label and the lifelines at the ends of its arrow, and between a
  // nested combined fragment and the one around it.
  private static final int LABEL_MARGIN = 30;
  private static final int NEST_INSET = 12;
  // Horizontal lifeline layout (tighter than the default 250px spread).
  private static final int LIFELINE_X0 = 40;
  private static final int LIFELINE_DX = 150;
  private static final int LIFELINE_W = 90;
  // Free space kept between the heads of two lifelines.
  private static final int LIFELINE_GAP = 20;
  private static final int LIFELINE_Y0 = 30;
  private static final int LIFELINE_HEAD_H = 40;
  // Length of the lifeline below the arrow of a destroy message.
  private static final int DESTROY_TAIL = 14;

  /**
   * The points of a message arrow at height {@code y}: lifeline center to lifeline center, or the
   * small loop right of the lifeline for a message to itself.
   */
  private static Point[] messagePoints(IShapeUIModel from, IShapeUIModel to, int y, boolean self) {
    int fromCx = from.getX() + from.getWidth() / 2;
    if (self) {
      return new Point[] {
        new Point(fromCx, y),
        new Point(fromCx + SELF_LOOP_W, y),
        new Point(fromCx + SELF_LOOP_W, y + SELF_LOOP_H),
        new Point(fromCx, y + SELF_LOOP_H)
      };
    }
    return new Point[] {new Point(fromCx, y), new Point(to.getX() + to.getWidth() / 2, y)};
  }

  private String createMessageConnector(
      IInteractionDiagramUIModel diagram,
      String fromLifeline,
      String toLifeline,
      String messageName,
      String sequenceNumber,
      boolean async,
      boolean isReturn,
      String special) {
    if (stripLeadingNumber(messageName).trim().isEmpty()) {
      return "Message name must not be empty: a message without a label reads as a bare number"
          + " (the book labels every message, e.g. \"goi\" or \"tra ve\")";
    }
    // All participants are real lifelines (boundary/entity/control + actor-as-box).
    IInteractionLifeLine from = SequenceDiagramUtils.findLifelineByName(diagram, fromLifeline);
    IInteractionLifeLine to = SequenceDiagramUtils.findLifelineByName(diagram, toLifeline);
    if (from == null) {
      return "From lifeline not found: " + fromLifeline;
    }
    if (to == null) {
      return "To lifeline not found: " + toLifeline;
    }

    boolean self = from.getId().equals(to.getId());
    if (!special.isEmpty() && self) {
      return "A " + special + " message goes to another lifeline, not to the same one";
    }
    java.util.Set<String> gone =
        destroyedLifelines.computeIfAbsent(diagram.getName(), k -> new java.util.HashSet<>());
    if (gone.contains(from.getId()) || gone.contains(to.getId())) {
      return "Lifeline '"
          + (gone.contains(from.getId()) ? fromLifeline : toLifeline)
          + "' was destroyed: no message can start or end there after its destroy message";
    }
    List<MessageRow> rows = messageRows(diagram);
    if ("create".equals(special)) {
      for (MessageRow row : rows) {
        if (row.connector.getToShape() != null
                && row.connector.getToShape().getModelElement() != null
                && row.connector.getToShape().getModelElement().getId().equals(to.getId())
            || row.connector.getFromShape() != null
                && row.connector.getFromShape().getModelElement() != null
                && row.connector.getFromShape().getModelElement().getId().equals(to.getId())) {
          return "Lifeline '"
              + toLifeline
              + "' already has messages: a create message must be the first one to it";
        }
      }
    }
    String seq =
        sequenceNumber != null && !sequenceNumber.trim().isEmpty()
            ? sequenceNumber.trim()
            : SequenceDiagramUtils.nextTopLevelNumber(sequenceNumbers(rows));
    for (MessageRow row : rows) {
      if (seq.equals(row.seq)) {
        return "Sequence number "
            + seq
            + " is already used on diagram '"
            + diagram.getName()
            + "' (message numbers must be unique; leave sequenceNumber empty for the next one)";
      }
    }
    // Messages are laid out in sequence-number order ("1" < "1.1" < "1.1.1" < "1.2" < "2"): one
    // row below the last message numbered before this one; later-numbered messages already drawn
    // move down to make room.
    int[] slot =
        SequenceDiagramUtils.messageSlot(
            sequenceNumbers(rows), rowYs(rows), seq, MSG_TOP_Y, MSG_STEP_Y);
    int y = slot[0];
    if (slot[1] > 0) {
      shiftMessagesDown(diagram, y, slot[1]);
    }

    IMessage message = getModelElementFactory().createMessage();
    // VP prepends the sequence number itself, so strip any leading "N:" the caller put in the name
    // (otherwise the label reads "2: 2: call").
    message.setName(stripLeadingNumber(messageName));
    message.setSequenceNumber(seq);
    message.setAsynchronous(async);

    if (self) {
      message.setType(IMessage.TYPE_RECURSIVE_MESSAGE);
    } else if ("create".equals(special)) {
      message.setType(IMessage.TYPE_CREATE_MESSAGE);
      message.setActionType(getModelElementFactory().createActionTypeCreate());
    } else if ("destroy".equals(special)) {
      message.setActionType(getModelElementFactory().createActionTypeDestroy());
    } else if (isReturn) {
      message.setActionType(getModelElementFactory().createActionTypeReturn());
    } else {
      message.setActionType(getModelElementFactory().createActionTypeCall());
    }
    extendLifelineToY(diagram, from, y);
    extendLifelineToY(diagram, to, y);

    // Anchor the connector to the LIFELINE shapes (the way VP draws messages by hand) and let VP
    // create/own the activations. Anchoring to our own activation bars made return arrows drift to
    // x=0 because an activation's connection point is unreliable. Direction follows from -> to
    // (returns callee -> caller).
    IShapeUIModel fromShape = findLifelineShape(diagram, from);
    IShapeUIModel toShape = findLifelineShape(diagram, to);
    if (fromShape == null || toShape == null) {
      return "Could not find lifeline for: " + (fromShape == null ? fromLifeline : toLifeline);
    }
    Point[] points = messagePoints(fromShape, toShape, y, self);
    if ("create".equals(special)) {
      // the created object stands where its create message arrives: its head is lowered to the
      // arrow, which ends at the head's side
      int headTop = Math.max(LIFELINE_Y0, y - LIFELINE_HEAD_H / 2);
      int bottom = Math.max(toShape.getY() + toShape.getHeight(), y + 40);
      toShape.setBounds(toShape.getX(), headTop, toShape.getWidth(), bottom - headTop);
      boolean fromLeft = fromShape.getX() < toShape.getX();
      points =
          new Point[] {
            new Point(fromShape.getX() + fromShape.getWidth() / 2, y),
            new Point(fromLeft ? toShape.getX() : toShape.getX() + toShape.getWidth(), y)
          };
    }
    final Point[] route = points;
    com.vp.plugin.diagram.IDiagramElement msgShape;
    try {
      // a destroy message makes VP ask whether to mark the lifeline stopped: answered Yes
      msgShape =
          answeringDeletePrompts(
              () ->
                  getDiagramManager().createConnector(diagram, message, fromShape, toShape, route),
              new ArrayList<>());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
    if (msgShape instanceof com.vp.plugin.diagram.IBaseDiagramElement) {
      // resetCaption() (as in the VP Open API sample) makes the message label render on the arrow.
      ((com.vp.plugin.diagram.IBaseDiagramElement) msgShape).resetCaption();
      if (self && msgShape instanceof com.vp.plugin.diagram.IConnectorUIModel) {
        moveSelfCaption((com.vp.plugin.diagram.IConnectorUIModel) msgShape);
      }
    }

    // VP just created a small activation per message end. Rather than add a new bar, grow the
    // lifeline's first (top) activation down to cover the small ones beside it, so the lifeline
    // reads as one continuous execution bar.
    coverTopBar(diagram, from, y);
    coverTopBar(diagram, to, y);
    if (self) {
      coverTopBar(diagram, from, y + SELF_LOOP_H + 4);
    }
    // Actors carry one bar down the whole diagram (first step -> last), so stretch theirs to the
    // top and to the latest message on every message.
    extendActorBarsFull(diagram, y);
    if ("destroy".equals(special)) {
      // the lifeline ends where it is destroyed, with the X marker
      to.setStopped(true);
      toShape.setBounds(
          toShape.getX(), toShape.getY(), toShape.getWidth(), y + DESTROY_TAIL - toShape.getY());
      gone.add(to.getId());
      toShape.resetCaption();
      diagram.realCloseDiagram(); // VP draws the stop marker when it lays the diagram out
      getDiagramManager().openDiagram(diagram);
    }
    // A label wider than the arrow runs over the lifelines at its ends (and their bars): the
    // lifelines right of the arrow's left end move apart until the label fits between them.
    if (!self && msgShape instanceof com.vp.plugin.diagram.IConnectorUIModel) {
      com.vp.plugin.diagram.ICaptionUIModel caption =
          ((com.vp.plugin.diagram.IConnectorUIModel) msgShape).getCaptionUIModel();
      int fromCx = fromShape.getX() + fromShape.getWidth() / 2;
      int toCx = toShape.getX() + toShape.getWidth() / 2;
      int deficit =
          caption == null || caption.getWidth() <= 0
              ? 0
              : caption.getWidth() + LABEL_MARGIN - Math.abs(toCx - fromCx);
      if (deficit > 0) {
        shiftLifelinesFrom(diagram, fromCx < toCx ? toShape.getX() : fromShape.getX(), deficit);
      }
    }

    return "Added "
        + (isReturn ? "return message" : "message")
        + " '"
        + seq
        + ": "
        + stripLeadingNumber(messageName)
        + "' at y="
        + y
        + " from '"
        + fromLifeline
        + "' to '"
        + toLifeline
        + "'";
  }

  /**
   * Grow the lifeline's top (first) activation down to {@code y} so it covers the smaller
   * per-message activations beside it, making the lifeline read as one continuous execution bar. We
   * pick one of VP's existing activations and stretch it -- no extra bar is added.
   */
  private void coverTopBar(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    java.util.Map<String, String> byLifeline =
        continuousBarId.computeIfAbsent(diagram.getName(), k -> new java.util.HashMap<>());
    String id = byLifeline.get(lifeline.getId());
    IActivationUIModel bar = id != null ? findActivationShapeById(diagram, id) : null;
    if (bar == null) {
      bar = findTopActivationOnLifeline(diagram, lifeline);
      if (bar == null) {
        return;
      }
      IModelElement model = bar.getModelElement();
      if (model != null) {
        byLifeline.put(lifeline.getId(), model.getId());
      }
    }
    growDown(bar, y);
    hideStubsUnder(diagram, bar);
  }

  /**
   * Make VP's small per-message activations that lie on the continuous bar fully transparent: they
   * are drawn in front of it whatever the z-order, and their borders showed as tick marks across
   * the bar. They stay in the model (the messages are attached to them).
   */
  private static void hideStubsUnder(IInteractionDiagramUIModel diagram, IActivationUIModel bar) {
    int barCenter = bar.getX() + bar.getWidth() / 2;
    List<IActivationUIModel> stubs = new ArrayList<>();
    int bottom = bar.getY() + bar.getHeight();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (!(obj instanceof IActivationUIModel)) {
        continue;
      }
      IActivationUIModel stub = (IActivationUIModel) obj;
      if (stub.getId().equals(bar.getId())
          || Math.abs(stub.getX() + stub.getWidth() / 2 - barCenter) > 2
          || stub.getY() < bar.getY()
          || stub.getY() > bar.getY() + bar.getHeight()) {
        continue;
      }
      stubs.add(stub);
      bottom = Math.max(bottom, stub.getY() + stub.getHeight());
    }
    if (bottom > bar.getY() + bar.getHeight()) {
      // a stub sticking out below the bar's end: the bar ends with it
      bar.setBounds(bar.getX(), bar.getY(), bar.getWidth(), bottom - bar.getY());
    }
    for (IActivationUIModel stub : stubs) {
      if (stub.getFillColor() != null) {
        stub.getFillColor().setTransparency(100, true);
      }
      if (stub.getLineModel() != null) {
        stub.getLineModel().setTransparency(100, true);
      }
    }
  }

  /** Stretch each actor's covered bar to span the whole diagram (MSG_TOP_Y down to {@code y}). */
  private void extendActorBarsFull(IInteractionDiagramUIModel diagram, int y) {
    java.util.Set<String> actors = actorLifelineIds.get(diagram.getName());
    java.util.Map<String, String> bars = continuousBarId.get(diagram.getName());
    if (actors == null || bars == null) {
      return;
    }
    int top = MSG_TOP_Y - 2;
    for (String lifelineId : actors) {
      String barId = bars.get(lifelineId);
      if (barId == null) {
        continue; // actor's bar not created yet (no message has touched it)
      }
      IActivationUIModel bar = findActivationShapeById(diagram, barId);
      if (bar == null) {
        continue;
      }
      int bottom = Math.max(bar.getY() + bar.getHeight(), y + 8);
      bar.setBounds(bar.getX(), top, IActivationUIModel.BODY_WIDTH, bottom - top);
      hideStubsUnder(diagram, bar);
    }
  }

  /** The topmost (smallest y) activation sitting on a lifeline's vertical line. */
  private IActivationUIModel findTopActivationOnLifeline(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    int cx = lifelineCenterX(diagram, lifeline);
    IActivationUIModel top = null;
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IActivationUIModel) {
        IActivationUIModel a = (IActivationUIModel) obj;
        if (Math.abs(a.getX() + a.getWidth() / 2 - cx) <= 5
            && (top == null || a.getY() < top.getY())) {
          top = a;
        }
      }
    }
    return top;
  }

  /** Strip a leading "N:" sequence prefix from a message name (VP renders the number itself). */
  private String stripLeadingNumber(String name) {
    if (name == null) {
      return "";
    }
    int colon = name.indexOf(':');
    if (colon > 0) {
      String prefix = name.substring(0, colon).trim();
      boolean allDigits = !prefix.isEmpty();
      for (int i = 0; i < prefix.length(); i++) {
        if (!Character.isDigit(prefix.charAt(i))) {
          allDigits = false;
          break;
        }
      }
      if (allDigits) {
        return name.substring(colon + 1).trim();
      }
    }
    return name.trim();
  }

  private java.util.Deque<String> stackOf(String diagramName, String lifelineId) {
    return openStacks
        .computeIfAbsent(diagramName, k -> new java.util.HashMap<>())
        .computeIfAbsent(lifelineId, k -> new java.util.ArrayDeque<>());
  }

  /** Open a NEW execution (activation bar) on a lifeline and push it on the stack. */
  private IActivationUIModel openNewActivation(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    IActivation activation = getModelElementFactory().createActivation();
    lifeline.addActivation(activation);
    Object shapeObj = getDiagramManager().createDiagramElement(diagram, activation);
    if (!(shapeObj instanceof IActivationUIModel)) {
      return null;
    }
    IActivationUIModel shape = (IActivationUIModel) shapeObj;
    int depth = stackOf(diagram.getName(), lifeline.getId()).size();
    int centerX = lifelineCenterX(diagram, lifeline);
    shape.setBounds(
        centerX - IActivationUIModel.BODY_WIDTH / 2 + depth * 5,
        y - 2,
        IActivationUIModel.BODY_WIDTH,
        12);
    applyBlueFill(shape);
    stackOf(diagram.getName(), lifeline.getId()).push(activation.getId());
    myActivations
        .computeIfAbsent(diagram.getName(), k -> new java.util.HashSet<>())
        .add(activation.getId());
    return shape;
  }

  /** Top (innermost) open execution on a lifeline, or null. */
  private IActivationUIModel topActivationShape(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    String id = stackOf(diagram.getName(), lifeline.getId()).peek();
    return id == null ? null : findActivationShapeById(diagram, id);
  }

  /** The lifeline's current open execution, opening one if it has none. */
  private IActivationUIModel ensureOpenActivation(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    IActivationUIModel top = topActivationShape(diagram, lifeline);
    return top != null ? top : openNewActivation(diagram, lifeline, y);
  }

  /** Close the lifeline's innermost open execution at position {@code y}. */
  private void closeTopActivation(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    String id = stackOf(diagram.getName(), lifeline.getId()).poll();
    if (id == null) {
      return;
    }
    IActivationUIModel shape = findActivationShapeById(diagram, id);
    if (shape != null) {
      growDown(shape, y);
    }
  }

  /** Grow an activation bar downward so its bottom reaches message position {@code y}. */
  private void growDown(IActivationUIModel shape, int y) {
    int top = shape.getY();
    int bottom = top + shape.getHeight();
    int newBottom = Math.max(bottom, y + 8);
    if (newBottom > bottom) {
      shape.setBounds(shape.getX(), top, IActivationUIModel.BODY_WIDTH, newBottom - top);
    }
  }

  /** A message drawn on the diagram: its connector, sequence number and arrow y. */
  private static final class MessageRow {
    private final com.vp.plugin.diagram.IConnectorUIModel connector;
    private final String seq;
    private final int arrowY;
    private final boolean self;

    private MessageRow(
        com.vp.plugin.diagram.IConnectorUIModel connector, String seq, int y, boolean self) {
      this.connector = connector;
      this.seq = seq;
      this.arrowY = y;
      this.self = self;
    }

    /** Lowest point of the message: the arrow, or the bottom of a self-message loop. */
    private int bottom() {
      return self ? arrowY + SELF_LOOP_H : arrowY;
    }
  }

  /** Every message connector of the diagram with its sequence number and y, top to bottom. */
  private List<MessageRow> messageRows(IInteractionDiagramUIModel diagram) {
    List<MessageRow> rows = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof com.vp.plugin.diagram.IConnectorUIModel
          && de.getModelElement() instanceof IMessage) {
        com.vp.plugin.diagram.IConnectorUIModel c = (com.vp.plugin.diagram.IConnectorUIModel) de;
        Point[] points = c.getPoints();
        if (points == null || points.length == 0) {
          continue;
        }
        IShapeUIModel from = c.getFromShape();
        IShapeUIModel to = c.getToShape();
        boolean self = from != null && to != null && from.getId().equals(to.getId());
        rows.add(
            new MessageRow(
                c, ((IMessage) de.getModelElement()).getSequenceNumber(), points[0].y, self));
      }
    }
    rows.sort((a, b) -> Integer.compare(a.arrowY, b.arrowY));
    return rows;
  }

  private static String[] sequenceNumbers(List<MessageRow> rows) {
    String[] seqs = new String[rows.size()];
    for (int i = 0; i < seqs.length; i++) {
      seqs[i] = rows.get(i).seq;
    }
    return seqs;
  }

  private static int[] rowYs(List<MessageRow> rows) {
    int[] ys = new int[rows.size()];
    for (int i = 0; i < ys.length; i++) {
      ys[i] = rows.get(i).bottom();
    }
    return ys;
  }

  /**
   * Move every message at or below {@code fromY} down by {@code dy}, with the activation bars and
   * combined fragments around them: bars and frames below {@code fromY} move, the ones spanning it
   * grow. Lifelines are extended to the new last message.
   */
  private void shiftMessagesDown(IInteractionDiagramUIModel diagram, int fromY, int dy) {
    int lowest = 0;
    for (MessageRow row : messageRows(diagram)) {
      if (row.arrowY < fromY) {
        continue;
      }
      Point[] points = row.connector.getPoints();
      row.connector.clearPoints();
      for (Point p : points) {
        row.connector.addPoint(new Point(p.x, p.y + dy));
      }
      row.connector.resetCaption();
      if (row.self) {
        moveSelfCaption(row.connector);
      }
      lowest = Math.max(lowest, row.bottom() + dy);
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IShapeUIModel)
          || de instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel
          || ((IShapeUIModel) de).getParent() != null) {
        continue;
      }
      boolean bar = de instanceof IActivationUIModel;
      boolean frame = de.getModelElement() instanceof ICombinedFragment;
      if (!bar && !frame) {
        continue;
      }
      shiftShape((IShapeUIModel) de, fromY - 4, dy);
    }
    for (IInteractionLifeLine lifeline : SequenceDiagramUtils.getAllLifelines(diagram)) {
      extendLifelineToY(diagram, lifeline, lowest);
    }
  }

  /**
   * Make room below {@code fromY}: a shape starting below it moves down by dy (children too), a
   * shape spanning it grows by dy and its children are shifted the same way.
   */
  private static void shiftShape(IShapeUIModel shape, int fromY, int dy) {
    if (shape.getY() >= fromY) {
      shape.setBounds(shape.getX(), shape.getY() + dy, shape.getWidth(), shape.getHeight());
    } else if (shape.getY() + shape.getHeight() > fromY) {
      shape.setBounds(shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight() + dy);
    } else {
      return;
    }
    IShapeUIModel[] children = shape.toChildArray();
    if (children != null) {
      for (IShapeUIModel child : children) {
        if (shape.getY() >= fromY + dy) {
          // the parent moved as a whole: its children follow
          child.setBounds(child.getX(), child.getY() + dy, child.getWidth(), child.getHeight());
        } else {
          shiftShape(child, fromY, dy);
        }
      }
    }
  }

  private IActivationUIModel findActivationShapeById(
      IInteractionDiagramUIModel diagram, String activationId) {
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IActivationUIModel) {
        IModelElement model = ((IActivationUIModel) obj).getModelElement();
        if (model != null && activationId.equals(model.getId())) {
          return (IActivationUIModel) obj;
        }
      }
    }
    return null;
  }

  /** Left edge for the next lifeline: right of the rightmost head plus the usual gap. */
  private int nextParticipantX(IInteractionDiagramUIModel diagram) {
    int right = -1;
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) {
        IShapeUIModel head = (IShapeUIModel) obj;
        right = Math.max(right, head.getX() + head.getWidth());
      }
    }
    return right < 0 ? LIFELINE_X0 : right + (LIFELINE_DX - LIFELINE_W);
  }

  /** Width a lifeline head needs to show its name on one line. */
  private static int estimateHeadWidth(String name) {
    return name == null ? 0 : name.trim().length() * 7 + 24;
  }

  private IShapeUIModel findLifelineShape(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    String lifelineId = lifeline.getId();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      // Activation shapes are IShapeUIModel too, but their model id differs from the lifeline id.
      if (obj instanceof IShapeUIModel && !(obj instanceof IActivationUIModel)) {
        IShapeUIModel shape = (IShapeUIModel) obj;
        IModelElement model = shape.getModelElement();
        if (model != null && lifelineId.equals(model.getId())) {
          return shape;
        }
      }
    }
    return null;
  }

  private int lifelineCenterX(IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    IShapeUIModel shape = findLifelineShape(diagram, lifeline);
    return shape != null ? shape.getX() + shape.getWidth() / 2 : 100;
  }

  /** Grow a lifeline's dashed line so it extends below message position {@code y}. */
  private void extendLifelineToY(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    IShapeUIModel shape = findLifelineShape(diagram, lifeline);
    if (shape == null) {
      return;
    }
    int needed = (y + 40) - shape.getY();
    if (shape.getHeight() < needed) {
      shape.setBounds(shape.getX(), shape.getY(), shape.getWidth(), needed);
    }
  }
}
