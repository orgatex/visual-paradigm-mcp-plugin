package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeTypeConstants;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IBaseCommunicationMessageUIModel;
import com.vp.plugin.diagram.shape.ICommunicationReverseMessageUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IInteractionActor;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionLifeLineLink;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.INOTE;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Communication diagrams: the participants are laid out in the book's layers (the actor on top,
 * then boundaries, controls, entities), every link is a horizontal or vertical line, and every
 * message of a link is an arrow with its text beside the link: the arrow turns along the leg it
 * stands next to, forward messages on one side of the line and messages back on the other.
 */
public final class CommunicationLayoutProfile extends LayoutProfile {

  /** Side of the box an arrow occupies beside its link. */
  public static final int ARROW = 24;

  /** Side of the invisible box Visual Paradigm draws a message arrow in (the arrow is centered). */
  public static final int MESSAGE_BOX = 60;

  /** Free space between the text of a message and its arrow. */
  private static final int TEXT_GAP = 2;

  /** Least distance between two links that run side by side: room for an arrow and its text. */
  private static final int LINK_GAP = 36;

  /** Row of each role (actor, boundary, control, entity) among the roles the diagram uses. */
  private final int[] rowOfRank = {-1, -1, -1, -1};

  @Override
  public boolean routesAll() {
    return true;
  }

  @Override
  public boolean shiftsIntoCanvas() {
    return true;
  }

  @Override
  public int laneGap() {
    return LINK_GAP;
  }

