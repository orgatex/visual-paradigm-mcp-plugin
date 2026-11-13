package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.INOTE;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the shared graph layout ({@link VpGraphLayout}) needs to know about one diagram type: which
 * shapes are nodes (and which of them are containers), how connectors point and what labels they
 * carry, how a node's outline looks and where its labels are written. A family overrides what
 * differs from the default: every shape is a rectangular node, every connector an unlabelled edge
 * that points from its from-shape to its to-shape.
 */
public class LayoutProfile {

  /** How one shape takes part in the layout. */
  public static final class NodeSpec {
    /**
     * {@link ElkLayout#FIRST_LAYER}, {@link ElkLayout#LAST_LAYER} or {@link ElkLayout#ANY_LAYER}.
     */
    public int layer = ElkLayout.ANY_LAYER;

    /** Outline of the shape, for where connectors end on it. */
    public ShapeOutline.Kind outline = ShapeOutline.Kind.RECT;

    /** Width and height of a text hanging outside the shape (an actor's name), null for none. */
    public int[] outsideLabel;

    /**
     * Size {w, h} the layout gives the shape instead of the one it has (a bar starts at its
     * notation size, however wide an earlier layout made it), null to keep its size.
     */
    public int[] size;

    /** Layer (row) the shape belongs to, -1 for any: shapes of one role stand in one row. */
    public int row = -1;

    /** The side of the shape that text hangs on. */
    public ElkLayout.Side labelSide = ElkLayout.Side.BELOW;

    /**
     * A bar (fork, join): as wide as the span of the shapes it joins, so every branch meets it at
     * its own place.
     */
    public boolean bar;

    /**
     * A shape with a vertex per side (a decision or merge diamond): every connector end gets a side
     * of its own where the shape has enough sides.
     */
    public boolean ownSides;

    /** Whether the layout may enlarge the shape when a side carries more connectors than fit. */
    public boolean growable = true;

    /** Least width and height of a container (room for its title), null for none. */
    public int[] minSize;

    /**
     * Room {left, top, right, bottom} the layout keeps free around the shape for what hangs on its
     * border (the names of ports): neighbours keep that far away, the shape itself keeps its size.
     * Only for a layout whose connectors are all drawn by the router ({@link
     * LayoutProfile#routesAll()}), null for none.
     */
    public int[] pad;
  }

  /** A label of a connector. */
  public static final class LabelSpec {
    /** What the label is, handed back to {@link LayoutProfile#applyLabel}. */
    public final String key;

    /** Width. */
    public final int w;

    /** Height. */
    public final int h;

    /** Near the from-shape, near the to-shape or beside the middle. */
    public final ElkLayout.Slot slot;

    /** Width beside a vertical leg (the same as {@link #w} unless set). */
    public int verticalW;

    /** Height beside a vertical leg (the same as {@link #h} unless set). */
    public int verticalH;

    /** Preferred side of the line: -1 above/left, 1 below/right, 0 none. */
    public int side;

    /** Width and height of a wrapped (narrower) box the text may take, 0 for none. */
    public int altW;

    /** Height of the wrapped box. */
    public int altH;

    /**
     * A label.
     *
     * @param key what the label is
     * @param w width
     * @param h height
     * @param slot where it sits
     */
    public LabelSpec(String key, int w, int h, ElkLayout.Slot slot) {
      this.key = key;
      this.w = w;
      this.h = h;
      this.slot = slot;
      this.verticalW = w;
      this.verticalH = h;
    }

    /**
     * Another size beside a vertical leg of the connector.
     *
     * @param vw width
     * @param vh height
     * @return this
     */
    public LabelSpec vertical(int vw, int vh) {
      this.verticalW = vw;
      this.verticalH = vh;
      return this;
    }

