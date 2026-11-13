package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.brunnen.vp.mcp.util.UseCaseLayoutProfile;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.diagram.shape.IUseCaseUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.ISystem;
import com.vp.plugin.model.IUseCase;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/** MCP tools for Visual Paradigm Use Case diagram operations. */
public class UseCaseMcpTools extends AbstractDiagramMcpTools {

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return diagram instanceof IUseCaseDiagramUIModel ? new UseCaseLayoutProfile() : null;
  }

  @Tool(
      name = "createUseCaseDiagram",
      description = "Create a new use case diagram in Visual Paradigm")
  public String createUseCaseDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            // The course notation never shows the "extension points" compartment of an extended
            // use case (VP adds one with a default "ExtensionPoint" entry for every <<extend>>).
            ((IUseCaseDiagramUIModel) diagram).setShowUseCaseExtensionPoint(false);
            dm.openDiagram(diagram);
            return "Created use case diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating use case diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addActor",
      description =
          "Add an actor to a use case diagram. x/y (>0) place it explicitly: it stays there and"
              + " the lines are routed around it; otherwise the layered layout puts the actors in"
              + " one column on the left (their names hang below the figures; a specialization of"
              + " an actor is joined to its general actor by a bracket left of the column)")
  public String addActor(String actorName, String diagramName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            String name = actorName == null ? "" : actorName.trim();
            if (name.isEmpty()) {
              return "Actor name must not be empty";
            }
            if (findModelElementOnDiagram(diagram, name, IActor.class) != null) {
              return "Actor already on diagram: " + name;
            }
            if (findModelElementOnDiagram(diagram, name, IUseCase.class) != null) {
              return nameTaken(name, "use case", diagramName);
            }
            // VP keeps root names unique per type: an actor of that name elsewhere in the
            // project (another use case diagram, a sequence lifeline) is the same actor.
            IActor actor = findRootElement(name, IActor.class);
            boolean reused = actor != null;
            if (!reused) {
              actor = getModelElementFactory().createActor();
              nameInOwner(actor, null, name);
            }
            IDiagramElement de = addToDiagram(diagram, actor, name);
            fitShape(de);
            de.resetCaption(); // the name is a detached label laid out at the final bounds
            placeNew(diagram, de, x, y);
            relayout(diagram);

            return "Added actor '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + (reused ? " (existing actor of the project)" : "");
          });
    } catch (Exception e) {
      return "Error adding actor: " + e.getMessage();
    }
  }

  @Tool(
      name = "addUseCase",
      description =
          "Add a use case to a use case diagram. x/y (>0) place it explicitly (it stays there and"
              + " the lines are routed around it); otherwise the layered layout places it")
  public String addUseCase(String useCaseName, String diagramName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            String name = useCaseName == null ? "" : useCaseName.trim();
            if (name.isEmpty()) {
              return "Use case name must not be empty";
            }
            if (findModelElementOnDiagram(diagram, name, IUseCase.class) != null) {
              return "Use case already on diagram: " + name;
            }
            if (findModelElementOnDiagram(diagram, name, IActor.class) != null) {
              return nameTaken(name, "actor", diagramName);
            }
            // a use case that stands in a system boundary of another diagram is not a project
            // root any more, but it is the same use case (a second one would be renamed "X2")
            IUseCase useCase = findUseCase(name);
            boolean reused = useCase != null;
            if (!reused) {
              useCase = getModelElementFactory().createUseCase();
              nameInOwner(useCase, null, name);
            }
            IDiagramElement de = addToDiagram(diagram, useCase, name);
            if (de instanceof IUseCaseUIModel) {
              ((IUseCaseUIModel) de).setShowExtensionPoint(false);
            }
            de.resetCaption();
            fitShape(de);
            placeNew(diagram, de, x, y);
            IShapeUIModel box = boundaryOf(diagram);
            if (box != null && de instanceof IShapeUIModel) {
              // the diagram has its system boundary: a use case added later belongs inside it
              ISystem system = (ISystem) box.getModelElement();
              if (!ownsOther(system, useCase)) {
                system.addUseCase(useCase);
              }
              box.addChild((IShapeUIModel) de);
              if (!layoutOwnsShapes(diagram)) {
                growToEnclose(box);
              }
            }
            relayout(diagram);

            return "Added use case '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + (reused ? " (existing use case of the project)" : "");
          });
    } catch (Exception e) {
      return "Error adding use case: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRelationship",
      description =
          "Add a relationship (Include/Extend/Generalization/Association) between elements in a"
              + " use case diagram. Include: sourceName is the base use case, targetName the"
              + " included one. Extend: sourceName is the extending (optional) use case and"
              + " targetName the base use case the arrow points to. Generalization: sourceName is"
              + " the child and targetName the parent (the hollow triangle is drawn at"
              + " targetName). A relationship already joining the pair is reported, not drawn"
              + " twice")
  public String addRelationship(
      String diagramName, String sourceName, String targetName, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            // Search both IUseCase and IActor for source
            IModelElement source = findModelElement(sourceName, IUseCase.class, diagram);
            if (source == null) {
              source = findModelElement(sourceName, IActor.class, diagram);
            }
            if (source == null) {
              return "Source element not found: " + sourceName;
            }

            // Search both IUseCase and IActor for target
            IModelElement target = findModelElement(targetName, IUseCase.class, diagram);
            if (target == null) {
              target = findModelElement(targetName, IActor.class, diagram);
            }
            if (target == null) {
              return "Target element not found: " + targetName;
            }

            // the shape of the use case or actor found above, never one of the other kind that
            // happens to carry the same name
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Element not on diagram: " + (fromElement == null ? sourceName : targetName);
            }
            String kind = relationshipKind(relationshipType);
            if (kind == null) {
              return "Unknown relationship type: "
                  + relationshipType
                  + ". Use Include, Extend, Generalization, or Association.";
            }
            String ends = endsError(kind, source, sourceName, target, targetName);
            if (ends != null) {
              return ends;
            }
            if (fromElement.getId().equals(toElement.getId())) {
              return "A " + kind + " needs two different elements (got '" + sourceName + "' twice)";
            }
            if (findRelationship(diagram, kind, source, target) != null) {
              return kind
                  + " between '"
                  + sourceName
                  + "' and '"
                  + targetName
                  + "' already exists on diagram '"
                  + diagramName
                  + "'";
            }

            IDiagramElement connector;
            switch (kind) {
              case "Include":
                IInclude include = getModelElementFactory().createInclude();
                include.setFrom(source);
                include.setTo(target);
                connector = connectAndLayout(diagram, include, fromElement, toElement);
                break;
              case "Extend":
                // VP's IExtend runs from the extended (base) use case to the extension and draws
                // the arrowhead on its from end, so the base (targetName) is "from" and the
                // extending use case (sourceName) is "to".
                IExtend extend = getModelElementFactory().createExtend();
                extend.setFrom(target);
                extend.setTo(source);
                connector = connectAndLayout(diagram, extend, toElement, fromElement);
                hideExtensionPoints(toElement);
                relayout(diagram); // the base use case lost its compartment: it is smaller
                break;
              case "Generalization":
                // VP stores a generalization as from = general (parent), to = specific (child)
                // and draws the hollow triangle on the from end.
                IGeneralization gen = getModelElementFactory().createGeneralization();
                gen.setFrom(target);
                gen.setTo(source);
                connector = connectAndLayout(diagram, gen, toElement, fromElement);
                break;
              default:
                IAssociation assoc = getModelElementFactory().createAssociation();
                assoc.setFrom(source);
                assoc.setTo(target);
                connector = connectAndLayout(diagram, assoc, fromElement, toElement);
                break;
            }
            return connected(connector, kind, sourceName, targetName);
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  /**
   * Why a relationship of that kind cannot join the two elements (an include or extend runs between
   * use cases, a generalization between two actors or two use cases, an association joins an actor
   * and a use case), or null.
   */
  private static String endsError(
      String kind,
      IModelElement source,
      String sourceName,
      IModelElement target,
      String targetName) {
    boolean sourceUseCase = source instanceof IUseCase;
    boolean targetUseCase = target instanceof IUseCase;
    switch (kind) {
      case "Include":
      case "Extend":
        if (!sourceUseCase || !targetUseCase) {
          return "Error adding relationship: "
              + kind
              + " joins two use cases; '"
              + (sourceUseCase ? targetName : sourceName)
              + "' is an actor";
        }
        return null;
      case "Generalization":
        if (sourceUseCase != targetUseCase) {
          return "Error adding relationship: a generalization joins two actors or two use cases, not '"
              + sourceName
              + "' and '"
              + targetName
              + "'";
        }
        return null;
      default:
        if (sourceUseCase == targetUseCase) {
          return "Error adding relationship: an association joins an actor and a use case, not two "
              + (sourceUseCase ? "use cases" : "actors")
              + " ('"
              + sourceName
              + "', '"
              + targetName
              + "')";
        }
        return null;
    }
  }

  private static String nameTaken(String name, String kind, String diagramName) {
    return "Error: diagram '"
        + diagramName
        + "' already shows a "
        + kind
        + " named '"
        + name
        + "' (a name is the handle of the tools, so one diagram cannot have an actor and a use"
        + " case of the same name)";
  }

  /** A use case of the project by name: at the root or inside a system boundary. */
  private IUseCase findUseCase(String name) {
    IUseCase root = findRootElement(name, IUseCase.class);
    if (root != null) {
      return root;
    }
    for (ISystem system : allSystems()) {
      IModelElement[] children = system.toChildArray();
      for (IModelElement child : children == null ? new IModelElement[0] : children) {
        if (child instanceof IUseCase && name.equals(child.getName())) {
          return (IUseCase) child;
        }
      }
    }
    return null;
  }

  private List<ISystem> allSystems() {
    List<ISystem> systems = new ArrayList<>();
    com.vp.plugin.model.IProject project =
        com.vp.plugin.ApplicationManager.instance().getProjectManager().getProject();
    IModelElement[] roots = project == null ? null : project.toModelElementArray();
    for (IModelElement m : roots == null ? new IModelElement[0] : roots) {
      if (m instanceof ISystem) {
        systems.add((ISystem) m);
      }
    }
    return systems;
  }

  /** Canonical relationship kind (Include, Extend, Generalization, Association), or null. */
  private static String relationshipKind(String type) {
    if (type == null) {
      return null;
    }
    for (String kind : new String[] {"Include", "Extend", "Generalization", "Association"}) {
      if (kind.equalsIgnoreCase(type.trim())) {
        return kind;
      }
    }
    return null;
  }

  /**
   * The relationship of that kind already drawn between the two elements: in the requested
   * direction for include, extend and generalization (whatever VP's storage direction), in either
   * direction for an association.
   */
  private IModelElement findRelationship(
      IDiagramUIModel diagram, String kind, IModelElement source, IModelElement target) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (!(model instanceof IRelationship)) {
        continue;
      }
      IRelationship rel = (IRelationship) model;
      boolean sameKind;
      switch (kind) {
        case "Include":
          sameKind = model instanceof IInclude;
          break;
        case "Extend":
          sameKind = model instanceof IExtend;
          break;
        case "Generalization":
          sameKind = model instanceof IGeneralization;
          break;
        default:
          sameKind = model instanceof IAssociation;
          break;
      }
      if (!sameKind) {
        continue;
      }
      // the source/target the caller named, i.e. the drawn direction
      IModelElement from = isStoredReversed(model) ? rel.getTo() : rel.getFrom();
      IModelElement to = isStoredReversed(model) ? rel.getFrom() : rel.getTo();
      boolean forward = same(from, source) && same(to, target);
      boolean backward = same(from, target) && same(to, source);
      if (forward || ("Association".equals(kind) && backward)) {
        return model;
      }
    }
    return null;
  }

  private static boolean same(IModelElement a, IModelElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  /**
   * Hide the "extension points" compartment VP adds to an extended use case and shrink the ellipse
   * back to its name.
   */
  private void hideExtensionPoints(IDiagramElement useCase) {
    if (useCase instanceof IUseCaseUIModel) {
      ((IUseCaseUIModel) useCase).setShowExtensionPoint(false);
      useCase.resetCaption();
      fitShape(useCase);
    }
  }

  @Tool(
      name = "addSystemBoundary",
      description =
          "Wrap all use cases of a use case diagram in a labeled system boundary rectangle "
              + "(the module box) and lay the diagram out around it: the box is sized to the use "
              + "cases it holds, actors stand outside. A diagram has one boundary: calling it "
              + "again renames it and adds the use cases added since.")
  public String addSystemBoundary(String diagramName, String systemName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            return wrapInBoundary(diagram, diagramName, systemName);
          });
    } catch (Exception e) {
      return "Error adding system boundary: " + e.getMessage();
    }
  }

  /**
   * Wrap every use case of the diagram in the system boundary (created, or renamed and re-sized
   * when the diagram has one), moving actors the box would cover outside it. Runs on the EDT.
   */
  private String wrapInBoundary(
      IUseCaseDiagramUIModel diagram, String diagramName, String systemName) {

    // Collect use case shapes and compute their bounding box.
    List<IUseCase> useCases = new ArrayList<>();
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IDiagramElement de = (IDiagramElement) obj;
        if (de.getModelElement() instanceof IUseCase) {
          useCases.add((IUseCase) de.getModelElement());
          minX = Math.min(minX, de.getX());
          minY = Math.min(minY, de.getY());
          maxX = Math.max(maxX, de.getX() + de.getWidth());
          maxY = Math.max(maxY, de.getY() + de.getHeight());
        }
      }
    }
    if (useCases.isEmpty()) {
      return "No use cases found on diagram '" + diagramName + "' to wrap";
    }
    String boundaryName = systemName == null ? "" : systemName.trim();
    if (boundaryName.isEmpty()) {
      return "System name must not be empty";
    }
    // One boundary per diagram: a second call renames and re-sizes the existing one.
    IShapeUIModel existingBox = null;
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de.getModelElement() instanceof ISystem && de instanceof IShapeUIModel) {
        existingBox = (IShapeUIModel) de;
      }
    }
    ISystem system;
    if (existingBox != null) {
      system = (ISystem) existingBox.getModelElement();
      if (!boundaryName.equals(system.getName())) {
        system.setName(boundaryName);
      }
    } else {
      system = findRootElement(boundaryName, ISystem.class);
      if (system == null) {
        system = getModelElementFactory().createSystem();
        nameInOwner(system, null, boundaryName);
      }
    }
    List<String> notOwned = new ArrayList<>();
    for (IUseCase uc : useCases) {
      if (uc.getParent() == null || !uc.getParent().getId().equals(system.getId())) {
        // a boundary owns one use case of each name: Visual Paradigm would rename the second one
        // ("Login2") as it enters
        if (ownsOther(system, uc)) {
          notOwned.add(uc.getName());
        } else {
          system.addUseCase(uc);
        }
      }
    }

    int pad = 40;
    int boxX = minX - pad;
    int boxY = minY - pad;
    int boxW = (maxX - minX) + 2 * pad;
    int boxH = (maxY - minY) + 2 * pad;
    // Actors stand outside the system: move any actor the box would cover to the
    // nearer side of it (an auto-layout may have put a use case in the actor column).
    List<String> moved = new ArrayList<>();
    int actorGap = 40;
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de.getModelElement() instanceof IActor) || !(de instanceof IShapeUIModel)) {
        continue;
      }
      boolean inside =
          de.getX() < boxX + boxW
              && boxX < de.getX() + de.getWidth()
              && de.getY() < boxY + boxH
              && boxY < de.getY() + de.getHeight();
      if (!inside) {
        continue;
      }
      boolean left = de.getX() + de.getWidth() / 2 < boxX + boxW / 2;
      int nx = left ? boxX - actorGap - de.getWidth() : boxX + boxW + actorGap;
      if (nx < 0) {
        // no room on the left: shift the box (and its use cases) right instead
        int shift = -nx;
        boxX += shift;
        for (IDiagramElement uc : getDiagramElementsList(diagram)) {
          if (uc.getModelElement() instanceof IUseCase) {
            uc.setLocation(uc.getX() + shift, uc.getY());
          }
        }
        nx = 0;
      }
      de.setLocation(nx, de.getY());
      de.resetCaption(); // an actor's name is a detached label
      moved.add(de.getModelElement().getName());
    }

    IDiagramElement sysDe =
        existingBox != null
            ? existingBox
            : getDiagramManager().createDiagramElement(diagram, system);
    if (sysDe instanceof IShapeUIModel) {
      IShapeUIModel shape = (IShapeUIModel) sysDe;
      shape.setCustomText(boundaryName);
      shape.setBounds(boxX, boxY, boxW, boxH);
      shape.sendToBack();
      resetCaption(shape);
      // the use cases are children of the boundary on the diagram too, not only in the model
      for (IDiagramElement de : getDiagramElementsList(diagram)) {
        if (de.getModelElement() instanceof IUseCase && de instanceof IShapeUIModel) {
          IDiagramElement parent = ((IShapeUIModel) de).getParent();
          if (parent == null || !shape.getId().equals(parent.getId())) {
            shape.addChild((IShapeUIModel) de);
          }
        }
      }
      // the layout sizes the boundary around the use cases it owns (a pinned diagram keeps the box
      // where it is) and routes the connectors again
      relayout(diagram);
      if (existingBox != null) {
        // Renaming a shown element makes VP fit its shape to the new name once our call
        // returns (the diagram auto-fits); the box is a container, so its bounds are
        // applied again after that.
        final int bx = shape.getX();
        final int by = shape.getY();
        final int bw = shape.getWidth();
        final int bh = shape.getHeight();
        SwingUtilities.invokeLater(() -> shape.setBounds(bx, by, bw, bh));
      }
    }

    return (existingBox != null ? "Updated" : "Added")
        + " system boundary '"
        + boundaryName
        + "' wrapping "
        + useCases.size()
        + " use case(s) on diagram '"
        + diagramName
        + "'"
        + (moved.isEmpty()
            ? ""
            : "; moved actor(s) out of the boundary: " + String.join(", ", moved))
        + (notOwned.isEmpty()
            ? ""
            : "; WARNING: the boundary already owns another use case of the name of "
                + String.join(", ", notOwned)
                + " (a different use case): they are drawn inside the box but not owned by it");
  }

  /** The shape of the diagram's system boundary, or null. */
  private IShapeUIModel boundaryOf(IUseCaseDiagramUIModel diagram) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de.getModelElement() instanceof ISystem && de instanceof IShapeUIModel) {
        return (IShapeUIModel) de;
      }
    }
    return null;
  }

  /** Whether the system owns a use case of the same name as {@code uc} that is another element. */
  private static boolean ownsOther(ISystem system, IUseCase uc) {
    IModelElement[] children = system.toChildArray();
    for (IModelElement child : children == null ? new IModelElement[0] : children) {
      if (child instanceof IUseCase
          && uc.getName() != null
          && uc.getName().equals(child.getName())
          && !child.getId().equals(uc.getId())) {
        return true;
      }
    }
    return false;
  }

  private static String connected(
      IDiagramElement connector, String kind, String sourceName, String targetName) {
    if (connector == null) {
      return "Error adding relationship: Visual Paradigm refused a "
          + kind
          + " from '"
          + sourceName
          + "' to '"
          + targetName
          + "'";
    }
    return "Added " + kind + " from '" + sourceName + "' to '" + targetName + "'";
  }

  /** The model element of the given type and name shown on the diagram, or null. */
  private <T extends IModelElement> T findModelElementOnDiagram(
      IDiagramUIModel diagram, String name, Class<T> type) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement m = de.getModelElement();
      if (type.isInstance(m) && name.equals(m.getName())) {
        return type.cast(m);
      }
    }
    return null;
  }

  /** Freshly created actor and system shapes render without a name until the caption is reset. */
  private static void resetCaption(IDiagramElement de) {
    if (de instanceof IBaseDiagramElement) {
      ((IBaseDiagramElement) de).resetCaption();
    }
  }

  @Tool(
      name = "generateUseCaseReport",
      description = "Generate a use case analysis report for a diagram")
  public String generateReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            List<String> actorNames = new ArrayList<>();
            List<String> useCaseNames = new ArrayList<>();
            List<String> relationshipDetails = new ArrayList<>();
            java.util.Map<IModelElement, String> nameMap = new java.util.LinkedHashMap<>();

            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                String displayName = model.getName();
                if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
                  String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
                  if (caption != null && !caption.isEmpty()) {
                    displayName = caption;
                  }
                }
                nameMap.put(model, displayName);
                if (model instanceof IActor) {
                  actorNames.add(displayName);
                } else if (model instanceof IUseCase) {
                  useCaseNames.add(displayName);
                } else if (model instanceof IRelationship) {
                  String relType;
                  if (model instanceof IInclude) {
                    relType = "Include";
                  } else if (model instanceof IExtend) {
                    relType = "Extend";
                  } else if (model instanceof IGeneralization) {
                    relType = "Generalization";
                  } else if (model instanceof IAssociation) {
                    relType = "Association";
                  } else {
                    relType = "Relationship";
                  }
                  IRelationship rel = (IRelationship) model;
                  // the drawn direction: extension -> base, child -> parent
                  IModelElement fromEnd = isStoredReversed(model) ? rel.getTo() : rel.getFrom();
                  IModelElement toEnd = isStoredReversed(model) ? rel.getFrom() : rel.getTo();
                  String from =
                      fromEnd != null ? nameMap.getOrDefault(fromEnd, fromEnd.getName()) : "?";
                  String to = toEnd != null ? nameMap.getOrDefault(toEnd, toEnd.getName()) : "?";
                  relationshipDetails.add(relType + ": " + from + " -> " + to);
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("USE CASE REPORT: ").append(diagramName).append("\n");
            report.append("================================\n");
            report.append("Actors (").append(actorNames.size()).append("):\n");
            for (String name : actorNames) {
              report.append("  - ").append(name).append("\n");
            }
            report.append("Use Cases (").append(useCaseNames.size()).append("):\n");
            for (String name : useCaseNames) {
              report.append("  - ").append(name).append("\n");
            }
            report.append("Relationships (").append(relationshipDetails.size()).append("):\n");
            for (String rel : relationshipDetails) {
              report.append("  - ").append(rel).append("\n");
            }
            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }
}
