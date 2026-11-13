package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IModelElement;

/**
 * Entity-relationship diagrams: the referenced (parent) tables stand left of the tables that hold
 * their foreign keys, the tables of one level in a column. A foreign key is a horizontal or
 * vertical line that meets each table on its border, where Visual Paradigm draws the crow's foot or
 * the bar; the name of a relationship sits beside its line.
 */
public final class ErdLayoutProfile extends LayoutProfile {

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options().direction(ElkLayout.Dir.RIGHT).gaps(50, 110, 18).padding(30, 30);
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
    spec.priority = 2;
    addCaptionLabel(spec, c, model == null ? null : model.getName());
    return spec;
  }
}