    /**
     * Let the text wrap into two lines when the box finds no clear place beside the line (only for
     * a text with a space in it). Visual Paradigm draws a text that is wider than its box in as
     * many lines as the box is high, so the wrapped box is as wide as the longer half of the text
     * plus the padding of a caption ({@link TextMetrics#twoLines}), never narrower: a caption that
     * is too small for its text ends in "...".
     *
     * @param text the label's text
     * @return this
     */
    public LabelSpec wrappable(String text) {
      int[] box = h <= TextMetrics.LINE_HEIGHT + 2 ? TextMetrics.twoLines(text, w) : null;
      if (box != null) {
        this.altW = box[0];
        this.altH = box[1];
      }
      return this;
    }

    /**
     * Prefer a side of the line.
     *
     * @param value -1 above a horizontal leg or left of a vertical one, 1 below or right
     * @return this
     */
    public LabelSpec prefer(int value) {
      this.side = value;
      return this;
    }
  }

  /** How one connector takes part in the layout. */
  public static final class EdgeSpec {
    /** Layer the to-shape above/before the from-shape (child to parent drawn as parent first). */
    public boolean reverse;

    /** How strongly the edge wants to be short, straight and along the flow. */
    public int priority = 1;

    /**
     * The connector does not take part in the layering: it is routed after the shapes have their
     * places (the lines between the actors of a generalization, drawn in a channel of their own).
     */
    public boolean deferred;

    /**
     * Side of the from-shape the connector may leave by: 0 north, 1 east, 2 south, 3 west, -1 any.
     */
    public int fromSide = -1;

    /** Side of the to-shape the connector may enter by, same values. */
    public int toSide = -1;

    /** Side the connector prefers to leave the from-shape by (soft, routed connectors), -1 none. */
    public int preferFromSide = -1;

    /** Side the connector prefers to enter the to-shape by (soft, routed connectors), -1 none. */
    public int preferToSide = -1;

    /** The labels of the connector. */
    public final List<LabelSpec> labels = new ArrayList<>();
  }

  /**
   * Whether the layered layout lays out the shapes that were not placed explicitly around the ones
   * that were (they stay, the others are pushed away from them and the connectors are routed
   * around). A family that answers false keeps every shape where it is as soon as one is pinned.
   *
   * @return true when pinned shapes are fixed obstacles of the layout
   */
  public boolean pinAware() {
    return false;
  }

  /**
   * Whether the layered layout may move the shapes of this diagram type. A family that places its
   * shapes itself as they are added (nested containers, explicit grids) answers false: only its
   * connectors and labels are routed.
   *
   * @return true when the shapes are laid out by the shared layout
   */
  public boolean placesShapes() {
    return true;
  }

  /**
   * Give a connector a label beside the middle of its line, the size of its caption: the name of
   * the relationship or the keyword Visual Paradigm prints (&lt;&lt;use&gt;&gt;). A connector
   * without any text gets none.
   *
   * @param spec the connector's spec
   * @param c the connector (its caption has been reset, so its size is known)
   * @param name the relationship's name, whose width is the fallback when the caption is empty
   */
  protected static void addCaptionLabel(EdgeSpec spec, IConnectorUIModel c, String name) {
    ICaptionUIModel caption = c.getCaptionUIModel();
    int w = caption == null ? 0 : caption.getWidth();
    int h = caption == null ? 0 : caption.getHeight();
    if (name != null && !name.trim().isEmpty()) {
      w = Math.max(w, TextMetrics.caption(name.trim())[0]);
      h = Math.max(h, TextMetrics.LINE_HEIGHT);
    }
    if (w > 0 && h > 0) {
      spec.labels.add(new LabelSpec("name", w, h, ElkLayout.Slot.CENTER));
    }
  }

