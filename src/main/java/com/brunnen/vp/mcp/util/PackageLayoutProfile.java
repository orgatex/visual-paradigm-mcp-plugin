package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IPackage;

/**
 * Package diagrams: a package is a container around the packages and classes it holds, a dependency
 * leaves the package that depends (it stands above) for the package it depends on. The keyword of a
 * dependency (&lt;&lt;use&gt;&gt;, &lt;&lt;import&gt;&gt;) sits beside its line.
 */
public final class PackageLayoutProfile extends LayoutProfile {

  /** Height of the band across the top of a frame: the folder tab and the name. */
  private static final int FRAME_HEADER = 44;

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options().direction(ElkLayout.Dir.DOWN).gaps(60, 90, 18).padding(44, 24);
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (model == null) {
      return null;
    }
    NodeSpec spec = new NodeSpec();
    if (model instanceof IPackage) {
      // the name sits in the folder tab and across the top of the frame
      spec.minSize = new int[] {TextMetrics.width(model.getName(), true) + 60, 0};
      spec.outline = ShapeOutline.Kind.FOLDER;
    }
    return spec;
  }

  @Override
  public int headerHeight(IShapeUIModel container) {
    return FRAME_HEADER;
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    IModelElement model = c.getModelElement();
    if (model instanceof IAnchor) {
      spec.reverse = true; // a note stands next to what it explains
      return spec;
    }
    addCaptionLabel(spec, c, null);
    return spec;
  }
}