  @Override
  public void beforeLayout(IDiagramUIModel diagram) {
    boolean[] used = new boolean[4];
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      int rank = isParticipant(s.getModelElement()) ? rank(s) : -1;
      if (rank >= 0) {
        used[rank] = true;
      }
    }
    int next = 0;
    for (int r = 0; r < used.length; r++) {
      rowOfRank[r] = used[r] ? next++ : -1;
    }
  }

  @Override
  public boolean reopensDiagram() {
    return true;
  }

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    int widest = 0;
    int most = 1;
    for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
      if (c.getModelElement() instanceof IInteractionLifeLineLink) {
        List<IShapeUIModel> messages = messagesOf(c);
        most = Math.max(most, messages.size());
        for (IShapeUIModel m : messages) {
          widest = Math.max(widest, textWidth(m));
        }
      }
    }
    // labels stand beside the links: room for the widest between two neighbours of a row, for the
    // stacked messages of the busiest link between two rows
    return new ElkLayout.Options()
        .direction(ElkLayout.Dir.DOWN)
        .gaps(
            Math.min(Math.max(110, widest + ARROW + 50), 280), Math.max(110, most * ARROW + 90), 18)
        .edgeNodeGap(40)
        .padding(40, 40)
        .labelAllowance(0)
        .flowOrder()
        .centerParents();
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (model instanceof INOTE) {
      return new NodeSpec(); // a note stands next to what it explains, its anchor is routed
    }
    if (!isParticipant(model)) {
      return null;
    }
    NodeSpec spec = new NodeSpec();
    spec.growable = false;
    int rank = rank(shape);
    spec.row = rank < 0 ? -1 : rowOfRank[rank];
    int[] name = nameBox(shape);
    if (name != null) {
      spec.outsideLabel = name;
      spec.labelSide = ElkLayout.Side.BELOW;
    }
    return spec;
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    IModelElement model = c.getModelElement();
    if (model instanceof IAnchor) {
      spec.reverse = true; // a note stands next to what it explains
      return spec;
    }
    if (!(model instanceof IInteractionLifeLineLink)
        || c.getFromShape() == null
        || c.getToShape() == null) {
      return null;
    }
    // the rows follow the book: actor above boundary above control above entity, whichever way
    // the first message of a link travelled; a link inside a row (or a loop) is only routed
    int from = rank(c.getFromShape());
    int to = rank(c.getToShape());
    spec.deferred = from >= 0 && from == to;
    spec.reverse = from >= 0 && to >= 0 && from > to;
    List<IShapeUIModel> messages = messagesOf(c);
    for (int i = 0; i < messages.size(); i++) {
      IShapeUIModel m = messages.get(i);
      int text = textWidth(m);
      int h = Math.max(captionHeight(m), TextMetrics.LINE_HEIGHT);
      // above a horizontal leg: the arrow next to the line, its text beyond it; beside a vertical
      // leg: the arrow next to the line, the text beyond it
      spec.labels.add(
          new LabelSpec("m" + i, Math.max(text, ARROW), ARROW + TEXT_GAP + h, ElkLayout.Slot.CENTER)
              .vertical(ARROW + TEXT_GAP + text, Math.max(ARROW, h))
              .prefer(isReverse(m) ? 1 : -1));
    }
    return spec;
  }

  /**
   * The role of a participant in the book's rows: 0 actor, 1 boundary, 2 control, 3 entity, -1 for
   * a plain object (it stands wherever its links lead).
   */
  private static int rank(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (model instanceof IInteractionActor) {
      return 0;
    }
    if (model instanceof IInteractionLifeLine) {
      String[] roles = {"boundary", "control", "entity"};
      for (int i = 0; i < roles.length; i++) {
        if (model.hasStereotype(roles[i])) {
          return i + 1;
        }
      }
    }
    return -1;
  }

  /** The message arrows of a link, in the order they were added. */
  public static List<IShapeUIModel> messagesOf(IConnectorUIModel link) {
    List<IShapeUIModel> messages = new ArrayList<>();
    IShapeUIModel[] children = link.toChildArray();
    for (IShapeUIModel child : children == null ? new IShapeUIModel[0] : children) {
      if (child instanceof IBaseCommunicationMessageUIModel) {
        messages.add(child);
      }
    }
    return messages;
  }

  /** Whether a message travels against the direction of its link. */
  public static boolean isReverse(IShapeUIModel message) {
    return message instanceof ICommunicationReverseMessageUIModel
        || IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_REVERSE_MESSAGE.equals(
            message.getShapeType());
  }

  private static boolean isParticipant(IModelElement model) {
    return model instanceof IInteractionLifeLine || model instanceof IInteractionActor;
  }

  /**
   * Width of the text of a message: Visual Paradigm's caption box can be narrower than its text, so
   * the measured text decides when it is the larger.
   */
  private static int textWidth(IShapeUIModel message) {
    ICaptionUIModel caption = message.getCaptionUIModel();
    int measured = caption != null && caption.getWidth() > 0 ? caption.getWidth() : 0;
    IModelElement model = message.getModelElement();
    int text = 0;
    if (model instanceof IMessage && model.getName() != null) {
      text =
          TextMetrics.width(
                  CommunicationDiagramUtils.formatMessageLabel(
                      ((IMessage) model).getSequenceNumber(), model.getName()),
                  false)
              + 8;
    }
    return Math.max(Math.max(measured, text), 24);
  }

  private static int captionHeight(IShapeUIModel message) {
    ICaptionUIModel caption = message.getCaptionUIModel();
    return caption != null && caption.getHeight() > 0 ? caption.getHeight() : 0;
  }

  /**
   * Width and height of the name hanging below an actor or a robustness icon (their box shows no
   * text); null for a plain object, which shows its name inside.
   */
  private static int[] nameBox(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    boolean icon =
        model instanceof IInteractionLifeLine
            && (model.hasStereotype("boundary")
                || model.hasStereotype("control")
                || model.hasStereotype("entity"));
    if (!(model instanceof IInteractionActor) && !icon) {
      return null;
    }
    ICaptionUIModel caption = shape.getCaptionUIModel();
    int w = caption != null && caption.getWidth() > 0 ? caption.getWidth() : 0;
    String name = model.getName();
    if (name == null || name.isEmpty()) {
      IModelElement type =
          model instanceof IInteractionLifeLine
              ? ((IInteractionLifeLine) model).getBaseClassifierAsModel()
              : null;
      name = type == null ? "" : ":" + type.getName();
    }
    w = Math.max(w, TextMetrics.width(name, false) + 8);
    return new int[] {w, TextMetrics.LINE_HEIGHT + 4};
  }

  @Override
  public int[] hanging(IShapeUIModel shape) {
    int[] name = nameBox(shape);
    if (name == null) {
      return null;
    }
    ICaptionUIModel caption = shape.getCaptionUIModel();
    if (caption != null && caption.isVisible() && caption.getWidth() > 0 && caption.getX() != 0) {
      return new int[] {
        caption.getX(), caption.getY(), caption.getWidth(), Math.max(caption.getHeight(), name[1])
      };
    }
    return new int[] {
      shape.getX() + shape.getWidth() / 2 - name[0] / 2,
      shape.getY() + shape.getHeight() + 2,
      name[0],
      name[1]
    };
  }

  @Override
  public void placed(IShapeUIModel shape) {
    shape.resetCaption(); // the name of an actor or an icon is a detached label
  }

  @Override
  public void applyLabel(IConnectorUIModel c, String key, int[] rect) {
    List<IShapeUIModel> messages = messagesOf(c);
    int index = Integer.parseInt(key.substring(1));
    if (index >= messages.size()) {
      return;
    }
    IShapeUIModel message = messages.get(index);
    ICaptionUIModel caption = message.getCaptionUIModel();
    int capW = textWidth(message);
    int capH = Math.max(captionHeight(message), TextMetrics.LINE_HEIGHT);
    // the size of the rectangle says which kind of leg the placer put it beside
    int hw = Math.max(capW, ARROW);
    int hh = ARROW + TEXT_GAP + capH;
    int vw = ARROW + TEXT_GAP + capW;
    int vh = Math.max(ARROW, capH);
    boolean horizontalSize = rect[2] == hw && rect[3] == hh;
    boolean verticalSize = rect[2] == vw && rect[3] == vh;
    int only = horizontalSize == verticalSize ? -1 : horizontalSize ? 0 : 1;
    int[] leg = nearestLeg(c.getPoints(), rect, only);
    int cx;
    int cy;
    int textX;
    int textY;
    if (leg == null || leg[1] == leg[3]) {
      // beside a horizontal leg: the arrow on the side of the line, the text beyond it
      boolean below = rect[1] + rect[3] / 2 > (leg == null ? rect[1] : leg[1]);
      cx = rect[0] + rect[2] / 2;
      cy = below ? rect[1] + ARROW / 2 : rect[1] + rect[3] - ARROW / 2;
      textX = rect[0] + (rect[2] - capW) / 2;
      textY = below ? rect[1] + ARROW + TEXT_GAP : rect[1];
    } else {
      boolean right = rect[0] + rect[2] / 2 > leg[0];
      cx = right ? rect[0] + ARROW / 2 : rect[0] + rect[2] - ARROW / 2;
      cy = rect[1] + rect[3] / 2;
      textX = right ? rect[0] + ARROW + TEXT_GAP : rect[0];
      textY = rect[1] + (rect[3] - capH) / 2;
    }
    message.setBounds(cx - MESSAGE_BOX / 2, cy - MESSAGE_BOX / 2, MESSAGE_BOX, MESSAGE_BOX);
    if (caption != null) {
      caption.setVisible(true);
      caption.setBounds(textX, textY, capW, capH);
    }
  }

  /**
   * The leg {x1, y1, x2, y2} of a route that lies nearest to a rectangle (not to its middle), or
   * null; {@code only} restricts the legs to horizontal (0) or vertical (1) ones, -1 for any.
   */
  private static int[] nearestLeg(Point[] points, int[] rect, int only) {
    if (points == null || points.length < 2) {
      return null;
    }
    int[] best = null;
    long bestDistance = Long.MAX_VALUE;
    for (int i = 0; i + 1 < points.length; i++) {
      int x1 = points[i].x;
      int y1 = points[i].y;
      int x2 = points[i + 1].x;
      int y2 = points[i + 1].y;
      if (only >= 0 && (y1 == y2) != (only == 0)) {
        continue; // 0: only horizontal legs, 1: only vertical ones
      }
      long dx =
          Math.max(Math.max(Math.min(x1, x2) - (rect[0] + rect[2]), rect[0] - Math.max(x1, x2)), 0);
      long dy =
          Math.max(Math.max(Math.min(y1, y2) - (rect[1] + rect[3]), rect[1] - Math.max(y1, y2)), 0);
      long distance = dx * dx + dy * dy;
      if (distance < bestDistance) {
        bestDistance = distance;
        best = new int[] {x1, y1, x2, y2};
      }
    }
    return best;
  }
}
