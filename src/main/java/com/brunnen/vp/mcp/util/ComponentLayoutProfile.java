package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IComponent;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IPort;

/**
 * Component diagrams: layer frames (packages) and components that hold other components are
 * containers, a dependency leaves the client (it stands above) for the supplier, an interface
 * stands between the component that requires it and the one that provides it. Every line is
 * horizontal or vertical and drawn by the router, so that a line to a port leaves it outwards. A
 * port sits on the border of its component, on the side its lines lead away to, with its name
 * outside.
 */
public final class ComponentLayoutProfile extends LayoutProfile {

  /** Gap between a port and its name. */
  private static final int NAME_GAP = 3;

  /** Room beside and above/below a component that has ports: the names hang there. */
  private static final int NAME_ROOM = 18;

  /** Height of the band across the top of a frame: the tab and the name. */
  private static final int FRAME_HEADER = 44;

  /** Height of the keyword and name at the top of a component that holds other components. */
  private static final int COMPONENT_HEADER = 40;

  @Override
  public boolean routesAll() {
    return true;
  }

  @Override
  public int laneGap() {
    return 24;
  }

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options().direction(ElkLayout.Dir.DOWN).gaps(70, 100, 18).padding(48, 30);
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (model == null) {
      return null;
    }
    NodeSpec spec = new NodeSpec();
    if (model instanceof IPort) {
      spec.outsideLabel = nameBox(shape);
      spec.growable = false;
    } else if (model instanceof IPackage) {
      // the name sits in the folder tab and across the top of the frame
      spec.minSize = new int[] {TextMetrics.width(model.getName(), true) + 60, 0};
      spec.outline = ShapeOutline.Kind.FOLDER;
    } else if (model instanceof IComponent) {
      int[] names = portNames(shape);
      if (names != null) {
        // the names of the ports hang beside and above/below the component: neighbours keep away
        spec.pad = new int[] {names[0] + NAME_ROOM, NAME_ROOM, names[0] + NAME_ROOM, NAME_ROOM};
      }
    }
    return spec;
  }

  /** Width of the widest port name of a component that holds no other shape, null without ports. */
  private static int[] portNames(IShapeUIModel component) {
    IShapeUIModel[] children = component.toChildArray();
    int widest = -1;
    for (IShapeUIModel child : children == null ? new IShapeUIModel[0] : children) {
      if (child.getModelElement() instanceof IPort) {
        widest = Math.max(widest, nameBox(child)[0]);
      } else {
        return null; // a component that holds components is sized around them
      }
    }
    return widest < 0 ? null : new int[] {widest};
  }

  @Override
  public IShapeUIModel attachedTo(IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    return shape.getModelElement() instanceof IPort && parent instanceof IShapeUIModel
        ? (IShapeUIModel) parent
        : null;
  }

  @Override
  public IShapeUIModel container(IShapeUIModel shape) {
    return shape.getModelElement() instanceof IPort ? null : super.container(shape);
  }

  @Override
  public int headerHeight(IShapeUIModel container) {
    return container.getModelElement() instanceof IPackage ? FRAME_HEADER : COMPONENT_HEADER;
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

  /** Width and height of the name a port shows. */
  private static int[] nameBox(IShapeUIModel port) {
    ICaptionUIModel caption = port.getCaptionUIModel();
    String name = port.getModelElement().getName();
    int w = Math.max(TextMetrics.width(name, false) + 8, caption == null ? 0 : caption.getWidth());
    return new int[] {w, TextMetrics.LINE_HEIGHT};
  }

  /**
   * Where the name of a port goes: above-left of a port on the west side and above-right of one on
   * the east or north side (the line leaves the square at its middle, below the name), below-right
   * of a port on the south side, outside the component.
   */
  private static int[] labelRect(IShapeUIModel port, int side) {
    int[] box = nameBox(port);
    int x = port.getX();
    int y = port.getY();
    int w = port.getWidth();
    int h = port.getHeight();
    switch (side) {
      case 3:
        return new int[] {x - NAME_GAP - box[0], y - box[1] - 1, box[0], box[1]};
      case 2:
        return new int[] {x + w + NAME_GAP, y + h + 1, box[0], box[1]};
      default:
        return new int[] {x + w + NAME_GAP, y - box[1] - 1, box[0], box[1]};
    }
  }

  /** The side of its owner a port sits on, from where it stands now. */
  private static int sideOf(IShapeUIModel port) {
    IDiagramElement parent = port.getParent();
    if (!(parent instanceof IShapeUIModel)) {
      return 1;
    }
    IShapeUIModel owner = (IShapeUIModel) parent;
    int cx = port.getX() + port.getWidth() / 2;
    int cy = port.getY() + port.getHeight() / 2;
    int west = Math.abs(cx - owner.getX());
    int east = Math.abs(cx - (owner.getX() + owner.getWidth()));
    int north = Math.abs(cy - owner.getY());
    int south = Math.abs(cy - (owner.getY() + owner.getHeight()));
    int best = Math.min(Math.min(west, east), Math.min(north, south));
    return best == east ? 1 : best == west ? 3 : best == north ? 0 : 2;
  }

  @Override
  public int[] hanging(IShapeUIModel shape) {
    return shape.getModelElement() instanceof IPort ? labelRect(shape, sideOf(shape)) : null;
  }

  @Override
  public void placedAttached(IShapeUIModel shape, int side) {
    shape.resetCaption(); // a port's name is a detached label laid out where the caption is reset
    ICaptionUIModel caption = shape.getCaptionUIModel();
    if (caption != null) {
      int[] r = labelRect(shape, side);
      caption.setBounds(r[0], r[1], Math.max(r[2], caption.getWidth()), r[3]);
    }
  }
}
