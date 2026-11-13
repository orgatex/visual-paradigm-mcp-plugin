package com.brunnen.vp.mcp.util;

import com.brunnen.vp.mcp.util.FlowchartDfdUtils.DfdRole;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IDFDataStore;
import com.vp.plugin.model.IDFExternalEntity;
import com.vp.plugin.model.IDFProcess;
import com.vp.plugin.model.IGsDfdDataStore;
import com.vp.plugin.model.IGsDfdExternalEntity;
import com.vp.plugin.model.IGsDfdProcess;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IYcDfdDataStore;
import com.vp.plugin.model.IYcDfdExternalEntity;
import com.vp.plugin.model.IYcDfdProcess;
import com.vp.plugin.model.IYdDfdDataStore;
import com.vp.plugin.model.IYdDfdExternalEntity;
import com.vp.plugin.model.IYdDfdProcess;

/**
 * Data flow diagrams of every notation (standard, Gane-Sarson, Yourdon-DeMarco, Yourdon-Coad): the
 * flow runs from the left, where the external entities stand (first layer), through the processes
 * to the data stores on the right (last layer). A flow from a process back to an entity (or from a
 * store back to a process) is laid out as the opposite flow of the same pair: two lines side by
 * side. Every line is horizontal or vertical, the name of a flow sits beside it.
 */
public final class DfdLayoutProfile extends LayoutProfile {

  /** Distance between two parallel flows: room for the label of each. */
  private static final int FLOW_LANE = 30;

  /**
   * The role a model element plays in a data flow diagram.
   *
   * @param model a model element
   * @return its role, or null when it is not a process, entity or store
   */
  public static DfdRole roleOf(IModelElement model) {
    if (model instanceof IDFProcess
        || model instanceof IGsDfdProcess
        || model instanceof IYdDfdProcess
        || model instanceof IYcDfdProcess) {
      return DfdRole.PROCESS;
    }
    if (model instanceof IDFDataStore
        || model instanceof IGsDfdDataStore
        || model instanceof IYdDfdDataStore
        || model instanceof IYcDfdDataStore) {
      return DfdRole.DATA_STORE;
    }
    if (model instanceof IDFExternalEntity
        || model instanceof IGsDfdExternalEntity
        || model instanceof IYdDfdExternalEntity
        || model instanceof IYcDfdExternalEntity) {
      return DfdRole.EXTERNAL_ENTITY;
    }
    return null;
  }

  @Override
  public int laneGap() {
    return FLOW_LANE;
  }

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options()
        .direction(ElkLayout.Dir.RIGHT)
        .gaps(70, 90, 32)
        .portGap(32) // a flow name fits above the upper and below the lower of two parallel flows
        .padding(30, 30)
        .flowOrder();
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    DfdRole role = roleOf(model);
    if (role == null) {
      return model == null ? null : new NodeSpec();
    }
    NodeSpec spec = new NodeSpec();
    if (role == DfdRole.EXTERNAL_ENTITY) {
      spec.layer = ElkLayout.FIRST_LAYER;
    } else if (role == DfdRole.DATA_STORE) {
      spec.layer = ElkLayout.LAST_LAYER;
    } else if (model instanceof IYdDfdProcess || model instanceof IYcDfdProcess) {
      spec.outline = ShapeOutline.Kind.ELLIPSE; // the circle of the Yourdon notations
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
    addCaptionLabel(spec, c, model == null ? null : model.getName());
    return spec;
  }

  @Override
  public void placed(IShapeUIModel shape) {
    shape.resetCaption(); // the name is a detached label laid out at the final bounds
  }
}