  /**
   * The size a small shape (a circle, a diamond) needs so that every connector that ends on it has
   * a port of its own: a side takes ports {@link OrthogonalRouter#PORT_GAP} apart inside the middle
   * half of its length.
   *
   * @param shape the shape
   * @param width its notation width
   * @param height its notation height
   * @param sides the sides connectors may use (a name hanging beside the shape blocks one)
   * @return {width, height}, or null when the notation size is enough
   */
  protected static int[] sizeForEnds(IShapeUIModel shape, int width, int height, int sides) {
    int ends = 0;
    for (IConnectorUIModel[] list :
        new IConnectorUIModel[][] {shape.toFromConnectorArray(), shape.toToConnectorArray()}) {
      for (IConnectorUIModel c : list == null ? new IConnectorUIModel[0] : list) {
        if (c.getFromShape() != null
            && c.getToShape() != null
            && !c.getFromShape().getId().equals(c.getToShape().getId())) {
          ends++;
        }
      }
    }
    int perSide = (ends + sides - 1) / sides;
    if (perSide <= 1) {
      return null;
    }
    int side = 32 + 2 * OrthogonalRouter.PORT_GAP * (perSide - 1);
    return new int[] {Math.max(width, side), Math.max(height, side)};
  }

  /**
   * Whether every connector is drawn by the router instead of taking the route of the layered
   * layout: the layout only decides where the shapes go. A family with ends that need their own
   * sides (diamonds, bars) and with loops answers true.
   *
   * @return true when the router draws all connectors
   */
  public boolean routesAll() {
    return false;
  }

  /**
   * Whether a full layout may put labels left of or above the first shape: everything is then moved
   * right and down until it lies on the canvas again (a transition label wider than the room left
   * of its line sits on the left, as the line deserves, instead of on another line).
   *
   * @return true when the layout shifts a drawing that reaches off the canvas
   */
  public boolean shiftsIntoCanvas() {
    return false;
  }

  /**
   * Whether Visual Paradigm draws things only when the diagram is opened again (the arrows of a
   * communication diagram turn along their link when it lays the diagram out from scratch): the
   * diagram is then closed, opened and its connectors drawn once more after a layout.
   *
   * @return true when the diagram has to be reopened after a layout
   */
  public boolean reopensDiagram() {
    return false;
  }

  /**
   * Least distance between two parallel connectors that run side by side: the room a label needs
   * between them.
   *
   * @return the distance in pixels
   */
  public int laneGap() {
    return OrthogonalRouter.LANE_GAP;
  }

