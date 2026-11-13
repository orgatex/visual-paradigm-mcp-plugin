package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ActivityDiagramUtils;
import com.brunnen.vp.mcp.util.ActivityLayoutProfile;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IActivityDiagramUIModel;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeTypeConstants;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivityPartitionHeaderUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2CompartmentUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2NewUIModel;
import com.vp.plugin.diagram.shape.IBranchShapeUIModel;
import com.vp.plugin.diagram.shape.IJoinNodeUIModel;
import com.vp.plugin.diagram.shape.IObjectNodeUIModel;
import com.vp.plugin.model.IActivityAction;
import com.vp.plugin.model.IActivityObjectFlow;
import com.vp.plugin.model.IActivityPartition;
import com.vp.plugin.model.IActivitySwimlane2;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IObjectNode;
import com.vp.plugin.model.IState2;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm UML activity diagrams.
 *
 * <p>Models UML 2 activities with the real Visual Paradigm model types: {@link IActivityAction} for
 * actions, {@code IInitialNode}, {@code IActivityFinalNode}, {@code IFlowFinalNode}, {@code
 * IDecisionNode}, {@code IMergeNode}, {@code IForkNode} and {@code IJoinNode} for control nodes,
 * {@link IObjectNode} for object nodes, and {@link IControlFlow} / {@link IActivityObjectFlow} for
 * the edges (an object flow whenever one end is an object node). Guards are stored on the flow
 * model and drawn by Visual Paradigm as {@code [guard]}.
 *
 * <p>Swimlanes follow the structure Visual Paradigm itself saves: one {@link IActivitySwimlane2}
 * frame per diagram holding vertical {@link IActivityPartition}s side by side, each drawn as a
 * partition header plus a compartment that owns the lane's node shapes. The frame and lanes are
 * containers: they are sized to enclose their nodes, never fitted to their label. Action and object
 * node boxes are fitted to their text with {@link IShapeUIModel#fitSize()}; control nodes keep
 * fixed UML sizes, with fork/join drawn as horizontal bars.
 *
 * <p>The diagram is laid out by the shared layered layout ({@link ActivityLayoutProfile}) whenever
 * something is added: rows by the flow, nodes in the column of their lane, every flow a horizontal
 * or vertical line, guards beside their flows.
 *
 * <p>Node names are the handles {@code addActivityFlow} looks nodes up by, scoped to the diagram,
 * so they must be unique on a diagram.
 */
public class ActivityDiagramMcpTools extends AbstractDiagramMcpTools {

  /** Name of the swimlane frame model (its caption is not drawn by Visual Paradigm). */
  private static final String SWIMLANE_NAME = "Swimlane";

  /** {@code orientation} value of fork/join shapes that draws a horizontal bar. */
  private static final int ORIENTATION_HORIZONTAL = 1;

  /** Fit results below this size mean Visual Paradigm could not measure the label. */
  private static final int MIN_FITTED_SIZE = 16;

  /** Fallback box height when Visual Paradigm could not fit a box. */
  private static final int FALLBACK_BOX_HEIGHT = 40;

  /** {@code IObjectNodeUIModel.setShowInStates} value that draws the [state] line. */
  private static final int SHOW_IN_STATES = 1;

  /** Free space between a frame's border and the nodes it encloses, and room for its title. */
  private static final int FRAME_PADDING = 20;

  private static final int FRAME_TITLE_HEIGHT = 24;

  /**
   * Handles of nodes whose handle differs from the label they show: a second final node called "End
   * #2" shows "End" (shape id -> handle).
   */
  private final java.util.Map<String, String> handles = new java.util.HashMap<>();

  /** Lane body height requested per diagram id (addActivityPartition height), 0 = by content. */
  private final Map<String, Integer> laneHeights = new java.util.HashMap<>();

  /** Page frames per diagram id: shape id of the frame to the shape ids of the nodes it holds. */
  private final Map<String, Map<String, Set<String>>> framesByDiagram = new java.util.HashMap<>();

  @Override
  protected String handleOf(IDiagramElement de) {
    return handles.get(de.getId());
  }

  /** A handle's displayed label: the text without a trailing " #2" style number. */
  private static String labelOf(String handle) {
    String label = handle.replaceAll("\\s*#\\d+$", "").trim();
    return label.isEmpty() ? handle : label;
  }

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    if (!(diagram instanceof IActivityDiagramUIModel)) {
      return null;
    }
    IActivityDiagramUIModel activity = (IActivityDiagramUIModel) diagram;
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(activity);
    ActivityLayoutProfile.Lanes lanes =
        swimlane == null || isGrid(swimlane) ? null : new LaneSet(activity, swimlane);
    return new ActivityLayoutProfile(
        lanes, framesByDiagram.computeIfAbsent(diagram.getId(), k -> new LinkedHashMap<>()));
  }

  /** A lane of the swimlane frame: its partition model, header shape and compartment shape. */
  private static final class Lane {
    private final IActivityPartition partition;
    private final IActivityPartitionHeaderUIModel header;
    private final IActivitySwimlane2CompartmentUIModel cell;

    private Lane(
        IActivityPartition partition,
        IActivityPartitionHeaderUIModel header,
        IActivitySwimlane2CompartmentUIModel cell) {
      this.partition = partition;
      this.header = header;
      this.cell = cell;
    }

    private String name() {
      return partition.getName();
    }
  }

  /** The lanes of one diagram as the layout profile sees them. */
  private final class LaneSet implements ActivityLayoutProfile.Lanes {
    private final IActivityDiagramUIModel diagram;
    private final IActivitySwimlane2NewUIModel swimlane;
    private final List<Lane> lanes;

    private LaneSet(IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane) {
      this.diagram = diagram;
      this.swimlane = swimlane;
      this.lanes = lanesOf(diagram, swimlane);
    }

    @Override
    public int size() {
      return lanes.size();
    }

    @Override
    public int laneOf(IShapeUIModel node) {
      IDiagramElement parent = node.getParent();
      for (int i = 0; parent != null && i < lanes.size(); i++) {
        if (lanes.get(i).cell.getId().equals(parent.getId())) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public int left() {
      return swimlane.getX();
    }

    @Override
    public int top() {
      return swimlane.getY();
    }

    @Override
    public int headerHeight() {
      return ActivityDiagramMcpTools.headerHeight(lanes);
    }

    @Override
    public int minWidth(int lane) {
      return ActivityDiagramUtils.laneWidth(lanes.get(lane).name(), 0);
    }

    @Override
    public int minBodyHeight() {
      return laneHeights.getOrDefault(diagram.getId(), 0);
    }

    @Override
    public void apply(int[] xs, int[] widths, int bodyHeight) {
      int top = swimlane.getY();
      int headerHeight = ActivityDiagramMcpTools.headerHeight(lanes);
      int right = xs.length == 0 ? swimlane.getX() : xs[0];
      for (int i = 0; i < lanes.size(); i++) {
        lanes.get(i).header.setBounds(xs[i], top, widths[i], headerHeight);
        lanes.get(i).cell.setBounds(xs[i], top + headerHeight, widths[i], bodyHeight);
        right = xs[i] + widths[i];
      }
      swimlane.setBounds(
          xs.length == 0 ? swimlane.getX() : xs[0],
          top,
          right - (xs.length == 0 ? swimlane.getX() : xs[0]),
          headerHeight + bodyHeight);
    }

    @Override
    public int[] xs() {
      int[] xs = new int[lanes.size()];
      for (int i = 0; i < xs.length; i++) {
        xs[i] = lanes.get(i).header.getX();
      }
      return xs;
    }

    @Override
    public int[] widths() {
      int[] widths = new int[lanes.size()];
      for (int i = 0; i < widths.length; i++) {
        widths[i] = lanes.get(i).header.getWidth();
      }
      return widths;
    }
  }

  /**
   * Create an activity diagram whose shapes auto-fit their text.
   *
   * @param diagramName name of the new diagram
   * @return a human-readable result or error message
   */
  @Tool(
      name = "createActivityDiagram",
      description =
          "Create a new UML activity diagram in Visual Paradigm. Shapes on it auto-fit their text")
  public String createActivityDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!ActivityDiagramUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ACTIVITY_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created activity diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating activity diagram: " + e.getMessage();
    }
  }

  /**
   * Add a vertical swimlane lane; the first one also creates the swimlane frame.
   *
   * @param diagramName the activity diagram
   * @param partitionName the lane title
   * @param x left of the frame for the first lane (&gt;0), otherwise ignored
   * @param y top of the frame for the first lane (&gt;0), otherwise ignored
   * @param width lane width (&gt;0), otherwise automatic
   * @param height lane body height (&gt;0), otherwise automatic
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityPartition",
      description =
          "Add a vertical swimlane (partition) to an activity diagram. Lanes sit side by side in"
              + " one swimlane frame: the first partition creates the frame at x/y (>0, otherwise"
              + " 40,40), later ones are appended on the right and x/y are ignored. width/height"
              + " (>0) size the lane body, otherwise 240x480 (wider for a long title); all lanes"
              + " share the tallest height and grow automatically to enclose the nodes added to"
              + " them with partitionName")
  public String addActivityPartition(
      String diagramName, String partitionName, int x, int y, int width, int height) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            if (!ActivityDiagramUtils.isValidName(partitionName)) {
              return "Partition name must not be empty";
            }
            String name = partitionName.trim();

            IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
            if (swimlane != null && isGrid(swimlane)) {
              return "The swimlane on '"
                  + diagramName
                  + "' has horizontal partitions (a grid); only vertical lanes are supported";
            }
            if (swimlane != null && findLane(diagram, swimlane, name) != null) {
              return "Partition already on diagram: " + name;
            }
            if (swimlane == null) {
              swimlane = createSwimlane(diagram, x, y);
              if (swimlane == null) {
                return "Error adding partition: Visual Paradigm did not create a swimlane frame";
              }
            }
            IModelElement frameModel = swimlane.getModelElement();
            if (!(frameModel instanceof IActivitySwimlane2)) {
              return "Error adding partition: swimlane frame has no swimlane model";
            }

            List<Lane> lanes = lanesOf(diagram, swimlane);
            int top = swimlane.getY();
            int headerHeight = headerHeight(lanes);
            int laneX = swimlane.getX();
            int bodyHeight = height > 0 ? height : ActivityDiagramUtils.DEFAULT_LANE_HEIGHT;
            if (!lanes.isEmpty()) {
              Lane last = lanes.get(lanes.size() - 1);
              laneX = last.header.getX() + last.header.getWidth();
              bodyHeight = Math.max(height, last.cell.getHeight());
            }
            int laneWidth = ActivityDiagramUtils.laneWidth(name, width);

            // Same construction as a swimlane drawn in Visual Paradigm: the partition is a child
            // of the frame model, the header shape names the lane and the compartment shape below
            // it holds the lane's nodes.
            IActivitySwimlane2 frame = (IActivitySwimlane2) frameModel;
            IActivityPartition partition = getModelElementFactory().createActivityPartition();
            partition.setName(name);
            frame.addVerticalPartition(partition);
            frame.addChild(partition);

            IDiagramElement headerDe = getDiagramManager().createDiagramElement(diagram, partition);
            if (!(headerDe instanceof IActivityPartitionHeaderUIModel)) {
              return "Error adding partition: Visual Paradigm did not create a partition header";
            }
            IActivityPartitionHeaderUIModel header = (IActivityPartitionHeaderUIModel) headerDe;
            header.setHorizontal(false);
            header.setSwimlane(swimlane);
            swimlane.addChild(header);
            header.setBounds(laneX, top, laneWidth, headerHeight);
            resetCaption(header);

            IDiagramElement cellDe =
                getDiagramManager()
                    .createDiagramElement(
                        diagram, IShapeTypeConstants.SHAPE_TYPE_ACTIVITY_SWIMLANE2_COMPARTMENT);
            if (!(cellDe instanceof IActivitySwimlane2CompartmentUIModel)) {
              return "Error adding partition: Visual Paradigm did not create a lane compartment";
            }
            IActivitySwimlane2CompartmentUIModel cell =
                (IActivitySwimlane2CompartmentUIModel) cellDe;
            cell.setVerticalPartitionId(header.getId());
            cell.setBounds(laneX, top + headerHeight, laneWidth, bodyHeight);
            swimlane.addChild(cell);

            storeLaneIds(diagram, swimlane);

            if (height > 0) {
              laneHeights.merge(diagram.getId(), height, Math::max);
            }
            fitLaneFrame(diagram, swimlane, bodyHeight);
            relayout(diagram);

            Lane lane = findLane(diagram, swimlane, name);
            IShapeUIModel laneHeader = lane != null ? lane.header : header;
            IShapeUIModel laneCell = lane != null ? lane.cell : cell;
            return "Added partition '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + laneHeader.getX()
                + ","
                + laneHeader.getY()
                + " "
                + laneHeader.getWidth()
                + "x"
                + (laneHeader.getHeight() + laneCell.getHeight());
          });
    } catch (Exception e) {
      return "Error adding partition: " + e.getMessage();
    }
  }

  /**
   * Add an action box fitted to its name, optionally inside a lane.
   *
   * @param diagramName the activity diagram
   * @param actionName the action name, unique on the diagram
   * @param partitionName the lane, or empty for none
   * @param x explicit left (&gt;0), otherwise auto-placed
   * @param y explicit top (&gt;0), otherwise auto-placed
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityAction",
      description =
          "Add an action (rounded box, fitted to its text) to an activity diagram. actionName is"
              + " also the handle used by addActivityFlow, so it must be unique on the diagram; a"
              + " trailing number (\"Show #2\") is only part of the handle, so the same text can"
              + " stand in several page frames and the box shows \"Show\". partitionName puts it in that swimlane lane (\"\" = no lane). x/y (>0) place its"
              + " top-left corner explicitly (kept inside the lane, the layout routes the flows"
              + " around it); otherwise the layout puts it in the row its flows lead to, in the"
              + " column of its lane")
  public String addActivityAction(
      String diagramName, String actionName, String partitionName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            if (!ActivityDiagramUtils.isValidName(actionName)) {
              return "Action name must not be empty";
            }
            String name = actionName.trim();
            if (findNodeShape(diagram, name) != null) {
              return duplicateNodeMessage(name);
            }
            Lane lane = null;
            if (isSet(partitionName)) {
              lane = findLaneOnDiagram(diagram, partitionName.trim());
              if (lane == null) {
                return "Partition not on diagram: " + partitionName.trim();
              }
            }

            IActivityAction action = getModelElementFactory().createActivityAction();
            // "Show #2" is the handle of a second action that shows "Show"
            String label = labelOf(name);
            IShapeUIModel shape = createNodeShape(diagram, action, label, lane);
            if (shape == null) {
              return "Error adding action: Visual Paradigm did not create a shape for " + name;
            }
            if (!label.equals(name)) {
              handles.put(shape.getId(), name);
            }
            resetCaption(shape);
            applyBlueFill(shape);
            fitBox(shape, label);
            placeIn(diagram, shape, lane, x, y);
            relayout(diagram);

            return "Added action '"
                + name
                + "' to diagram '"
                + diagramName
                + "'"
                + laneSuffix(lane)
                + " at "
                + bounds(shape);
          });
    } catch (Exception e) {
      return "Error adding action: " + e.getMessage();
    }
  }

  /**
   * Add an initial, final, flow final, decision, merge, fork or join node.
   *
   * @param diagramName the activity diagram
   * @param kind the control node kind
   * @param nodeName the node handle, unique on the diagram
   * @param partitionName the lane, or empty for none
   * @param x explicit left (&gt;0), otherwise auto-placed
   * @param y explicit top (&gt;0), otherwise auto-placed
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityControlNode",
      description =
          "Add a control node to an activity diagram. kind: initial | final | flowFinal |"
              + " decision | merge | fork | join (fork/join are horizontal bars, decision/merge"
              + " diamonds). nodeName is the handle used by addActivityFlow and must be unique on"
              + " the diagram; a decision shows its question (e.g. \"Valid?\"), an initial or final"
              + " node its name beside it (\"Start\"/\"End\"), the others none; a trailing number"
              + " (\"End #2\") is only part of the handle, so a second final node or a second"
              + " decision with the same text is added as \"End #2\" and shows \"End\"; a name that"
              + " is only a number (\"#1\") makes a diamond with no text. partitionName"
              + " puts it in that swimlane lane (\"\" = no lane). x/y (>0) place its top-left"
              + " corner explicitly; otherwise the layout puts it in the row its flows lead to, in"
              + " the column of its lane (a fork/join bar is as wide as the nodes it joins)")
  public String addActivityControlNode(
      String diagramName, String kind, String nodeName, String partitionName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            String nodeKind = ActivityDiagramUtils.parseControlNodeKind(kind);
            if (nodeKind == null) {
              return "Unknown control node kind '"
                  + kind
                  + "'; use one of: "
                  + ActivityDiagramUtils.CONTROL_NODE_KINDS;
            }
            if (!ActivityDiagramUtils.isValidName(nodeName)) {
              return "Node name must not be empty";
            }
            String name = nodeName.trim();
            if (findNodeShape(diagram, name) != null) {
              return duplicateNodeMessage(name);
            }
            Lane lane = null;
            if (isSet(partitionName)) {
              lane = findLaneOnDiagram(diagram, partitionName.trim());
              if (lane == null) {
                return "Partition not on diagram: " + partitionName.trim();
              }
            }

            IModelElement node = createControlNodeModel(nodeKind);
            // "End #2" is the handle of a second node that shows "End"
            String label = ActivityDiagramUtils.showsLabel(nodeKind) ? labelOf(name) : name;
            IShapeUIModel shape = createNodeShape(diagram, node, label, lane);
            if (shape == null) {
              return "Error adding control node: Visual Paradigm did not create a shape for "
                  + name;
            }
            if (!label.equals(name)) {
              handles.put(shape.getId(), name);
            }
            if (ActivityDiagramUtils.isBar(nodeKind)) {
              // orientation 1 is what Visual Paradigm saves for a horizontal fork/join bar
              if (shape instanceof IBranchShapeUIModel) {
                ((IBranchShapeUIModel) shape).setOrientation(ORIENTATION_HORIZONTAL);
              } else if (shape instanceof IJoinNodeUIModel) {
                ((IJoinNodeUIModel) shape).setOrientation(ORIENTATION_HORIZONTAL);
              }
            }
            // Control nodes keep their UML size (fitting would stretch a circle/diamond/bar to
            // the width of its name).
            int[] size = ActivityDiagramUtils.defaultControlNodeSize(nodeKind);
            shape.setBounds(shape.getX(), shape.getY(), size[0], size[1]);
            resetCaption(shape);
            if (!ActivityDiagramUtils.showsLabel(nodeKind)) {
              hideCaption(shape);
            }
            placeIn(diagram, shape, lane, x, y);
            relayout(diagram);
            String another = "";
            if (ActivityDiagramUtils.KIND_INITIAL.equals(nodeKind)) {
              for (IShapeUIModel other : nodeShapes(diagram)) {
                if (!sameShape(other, shape)
                    && ActivityDiagramUtils.KIND_INITIAL.equals(
                        ActivityLayoutProfile.kindOf(other.getModelElement()))) {
                  another =
                      "; WARNING: the diagram already has an initial node ('"
                          + other.getModelElement().getName()
                          + "'), an activity has one";
                  break;
                }
              }
            }

            return "Added "
                + nodeKind
                + " node '"
                + name
                + "' to diagram '"
                + diagramName
                + "'"
                + laneSuffix(lane)
                + " at "
                + bounds(shape)
                + another;
          });
    } catch (Exception e) {
      return "Error adding control node: " + e.getMessage();
    }
  }

  /**
   * Add an object node fitted to its name and optional {@code [state]}.
   *
   * @param diagramName the activity diagram
   * @param objectName the object name, unique on the diagram
   * @param state the object state, or empty for none
   * @param partitionName the lane, or empty for none
   * @param x explicit left (&gt;0), otherwise auto-placed
   * @param y explicit top (&gt;0), otherwise auto-placed
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityObjectNode",
      description =
          "Add an object node (rectangle, fitted to its text) to an activity diagram. state is"
              + " shown under the name as [state] (\"\" = none). objectName is the handle used by"
              + " addActivityFlow and must be unique on the diagram; flows to/from it are object"
              + " flows. partitionName puts it in that swimlane lane (\"\" = no lane, or the lane"
              + " under an explicit x/y). x/y (>0) place its top-left corner explicitly; otherwise"
              + " the layout puts it in the row its flows lead to, in the column of its lane")
  public String addActivityObjectNode(
      String diagramName, String objectName, String state, String partitionName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            if (!ActivityDiagramUtils.isValidName(objectName)) {
              return "Object name must not be empty";
            }
            String name = objectName.trim();
            if (findNodeShape(diagram, name) != null) {
              return duplicateNodeMessage(name);
            }
            Lane lane = null;
            if (isSet(partitionName)) {
              lane = findLaneOnDiagram(diagram, partitionName.trim());
              if (lane == null) {
                return "Partition not on diagram: " + partitionName.trim();
              }
            } else if (x > 0 && y > 0) {
              lane = laneAt(diagram, x, y);
            }

            IObjectNode node = getModelElementFactory().createObjectNode();
            String stateName = ActivityDiagramUtils.normalizeGuard(state);
            if (stateName != null) {
              // UML "in state": VP draws the object's states as [state] under its name
              IState2 inState = getModelElementFactory().createState2();
              inState.setName(stateName);
              node.addInState(inState);
            }
            String label = labelOf(name);
            IShapeUIModel shape = createNodeShape(diagram, node, label, lane);
            if (shape == null) {
              return "Error adding object node: Visual Paradigm did not create a shape for " + name;
            }
            if (!label.equals(name)) {
              handles.put(shape.getId(), name);
            }
            if (stateName != null && shape instanceof IObjectNodeUIModel) {
              ((IObjectNodeUIModel) shape).setShowInStates(SHOW_IN_STATES);
            }
            resetCaption(shape);
            fitBox(shape, stateName != null ? label + " [" + stateName + "]" : label);
            placeIn(diagram, shape, lane, x, y);
            relayout(diagram);

            return "Added object node '"
                + name
                + "'"
                + (stateName != null ? " [" + stateName + "]" : "")
                + " to diagram '"
                + diagramName
                + "'"
                + laneSuffix(lane)
                + " at "
                + bounds(shape);
          });
    } catch (Exception e) {
      return "Error adding object node: " + e.getMessage();
    }
  }

  /**
   * Add a rounded frame titled e.g. "Xử lí tại gdDangki.jsp" around a group of nodes.
   *
   * @param diagramName the activity diagram
   * @param title the frame title
   * @param nodeNames comma-separated node names the frame encloses, or empty for x/y/w/h
   * @param x explicit left (used when nodeNames is empty)
   * @param y explicit top
   * @param width explicit width
   * @param height explicit height
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityFrame",
      description =
          "Add a rounded frame with a bold title at its top-left (a UML activity) to an activity"
              + " diagram, the book's \"Xử lí tại gdXxx.jsp\" page frames. nodeNames is a comma"
              + " list of the nodes the frame encloses: the layout keeps them together inside the"
              + " frame (sized around them, with room for the title) and every other node outside"
              + " it, so frames never overlap; call it after the nodes and their flows exist. With"
              + " nodeNames \"\" the frame takes x/y/width/height. Calling it again with the same"
              + " title changes the nodes of that frame. The frame stays behind the nodes")
  public String addActivityFrame(
      String diagramName, String title, String nodeNames, int x, int y, int width, int height) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            if (!ActivityDiagramUtils.isValidName(title)) {
              return "Frame title must not be empty";
            }
            String name = title.trim();
            int[] box;
            Set<String> members = new HashSet<>();
            List<String> missing = new ArrayList<>();
            if (isSet(nodeNames)) {
              int left = Integer.MAX_VALUE;
              int top = Integer.MAX_VALUE;
              int right = Integer.MIN_VALUE;
              int bottom = Integer.MIN_VALUE;
              for (String part : nodeNames.split(",")) {
                if (part.trim().isEmpty()) {
                  continue;
                }
                IShapeUIModel node = findNodeShape(diagram, part.trim());
                if (node == null) {
                  missing.add(part.trim());
                  continue;
                }
                members.add(node.getId());
                left = Math.min(left, node.getX());
                top = Math.min(top, node.getY());
                right = Math.max(right, node.getX() + node.getWidth());
                bottom = Math.max(bottom, node.getY() + node.getHeight());
              }
              if (left == Integer.MAX_VALUE) {
                return "None of the nodes is on diagram '"
                    + diagramName
                    + "': "
                    + String.join(", ", missing);
              }
              int titleWidth = ActivityDiagramUtils.estimateTextWidth(name);
              int pad = FRAME_PADDING;
              int w = Math.max(right - left + 2 * pad, titleWidth);
              box =
                  new int[] {
                    Math.max(5, left - pad - (w - (right - left + 2 * pad)) / 2),
                    Math.max(5, top - pad - FRAME_TITLE_HEIGHT),
                    w,
                    bottom - top + 2 * pad + FRAME_TITLE_HEIGHT
                  };
            } else {
              if (width <= 0 || height <= 0) {
                return "Give nodeNames, or x/y/width/height for the frame";
              }
              box = new int[] {Math.max(x, 0), Math.max(y, 0), width, height};
              for (IShapeUIModel node : nodeShapes(diagram)) {
                if (node.getX() >= box[0]
                    && node.getY() >= box[1]
                    && node.getX() + node.getWidth() <= box[0] + box[2]
                    && node.getY() + node.getHeight() <= box[1] + box[3]) {
                  members.add(node.getId());
                }
              }
            }
            IShapeUIModel frame = null;
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de instanceof IShapeUIModel
                  && de.getModelElement() instanceof com.vp.plugin.model.IActivity
                  && name.equals(de.getModelElement().getName())) {
                frame = (IShapeUIModel) de;
              }
            }
            boolean updated = frame != null;
            if (frame == null) {
              com.vp.plugin.model.IActivity activity = getModelElementFactory().createActivity();
              activity.setName(name);
              IDiagramElement de = getDiagramManager().createDiagramElement(diagram, activity);
              if (!(de instanceof IShapeUIModel)) {
                activity.delete();
                return "Error adding frame: Visual Paradigm did not create the activity shape";
              }
              frame = (IShapeUIModel) de;
            }
            frame.setBounds(box[0], box[1], box[2], box[3]);
            resetCaption(frame);
            // the book's page frames are white, so the actions inside stay readable
            if (frame.getFillColor() != null) {
              frame.getFillColor().setColor1(java.awt.Color.WHITE, true);
            }
            frame.sendToBack();
            framesByDiagram
                .computeIfAbsent(diagram.getId(), k -> new LinkedHashMap<>())
                .put(frame.getId(), members);
            // the frames, their nodes and everything else are laid out again
            relayout(diagram);
            return (updated ? "Updated" : "Added")
                + " frame '"
                + name
                + "' on diagram '"
                + diagramName
                + "' at "
                + bounds(frame)
                + (missing.isEmpty() ? "" : "; not on the diagram: " + String.join(", ", missing));
          });
    } catch (Exception e) {
      return "Error adding frame: " + e.getMessage();
    }
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
    if (!(diagram instanceof IActivityDiagramUIModel) || !(de instanceof IShapeUIModel)) {
      return null;
    }
    IActivityDiagramUIModel activity = (IActivityDiagramUIModel) diagram;
    IShapeUIModel shape = (IShapeUIModel) de;
    String kind = ActivityLayoutProfile.kindOf(shape.getModelElement());
    boolean control =
        kind != null
            && !ActivityDiagramUtils.KIND_ACTION.equals(kind)
            && !ActivityDiagramUtils.KIND_OBJECT.equals(kind);
    String note = "";
    int left = x;
    int top = y;
    Lane lane = kind == null ? null : laneOf(activity, shape);
    if (lane != null) {
      // a node never leaves its lane
      int w = width > 0 ? width : shape.getWidth();
      left = ActivityDiagramUtils.clampIntoLane(x, w, lane.header.getX(), lane.header.getWidth());
      top = Math.max(y, lane.cell.getY() + ActivityDiagramUtils.LANE_PADDING);
    }
    if (control) {
      // initial/final/decision/merge/fork/join keep their UML size: fitting would shrink a
      // circle, diamond or bar to the width of its (hidden) name
      shape.setBounds(
          left, top, width > 0 ? width : shape.getWidth(), height > 0 ? height : shape.getHeight());
    } else {
      note = applyBounds(shape, left, top, width, height);
    }
    if (kind != null) {
      // explicitly placed: the layered layout leaves it here, the flows go around it
      note += avoidOverlap(diagram, shape);
      pin(shape);
    }
    relayout(diagram);
    if (shape.getModelElement() instanceof com.vp.plugin.model.IActivity
        && (shape.getX() != left || shape.getY() != top)) {
      note +=
          " (a page frame stands around the nodes it was made for: the layout put it back, move"
              + " its nodes instead)";
    }
    return boundsResult(name, shape) + note;
  }

  @Override
  protected int[] horizontalRange(IDiagramUIModel diagram, IShapeUIModel shape) {
    Lane lane =
        diagram instanceof IActivityDiagramUIModel
            ? laneOf((IActivityDiagramUIModel) diagram, shape)
            : null;
    return lane == null
        ? null
        : new int[] {
          lane.header.getX() + ActivityDiagramUtils.LANE_PADDING,
          lane.header.getX() + lane.header.getWidth() - ActivityDiagramUtils.LANE_PADDING
        };
  }

  // --- Node creation and placement ---

  private IModelElement createControlNodeModel(String kind) {
    IModelElementFactory factory = getModelElementFactory();
    switch (kind) {
      case ActivityDiagramUtils.KIND_INITIAL:
        return factory.createInitialNode();
      case ActivityDiagramUtils.KIND_FINAL:
        return factory.createActivityFinalNode();
      case ActivityDiagramUtils.KIND_FLOW_FINAL:
        return factory.createFlowFinalNode();
      case ActivityDiagramUtils.KIND_DECISION:
        return factory.createDecisionNode();
      case ActivityDiagramUtils.KIND_MERGE:
        return factory.createMergeNode();
      case ActivityDiagramUtils.KIND_FORK:
        return factory.createForkNode();
      case ActivityDiagramUtils.KIND_JOIN:
        return factory.createJoinNode();
      default:
        throw new IllegalArgumentException("Unknown control node kind: " + kind);
    }
  }

  /**
   * Name a node model and create its shape. A node in a lane is owned by the swimlane model, listed
   * as contained in the lane's partition and drawn as a child of the lane compartment, the way
   * Visual Paradigm stores a node dropped into a lane.
   */
  private IShapeUIModel createNodeShape(
      IActivityDiagramUIModel diagram, IModelElement node, String name, Lane lane) {
    if (lane != null) {
      IModelElement frame = lane.partition.getParent();
      if (frame != null) {
        frame.addChild(node);
      }
    }
    node.setName(name);
    if (lane != null) {
      lane.partition.addContainedElement(node);
    }
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, node);
    if (!(de instanceof IShapeUIModel)) {
      return null;
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    if (lane != null) {
      lane.cell.addChild(shape);
    }
    return shape;
  }

  /** Fit an action/object box to its text, falling back to an estimate if VP cannot measure. */
  private void fitBox(IShapeUIModel shape, String label) {
    fitShape(shape);
    if (shape.getWidth() < MIN_FITTED_SIZE || shape.getHeight() < MIN_FITTED_SIZE) {
      shape.setSize(
          Math.max(shape.getWidth(), ActivityDiagramUtils.estimateTextWidth(label)),
          Math.max(shape.getHeight(), FALLBACK_BOX_HEIGHT));
    }
  }

  /**
   * Put a freshly created node at its place: explicit x/y pin it there (clamped into its lane); a
   * diagram the layered layout owns places it itself on the next {@code relayout}; in a diagram
   * with pinned shapes it goes below the lowest node of its lane (or of the diagram).
   */
  private void placeIn(
      IActivityDiagramUIModel diagram, IShapeUIModel shape, Lane lane, int x, int y) {
    int w = shape.getWidth();
    int h = shape.getHeight();
    if (x > 0 || y > 0) {
      int px = x > 0 ? x : shape.getX();
      int py = y > 0 ? y : shape.getY();
      if (lane != null) {
        px =
            x > 0
                ? ActivityDiagramUtils.clampIntoLane(
                    x, w, lane.header.getX(), lane.header.getWidth())
                : lane.header.getX() + (lane.header.getWidth() - w) / 2;
        py = Math.max(y > 0 ? y : 0, lane.cell.getY() + ActivityDiagramUtils.LANE_PADDING);
      }
      placeNew(diagram, shape, px, py);
      return;
    }
    if (layoutOwnsShapes(diagram)) {
      return;
    }
    if (lane == null) {
      placeNew(diagram, shape, 0, 0);
      return;
    }
    int lowest = lane.cell.getY();
    for (IShapeUIModel other : nodeShapes(diagram)) {
      if (!sameShape(other, shape) && laneOf(diagram, other) == lane) {
        lowest = Math.max(lowest, other.getY() + other.getHeight());
      }
    }
    shape.setBounds(
        lane.header.getX() + (lane.header.getWidth() - w) / 2,
        lowest + ActivityDiagramUtils.ROW_GAP,
        w,
        h);
  }

  // --- Swimlane frame and lanes ---

  private IActivitySwimlane2NewUIModel createSwimlane(
      IActivityDiagramUIModel diagram, int x, int y) {
    IActivitySwimlane2 frame = getModelElementFactory().createActivitySwimlane2();
    frame.setName(SWIMLANE_NAME);
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, frame);
    if (!(de instanceof IActivitySwimlane2NewUIModel)) {
      return null;
    }
    IActivitySwimlane2NewUIModel swimlane = (IActivitySwimlane2NewUIModel) de;
    // Diagram-level auto-fit also fits containers: Visual Paradigm shrinks the frame to 100x100
    // and each lane compartment to its nodes, so lanes no longer line up with their headers.
    // Lanes are sized by the layout instead, and action/object boxes are still fitted to their
    // text one by one with fitShape().
    diagram.setAutoFitShapesSize(false);
    swimlane.setBounds(
        x > 0 ? x : ActivityDiagramUtils.SWIMLANE_X,
        y > 0 ? y : ActivityDiagramUtils.SWIMLANE_Y,
        ActivityDiagramUtils.DEFAULT_LANE_WIDTH,
        ActivityDiagramUtils.HEADER_HEIGHT + ActivityDiagramUtils.DEFAULT_LANE_HEIGHT);
    // Visual Paradigm saves the frame of a hand-drawn swimlane with its caption hidden; only the
    // partition headers carry text.
    hideCaption(swimlane);
    return swimlane;
  }

  /** A swimlane drawn by hand may also have horizontal partitions (rows); this tool does not. */
  private static boolean isGrid(IActivitySwimlane2NewUIModel swimlane) {
    String[] rows = swimlane.getHorizontalPartitionIds();
    if (rows != null && rows.length > 0) {
      return true;
    }
    for (IShapeUIModel child : childrenOf(swimlane)) {
      if (child instanceof IActivityPartitionHeaderUIModel
          && ((IActivityPartitionHeaderUIModel) child).isHorizontal()) {
        return true;
      }
    }
    return false;
  }

  /** Same height for every lane body and a frame around the lanes. */
  private void fitLaneFrame(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane, int minBodyHeight) {
    List<Lane> lanes = lanesOf(diagram, swimlane);
    if (lanes.isEmpty() || isGrid(swimlane)) {
      return;
    }
    int body = minBodyHeight;
    for (Lane lane : lanes) {
      body = Math.max(body, lane.cell.getHeight());
    }
    int headerHeight = headerHeight(lanes);
    int right = swimlane.getX();
    for (Lane lane : lanes) {
      lane.cell.setBounds(
          lane.header.getX(), swimlane.getY() + headerHeight, lane.header.getWidth(), body);
      right = Math.max(right, lane.header.getX() + lane.header.getWidth());
    }
    swimlane.setBounds(
        swimlane.getX(), swimlane.getY(), right - swimlane.getX(), headerHeight + body);
  }

  private static int headerHeight(List<Lane> lanes) {
    int height = 0;
    for (Lane lane : lanes) {
      height = Math.max(height, lane.header.getHeight());
    }
    return height > 0 ? height : ActivityDiagramUtils.HEADER_HEIGHT;
  }

  /**
   * Lanes of the swimlane frame in their left-to-right order. Visual Paradigm's getters for the
   * frame's partition and compartment ids return null for a frame built through the API (the
   * setters do not round-trip), so the lanes are also read from the frame's child shapes: each
   * vertical partition header plus the compartment whose vertical partition id is that header.
   */
  private List<Lane> lanesOf(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane) {
    Map<String, IShapeUIModel> shapes = shapesById(diagram);
    List<IShapeUIModel> children = childrenOf(swimlane);
    List<String> headerIds = new ArrayList<>();
    String[] storedHeaders = swimlane.getVerticalPartitionIds();
    if (storedHeaders != null) {
      headerIds.addAll(Arrays.asList(storedHeaders));
    }
    List<IActivitySwimlane2CompartmentUIModel> cells = new ArrayList<>();
    String[] storedCells = swimlane.getCompartmentIds();
    if (storedCells != null) {
      for (String cellId : storedCells) {
        IShapeUIModel cellShape = shapes.get(cellId);
        if (cellShape instanceof IActivitySwimlane2CompartmentUIModel) {
          cells.add((IActivitySwimlane2CompartmentUIModel) cellShape);
        }
      }
    }
    for (IShapeUIModel child : children) {
      if (child instanceof IActivityPartitionHeaderUIModel
          && !((IActivityPartitionHeaderUIModel) child).isHorizontal()
          && !headerIds.contains(child.getId())) {
        headerIds.add(child.getId());
        shapes.put(child.getId(), child);
      } else if (child instanceof IActivitySwimlane2CompartmentUIModel
          && !containsShape(cells, child)) {
        cells.add((IActivitySwimlane2CompartmentUIModel) child);
      }
    }

    List<Lane> lanes = new ArrayList<>();
    for (String headerId : headerIds) {
      IShapeUIModel headerShape = shapes.get(headerId);
      if (!(headerShape instanceof IActivityPartitionHeaderUIModel)
          || !(headerShape.getModelElement() instanceof IActivityPartition)) {
        continue;
      }
      IActivitySwimlane2CompartmentUIModel cell = null;
      for (IActivitySwimlane2CompartmentUIModel candidate : cells) {
        if (headerId.equals(candidate.getVerticalPartitionId())) {
          cell = candidate;
          break;
        }
      }
      if (cell != null) {
        lanes.add(
            new Lane(
                (IActivityPartition) headerShape.getModelElement(),
                (IActivityPartitionHeaderUIModel) headerShape,
                cell));
      }
    }
    return lanes;
  }

  /** Record the lanes' header and compartment ids on the frame, as Visual Paradigm saves them. */
  private void storeLaneIds(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane) {
    List<Lane> lanes = lanesOf(diagram, swimlane);
    String[] headerIds = new String[lanes.size()];
    String[] cellIds = new String[lanes.size()];
    for (int i = 0; i < lanes.size(); i++) {
      headerIds[i] = lanes.get(i).header.getId();
      cellIds[i] = lanes.get(i).cell.getId();
    }
    swimlane.setVerticalPartitionIds(headerIds);
    swimlane.setCompartmentIds(cellIds);
  }

  private static List<IShapeUIModel> childrenOf(IShapeUIModel shape) {
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

  private static boolean containsShape(List<? extends IShapeUIModel> shapes, IShapeUIModel shape) {
    for (IShapeUIModel candidate : shapes) {
      if (sameShape(candidate, shape)) {
        return true;
      }
    }
    return false;
  }

  private Lane findLane(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane, String name) {
    for (Lane lane : lanesOf(diagram, swimlane)) {
      if (name.equals(lane.name())) {
        return lane;
      }
    }
    return null;
  }

  private Lane findLaneOnDiagram(IActivityDiagramUIModel diagram, String name) {
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
    return swimlane == null ? null : findLane(diagram, swimlane, name);
  }

  /** The lane whose body contains the point, or null. */
  private Lane laneAt(IActivityDiagramUIModel diagram, int px, int py) {
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
    if (swimlane == null) {
      return null;
    }
    List<Lane> lanes = lanesOf(diagram, swimlane);
    int[] xs = new int[lanes.size()];
    int[] widths = new int[lanes.size()];
    for (int i = 0; i < lanes.size(); i++) {
      xs[i] = lanes.get(i).cell.getX();
      widths[i] = lanes.get(i).cell.getWidth();
    }
    int index = ActivityDiagramUtils.laneIndexAt(px, xs, widths);
    if (index < 0) {
      return null;
    }
    Lane lane = lanes.get(index);
    int bodyTop = lane.cell.getY();
    return py >= bodyTop && py < bodyTop + lane.cell.getHeight() ? lane : null;
  }

  private IActivitySwimlane2NewUIModel findSwimlane(IActivityDiagramUIModel diagram) {
    for (IShapeUIModel shape : shapesById(diagram).values()) {
      if (shape instanceof IActivitySwimlane2NewUIModel) {
        return (IActivitySwimlane2NewUIModel) shape;
      }
    }
    return null;
  }

  /** The lane whose compartment holds the shape, or null for a node outside the swimlane. */
  private Lane laneOf(IActivityDiagramUIModel diagram, IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
    if (parent == null || swimlane == null) {
      return null;
    }
    for (Lane lane : lanesOf(diagram, swimlane)) {
      if (lane.cell.getId().equals(parent.getId())) {
        return lane;
      }
    }
    return null;
  }

  // --- Flows ---

  /**
   * Connect two nodes with a control flow, or an object flow when an end is an object node.
   *
   * @param diagramName the activity diagram
   * @param fromNode the source node name
   * @param toNode the target node name
   * @param guard the guard condition, or empty for none
   * @param twoWay true to also draw the flow back, as a second arrow beside the first
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityFlow",
      description =
          "Connect two nodes of an activity diagram (by action/control/object node name) with a"
              + " flow arrow. guard is the condition shown as [guard] on the arrow, typically on"
              + " flows leaving a decision (\"\" = none; brackets optional). The flow is an object"
              + " flow when either end is an object node, otherwise a control flow. twoWay=true"
              + " also draws the flow back as a second arrow running beside the first (the book's"
              + " action <-> \"XxxDAO: method()\" exchange). Every flow is a horizontal or vertical"
              + " line (right-angle elbows); the branches of a decision leave from different"
              + " vertices of its diamond, the inputs of a merge arrive on different sides, the"
              + " flows of a fork or join meet the bar each at its own place. The diagram is laid"
              + " out again after each flow: rows follow the flow, nodes stand in the column of"
              + " their lane, nothing overlaps (autoLayoutDiagram does it on demand)")
  public String addActivityFlow(
      String diagramName, String fromNode, String toNode, String guard, boolean twoWay) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            IShapeUIModel from = isSet(fromNode) ? findNodeShape(diagram, fromNode.trim()) : null;
            if (from == null) {
              return "Node not on diagram: " + fromNode;
            }
            IShapeUIModel to = isSet(toNode) ? findNodeShape(diagram, toNode.trim()) : null;
            if (to == null) {
              return "Node not on diagram: " + toNode;
            }
            if (sameShape(from, to)) {
              return "A flow needs two different nodes; loop back through a merge or decision"
                  + " node instead";
            }
            IModelElement source = from.getModelElement();
            IModelElement target = to.getModelElement();
            String error =
                ActivityDiagramUtils.validateFlow(
                    ActivityLayoutProfile.kindOf(source), ActivityLayoutProfile.kindOf(target));
            if (error == null && twoWay) {
              error =
                  ActivityDiagramUtils.validateFlow(
                      ActivityLayoutProfile.kindOf(target), ActivityLayoutProfile.kindOf(source));
            }
            if (error != null) {
              return error;
            }

            if (flowExists(from, to) || (twoWay && flowExists(to, from))) {
              return "A flow '"
                  + fromNode.trim()
                  + "' -> '"
                  + toNode.trim()
                  + "' already exists on diagram '"
                  + diagramName
                  + "'";
            }
            String condition = ActivityDiagramUtils.normalizeGuard(guard);
            boolean objectFlow =
                ActivityDiagramUtils.isObjectFlow(
                    ActivityLayoutProfile.kindOf(source), ActivityLayoutProfile.kindOf(target));
            IModelElement flow = createFlow(source, target, condition, objectFlow);
            IDiagramElement created = connectShapes(diagram, flow, from, to);
            if (created == null) {
              flow.delete();
              return "Error adding flow: Visual Paradigm refused the connector";
            }
            if (twoWay) {
              IModelElement back = createFlow(target, source, null, objectFlow);
              if (connectShapes(diagram, back, to, from) == null) {
                back.delete();
                relayout(diagram);
                return "Added the flow '"
                    + fromNode.trim()
                    + "' -> '"
                    + toNode.trim()
                    + "' but Visual Paradigm refused the flow back";
              }
            }
            // every node, flow and guard is laid out again around the new flow
            relayout(diagram);

            String suffix = condition == null ? "" : " " + ActivityDiagramUtils.formatGuard(guard);
            return "Added "
                + (objectFlow ? "object" : "control")
                + " flow '"
                + fromNode.trim()
                + (twoWay ? "' <-> '" : "' -> '")
                + toNode.trim()
                + "'"
                + suffix;
          });
    } catch (Exception e) {
      return "Error adding flow: " + e.getMessage();
    }
  }

  /** Whether a flow from {@code from} to {@code to} is already drawn. */
  private static boolean flowExists(IShapeUIModel from, IShapeUIModel to) {
    IConnectorUIModel[] outgoing = from.toFromConnectorArray();
    if (outgoing != null) {
      for (IConnectorUIModel c : outgoing) {
        IModelElement model = c.getModelElement();
        if ((model instanceof IControlFlow || model instanceof IActivityObjectFlow)
            && sameShape(c.getToShape(), to)) {
          return true;
        }
      }
    }
    return false;
  }

  /** A control or object flow model from source to target, owned like VP saves it. */
  private IModelElement createFlow(
      IModelElement source, IModelElement target, String condition, boolean objectFlow) {
    if (objectFlow) {
      IActivityObjectFlow objectEdge = getModelElementFactory().createActivityObjectFlow();
      ownFlow(objectEdge, source, target);
      objectEdge.setFrom(source);
      objectEdge.setTo(target);
      if (condition != null) {
        objectEdge.setGuard(condition);
      }
      return objectEdge;
    }
    IControlFlow controlEdge = getModelElementFactory().createControlFlow();
    ownFlow(controlEdge, source, target);
    controlEdge.setFrom(source);
    controlEdge.setTo(target);
    if (condition != null) {
      controlEdge.setGuard(condition);
    }
    return controlEdge;
  }

  /** Flows between nodes of the swimlane are owned by the swimlane model, as VP saves them. */
  private static void ownFlow(IModelElement flow, IModelElement source, IModelElement target) {
    IModelElement owner = null;
    if (source.getParent() instanceof IActivitySwimlane2) {
      owner = source.getParent();
    } else if (target.getParent() instanceof IActivitySwimlane2) {
      owner = target.getParent();
    }
    if (owner != null) {
      owner.addChild(flow);
    }
  }

  // --- Lookups ---

  private IActivityDiagramUIModel findActivityDiagram(String diagramName) {
    return findDiagram(diagramName, IActivityDiagramUIModel.class, "activity");
  }

  /**
   * Every shape on the diagram by id, including shapes nested in containers (lane compartments), in
   * case the diagram iterator only returns top-level elements.
   */
  private Map<String, IShapeUIModel> shapesById(IDiagramUIModel diagram) {
    Map<String, IShapeUIModel> shapes = new LinkedHashMap<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel) {
        collectShapes((IShapeUIModel) de, shapes);
      }
    }
    return shapes;
  }

  private static void collectShapes(IShapeUIModel shape, Map<String, IShapeUIModel> shapes) {
    if (shape == null || shapes.containsKey(shape.getId())) {
      return;
    }
    shapes.put(shape.getId(), shape);
    Iterator<?> children = shape.childIterator();
    while (children != null && children.hasNext()) {
      Object child = children.next();
      if (child instanceof IShapeUIModel) {
        collectShapes((IShapeUIModel) child, shapes);
      }
    }
  }

  /** The activity node shapes (actions, control and object nodes) of the diagram. */
  private List<IShapeUIModel> nodeShapes(IDiagramUIModel diagram) {
    List<IShapeUIModel> nodes = new ArrayList<>();
    for (IShapeUIModel shape : shapesById(diagram).values()) {
      if (ActivityLayoutProfile.kindOf(shape.getModelElement()) != null) {
        nodes.add(shape);
      }
    }
    return nodes;
  }

  /** The activity node shape whose model is named {@code name}; partitions never match. */
  private IShapeUIModel findNodeShape(IDiagramUIModel diagram, String name) {
    for (IShapeUIModel shape : nodeShapes(diagram)) {
      String handle = handles.get(shape.getId());
      if (handle != null ? name.equals(handle) : name.equals(shape.getModelElement().getName())) {
        return shape;
      }
    }
    return null;
  }

  // --- Small helpers ---

  /**
   * Force Visual Paradigm to render the caption of a freshly created shape or connector. Without
   * this, node labels and flow guards come out blank.
   */
  private static void resetCaption(IDiagramElement de) {
    if (de instanceof IBaseDiagramElement) {
      ((IBaseDiagramElement) de).resetCaption();
    }
  }

  /** Hide the name label of an unlabelled UML control node. */
  private static void hideCaption(IShapeUIModel shape) {
    ICaptionUIModel caption = shape.getCaptionUIModel();
    if (caption != null) {
      caption.setVisible(false);
    }
  }

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IShapeUIModel a, IShapeUIModel b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  private static boolean isSet(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String duplicateNodeMessage(String name) {
    return "Node already on diagram: "
        + name
        + " (node names are the handles used by addActivityFlow; pick a distinct name)";
  }

  private static String laneSuffix(Lane lane) {
    return lane == null ? "" : " in partition '" + lane.name() + "'";
  }

  private static String bounds(IDiagramElement de) {
    return de.getX() + "," + de.getY() + " " + de.getWidth() + "x" + de.getHeight();
  }
}
