package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.ISystem;
import com.vp.plugin.model.IUseCase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Use case diagrams: the flow goes from the actors on the left (first layer) through the use cases
 * inside the system boundary. A boundary is a container around the use cases it owns; actors stay
 * outside. Include and extend lines carry a keyword label beside them, an actor's name hangs below
 * the figure.
 */
public final class UseCaseLayoutProfile extends LayoutProfile {

  private IShapeUIModel boundary;

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options()
        .direction(ElkLayout.Dir.RIGHT)
        .gaps(36, 110, 18)
        .padding(48, 36)
        .flowOrder();
  }

  @Override
  public void sortNodes(List<IShapeUIModel> shapes) {
    // actors in the order of their generalizations (a general actor, then its specializations, a
    // block per general actor: the brackets between them nest instead of crossing), the use cases
    // after the actor they are joined to
    List<IShapeUIModel> actors = new ArrayList<>();
    for (IShapeUIModel s : shapes) {
      if (s.getModelElement() instanceof IActor) {
        actors.add(s);
      }
    }
    List<IShapeUIModel> actorOrder = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (IShapeUIModel actor : actors) {
      if (parentOf(actor) == null) {
        visit(actor, actorOrder, seen);
      }
    }
    for (IShapeUIModel actor : actors) {
      visit(actor, actorOrder, seen); // a cycle of generalizations
    }
    List<IShapeUIModel> useCases = new ArrayList<>();
    List<IShapeUIModel> rest = new ArrayList<>();
    for (IShapeUIModel s : shapes) {
      if (s.getModelElement() instanceof IUseCase) {
        useCases.add(s);
      } else if (!(s.getModelElement() instanceof IActor)) {
        rest.add(s);
      }
    }
    List<IShapeUIModel> useCaseOrder = new ArrayList<>();
    Set<String> placed = new HashSet<>();
    for (IShapeUIModel actor : actorOrder) {
      for (IShapeUIModel uc : useCases) {
        if (!placed.contains(uc.getId()) && associated(actor, uc)) {
          placed.add(uc.getId());
          useCaseOrder.add(uc);
        }
      }
    }
    for (IShapeUIModel uc : useCases) {
      if (placed.add(uc.getId())) {
        useCaseOrder.add(uc);
      }
    }
    shapes.clear();
    shapes.addAll(rest);
    shapes.addAll(actorOrder);
    shapes.addAll(useCaseOrder);
  }

  private static void visit(IShapeUIModel actor, List<IShapeUIModel> order, Set<String> seen) {
    if (!seen.add(actor.getId())) {
      return;
    }
    order.add(actor);
    IConnectorUIModel[] out = actor.toFromConnectorArray();
    for (IConnectorUIModel c : out == null ? new IConnectorUIModel[0] : out) {
      // a generalization is stored from the general actor to the specialization
      if (c.getModelElement() instanceof IGeneralization
          && c.getToShape() != null
          && c.getToShape().getModelElement() instanceof IActor) {
        visit(c.getToShape(), order, seen);
      }
    }
  }

  private static IShapeUIModel parentOf(IShapeUIModel actor) {
    IConnectorUIModel[] in = actor.toToConnectorArray();
    for (IConnectorUIModel c : in == null ? new IConnectorUIModel[0] : in) {
      if (c.getModelElement() instanceof IGeneralization
          && c.getFromShape() != null
          && c.getFromShape().getModelElement() instanceof IActor) {
        return c.getFromShape();
      }
    }
    return null;
  }

  /** Whether a plain association joins the actor and the use case (either way round). */
  private static boolean associated(IShapeUIModel actor, IShapeUIModel useCase) {
    for (IConnectorUIModel[] list :
        new IConnectorUIModel[][] {actor.toFromConnectorArray(), actor.toToConnectorArray()}) {
      for (IConnectorUIModel c : list == null ? new IConnectorUIModel[0] : list) {
        IModelElement m = c.getModelElement();
        if (m != null
            && !(m instanceof IGeneralization)
            && ((c.getToShape() != null && c.getToShape().getId().equals(useCase.getId()))
                || (c.getFromShape() != null
                    && c.getFromShape().getId().equals(useCase.getId())))) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void beforeLayout(IDiagramUIModel diagram) {
    boundary = null;
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getModelElement() instanceof ISystem) {
        boundary = s;
      }
    }
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    NodeSpec spec = new NodeSpec();
    IModelElement model = shape.getModelElement();
    if (model instanceof IActor) {
      // a specialization of an actor follows its general actor instead of standing at the left
      spec.layer = ElkLayout.FIRST_LAYER;
      spec.outsideLabel = nameBox(shape);
      spec.growable = false;
      spec.outline = ShapeOutline.Kind.FIGURE;
    } else if (model instanceof IUseCase) {
      spec.outline = ShapeOutline.Kind.ELLIPSE;
    } else if (model instanceof ISystem) {
      // the title of the boundary is written across its top
      spec.minSize = new int[] {TextMetrics.width(model.getName(), false) + 48, 0};
    }
    return spec;
  }

  /** Width and height of the name an actor shows below its figure. */
  private static int[] nameBox(IShapeUIModel actor) {
    ICaptionUIModel caption = actor.getCaptionUIModel();
    String name = actor.getModelElement().getName();
    int w = caption != null && caption.getWidth() > 0 ? caption.getWidth() : 0;
    w = Math.max(w, TextMetrics.width(name, false) + 8);
    return new int[] {w, TextMetrics.LINE_HEIGHT + 6};
  }

  @Override
  public int headerHeight(IShapeUIModel container) {
    return container.getModelElement() instanceof ISystem ? 32 : 0;
  }

  @Override
  public IShapeUIModel container(IShapeUIModel shape) {
    if (boundary != null && shape.getModelElement() instanceof IUseCase) {
      IModelElement owner = ((IUseCase) shape.getModelElement()).getParent();
      if (owner != null && owner.getId().equals(boundary.getModelElement().getId())) {
        return boundary;
      }
    }
    return null;
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    IModelElement model = c.getModelElement();
    IModelElement from = c.getFromShape() == null ? null : c.getFromShape().getModelElement();
    IModelElement to = c.getToShape() == null ? null : c.getToShape().getModelElement();
    if (model instanceof IAnchor) {
      spec.reverse = true; // a note is drawn after what it explains: next to it, not far away
      return spec;
    }
    if (model instanceof IInclude || model instanceof IExtend) {
      spec.priority = 3;
      ICaptionUIModel caption = c.getCaptionUIModel();
      int w = caption != null && caption.getWidth() > 0 ? caption.getWidth() : 96;
      spec.labels.add(new LabelSpec("name", w, TextMetrics.LINE_HEIGHT, ElkLayout.Slot.CENTER));
    } else if (model instanceof IGeneralization) {
      spec.priority = 3;
      if (from instanceof IActor) {
        // the actors stand in one column: the line between a general actor and its specialization
        // runs in a channel left of the column (both ends on the west sides)
        spec.deferred = true;
        spec.fromSide = 3;
        spec.toSide = 3;
      }
    } else {
      // an actor's association: the actor first, whichever way it was stored
      spec.priority = 5;
      spec.reverse = !(from instanceof IActor) && to instanceof IActor;
    }
    return spec;
  }

  @Override
  public int[] hanging(IShapeUIModel shape) {
    if (!(shape.getModelElement() instanceof IActor)) {
      return null;
    }
    int[] name = nameBox(shape);
    return new int[] {
      shape.getX() + shape.getWidth() / 2 - name[0] / 2,
      shape.getY() + shape.getHeight(),
      name[0],
      name[1]
    };
  }

  @Override
  public void placed(IShapeUIModel shape) {
    if (shape.getModelElement() instanceof IActor) {
      shape.resetCaption(); // an actor's name is a detached label laid out at the final bounds
    }
  }
}