  /**
   * Layout options of the diagram: flow direction and gaps.
   *
   * @param diagram the diagram
   * @return the options
   */
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options();
  }

  /**
   * Put the shapes into the order the layered layout should see them in: a layout keeps the order
   * of the nodes of a layer when it has no reason to change it (the actors of a use case diagram
   * stand in the order of their generalizations, the use cases below the actor they belong to). The
   * default keeps the order of the diagram.
   *
   * @param shapes the shapes, reordered in place
   */
  public void sortNodes(List<IShapeUIModel> shapes) {
    // the order of the diagram
  }

  /**
   * The role of a shape in the layout.
   *
   * @param shape a shape of the diagram
   * @return how it takes part, or null when it is not part of the graph and stays where it is
   */
  public NodeSpec node(IShapeUIModel shape) {
    return new NodeSpec();
  }

  /**
   * The shape that contains {@code shape} in the layout (a package, a node, a system boundary).
   *
   * @param shape a shape of the diagram
   * @return the container shape, or null
   */
  public IShapeUIModel container(IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    if (parent instanceof IShapeUIModel) {
      return (IShapeUIModel) parent;
    }
    return shape.getModelElement() instanceof INOTE ? containerOfNoted(shape, 0) : null;
  }

  /**
   * A note stands in the container of the shape it explains (a note on a class in a package sits
   * inside that package's frame, not across its border).
   */
  private IShapeUIModel containerOfNoted(IShapeUIModel note, int depth) {
    IConnectorUIModel[] anchors = note.toFromConnectorArray();
    for (IConnectorUIModel c : anchors == null ? new IConnectorUIModel[0] : anchors) {
      IShapeUIModel to = c.getToShape();
      if (c.getModelElement() instanceof IAnchor && to != null && depth < 4) {
        return container(to);
      }
    }
    return null;
  }

  /**
   * The role of a connector in the layout.
   *
   * @param connector a connector of the diagram
   * @return how it takes part, or null when it is left alone
   */
  public EdgeSpec edge(IConnectorUIModel connector) {
    return new EdgeSpec();
  }

  /**
   * The rectangle {x, y, w, h} of what hangs outside a shape's bounds and is drawn with it (an
   * actor's name): connectors and labels keep off it like off the shape.
   *
   * @param shape the shape
   * @return the rectangle, or null when nothing hangs outside
   */
  public int[] hanging(IShapeUIModel shape) {
    return null;
  }

  /**
   * The rectangle {x, y, w, h} beside a shape that is kept free for the label of one of its own
   * connectors (the loop of a self-transition and its text): the routes of other connectors keep
   * off it, labels may use it.
   *
   * @param shape the shape
   * @return the rectangle, or null when nothing is reserved
   */
  public int[] reserved(IShapeUIModel shape) {
    return null;
  }

  /**
   * The shape that {@code shape} sits on the border of (a port on its component). Such a shape is
   * no node of the layered layout: the connectors that end on it are laid out as connectors of its
   * owner, and once the owner has its place the shape is put on the side of the owner that its
   * connectors lead away to ({@link #placedAttached}). Only for a layout whose connectors are all
   * drawn by the router ({@link #routesAll()}).
   *
   * @param shape a shape of the diagram
   * @return the owner shape, or null when the shape stands on its own
   */
  public IShapeUIModel attachedTo(IShapeUIModel shape) {
    return null;
  }

  /**
   * Called when an attached shape was put on the border of its owner: its name moves outside.
   *
   * @param shape the attached shape, at its new place
   * @param side the side of the owner it sits on: 0 north, 1 east, 2 south, 3 west
   */
  public void placedAttached(IShapeUIModel shape, int side) {
    // nothing by default
  }

  /**
   * Height of the band across the top of a container that holds its title (the tab and name of a
   * package, the keyword and name of a node): labels keep off it.
   *
   * @param container a container shape
   * @return the height in pixels, 0 for none
   */
  public int headerHeight(IShapeUIModel container) {
    return 0;
  }

  /**
   * Draw a label of a connector at a place.
   *
   * @param connector the connector
   * @param key the label's key from its {@link LabelSpec}
   * @param rect where it goes {x, y, w, h}, absolute
   */
  public void applyLabel(IConnectorUIModel connector, String key, int[] rect) {
    ICaptionUIModel caption = connector.getCaptionUIModel();
    if (caption != null) {
      caption.setVisible(true);
      caption.setBounds(rect[0], rect[1], rect[2], rect[3]);
    }
  }

  /**
   * Change the places the layered layout chose, before any shape is moved: a family with columns of
   * its own (swimlanes) puts every node in its column here and sizes the columns.
   *
   * @param shapes the shapes of the layout by id
   * @param rects their rectangles {x, y, w, h}, changed in place
   */
  public void arrange(Map<String, IShapeUIModel> shapes, Map<String, int[]> rects) {
    // nothing by default
  }

  /**
   * Straight lines {x1, y1, x2, y2} that connectors keep {@link #laneGap()} away from where they
   * run alongside, as they do from other connectors (the borders of swimlanes: a flow that ran on a
   * border would read as part of it). Crossing one is allowed.
   *
   * @return the lines, none by default
   */
  public List<int[]> guides() {
    return new ArrayList<>();
  }

  /**
   * The horizontal range {minX, maxX} a bar (fork, join) may span, null for none: a bar in a lane
   * stays inside it.
   *
   * @param bar the bar shape
   * @return the range, or null
   */
  public int[] barRange(IShapeUIModel bar) {
    return null;
  }

  /**
   * Called once a shape has been put at its new place (its detached labels follow).
   *
   * @param shape the shape
   */
  public void placed(IShapeUIModel shape) {
    // nothing by default
  }

  /**
   * Called before the layout reads the diagram (refit shapes, reset captions).
   *
   * @param diagram the diagram
   */
  public void beforeLayout(IDiagramUIModel diagram) {
    // nothing by default
  }

  /**
   * Called after the layout was applied.
   *
   * @param diagram the diagram
   */
  public void afterLayout(IDiagramUIModel diagram) {
    // nothing by default
  }
}
