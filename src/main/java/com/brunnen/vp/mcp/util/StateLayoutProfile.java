package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IFinalState2;
import com.vp.plugin.model.IInitialPseudoState;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.ITransition2;

/**
 * State machine diagrams: the flow runs from the initial state at the top (first layer) to the
 * final state at the bottom (last layer); transitions carry their {@code event [guard] / effect}
 * text beside them, a transition from a state to itself is a rectangular loop on a free side, two
 * opposite transitions between a pair of states run side by side. The name of an initial or final
 * state hangs on the right of its circle (below and right for the final one, so transitions can
 * arrive on every side).
 */
public final class StateLayoutProfile extends LayoutProfile {

  /** Gap between a pseudo state and its name. */
  private static final int NAME_GAP = 6;

  /** How far the loop of a self-transition reaches out of its state, with the gap to its label. */
  private static final int LOOP_EXTENT = 36;

  @Override
  public boolean routesAll() {
    return true;
  }

  @Override
  public boolean pinAware() {
    return true;
  }

  @Override
  public boolean shiftsIntoCanvas() {
    return true;
  }

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options()
        .direction(ElkLayout.Dir.DOWN)
        .gaps(80, 90, 18)
        .padding(30, 30)
        .labelAllowance(56)
        .flowOrder()
        .centerParents();
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (model == null) {
      return null;
    }
    NodeSpec spec = new NodeSpec();
    boolean initial = model instanceof IInitialPseudoState;
    if (initial || model instanceof IFinalState2) {
      spec.layer = initial ? ElkLayout.FIRST_LAYER : ElkLayout.LAST_LAYER;
      spec.outline = ShapeOutline.Kind.ELLIPSE;
      spec.growable = false;
      spec.labelSide = ElkLayout.Side.RIGHT;
      spec.outsideLabel = nameBox(shape);
      // a circle takes one connector per side; more of them make it larger
      spec.size = sizeForEnds(shape, shape.getWidth(), shape.getHeight(), 4);
    } else {
      int[] loop = loopLabelBox(shape);
      if (loop != null) {
        // the loop of a self-transition and its label go on the right of the state: room for both
        spec.labelSide = ElkLayout.Side.RIGHT;
        spec.outsideLabel = loop;
      }
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
    if (model instanceof ITransition2 && model.getName() != null && !model.getName().isEmpty()) {
      int[] box = TextMetrics.caption(model.getName());
      ICaptionUIModel caption = c.getCaptionUIModel();
      int w = Math.max(box[0], caption == null ? 0 : caption.getWidth());
      spec.labels.add(
          new LabelSpec("name", w, TextMetrics.LINE_HEIGHT, ElkLayout.Slot.CENTER)
              .wrappable(model.getName()));
    }
    return spec;
  }

  /** Room right of a state for the loop of a self-transition and its label, null without one. */
  private static int[] loopLabelBox(IShapeUIModel shape) {
    IConnectorUIModel[] outgoing = shape.toFromConnectorArray();
    int widest = 0;
    for (IConnectorUIModel c : outgoing == null ? new IConnectorUIModel[0] : outgoing) {
      IModelElement model = c.getModelElement();
      if (c.getToShape() != null
          && c.getToShape().getId().equals(shape.getId())
          && model != null
          && model.getName() != null
          && !model.getName().isEmpty()) {
        widest = Math.max(widest, TextMetrics.caption(model.getName())[0]);
      }
    }
    return widest == 0 ? null : new int[] {widest + LOOP_EXTENT, TextMetrics.LINE_HEIGHT};
  }

  @Override
  public int[] reserved(IShapeUIModel shape) {
    int[] loop = loopLabelBox(shape);
    if (loop == null) {
      return null;
    }
    // right of the loop (it reaches out 26 px): the text of the self-transition goes there
    return new int[] {
      shape.getX() + shape.getWidth() + LOOP_EXTENT - 6,
      shape.getY() + shape.getHeight() / 2 - loop[1] / 2 - 2,
      loop[0] - LOOP_EXTENT + 6,
      loop[1] + 4
    };
  }

  /** Width and height of the name an initial or final state shows. */
  private static int[] nameBox(IShapeUIModel shape) {
    String name = shape.getModelElement().getName();
    ICaptionUIModel caption = shape.getCaptionUIModel();
    int w = Math.max(TextMetrics.width(name, false) + 8, caption == null ? 0 : caption.getWidth());
    return new int[] {w, TextMetrics.LINE_HEIGHT + 2};
  }

  @Override
  public int[] hanging(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (!(model instanceof IInitialPseudoState) && !(model instanceof IFinalState2)) {
      return null;
    }
    int[] name = nameBox(shape);
    if (model instanceof IFinalState2) {
      // below and right of the circle: transitions arrive on all four sides
      return new int[] {
        shape.getX() + shape.getWidth() + 2, shape.getY() + shape.getHeight() + 2, name[0], name[1]
      };
    }
    return new int[] {
      shape.getX() + shape.getWidth() + NAME_GAP,
      shape.getY() + shape.getHeight() / 2 - name[1] / 2,
      name[0],
      name[1]
    };
  }

  @Override
  public void placed(IShapeUIModel shape) {
    shape.resetCaption(); // a name is a detached label laid out at the final bounds
    int[] spot = hanging(shape);
    ICaptionUIModel caption = shape.getCaptionUIModel();
    if (spot != null && caption != null) {
      caption.setBounds(spot[0], spot[1], spot[2], spot[3]);
    }
  }
}
