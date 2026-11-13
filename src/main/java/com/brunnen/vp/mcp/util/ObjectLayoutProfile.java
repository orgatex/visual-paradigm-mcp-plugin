package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IModelElement;

/**
 * Object diagrams: the objects stand in layers by their links (the object a link is drawn from
 * above the one it goes to), every link is a horizontal or vertical line and its name sits beside
 * the longest leg.
 */
public final class ObjectLayoutProfile extends LayoutProfile {

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options().direction(ElkLayout.Dir.DOWN).gaps(70, 90, 18).padding(30, 30);
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    return shape.getModelElement() == null ? null : new NodeSpec();
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    IModelElement model = c.getModelElement();
    if (model instanceof IAnchor) {
      spec.reverse = true; // a note stands next to what it explains
      return spec;
    }
    addCaptionLabel(spec, c, model == null ? null : model.getName());
    return spec;
  }
}
