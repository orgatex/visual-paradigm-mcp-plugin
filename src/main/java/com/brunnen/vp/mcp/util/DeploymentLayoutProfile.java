package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IArtifact;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.INode;
import com.vp.plugin.model.IStereotype;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deployment diagrams: nodes, devices and execution environments are flat rectangles that hold the
 * nodes, artifacts and components deployed on them (containers sized around their contents, the
 * keyword and name across the top), communication paths and dependencies run from the left to the
 * right as horizontal and vertical lines, the protocol or keyword of a path sits beside its line.
 */
public final class DeploymentLayoutProfile extends LayoutProfile {

  /** Height of the band across the top of a node: its keyword and name. */
  public static final int HEADER = 46;

  /** Padding between the border of a node and what it holds. */
  private static final int PAD = 22;

  /** Width Visual Paradigm needs beside the name of an artifact for its icon. */
  private static final int ARTIFACT_ICON = 44;

  private final Map<String, int[]> asked;

  /**
   * A profile.
   *
   * @param asked width and height asked for by shape id (nodes never get smaller than that)
   */
  public DeploymentLayoutProfile(Map<String, int[]> asked) {
    this.asked = asked;
  }

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options()
        .direction(ElkLayout.Dir.RIGHT)
        .flexibleDirection()
        .gaps(48, 80, 18)
        .labelAllowance(44)
        .padding(HEADER, PAD);
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (model == null) {
      return null;
    }
    NodeSpec spec = new NodeSpec();
    int[] title = titleSize(shape);
    int[] wanted = asked.get(shape.getId());
    spec.minSize =
        new int[] {
          Math.max(title[0], wanted == null ? 0 : wanted[0]), wanted == null ? 0 : wanted[1]
        };
    return spec;
  }

  @Override
  public int headerHeight(IShapeUIModel container) {
    return HEADER;
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

  @Override
  public void placed(IShapeUIModel shape) {
    syncCaption(shape);
  }

  /**
   * Draw a node as a flat rectangle, never as Visual Paradigm's 3-D box: the primitive rectangle
   * shows the keyword (&lt;&lt;device&gt;&gt;, &lt;&lt;executionEnvironment&gt;&gt;) from the
   * model's stereotype and the name as the shape's text.
   *
   * @param shape the shape of a node
   */
  public static void flatten(IShapeUIModel shape) {
    shape.setPresentationOption(IShapeUIModel.PRESENTATION_OPTION_PRIMITIVE);
    shape.setPrimitiveShapeType(IShapeUIModel.PRIMITIVE_SHAPE_TYPE_RECTANGLE);
    shape.setCustomText(shape.getModelElement().getName());
    shape.resetCaption();
  }

  /** The lines of the title of a node or artifact: its keyword (when it has one) and its name. */
  private static List<String> titleLines(IShapeUIModel shape) {
    List<String> lines = new ArrayList<>();
    IModelElement model = shape.getModelElement();
    if (model instanceof IArtifact) {
      lines.add(DeploymentDiagramUtils.keyword("artifact"));
    }
    IStereotype[] stereotypes = model == null ? null : model.toStereotypeModelArray();
    for (IStereotype st : stereotypes == null ? new IStereotype[0] : stereotypes) {
      lines.add(DeploymentDiagramUtils.keyword(st.getName()));
    }
    lines.add(model == null ? "" : model.getName());
    return lines;
  }

  /**
   * Smallest {width, height} of a node or artifact that shows its whole title.
   *
   * @param shape the shape
   * @return the size, {0, 0} for a shape that is neither
   */
  public static int[] titleSize(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (!(model instanceof INode) && !(model instanceof IArtifact)) {
      return new int[] {0, 0};
    }
    int width = 0;
    List<String> lines = titleLines(shape);
    for (String line : lines) {
      width = Math.max(width, TextMetrics.width(line, true));
    }
    int extra = model instanceof IArtifact ? ARTIFACT_ICON : 28;
    return new int[] {width + extra, lines.size() * 16 + 18};
  }

  /**
   * Make the title of a flat node as wide as the node (VP wraps it at the width of the caption box,
   * which stays at the size it had when the shape was created) and put it at the top of a container
   * or the middle of a leaf.
   *
   * @param shape the shape; anything but a node is left alone
   */
  public static void syncCaption(IShapeUIModel shape) {
    if (!(shape.getModelElement() instanceof INode)) {
      return;
    }
    ICaptionUIModel caption = shape.getCaptionUIModel();
    if (caption == null) {
      return;
    }
    IShapeUIModel[] children = shape.toChildArray();
    boolean leaf = children == null || children.length == 0;
    int height = Math.max(caption.getHeight(), titleLines(shape).size() * 15);
    int top = leaf ? Math.max((shape.getHeight() - height) / 2, 4) : 8;
    caption.setBounds(0, top, shape.getWidth(), height);
  }
}
