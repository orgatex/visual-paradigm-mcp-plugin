package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IFlowchartDecision;
import com.vp.plugin.model.IFlowchartFlowline;
import com.vp.plugin.model.IFlowchartOnPageReference;
import com.vp.plugin.model.IModelElement;

/**
 * Flowcharts: the flow runs from the top to the bottom, every flow line is horizontal or vertical,
 * the branches of a decision leave from different vertices of its diamond (the Yes/No label beside
 * each), a flow back to an earlier shape goes around the shapes in between.
 */
public final class FlowchartLayoutProfile extends LayoutProfile {

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
        .gaps(70, 80, 18)
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
    if (model instanceof IFlowchartDecision) {
      spec.outline = ShapeOutline.Kind.DIAMOND;
      spec.ownSides = true;
    } else if (model instanceof IFlowchartOnPageReference) {
      spec.outline = ShapeOutline.Kind.ELLIPSE;
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
    if (model instanceof IFlowchartFlowline
        && model.getName() != null
        && !model.getName().trim().isEmpty()) {
      int[] box = TextMetrics.caption(model.getName());
      ICaptionUIModel caption = c.getCaptionUIModel();
      int w = Math.max(box[0], caption == null ? 0 : caption.getWidth());
      // the Yes/No of a decision stands next to the diamond it belongs to, not at the middle of a
      // long flow that may pass another decision
      boolean fromDecision =
          c.getFromShape() != null
              && c.getFromShape().getModelElement() instanceof IFlowchartDecision;
      spec.labels.add(
          new LabelSpec(
                  "name",
                  w,
                  TextMetrics.LINE_HEIGHT,
                  fromDecision ? ElkLayout.Slot.TAIL : ElkLayout.Slot.CENTER)
              .wrappable(model.getName()));
    }
    return spec;
  }

  @Override
  public void placed(IShapeUIModel shape) {
    shape.resetCaption(); // a name is a detached label laid out at the final bounds
    ICaptionUIModel caption = shape.getCaptionUIModel();
    if (shape.getModelElement() instanceof IFlowchartOnPageReference
        && caption != null
        && caption.getWidth() > 0) {
      // the letter of an on-page connector goes inside its circle (VP hangs it below)
      int h = caption.getHeight() > 0 ? caption.getHeight() : TextMetrics.LINE_HEIGHT;
      caption.setBounds(
          shape.getX() + (shape.getWidth() - caption.getWidth()) / 2,
          shape.getY() + (shape.getHeight() - h) / 2,
          caption.getWidth(),
          h);
    }
  }
}
