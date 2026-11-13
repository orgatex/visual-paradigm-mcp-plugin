package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.CaptionFit;
import com.brunnen.vp.mcp.util.DiagramLayoutEngine;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.brunnen.vp.mcp.util.OrthogonalRouter;
import com.brunnen.vp.mcp.util.ShapeOutline;
import com.brunnen.vp.mcp.util.TextMetrics;
import com.brunnen.vp.mcp.util.VpGraphLayout;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IActivityObjectFlow;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionLifeLineLink;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IStereotype;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;

/**
 * Base class for all MCP tool services. Provides shared layout, VP API access, EDT dispatch, and
 * diagram management tools.
 */
public abstract class AbstractDiagramMcpTools {

  private final java.util.Map<String, Integer> elementZoneCounts = new HashMap<>();

  /** The tool instance of each family the server registered, for the cross-family tools. */
  private static final java.util.Map<Class<?>, AbstractDiagramMcpTools> FAMILIES =
      new java.util.concurrent.ConcurrentHashMap<>();

  protected AbstractDiagramMcpTools() {
    FAMILIES.put(getClass(), this); // the newest instance is the one the server registers
  }

  /**
   * Diagrams created by the tools in this session. Their layout belongs to the shared layered
   * layout: every shape the tools add is placed by it. A diagram opened from a file is not managed:
   * its shapes stay where they are and only the connectors are routed.
   */
  private static final java.util.Set<String> MANAGED =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  /**
   * Shapes placed explicitly (x/y given, setElementBounds): the layered layout never moves them.
   */
  private static final java.util.Map<String, java.util.Set<String>> PINNED =
      new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * Hook: how a family's diagram takes part in the shared layout (which shapes are nodes, how
   * connectors point, what labels they carry). The default returns null: the diagram is not this
   * family's.
   *
   * @param diagram the diagram
   * @return the profile, or null
   */
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return null;
  }

  /**
   * The profile of the family that owns the diagram.
   *
   * @param diagram the diagram
   * @return the profile, or null when no family has one (the diagram is not laid out by the shared
   *     layout)
   */
  protected static LayoutProfile profileOf(IDiagramUIModel diagram) {
    for (AbstractDiagramMcpTools family : families()) {
      LayoutProfile profile = family.layoutProfile(diagram);
      if (profile != null) {
        return profile;
      }
    }
    return null;
  }

  /**
   * Remember that a shape was placed explicitly: the layered layout leaves it where it is (the
   * connectors are routed around it instead).
   *
   * @param shape the shape
   */
  protected static void pin(IDiagramElement shape) {
    if (shape.getDiagramUIModel() != null) {
      PINNED
          .computeIfAbsent(
              shape.getDiagramUIModel().getId(),
              k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
          .add(shape.getId());
    }
  }

  /**
   * Bring the diagram into a laid-out state after something was added or changed. A managed diagram
   * without explicitly placed shapes gets the full layered, orthogonal layout (shapes, connectors
   * and labels); otherwise the shapes stay and every connector is routed around them. Does nothing
   * for a diagram type without a layout profile.
   *
   * @param diagram the diagram
   */
  protected void relayout(IDiagramUIModel diagram) {
    LayoutProfile profile = profileOf(diagram);
    if (profile == null) {
      return;
    }
    layoutNow(diagram, profile);
    // Visual Paradigm fits a shape to its content (members of a class that is shown again, a
    // renamed element) when it next repaints, i.e. after this call: a shape that changed size by
    // then makes the layout stale, so it is laid out once more with the final sizes
    String laidOutWith = shapeSizes(diagram);
    afterVpSettles(
        () -> {
          if (!laidOutWith.equals(shapeSizes(diagram))) {
            layoutNow(diagram, profile);
          }
        });
  }

  private void layoutNow(IDiagramUIModel diagram, LayoutProfile profile) {
    activate(diagram);
    java.util.Set<String> pinned = PINNED.get(diagram.getId());
    boolean pins = pinned != null && !pinned.isEmpty();
    if (profile.placesShapes()
        && MANAGED.contains(diagram.getId())
        && (!pins || profile.pinAware())) {
      try {
        VpGraphLayout.layout(diagram, profile, pins ? pinned : new java.util.HashSet<String>());
        settleReopen(diagram, profile);
        return;
      } catch (RuntimeException e) {
        // the layered layout could not handle this graph: the shapes stay, the lines are routed
        System.err.println("Layered layout failed on '" + diagram.getName() + "': " + e);
      }
    }
    VpGraphLayout.route(diagram, profile);
    settleReopen(diagram, profile);
  }

  /**
   * After a layout of a diagram type that Visual Paradigm only finishes when it opens the diagram
   * (message arrows turn along their link then): close it, open it again and draw the connectors
   * once more, as the opening replaces some of them (a loop).
   *
   * @param diagram the diagram that was just laid out
   * @param profile its profile
   */
  protected void settleReopen(IDiagramUIModel diagram, LayoutProfile profile) {
    if (profile.reopensDiagram()) {
      diagram.realCloseDiagram();
      getDiagramManager().openDiagram(diagram);
      VpGraphLayout.route(diagram, profile);
    }
  }

  /** The size of every shape of the diagram, to notice that Visual Paradigm fitted one later. */
  private static String shapeSizes(IDiagramUIModel diagram) {
    StringBuilder sb = new StringBuilder();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      sb.append(s.getId())
          .append(':')
          .append(s.getWidth())
          .append('x')
          .append(s.getHeight())
          .append(';');
    }
    return sb.toString();
  }

  /**
   * The registered instance of a tool family (it holds that family's per-diagram state), or a fresh
   * one when none was registered, e.g. in a unit test.
   */
  protected static <T extends AbstractDiagramMcpTools> T familyOf(
      Class<T> type, java.util.function.Supplier<T> fallback) {
    AbstractDiagramMcpTools registered = FAMILIES.get(type);
    return registered != null ? type.cast(registered) : fallback.get();
  }

  /**
   * Hook of {@code setElementBounds}: a family that lays out its own diagram type (it knows what a
   * moved or resized shape drags along) moves {@code shape} itself and returns the result text. The
   * default returns null: the diagram is not this family's, the shared move applies.
   *
   * @param diagram the diagram
   * @param shape the shape called {@code name}
   * @param name the name the caller used
   * @param x new left edge
   * @param y new top edge
   * @param width new width, {@code <= 0} to fit the content
   * @param height new height, {@code <= 0} to fit the content
   * @return the result text, or null when the diagram is not this family's
   */
  protected String moveOwnShape(
      IDiagramUIModel diagram,
      IDiagramElement shape,
      String name,
      int x,
      int y,
      int width,
      int height) {
    return null;
  }

  /**
   * Hook of {@code rerouteConnectors}: a family with its own connector routing re-routes the whole
   * diagram. The default returns -1: the diagram is not this family's.
   *
   * @param diagram the diagram
   * @return the number of connectors rerouted, or -1 when the diagram is not this family's
   */
  protected int rerouteOwnConnectors(IDiagramUIModel diagram) {
    return -1;
  }

  /**
   * Hook of {@code autoLayoutDiagram}: a family that knows how to arrange its own diagram type does
   * it and returns the result text. The default returns null: Visual Paradigm's generic layout
   * applies.
   *
   * @param diagram the diagram
   * @return the result text, or null when the diagram is not this family's
   */
  protected String layoutOwnDiagram(IDiagramUIModel diagram) {
    return null;
  }

  /**
   * Run {@code fix} from the event queue once the current tool call is over. Visual Paradigm
   * applies some of its own geometry (auto-fit of a container around its children, the clipping of
   * a fresh connector at a package's bounding box) from events queued during the call, which undo
   * what the call set; a fix queued last wins, and the next tool call finds the result.
   *
   * @param fix the geometry to put back
   */
  protected static void afterVpSettles(Runnable fix) {
    SwingUtilities.invokeLater(
        () -> {
          try {
            fix.run();
          } catch (RuntimeException e) {
            // the shape or connector is gone (removed, diagram closed): nothing to put back
            try (java.io.PrintWriter log =
                new java.io.PrintWriter(new java.io.FileWriter("/tmp/vpmcp_later.log", true))) {
              e.printStackTrace(log);
            } catch (java.io.IOException io) {
              // no log
            }
          }
        });
  }

  /**
   * Hook of {@code addNote}: Visual Paradigm re-lays out some diagram types when a shape or an
   * anchor is added (the message arrows of a communication diagram collapse onto their links), so
   * the family that owns the diagram puts its own geometry back. The default does nothing.
   *
   * @param diagram the diagram a note was just added to
   */
  protected void afterNoteAdded(IDiagramUIModel diagram) {
    // nothing to repair by default
  }

  /**
   * The handle a family gave a shape when it differs from the name the shape shows (a second final
   * node "End #2" shows "End"), so that every tool finds the shape by the handle it was created
   * with. The default is none.
   *
   * @param de the shape
   * @return its handle, or null when the shape is known by the name it shows
   */
  protected String handleOf(IDiagramElement de) {
    return HANDLES.get(de.getId());
  }

  /**
   * Handles of shapes that show another text than the name they were added with: "End #2" is the
   * handle of a second shape that shows "End" (shape id to handle). Shared by the families whose
   * shapes are found by their text.
   */
  private static final java.util.Map<String, String> HANDLES =
      new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * The text a handle shows: the handle without a trailing " #2" style number.
   *
   * @param handle the name the caller gave
   * @return the text the shape shows
   */
  protected static String labelOfHandle(String handle) {
    String label = handle.replaceAll("\\s*#\\d+$", "").trim();
    return label.isEmpty() ? handle : label;
  }

  /**
   * Remember that a shape is known by a handle that differs from its text.
   *
   * @param shape the shape
   * @param handle the name the caller gave
   * @param label the text the shape shows
   */
  protected static void registerHandle(IDiagramElement shape, String handle, String label) {
    if (!handle.equals(label)) {
      HANDLES.put(shape.getId(), handle);
    }
  }

  /**
   * Whether a shape answers to a name: by its handle when it has one, else by the name of its
   * model.
   *
   * @param shape the shape
   * @param modelName the name of its model element
   * @param name the name looked for
   * @return true when the name is the shape's
   */
  protected static boolean answersTo(IDiagramElement shape, String modelName, String name) {
    String handle = HANDLES.get(shape.getId());
    return handle != null
        ? name.equals(handle)
        : modelName != null && name.equals(modelName.trim());
  }

  /**
   * Every tool family registered with the server, for the tools that act on any diagram type.
   *
   * @return the registered tool instances
   */
  protected static List<AbstractDiagramMcpTools> families() {
    return new ArrayList<>(FAMILIES.values());
  }

  /**
   * Run a callable on the Swing EDT and return the result.
   *
   * @param callable the callable to execute
   * @param <T> the return type
   * @return the result
   * @throws Exception if the callable throws
   */
  protected <T> T runOnEdt(Callable<T> callable) throws Exception {
    final Object[] result = new Object[1];
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            result[0] = callable.call();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  /**
   * Run a runnable on the Swing EDT.
   *
   * @param runnable the runnable to execute
   * @throws Exception if the runnable throws
   */
  protected void runOnEdt(Runnable runnable) throws Exception {
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
  }

  /**
   * Add a model element to a diagram. Sets the name on the model element, creates the diagram
   * element, and sets the visual caption text. Positions using VP's built-in layout.
   *
   * @param diagram the diagram
   * @param element the model element
   * @param name the display name for the element
   * @return the diagram element
   */
  protected IDiagramElement addToDiagram(
      IDiagramUIModel diagram, IModelElement element, String name) {
    DiagramManager dm = ApplicationManager.instance().getDiagramManager();
    element.setName(name);
    IDiagramElement diagramElement = dm.createDiagramElement(diagram, element);
    if (diagramElement instanceof IShapeUIModel) {
      ((IShapeUIModel) diagramElement).setCustomText(name);
    }
    applyConventionalFill(diagramElement, element);
    String key = diagram.getName();
    DiagramLayoutEngine.ElementZone zone =
        DiagramLayoutEngine.classifyElement(diagram.getType(), element);
    String zoneKey = key + ":" + zone;
    int indexInZone = elementZoneCounts.getOrDefault(zoneKey, 0);
    int[] bounds =
        DiagramLayoutEngine.calculateInitialBounds(diagram.getType(), element, indexInZone);
    diagramElement.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
    elementZoneCounts.put(zoneKey, indexInZone + 1);
    return diagramElement;
  }

  /**
   * The project's convention fill color (#7AD2FF) for use-case-style shapes. Matches the blue used
   * in the reference Visual Paradigm diagrams and the cnpm PlantUML theme.
   */
  protected static final java.awt.Color VP_FILL_BLUE = new java.awt.Color(0x7A, 0xD2, 0xFF);

  /**
   * Apply the conventional fill color to a freshly added shape. Actors, use cases and lifelines get
   * the project blue (#7AD2FF) to match the reference diagrams; class boxes and ERD tables keep
   * Visual Paradigm's default white.
   *
   * @param de the diagram element just created
   * @param element the underlying model element
   */
  protected void applyConventionalFill(IDiagramElement de, IModelElement element) {
    if (element instanceof IActor
        || element instanceof IUseCase
        || element instanceof IInteractionLifeLine) {
      applyBlueFill(de);
    }
  }

  /**
   * Fill a shape with the project blue (#7AD2FF). No-op for non-shape elements.
   *
   * @param de the diagram element to color
   */
  protected void applyBlueFill(IDiagramElement de) {
    if (de instanceof IShapeUIModel) {
      com.vp.plugin.diagram.format.IShapeUIModelFillColor fill =
          ((IShapeUIModel) de).getFillColor();
      if (fill != null) {
        fill.setColor1(VP_FILL_BLUE, true);
      }
    }
  }

  /**
   * Find a diagram element by its model element name on a specific diagram. Diagram-scoped only —
   * does not check the global registry to avoid cross-diagram mismatches.
   *
   * @param diagram the diagram to search
   * @param name the model element name
   * @return the diagram element, or null if not found
   */
  protected IDiagramElement findDiagramElementByName(IDiagramUIModel diagram, String name) {
    if (diagram == null || name == null) {
      return null;
    }
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IDiagramElement de = (IDiagramElement) obj;
        for (AbstractDiagramMcpTools family : families()) {
          if (name.equals(family.handleOf(de))) {
            return de;
          }
        }
        IModelElement model = de.getModelElement();
        if (model != null && name.equals(model.getName())) {
          return de;
        }
        if (de instanceof IShapeUIModel) {
          String caption = ((IShapeUIModel) de).getCustomText();
          if (name.equals(caption)) {
            return de;
          }
        }
      }
    }
    // An anonymous object (":Classifier" lifeline of a sequence or communication diagram) has no
    // name of its own: its classifier name, with or without the colon, is its handle.
    String classifierName = name.startsWith(":") ? name.substring(1).trim() : name.trim();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (model instanceof IInteractionLifeLine
          && (model.getName() == null || model.getName().trim().isEmpty())) {
        Object classifier = ((IInteractionLifeLine) model).getBaseClassifier();
        if (classifier instanceof IModelElement
            && classifierName.equals(((IModelElement) classifier).getName())) {
          return de;
        }
      }
    }
    return null;
  }

  /**
   * Find a model element by name, scoped to a specific diagram first. Falls back to project-wide
   * search. The diagram-scoped lookup avoids cross-diagram mismatches when the same element name
   * exists in multiple diagrams.
   *
   * @param name the element name
   * @param type the expected model element type
   * @param diagram the diagram to search first (may be null for project-wide only)
   * @param <T> the model element type
   * @return the model element, or null if not found
   */
  protected <T extends IModelElement> T findModelElement(
      String name, Class<T> type, IDiagramUIModel diagram) {
    if (name == null) {
      return null;
    }
    // 1. Diagram-scoped search (most reliable)
    if (diagram != null) {
      T result = findModelElementInDiagram(diagram, name, type);
      if (result != null) {
        return result;
      }
    }
    // 2. When no diagram specified, search ALL diagrams
    if (diagram == null) {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project != null) {
        Iterator<?> diagramIter = project.diagramIterator();
        while (diagramIter.hasNext()) {
          Object diagramObj = diagramIter.next();
          if (diagramObj instanceof IDiagramUIModel) {
            T result = findModelElementInDiagram((IDiagramUIModel) diagramObj, name, type);
            if (result != null) {
              return result;
            }
          }
        }
      }
    }
    // 3. Fallback to project-wide search (getName only)
    return DiagramUtils.findModelElementByName(name, type);
  }

  private <T extends IModelElement> T findModelElementInDiagram(
      IDiagramUIModel diagram, String name, Class<T> type) {
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IDiagramElement de = (IDiagramElement) obj;
        IModelElement model = de.getModelElement();
        if (model != null && type.isInstance(model)) {
          if (name.equals(model.getName())) {
            return type.cast(model);
          }
          if (de instanceof IShapeUIModel) {
            String caption = ((IShapeUIModel) de).getCustomText();
            if (name.equals(caption)) {
              return type.cast(model);
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Find a diagram element by model element reference on a specific diagram.
   *
   * @param diagram the diagram to search
   * @param modelElement the model element
   * @return the diagram element, or null if not found
   */
  protected IDiagramElement findDiagramElementByModel(
      IDiagramUIModel diagram, IModelElement modelElement) {
    if (diagram == null || modelElement == null) {
      return null;
    }
    String targetName = modelElement.getName();
    IDiagramElement sameName = null;
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IDiagramElement de = (IDiagramElement) obj;
        IModelElement m = de.getModelElement();
        if (m == modelElement || (m != null && m.getId().equals(modelElement.getId()))) {
          return de;
        }
        // a shape of the same kind and name stands for it too, but never an actor for a use case
        if (sameName == null
            && m != null
            && targetName != null
            && targetName.equals(m.getName())
            && m.getModelType().equals(modelElement.getModelType())) {
          sameName = de;
        }
      }
    }
    return sameName;
  }

  /**
   * Get all diagram elements on a diagram as a list.
   *
   * @param diagram the diagram
   * @return list of diagram elements
   */
  protected List<IDiagramElement> getDiagramElementsList(IDiagramUIModel diagram) {
    List<IDiagramElement> elements = new ArrayList<>();
    if (diagram == null) {
      return elements;
    }
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        elements.add((IDiagramElement) obj);
      }
    }
    return elements;
  }

  /**
   * Resize a shape to fit its content (name, stereotypes, compartments) via VP's fit-size API.
   * Shapes otherwise keep their creation size, so members added later overflow the box and short
   * names leave empty space. No-op for connectors.
   *
   * @param de the diagram element to fit
   */
  protected void fitShape(IDiagramElement de) {
    if (de instanceof IShapeUIModel) {
      // fitSize() measures the caption through the diagram's open editor; on a diagram that is
      // not the active one it silently does nothing (0x0 or creation-default sizes).
      activate(de.getDiagramUIModel());
      ((IShapeUIModel) de).fitSize();
    }
  }

  /**
   * Keep a shape at the size just set: Visual Paradigm marks a shape for a fit to its content
   * (whenever its caption or a child changes) and applies the fit on the next repaint, shrinking a
   * container around its children or a component under its ports. Clearing the mark makes the
   * bounds set by the tools stick.
   *
   * @param shape the shape whose size was set explicitly
   */
  protected static void keepSize(IShapeUIModel shape) {
    shape.setRequestFitSize(false);
    shape.setRequestFitSizeFromCenter(false);
  }

  /**
   * Make {@code diagram} the active diagram in VP (open it when another one is in front). Shape
   * measurement ({@code fitSize}) and connector rebuilds only work on the active diagram.
   *
   * @param diagram the diagram to bring to front, may be null
   */
  protected void activate(IDiagramUIModel diagram) {
    if (diagram == null) {
      return;
    }
    DiagramManager dm = getDiagramManager();
    IDiagramUIModel active = dm.getActiveDiagram();
    if (active == null || !diagram.getId().equals(active.getId())) {
      dm.openDiagram(diagram);
    }
  }

  /**
   * Fit every shape that shows {@code element}, on all diagrams. Call after changing what a shape
   * displays (adding attributes/operations, stereotypes, renaming).
   *
   * @param element the model element whose views are refitted
   */
  protected void fitShapesOf(IModelElement element) {
    if (element == null) {
      return;
    }
    IDiagramElement[] views = element.getDiagramElements();
    if (views == null) {
      return;
    }
    for (IDiagramElement de : views) {
      fitShape(de);
    }
  }

  /**
   * Message for a diagram name the tool could not use: "Diagram not found" when no diagram has that
   * name, otherwise which type the diagram with that name has.
   *
   * @param name the diagram name
   * @return the message
   */
  protected static String diagramNotFound(String name) {
    IDiagramUIModel other = DiagramUtils.findDiagramByName(name);
    return other != null
        ? "Diagram '"
            + name
            + "' is a "
            + other.getType()
            + ", a diagram type this tool does not work on (getDiagramGeometry and"
            + " exportDiagramImage work on every type)"
        : "Diagram not found: " + name;
  }

  /**
   * The diagram called {@code name} as the expected diagram type. Throws with a readable message
   * when there is no such diagram or it is a diagram of another type (callers turn exceptions into
   * "Error ...: message" results).
   *
   * @param name the diagram name
   * @param type the expected diagram UI model interface
   * @param label the diagram kind for the message, e.g. "activity"
   * @param <T> the diagram UI model type
   * @return the diagram
   */
  protected <T extends IDiagramUIModel> T findDiagram(String name, Class<T> type, String label) {
    return findDiagramOfKind(name, type, label + " diagram");
  }

  /**
   * {@link #findDiagram(String, Class, String)} with the full noun of the expected kind for the
   * wrong-type message, e.g. "flowchart" or "BPMN business process diagram".
   *
   * @param name the diagram name
   * @param type the expected diagram UI model interface
   * @param noun the expected kind for the message
   * @param <T> the diagram UI model type
   * @return the diagram
   */
  protected <T extends IDiagramUIModel> T findDiagramOfKind(
      String name, Class<T> type, String noun) {
    IDiagramUIModel typed = DiagramUtils.findDiagramByName(name, type);
    if (typed != null) {
      return type.cast(typed);
    }
    IDiagramUIModel other = DiagramUtils.findDiagramByName(name);
    if (other != null) {
      throw new IllegalArgumentException(
          "Diagram '"
              + name
              + "' is a "
              + other.getType()
              + ", not "
              + ("aeiou".indexOf(Character.toLowerCase(noun.charAt(0))) >= 0 ? "an " : "a ")
              + noun);
    }
    throw new IllegalArgumentException("Diagram not found: " + name);
  }

  /**
   * Create a diagram of the given type called {@code name} (trimmed). A blank name or a name any
   * diagram of the project already has is refused: every tool looks diagrams up by name (the last
   * match wins), so a second diagram with that name would hide the first one. A diagram Visual
   * Paradigm refuses to name is deleted again instead of being left as "Diagram1".
   *
   * @param typeConstant an {@code IDiagramTypeConstants.DIAGRAM_TYPE_*} value
   * @param name the diagram name
   * @return the new diagram (not opened yet)
   */
  protected IDiagramUIModel newDiagram(String typeConstant, String name) {
    requireProject();
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Diagram name must not be empty");
    }
    IDiagramUIModel existing = DiagramUtils.findDiagramByName(trimmed);
    if (existing != null) {
      throw new IllegalArgumentException(
          "A diagram named '" + trimmed + "' already exists (" + existing.getType() + ")");
    }
    DiagramManager dm = getDiagramManager();
    IDiagramUIModel diagram = dm.createDiagram(typeConstant);
    if (diagram == null) {
      throw new IllegalStateException("Visual Paradigm could not create the diagram");
    }
    try {
      diagram.setName(trimmed);
    } catch (RuntimeException e) {
      diagram.delete();
      throw e;
    }
    if (!trimmed.equals(diagram.getName())) {
      String given = diagram.getName();
      diagram.delete();
      throw new IllegalStateException(
          "Visual Paradigm named the diagram '" + given + "' instead of '" + trimmed + "'");
    }
    MANAGED.add(diagram.getId());
    return diagram;
  }

  // --- Model names ---

  /**
   * The project-root model element of the given type called {@code name}, or null. Visual Paradigm
   * keeps names unique per owner and type: a second root element with a taken name is silently
   * renamed ("Actor2", "Class2", "Component3"), so tools reuse the existing element instead (a new
   * view of it on their diagram), which is also what UML means by the same name.
   *
   * @param name the element name
   * @param type the model element interface
   * @param <T> the model element type
   * @return the existing root element, or null
   */
  protected static <T extends IModelElement> T findRootElement(String name, Class<T> type) {
    IProject project = ApplicationManager.instance().getProjectManager().getProject();
    if (project == null || name == null) {
      return null;
    }
    IModelElement[] roots = project.toModelElementArray();
    if (roots != null) {
      for (IModelElement m : roots) {
        if (type.isInstance(m) && name.equals(m.getName())) {
          return type.cast(m);
        }
      }
    }
    return null;
  }

  /** The package called {@code name} that {@code parent} owns, or null. */
  protected static com.vp.plugin.model.IPackage findOwnedPackage(
      com.vp.plugin.model.IPackage parent, String name) {
    IModelElement[] children = parent.toChildArray();
    if (children != null) {
      for (IModelElement child : children) {
        if (child instanceof com.vp.plugin.model.IPackage && name.equals(child.getName())) {
          return (com.vp.plugin.model.IPackage) child;
        }
      }
    }
    return null;
  }

  /**
   * Put a fresh element into its owner and name it there (in that order: named at the project root
   * first, it would be renamed when the root already has that name). Fails when Visual Paradigm
   * still changed the name.
   *
   * @param element the new model element
   * @param owner its owner, or null for the project root
   * @param name the wanted name
   */
  protected static void nameInOwner(IModelElement element, IModelElement owner, String name) {
    if (owner != null) {
      owner.addChild(element);
    }
    element.setName(name);
    if (!name.equals(element.getName())) {
      String given = element.getName();
      element.delete();
      throw new IllegalStateException(
          "Visual Paradigm renamed '"
              + name
              + "' to '"
              + given
              + "' because its owner already has an element of that type and name");
    }
  }

  // --- Connectors ---

  /**
   * Make connector ends on {@code diagram} point at the shapes' centers (VP's "follow center"
   * connection points). With the default "round the shape" style VP snaps every end to a shape
   * corner, so diagonal lines leave and enter at corners and several arrows share one corner.
   *
   * @param diagram the diagram to switch, may be null
   */
  protected static void useCenterConnectionPoints(IDiagramUIModel diagram) {
    if (diagram != null
        && diagram.getConnectionPointStyle() != IShapeUIModel.CONNECTION_POINT_TYPE_CENTER) {
      diagram.setConnectionPointStyle(IShapeUIModel.CONNECTION_POINT_TYPE_CENTER);
    }
  }

  /**
   * Create a connector between two shapes and lay the diagram out: the shared layout routes every
   * connector orthogonally and puts its labels beside it.
   *
   * @param diagram the diagram
   * @param model the relationship model
   * @param from the source shape
   * @param to the target shape
   * @return the connector, or null when VP did not create one
   */
  protected IDiagramElement connectAndLayout(
      IDiagramUIModel diagram, IModelElement model, IDiagramElement from, IDiagramElement to) {
    IDiagramElement connector = connectShapes(diagram, model, from, to);
    if (connector != null) {
      relayout(diagram);
    }
    return connector;
  }

  /**
   * Create a connector between two shapes without routing it: the tool calls {@link
   * #relayout(IDiagramUIModel)} once everything it adds is in place (the shared layout then routes
   * every connector orthogonally and puts its labels beside it). Refuses a second connector of the
   * same kind, stereotypes and name between the same two shapes.
   *
   * @param diagram the diagram
   * @param model the relationship model
   * @param from the source shape
   * @param to the target shape
   * @return the connector, or null when VP did not create one
   */
  protected IDiagramElement connectShapes(
      IDiagramUIModel diagram, IModelElement model, IDiagramElement from, IDiagramElement to) {
    IDiagramElement connector = createChecked(diagram, model, from, to);
    if (connector != null) {
      connector.resetCaption(); // fresh connectors render blank until the caption is reset
    }
    return connector;
  }

  /** Duplicate check and creation of a connector between the centers of two shapes. */
  private IDiagramElement createChecked(
      IDiagramUIModel diagram, IModelElement model, IDiagramElement from, IDiagramElement to) {
    activate(diagram);
    useCenterConnectionPoints(diagram);
    if (duplicateConnector(from, to, model) != null) {
      // the model was never placed anywhere: no orphan is left behind
      String label = connectorLabel(model);
      // name the ends in the direction the arrow is drawn (VP stores a generalization, extend or
      // realization the other way round)
      boolean reversed = isStoredReversed(model);
      model.delete();
      throw new IllegalStateException(
          label
              + " from '"
              + shapeName(reversed ? to : from)
              + "' to '"
              + shapeName(reversed ? from : to)
              + "' already exists on diagram '"
              + diagram.getName()
              + "'");
    }
    try {
      return getDiagramManager()
          .createConnector(diagram, model, from, to, new Point[] {center(from), center(to)});
    } catch (RuntimeException e) {
      return null; // VP rejects connector types it does not allow between these shapes
    }
  }

  /**
   * Move a shape that was placed explicitly to the nearest spot where it overlaps no sibling shape
   * (48 px clear): the request is honoured as far as the diagram allows, shapes never overlap.
   * Containers (packages, nodes, the system boundary) are not counted: a shape may be put inside
   * one.
   *
   * @param diagram the diagram
   * @param shape the shape just placed (with the shapes nested in it)
   * @return a note for the tool's answer, empty when the shape stays where it was put
   */
  protected String avoidOverlap(IDiagramUIModel diagram, IDiagramElement shape) {
    if (!(shape instanceof IShapeUIModel)) {
      return "";
    }
    IShapeUIModel self = (IShapeUIModel) shape;
    // a container keeps clear of the other containers beside it; a leaf shape may stand inside one
    boolean container = isContainerModel(self);
    String parentId = self.getParent() == null ? null : self.getParent().getId();
    LayoutProfile profile = profileOf(diagram);
    List<int[]> others = new ArrayList<>();
    for (IShapeUIModel other : diagram.toShapeUIModelArray()) {
      String otherParent = other.getParent() == null ? null : other.getParent().getId();
      if (other.getId().equals(self.getId())
          || other.getWidth() <= 0
          || isHiddenStub(other)
          || other.getModelElement() == null
          || isContainerModel(other) != container
          || !java.util.Objects.equals(parentId, otherParent)) {
        continue;
      }
      others.add(footprint(profile, other));
    }
    // the shape and the name that hangs beside it move as one
    int[] own = footprint(profile, self);
    int w = own[2];
    int h = own[3];
    int x = own[0];
    int y = own[1];
    if (!overlapsAny(x, y, w, h, others)) {
      return "";
    }
    int[] range = horizontalRange(diagram, self); // a node of a lane stays in its column
    for (int ring = 1; ring <= 80; ring++) {
      int step = 20 * ring;
      int[] best = null;
      long bestDistance = Long.MAX_VALUE;
      for (int dx = -step; dx <= step; dx += 20) {
        for (int dy = -step; dy <= step; dy += 20) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != step || x + dx < 20 || y + dy < 20) {
            continue;
          }
          if (range != null && (x + dx < range[0] || x + dx + (self.getWidth()) > range[1])) {
            continue;
          }
          long distance = (long) dx * dx + (long) dy * dy;
          if (distance < bestDistance && !overlapsAny(x + dx, y + dy, w, h, others)) {
            bestDistance = distance;
            best = new int[] {x + dx, y + dy};
          }
        }
      }
      if (best != null) {
        moveShapeTree(self, best[0] - x, best[1] - y);
        return " (moved from "
            + x
            + ","
            + y
            + " to "
            + best[0]
            + ","
            + best[1]
            + ": the requested place overlaps another shape)";
      }
    }
    return "";
  }

  /**
   * Hook: the horizontal range {minX, maxX} a shape may be moved within when an explicit place is
   * adjusted (a node of a swimlane stays inside its lane), null for no limit.
   *
   * @param diagram the diagram
   * @param shape the shape
   * @return the range, or null
   */
  protected int[] horizontalRange(IDiagramUIModel diagram, IShapeUIModel shape) {
    return null;
  }

  /** A shape's rectangle together with the name or label that hangs outside it. */
  private static int[] footprint(LayoutProfile profile, IShapeUIModel shape) {
    int[] box = {shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight()};
    int[] hanging = profile == null ? null : profile.hanging(shape);
    if (hanging == null) {
      return box;
    }
    int x = Math.min(box[0], hanging[0]);
    int y = Math.min(box[1], hanging[1]);
    return new int[] {
      x,
      y,
      Math.max(box[0] + box[2], hanging[0] + hanging[2]) - x,
      Math.max(box[1] + box[3], hanging[1] + hanging[3]) - y
    };
  }

  /**
   * Room kept between explicitly placed shapes: two stubs and a lane for the lines between them.
   */
  private static final int CLEARANCE = 48;

  private static boolean overlapsAny(int x, int y, int w, int h, List<int[]> others) {
    for (int[] o : others) {
      if (x < o[0] + o[2] + CLEARANCE
          && o[0] < x + w + CLEARANCE
          && y < o[1] + o[3] + CLEARANCE
          && o[1] < y + h + CLEARANCE) {
        return true;
      }
    }
    return false;
  }

  private static boolean isContainerModel(IShapeUIModel shape) {
    IModelElement m = shape.getModelElement();
    return m instanceof com.vp.plugin.model.IPackage
        || m instanceof com.vp.plugin.model.ISystem
        || m instanceof com.vp.plugin.model.INode
        || m instanceof com.vp.plugin.model.IActivity;
  }

  /**
   * Put a shape that was just created at its place. An explicit x/y pins it there. Otherwise a
   * diagram the shared layout owns places it itself on the next {@link #relayout}; any other
   * diagram gets the first free spot (row by row from the top left).
   *
   * @param diagram the diagram
   * @param de the new shape
   * @param x requested left edge, {@code > 0} to pin
   * @param y requested top edge, {@code > 0} to pin
   */
  protected void placeNew(IDiagramUIModel diagram, IDiagramElement de, int x, int y) {
    if (x > 0 || y > 0) {
      de.setBounds(Math.max(x, 0), Math.max(y, 0), de.getWidth(), de.getHeight());
      avoidOverlap(diagram, de);
      pin(de);
      if (de instanceof IShapeUIModel && isContainerModel((IShapeUIModel) de)) {
        holdFit(diagram, (IShapeUIModel) de);
      }
      return;
    }
    if (layoutOwnsShapes(diagram)) {
      return; // the layered layout finds the place
    }
    int[] spot = freeSpot(diagram, de, 40, 40, 1100, 60);
    de.setBounds(spot[0], spot[1], de.getWidth(), de.getHeight());
  }

  /**
   * Whether the layered layout places the shapes of the diagram: the tools created it and no shape
   * was placed explicitly.
   *
   * @param diagram the diagram
   * @return true when new shapes are placed by the next {@link #relayout}
   */
  protected boolean layoutOwnsShapes(IDiagramUIModel diagram) {
    java.util.Set<String> pinned = PINNED.get(diagram.getId());
    LayoutProfile profile = profileOf(diagram);
    return profile != null
        && profile.placesShapes()
        && MANAGED.contains(diagram.getId())
        && (pinned == null || pinned.isEmpty() || profile.pinAware());
  }

  /**
   * Keep the size a container was given: with the diagram's auto-fit on, Visual Paradigm shrinks a
   * frame to its title and children as soon as the call is over, so a size asked for would be lost.
   * The shapes stay fitted because every tool fits them when it changes them.
   *
   * @param diagram the diagram
   * @param container the container whose size was set explicitly
   */
  protected static void holdFit(IDiagramUIModel diagram, IShapeUIModel container) {
    if (diagram.isAutoFitShapesSize()) {
      diagram.setAutoFitShapesSize(false);
    }
    keepSize(container);
  }

  /** Space between a container's border and the shapes in its body. */
  private static final int NEST_PAD = 24;

  /** Room above the first shape of a container body: the folder tab and the title. */
  private static final int NEST_HEADER = 46;

  /** Space between the shapes of a container body that is filled by hand. */
  private static final int NEST_GAP = 36;

  /**
   * Make {@code child} a diagram child of the container {@code parent} (a package, a component, a
   * node). Where it stands: x/y (&gt; 0) put it there and pin it; otherwise the shared layout finds
   * the place and sizes the container around it, or, when the diagram is not laid out by it (a
   * shape was placed explicitly, or the diagram was opened from a file), the child goes to the
   * first free spot of the container's body and the container grows to enclose it.
   *
   * @param parent the container shape
   * @param child the new shape, at its final size
   * @param x requested left edge, {@code > 0} to pin
   * @param y requested top edge, {@code > 0} to pin
   */
  protected void nestChild(IShapeUIModel parent, IShapeUIModel child, int x, int y) {
    IDiagramUIModel diagram = parent.getDiagramUIModel();
    boolean explicit = x > 0 || y > 0;
    boolean owned = !explicit && layoutOwnsShapes(diagram);
    int w = child.getWidth();
    int h = child.getHeight();
    int[] spot = {child.getX(), child.getY()};
    if (explicit) {
      spot = new int[] {Math.max(x, 0), Math.max(y, 0)};
    } else if (!owned) {
      List<int[]> occupied = new ArrayList<>();
      IShapeUIModel[] siblings = parent.toChildArray();
      for (IShapeUIModel sibling : siblings == null ? new IShapeUIModel[0] : siblings) {
        if (!sibling.getId().equals(child.getId())
            && !(sibling.getModelElement() instanceof com.vp.plugin.model.IPort)
            && sibling.getWidth() > 0) {
          occupied.add(rect(sibling));
        }
      }
      int left = parent.getX() + NEST_PAD;
      int right = Math.max(parent.getX() + parent.getWidth() - NEST_PAD, left + 3 * (w + NEST_GAP));
      spot =
          DiagramUtils.firstFreeSpot(
              occupied, left, parent.getY() + NEST_HEADER, w, h, right, NEST_GAP);
    }
    parent.addChild(child);
    child.setBounds(spot[0], spot[1], w, h); // addChild may re-base the child: restore its bounds
    child.bringToFront(); // a frame created later would hide it
    if (explicit) {
      pin(child);
    }
    if (!owned) {
      growToEnclose(parent);
    }
  }

  /**
   * Grow a container, and every container around it, so that each one encloses the shapes it holds
   * with {@link #NEST_PAD} to spare and the room for its title above them; the frames beside a
   * grown one move away. Containers are sized by what they hold, never fitted to their label.
   *
   * @param container the container whose content changed
   */
  protected void growToEnclose(IShapeUIModel container) {
    IShapeUIModel current = container;
    java.util.Set<String> seen = new java.util.HashSet<>();
    while (current != null && seen.add(current.getId())) {
      int left = current.getX();
      int top = current.getY();
      int right = left + current.getWidth();
      int bottom = top + current.getHeight();
      int[] least = minimumSizeOf(current); // a size asked for is a minimum
      if (least != null) {
        right = Math.max(right, left + least[0]);
        bottom = Math.max(bottom, top + least[1]);
      }
      IShapeUIModel[] children = current.toChildArray();
      for (IShapeUIModel child : children == null ? new IShapeUIModel[0] : children) {
        if (child.getModelElement() instanceof com.vp.plugin.model.IPort || child.getWidth() <= 0) {
          continue; // ports straddle the border by design
        }
        left = Math.min(left, child.getX() - NEST_PAD);
        top = Math.min(top, child.getY() - NEST_HEADER);
        right = Math.max(right, child.getX() + child.getWidth() + NEST_PAD);
        bottom = Math.max(bottom, child.getY() + child.getHeight() + NEST_PAD);
      }
      if (left != current.getX()
          || top != current.getY()
          || right - left != current.getWidth()
          || bottom - top != current.getHeight()) {
        current.setBounds(left, top, right - left, bottom - top);
        keepSize(current);
        pushApart(current, PUSH_GAP);
      }
      IDiagramElement up = current.getParent();
      current = up instanceof IShapeUIModel ? (IShapeUIModel) up : null;
    }
  }

  /**
   * Hook: the least size {width, height} a container may be given by what it holds (the size that
   * was asked for when it was added), null for none.
   *
   * @param container a container shape
   * @return the least size, or null
   */
  protected int[] minimumSizeOf(IShapeUIModel container) {
    for (AbstractDiagramMcpTools family : families()) {
      int[] least = family.minimumSize(container);
      if (least != null) {
        return least;
      }
    }
    return null;
  }

  /**
   * Hook of {@link #minimumSizeOf}: a family that remembers the size it was asked for.
   *
   * @param container a container shape
   * @return the size it must keep, or null
   */
  protected int[] minimumSize(IShapeUIModel container) {
    return null;
  }

  /**
   * The connector from {@code from} to {@code to} that already draws a relationship like {@code
   * model} (same kind, stereotypes and name), or null. Plain unnamed associations are not compared:
   * two of them between the same classes can differ in their multiplicities.
   */
  protected static IDiagramElement duplicateConnector(
      IDiagramElement from, IDiagramElement to, IModelElement model) {
    if (model == null
        || (model instanceof IAssociation && cleanName(model).isEmpty())
        || from.toFromConnectorArray() == null) {
      return null;
    }
    for (IConnectorUIModel c : from.toFromConnectorArray()) {
      IModelElement other = c.getModelElement();
      if (other == null
          || other.getId().equals(model.getId())
          || c.getToShape() == null
          || !c.getToShape().getId().equals(to.getId())
          || !other.getModelType().equals(model.getModelType())
          || !cleanName(other).equals(cleanName(model))) {
        continue;
      }
      if (stereotypeNames(other).equals(stereotypeNames(model))) {
        return c;
      }
    }
    return null;
  }

  private static List<String> stereotypeNames(IModelElement model) {
    List<String> names = new ArrayList<>();
    IStereotype[] stereotypes = model.toStereotypeModelArray();
    if (stereotypes != null) {
      for (IStereotype st : stereotypes) {
        names.add(st.getName());
      }
    }
    java.util.Collections.sort(names);
    return names;
  }

  private static String cleanName(IModelElement model) {
    return model.getName() == null ? "" : model.getName().trim();
  }

  private static String connectorLabel(IModelElement model) {
    StringBuilder sb = new StringBuilder(model.getModelType());
    for (String st : stereotypeNames(model)) {
      sb.append(" <<").append(st).append(">>");
    }
    String name = cleanName(model);
    return name.isEmpty() ? sb.toString() : sb + " '" + name + "'";
  }

  private static String shapeName(IDiagramElement shape) {
    IModelElement m = shape.getModelElement();
    return m != null ? displayName(m) : shape.getShapeType();
  }

  /**
   * Center point of a shape in diagram coordinates.
   *
   * @param shape the shape
   * @return its center
   */
  protected static Point center(IDiagramElement shape) {
    return new Point(shape.getX() + shape.getWidth() / 2, shape.getY() + shape.getHeight() / 2);
  }

  // --- Shape trees and bounds ---

  /** Space kept between a container's border and the shapes it holds when it is resized. */
  protected static final int CONTAINER_PAD = 12;

  /**
   * Every shape nested in {@code shape} at any depth, parents before their children.
   *
   * @param shape the container
   * @return its descendants
   */
  protected static List<IShapeUIModel> nestedShapes(IShapeUIModel shape) {
    List<IShapeUIModel> result = new ArrayList<>();
    collectNested(shape, result, new java.util.HashSet<>());
    return result;
  }

  private static void collectNested(
      IShapeUIModel shape, List<IShapeUIModel> out, java.util.Set<String> seen) {
    IShapeUIModel[] children = shape.toChildArray();
    if (children == null) {
      return;
    }
    for (IShapeUIModel child : children) {
      if (child != null && seen.add(child.getId())) {
        out.add(child);
        collectNested(child, out, seen);
      }
    }
  }

  /**
   * Move a shape and everything nested in it by (dx, dy). Moving a container through the API leaves
   * the shapes inside it where they were, so each descendant that did not follow is moved
   * explicitly; connectors are not touched.
   *
   * @param shape the container
   * @param dx horizontal shift
   * @param dy vertical shift
   */
  protected static void moveShapeTree(IShapeUIModel shape, int dx, int dy) {
    List<IShapeUIModel> nested = nestedShapes(shape);
    int[][] before = new int[nested.size()][];
    for (int i = 0; i < before.length; i++) {
      before[i] = rect(nested.get(i));
    }
    shape.setBounds(shape.getX() + dx, shape.getY() + dy, shape.getWidth(), shape.getHeight());
    for (int i = 0; i < before.length; i++) {
      IShapeUIModel d = nested.get(i);
      if (d.getX() == before[i][0] && d.getY() == before[i][1]) {
        d.setBounds(before[i][0] + dx, before[i][1] + dy, before[i][2], before[i][3]);
      }
    }
  }

  /**
   * Move and/or resize a shape the way {@code setElementBounds} promises: a container (package,
   * node, component, boundary...) takes the shapes nested in it along, and is never smaller than
   * they need ({@link #CONTAINER_PAD} beyond the farthest one); a shape without children is fitted
   * to its content when {@code width} or {@code height} is {@code <= 0}, a container then keeps its
   * size (it is sized by what it holds, not by its label).
   *
   * @param de the shape
   * @param x new left edge
   * @param y new top edge
   * @param width new width, {@code <= 0} to fit
   * @param height new height, {@code <= 0} to fit
   * @return a note for the result text, empty when nothing needs saying
   */
  protected String applyBounds(IDiagramElement de, int x, int y, int width, int height) {
    if (!(de instanceof IShapeUIModel)) {
      if (width <= 0 || height <= 0) {
        de.setBounds(x, y, de.getWidth(), de.getHeight());
      } else {
        de.setBounds(x, y, width, height);
      }
      return "";
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    List<IShapeUIModel> nested = nestedShapes(shape);
    int w = width;
    int h = height;
    if (width <= 0 || height <= 0) {
      if (!holdsShapes(nested)) {
        fitShape(shape);
      }
      w = shape.getWidth();
      h = shape.getHeight();
    }
    int dx = x - shape.getX();
    int dy = y - shape.getY();
    String note = "";
    if (holdsShapes(nested)) {
      int right = 0;
      int bottom = 0;
      IShapeUIModel[] direct = shape.toChildArray();
      for (IShapeUIModel child : direct == null ? new IShapeUIModel[0] : direct) {
        if (child.getModelElement() instanceof com.vp.plugin.model.IPort) {
          continue; // ports straddle the border by design
        }
        right = Math.max(right, child.getX() + dx + child.getWidth());
        bottom = Math.max(bottom, child.getY() + dy + child.getHeight());
      }
      if (right > x + w || bottom > y + h) {
        w = Math.max(w, right + CONTAINER_PAD - x);
        h = Math.max(h, bottom + CONTAINER_PAD - y);
        note = " (grown to enclose its contents)";
      }
    }
    int[][] before = new int[nested.size()][];
    for (int i = 0; i < before.length; i++) {
      before[i] = rect(nested.get(i));
    }
    shape.setBounds(x, y, w, h);
    for (int i = 0; i < before.length; i++) {
      IShapeUIModel d = nested.get(i);
      if (d.getX() == before[i][0] && d.getY() == before[i][1] && (dx != 0 || dy != 0)) {
        d.setBounds(before[i][0] + dx, before[i][1] + dy, before[i][2], before[i][3]);
      }
    }
    // a shape moved or resized inside a node, package or component: the container grows around it
    IDiagramElement parent = shape.getParent();
    if (parent instanceof IShapeUIModel && isSolidContainer(parent.getModelElement())) {
      IShapeUIModel container = (IShapeUIModel) parent;
      int[] was = rect(container);
      growToEnclose(container);
      if (!java.util.Arrays.equals(was, rect(container))) {
        note += " (" + shapeName(container) + " grew to enclose it)";
      }
    }
    return note;
  }

  /** Whether nested shapes include more than ports (a container is sized by what it holds). */
  private static boolean holdsShapes(List<IShapeUIModel> nested) {
    for (IShapeUIModel n : nested) {
      if (!(n.getModelElement() instanceof com.vp.plugin.model.IPort)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The connectors attached to a shape or to anything nested in it, each once.
   *
   * @param shape the shape
   * @return its connectors
   */
  protected static List<IConnectorUIModel> connectorsOfTree(IDiagramElement shape) {
    List<IConnectorUIModel> result = new ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    List<IDiagramElement> shapes = new ArrayList<>();
    shapes.add(shape);
    if (shape instanceof IShapeUIModel) {
      shapes.addAll(nestedShapes((IShapeUIModel) shape));
    }
    for (IDiagramElement s : shapes) {
      for (IConnectorUIModel[] connectors :
          new IConnectorUIModel[][] {s.toFromConnectorArray(), s.toToConnectorArray()}) {
        for (IConnectorUIModel c : connectors == null ? new IConnectorUIModel[0] : connectors) {
          if (seen.add(c.getId())) {
            result.add(c);
          }
        }
      }
    }
    return result;
  }

  /**
   * The result text of a bounds change.
   *
   * @param elementName the name the caller used
   * @param de the shape
   * @return {@code Bounds of 'name': x,y WxH}
   */
  protected static String boundsResult(String elementName, IDiagramElement de) {
    return "Bounds of '"
        + elementName
        + "': "
        + de.getX()
        + ","
        + de.getY()
        + " "
        + de.getWidth()
        + "x"
        + de.getHeight();
  }

  // --- Overlap resolution ---

  /** Free space kept between neighbouring shapes when they are pushed apart. */
  protected static final int PUSH_GAP = 40;

  /**
   * After {@code grown} got bigger, push the sibling shapes it now overlaps to the right (shapes
   * that start right of its left edge) or down (shapes below its top edge), cascading to the shapes
   * those collide with, and re-anchor the connectors of every moved shape.
   *
   * @param grown the shape that was resized or placed
   * @return number of shapes moved
   */
  protected int pushApart(IShapeUIModel grown) {
    return pushApart(grown, PUSH_GAP);
  }

  /**
   * {@link #pushApart(IShapeUIModel)} with a custom free space between the shapes.
   *
   * @param grown the shape that was resized or placed
   * @param gap free space to keep between neighbours
   * @return number of shapes moved
   */
  protected int pushApart(IShapeUIModel grown, int gap) {
    IDiagramUIModel diagram = grown.getDiagramUIModel();
    if (diagram == null) {
      return 0;
    }
    List<IShapeUIModel> siblings = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      // a container moves with everything it holds (moveShapeTree)
      if (s.getModelElement() != null && sameParent(s, grown) && s.getWidth() > 0) {
        siblings.add(s);
      }
    }
    java.util.Deque<IShapeUIModel> queue = new java.util.ArrayDeque<>();
    queue.add(grown);
    java.util.Set<String> moved = new java.util.HashSet<>();
    int guard = 0;
    while (!queue.isEmpty() && guard++ < 200) {
      IShapeUIModel a = queue.poll();
      for (IShapeUIModel b : siblings) {
        if (b.getId().equals(a.getId()) || !overlapsWithGap(a, b, gap / 2)) {
          continue;
        }
        int[] shift = DiagramUtils.pushVector(rect(a), rect(b), gap);
        if (shift[0] == 0 && shift[1] == 0) {
          continue;
        }
        moveShapeTree(b, shift[0], shift[1]);
        moved.add(b.getId());
        queue.add(b);
      }
    }
    return moved.size();
  }

  /**
   * Top-left corner for an auto-placed top-level shape: the first spot from (x0, y0), row by row,
   * where it keeps {@code gap} from every other top-level shape at its current size.
   *
   * @param diagram the diagram
   * @param self the shape being placed (ignored as an obstacle)
   * @param x0 left edge of the rows
   * @param y0 top of the first row
   * @param maxRight right edge a row should not cross
   * @param gap free space kept around every shape
   * @return {x, y}
   */
  protected static int[] freeSpot(
      IDiagramUIModel diagram, IDiagramElement self, int x0, int y0, int maxRight, int gap) {
    List<int[]> occupied = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getId().equals(self.getId()) || s.getParent() != null || s.getWidth() <= 0) {
        continue;
      }
      occupied.add(rect(s));
    }
    return DiagramUtils.firstFreeSpot(
        occupied, x0, y0, self.getWidth(), self.getHeight(), maxRight, gap);
  }

  private static boolean sameParent(IShapeUIModel a, IShapeUIModel b) {
    IDiagramElement pa = a.getParent();
    IDiagramElement pb = b.getParent();
    if (pa == null || pb == null) {
      return pa == null && pb == null;
    }
    return pa.getId().equals(pb.getId());
  }

  private static boolean overlapsWithGap(IDiagramElement a, IDiagramElement b, int gap) {
    return a.getX() < b.getX() + b.getWidth() + gap
        && b.getX() < a.getX() + a.getWidth() + gap
        && a.getY() < b.getY() + b.getHeight() + gap
        && b.getY() < a.getY() + a.getHeight() + gap;
  }

  /**
   * Bounds of a diagram element as {x, y, w, h}.
   *
   * @param de the element
   * @return its rectangle
   */
  protected static int[] rect(IDiagramElement de) {
    return new int[] {de.getX(), de.getY(), de.getWidth(), de.getHeight()};
  }

  // --- Diagram Management Tools ---

  @Tool(
      name = "listDiagrams",
      description =
          "List all diagrams in the project, optionally filtered by type: UseCase, Class,"
              + " Sequence (= Interaction), Communication (= Collaboration), State (= StateMachine),"
              + " Activity, Component, Deployment, Package, Object, ER (= ERD), BPMN, Flowchart,"
              + " DFD, or the exact type shown in the list. The type is matched exactly, never as"
              + " part of another name")
  public String listDiagrams(String type) {
    try {
      return runOnEdt(
          () -> {
            IProject project = requireProject();
            List<String> diagrams = new ArrayList<>();
            Iterator<?> iter = project.diagramIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramUIModel) {
                IDiagramUIModel d = (IDiagramUIModel) obj;
                String diagramType = d.getType();
                if (DiagramUtils.matchesTypeFilter(diagramType, type)) {
                  diagrams.add(d.getName() + " (" + diagramType + ")");
                }
              }
            }
            if (diagrams.isEmpty()) {
              return "No diagrams found" + (type != null ? " of type: " + type : "");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Diagrams (").append(diagrams.size()).append("):\n");
            for (String d : diagrams) {
              sb.append("  - ").append(d).append("\n");
            }
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error listing diagrams: " + e.getMessage();
    }
  }

  @Tool(
      name = "getDiagramElements",
      description =
          "Get all elements (shapes and connectors) on a diagram with their names, types,"
              + " details and positions; connectors as 'Type <<stereotype>> name: from -> to"
              + " [guard]'")
  public String getDiagramElements(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IDiagramElement> elements = getDiagramElementsList(diagram);
            if (elements.isEmpty()) {
              return "Diagram '" + diagramName + "' is empty";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Elements on '")
                .append(diagramName)
                .append("' (")
                .append(elements.size())
                .append("):\n");
            for (IDiagramElement de : elements) {
              if (isHiddenStub(de)) {
                continue;
              }
              IModelElement model = de.getModelElement();
              String type = model != null ? getSemanticTypeName(model) : de.getShapeType();
              String name = model != null ? listName(model) : "(unnamed)";

              if (model instanceof IRelationship || de instanceof IConnectorUIModel) {
                sb.append("  - ").append(describeConnector(de, model, type)).append("\n");
              } else if (model instanceof IClass) {
                // Classes: show attributes and operations
                sb.append("  - ").append(type).append(": ").append(name);
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
                // Attributes
                List<String> attrs = new ArrayList<>();
                Iterator<?> attrIter = ((IClass) model).attributeIterator();
                while (attrIter.hasNext()) {
                  Object attrObj = attrIter.next();
                  if (attrObj instanceof IAttribute) {
                    IAttribute attr = (IAttribute) attrObj;
                    String attrStr =
                        visibilitySymbol(attr.getVisibility())
                            + attr.getName()
                            + (attr.getType() != null ? ":" + attr.getType() : "");
                    attrs.add(attrStr);
                  }
                }
                if (!attrs.isEmpty()) {
                  sb.append("    Attributes: ").append(String.join(", ", attrs)).append("\n");
                }
                // Operations
                List<String> ops = new ArrayList<>();
                Iterator<?> opIter = ((IClass) model).operationIterator();
                while (opIter.hasNext()) {
                  Object opObj = opIter.next();
                  if (opObj instanceof IOperation) {
                    IOperation op = (IOperation) opObj;
                    StringBuilder opStr = new StringBuilder();
                    opStr.append(visibilitySymbol(op.getVisibility()));
                    opStr.append(op.getName()).append("(");
                    List<String> params = new ArrayList<>();
                    Iterator<?> paramIter = op.parameterIterator();
                    while (paramIter.hasNext()) {
                      Object paramObj = paramIter.next();
                      if (paramObj instanceof IParameter) {
                        IParameter p = (IParameter) paramObj;
                        String paramText = p.getName();
                        if (p.getType() != null) {
                          paramText += ":" + p.getType();
                        }
                        params.add(paramText);
                      }
                    }
                    opStr.append(String.join(", ", params)).append(")");
                    if (op.getReturnType() != null) {
                      opStr.append(":").append(op.getReturnType());
                    }
                    ops.add(opStr.toString());
                  }
                }
                if (!ops.isEmpty()) {
                  sb.append("    Operations: ").append(String.join(", ", ops)).append("\n");
                }
              } else if (model instanceof IDBTable) {
                // Tables: show columns
                sb.append("  - ").append(type).append(": ").append(name);
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
                List<String> cols = new ArrayList<>();
                Iterator<?> colIter = ((IDBTable) model).dBColumnIterator();
                while (colIter.hasNext()) {
                  Object colObj = colIter.next();
                  if (colObj instanceof IDBColumn) {
                    IDBColumn col = (IDBColumn) colObj;
                    String colStr = col.getName();
                    if (col.getTypeInText() != null) {
                      colStr += "(" + col.getTypeInText() + ")";
                    }
                    if (col.isPrimaryKey()) {
                      colStr += ",PK";
                    }
                    cols.add(colStr);
                  }
                }
                if (!cols.isEmpty()) {
                  sb.append("    Columns: ").append(String.join(", ", cols)).append("\n");
                }
              } else if (model instanceof IInteractionLifeLine) {
                // Lifelines: show base classifier
                sb.append("  - ").append(type).append(": ").append(name);
                Object classifierObj = ((IInteractionLifeLine) model).getBaseClassifier();
                if (classifierObj instanceof IModelElement) {
                  sb.append(" [").append(((IModelElement) classifierObj).getName()).append("]");
                }
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
              } else {
                // Default: type + stereotypes + name + position
                sb.append("  - ").append(type);
                if (model != null) {
                  appendStereotypes(sb, model, type);
                }
                sb.append(": ").append(name).append(extraDetails(de, model));
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
              }
            }
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error getting diagram elements: " + e.getMessage();
    }
  }

  @Tool(
      name = "getDiagramGeometry",
      description =
          "Dump the geometry of any diagram (the positions the add* tools answer with are the ones"
              + " of that moment: the diagram is laid out again after every addition, this is the"
              + " final picture): every shape with its bounds (nested shapes indented"
              + " under their container, with the owner of a port) and every connector with its"
              + " end shapes, its points, the bounds of its caption and the caption's text; a"
              + " caption whose box is too small for Visual Paradigm to draw all of its text (it"
              + " would end in \"...\") is marked TRUNCATED, with the size it needs. For checking a"
              + " layout without a picture: connector ends, label positions, shapes inside their"
              + " frame")
  public String getDiagramGeometry(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Geometry of '")
                .append(diagramName)
                .append("' (")
                .append(diagram.getType())
                .append("):\n");
            // shapes that stand inside a frame without being nested in it in Visual Paradigm (the
            // nodes of an activity page frame) are listed under that frame
            LayoutProfile profile = profileOf(diagram);
            Map<String, List<IShapeUIModel>> inside = new LinkedHashMap<>();
            Set<String> listedInside = new HashSet<>();
            if (profile != null) {
              for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
                IShapeUIModel frame = s.getParent() == null ? profile.container(s) : null;
                if (frame != null
                    && frame.getParent() == null
                    && !frame.getId().equals(s.getId())) {
                  inside.computeIfAbsent(frame.getId(), k -> new ArrayList<>()).add(s);
                  listedInside.add(s.getId());
                }
              }
            }
            for (IShapeUIModel top : diagram.toShapeUIModelArray()) {
              if (top.getParent() == null && !listedInside.contains(top.getId())) {
                describeShape(sb, top, 1, inside);
              }
            }
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de instanceof IConnectorUIModel) {
                describeConnectorGeometry(sb, (IConnectorUIModel) de);
              }
            }
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error reading geometry: " + e.getMessage();
    }
  }

  private static void describeShape(
      StringBuilder sb, IShapeUIModel shape, int depth, Map<String, List<IShapeUIModel>> inside) {
    if (isHiddenStub(shape)) {
      return;
    }
    IModelElement model = shape.getModelElement();
    String indent = "  ".repeat(depth);
    sb.append(indent)
        .append(model != null ? getSemanticTypeName(model) : shape.getShapeType())
        .append(": ")
        .append(model != null ? displayName(model) : shape.getCustomText())
        .append(" ")
        .append(shape.getX())
        .append(",")
        .append(shape.getY())
        .append(" ")
        .append(shape.getWidth())
        .append("x")
        .append(shape.getHeight());
    com.vp.plugin.diagram.ICaptionUIModel cap = shape.getCaptionUIModel();
    if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
      sb.append(" caption ")
          .append(cap.getX())
          .append(",")
          .append(cap.getY())
          .append(" ")
          .append(cap.getWidth())
          .append("x")
          .append(cap.getHeight());
    }
    sb.append("\n");
    IShapeUIModel[] children = shape.toChildArray();
    if (children != null) {
      for (IShapeUIModel child : children) {
        if (child != null) {
          describeShape(sb, child, depth + 1, inside);
        }
      }
    }
    for (IShapeUIModel member : inside.getOrDefault(shape.getId(), new ArrayList<>())) {
      describeShape(sb, member, depth + 1, inside);
    }
  }

  private static void describeConnectorGeometry(StringBuilder sb, IConnectorUIModel c) {
    IModelElement model = c.getModelElement();
    sb.append("  ")
        .append(model != null ? getSemanticTypeName(model) : c.getShapeType())
        .append(
            model != null && model.getName() != null && !model.getName().isEmpty()
                ? " '" + model.getName() + "'"
                : "")
        .append(": ")
        .append(c.getFromShape() != null ? shapeLabel(c.getFromShape()) : "?")
        .append(" -> ")
        .append(c.getToShape() != null ? shapeLabel(c.getToShape()) : "?")
        .append(" points");
    Point[] points = c.getPoints();
    if (points != null) {
      for (Point p : points) {
        sb.append(" (").append(p.x).append(",").append(p.y).append(")");
      }
    }
    com.vp.plugin.diagram.ICaptionUIModel cap = c.getCaptionUIModel();
    if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
      sb.append(" caption ")
          .append(cap.getX())
          .append(",")
          .append(cap.getY())
          .append(" ")
          .append(cap.getWidth())
          .append("x")
          .append(cap.getHeight());
      // the text and whether the box is too small for VP to draw all of it ("<<use..."): a caption
      // is never narrower or lower than its text needs
      CaptionFit.Result fit = CaptionFit.check(c);
      if (fit != null) {
        sb.append(" text \"").append(fit.text.replace("\n", "\\n")).append("\"");
        if (fit.truncated) {
          sb.append(" TRUNCATED needs ").append(fit.needed[0]).append("x").append(fit.needed[1]);
        }
      }
    }
    if (c instanceof com.vp.plugin.diagram.connector.IAssociationUIModel
        && c.getModelElement() instanceof IAssociation) {
      // the multiplicities of an association are labels of their own
      IAssociation assoc = (IAssociation) c.getModelElement();
      com.vp.plugin.diagram.connector.IAssociationUIModel uiModel =
          (com.vp.plugin.diagram.connector.IAssociationUIModel) c;
      appendMultiplicity(
          sb, uiModel.getMultiplicityARectangle(), (IAssociationEnd) assoc.getFromEnd());
      appendMultiplicity(
          sb, uiModel.getMultiplicityBRectangle(), (IAssociationEnd) assoc.getToEnd());
    }
    sb.append("\n");
    // the messages of a communication link are shapes of their own, children of the link
    IShapeUIModel[] children = c.toChildArray();
    for (IShapeUIModel child : children == null ? new IShapeUIModel[0] : children) {
      IModelElement m = child.getModelElement();
      sb.append("    ")
          .append(m != null ? getSemanticTypeName(m) : child.getShapeType())
          .append(
              m instanceof IMessage
                  ? " '" + ((IMessage) m).getSequenceNumber() + ": " + m.getName() + "'"
                  : "")
          .append(" ")
          .append(child.getX())
          .append(",")
          .append(child.getY())
          .append(" ")
          .append(child.getWidth())
          .append("x")
          .append(child.getHeight());
      com.vp.plugin.diagram.ICaptionUIModel childCap = child.getCaptionUIModel();
      if (childCap != null && childCap.isVisible() && childCap.getWidth() > 0) {
        sb.append(" caption ")
            .append(childCap.getX())
            .append(",")
            .append(childCap.getY())
            .append(" ")
            .append(childCap.getWidth())
            .append("x")
            .append(childCap.getHeight());
      }
      sb.append("\n");
    }
  }

  /** " mult x,y wxh" for the label of an association end that shows a multiplicity. */
  private static void appendMultiplicity(
      StringBuilder sb, java.awt.Rectangle rect, IAssociationEnd end) {
    String text = end == null ? null : end.getMultiplicity();
    if (rect != null
        && rect.width > 0
        && text != null
        && !text.isEmpty()
        && !"Unspecified".equalsIgnoreCase(text)) {
      sb.append(" mult ")
          .append(rect.x)
          .append(",")
          .append(rect.y)
          .append(" ")
          .append(rect.width)
          .append("x")
          .append(rect.height);
    }
  }

  private static String shapeLabel(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    return model != null ? displayName(model) : shape.getShapeType();
  }

  @Tool(
      name = "addNote",
      description =
          "Add a note (a comment box with a folded corner) to any diagram, e.g. to explain a"
              + " relation, a message or a state. attachedTo (the name of a shape on the diagram,"
              + " \"\" = free-standing) joins it to that shape with a dashed anchor line. x/y (>0)"
              + " place it; otherwise it goes at the first free spot beside the attached shape"
              + " (right, left, below, above: overlapping no shape, label or line, and inside the"
              + " lane of a shape in a swimlane), or below everything on the diagram. The text is"
              + " broken into lines of about 30 characters and the box sized to it")
  public String addNote(String diagramName, String text, String attachedTo, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (text == null || text.trim().isEmpty()) {
              return "Note text must not be empty";
            }
            IShapeUIModel target = null;
            if (attachedTo != null && !attachedTo.trim().isEmpty()) {
              IDiagramElement found = findDiagramElementByName(diagram, attachedTo.trim());
              if (!(found instanceof IShapeUIModel)) {
                return "Shape not on diagram: " + attachedTo;
              }
              target = (IShapeUIModel) found;
            }
            // lines of about 30 characters (the size before Visual Paradigm measures the text)
            List<String> lines = wrapNote(text, NOTE_COLUMNS);
            int width = noteWidth(lines);
            int height = noteHeight(lines);

            com.vp.plugin.model.INOTE note = getModelElementFactory().createNOTE();
            // hard line breaks: the box does not depend on how Visual Paradigm wraps the text
            note.setDescription(String.join("\n", lines));
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, note);
            if (!(de instanceof IShapeUIModel)) {
              note.delete();
              return "Error adding note: Visual Paradigm did not draw a note on '"
                  + diagramName
                  + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            int px = x;
            int py = y;
            if (x <= 0 && y <= 0) {
              int[] own =
                  target == null ? null : ownNoteSpot(diagram, shape, target, width, height);
              if (own != null) {
                px = own[0];
                py = own[1];
              } else if (target != null) {
                int[] spot =
                    noteSpot(
                        diagram,
                        shape,
                        target,
                        noteContainer(diagram, target),
                        width,
                        height,
                        true);
                if (spot == null) {
                  // no room in the lane of the target: narrower lines (a taller, slimmer box)
                  lines = wrapNote(text, NOTE_NARROW_COLUMNS);
                  width = noteWidth(lines);
                  height = noteHeight(lines);
                  note.setDescription(String.join("\n", lines));
                  spot =
                      noteSpot(
                          diagram,
                          shape,
                          target,
                          noteContainer(diagram, target),
                          width,
                          height,
                          true);
                }
                if (spot == null) {
                  spot =
                      noteSpot(
                          diagram,
                          shape,
                          target,
                          noteContainer(diagram, target),
                          width,
                          height,
                          false);
                }
                px = spot[0];
                py = spot[1];
              } else {
                int bottom = 0;
                for (IShapeUIModel other : diagram.toShapeUIModelArray()) {
                  if (!other.getId().equals(shape.getId()) && other.getParent() == null) {
                    bottom = Math.max(bottom, other.getY() + other.getHeight());
                  }
                }
                px = 40;
                py = bottom + 40;
              }
            }
            shape.setBounds(Math.max(px, 0), Math.max(py, 0), width, height);
            shape.resetCaption();
            String anchored = "";
            if (target != null) {
              com.vp.plugin.model.IAnchor anchor = getModelElementFactory().createAnchor();
              anchor.setFrom(note);
              anchor.setTo(target.getModelElement());
              IDiagramElement link = connectAndLayout(diagram, anchor, shape, target);
              if (link instanceof IConnectorUIModel) {
                routeNoteAnchor(diagram, (IConnectorUIModel) link);
              }
              anchored =
                  link != null
                      ? " attached to '" + attachedTo.trim() + "'"
                      : " (Visual Paradigm refused the anchor line to '" + attachedTo.trim() + "')";
            }
            for (AbstractDiagramMcpTools family : families()) {
              family.afterNoteAdded(diagram);
            }
            return "Added note '"
                + (text.trim().length() > 40 ? text.trim().substring(0, 40) + "..." : text.trim())
                + "' to diagram '"
                + diagramName
                + "' at "
                + shape.getX()
                + ","
                + shape.getY()
                + " "
                + shape.getWidth()
                + "x"
                + shape.getHeight()
                + anchored;
          });
    } catch (Exception e) {
      return "Error adding note: " + e.getMessage();
    }
  }

  /**
   * Hook of {@code addNote}: where a note for {@code target} goes when the family knows better than
   * the first free spot beside the shape (a sequence diagram puts it under the lifeline). The
   * default returns null.
   *
   * @param diagram the diagram
   * @param note the note's shape
   * @param target the shape the note explains
   * @param w the note's width
   * @param h the note's height
   * @return the top-left corner, or null
   */
  protected int[] noteSpotOwn(
      IDiagramUIModel diagram, IShapeUIModel note, IShapeUIModel target, int w, int h) {
    return null;
  }

  private static int[] ownNoteSpot(
      IDiagramUIModel diagram, IShapeUIModel note, IShapeUIModel target, int w, int h) {
    for (AbstractDiagramMcpTools family : families()) {
      int[] spot = family.noteSpotOwn(diagram, note, target, w, h);
      if (spot != null) {
        return spot;
      }
    }
    return null;
  }

  /**
   * The lane (or pool) a note for {@code target} must stay inside: the shape that holds the target.
   * A family whose shapes are only drawn inside their lane (BPMN) finds it by geometry.
   *
   * @param diagram the diagram
   * @param target the shape the note explains
   * @return the lane shape, or null
   */
  protected IShapeUIModel noteContainer(IDiagramUIModel diagram, IShapeUIModel target) {
    for (AbstractDiagramMcpTools family : families()) {
      IShapeUIModel own = family.noteContainerOwn(diagram, target);
      if (own != null) {
        return own;
      }
    }
    IDiagramElement parent = target.getParent();
    return parent instanceof IShapeUIModel ? (IShapeUIModel) parent : null;
  }

  /**
   * Hook of {@link #noteContainer}: a family that knows the lane of a shape by its geometry.
   *
   * @param diagram the diagram
   * @param target the shape the note explains
   * @return the lane shape, or null
   */
  protected IShapeUIModel noteContainerOwn(IDiagramUIModel diagram, IShapeUIModel target) {
    return null;
  }

  /**
   * Hook: draw the anchor line of a note in a diagram type that has no layout profile. A family
   * with an own idea of where the line ends (a sequence diagram: on the dashed line of a lifeline)
   * draws it and answers true; the default answers false and the line is routed by {@link
   * OrthogonalRouter} around the shapes.
   *
   * @param diagram the diagram
   * @param anchor the anchor connector, from the note to the shape it explains
   * @param note the note's shape
   * @param target the shape the note explains
   * @return true when the family drew the line
   */
  protected boolean routeAnchorOwn(
      IDiagramUIModel diagram, IConnectorUIModel anchor, IShapeUIModel note, IShapeUIModel target) {
    return false;
  }

  /**
   * Hook: the outline of a shape in a diagram type that has no layout profile, for where a note's
   * anchor line ends on it (a BPMN event is a circle, a gateway a diamond).
   *
   * @param shape the shape
   * @return its outline, a rectangle by default
   */
  protected ShapeOutline.Kind outlineOf(IShapeUIModel shape) {
    return ShapeOutline.Kind.RECT;
  }

  /**
   * Draw the anchor of a note as a horizontal or vertical line (or an L): diagram types with a
   * layout profile lay it out with the diagram; the others (sequence, BPMN) route it here.
   *
   * @param diagram the diagram
   * @param anchor the anchor connector
   */
  protected void routeNoteAnchor(IDiagramUIModel diagram, IConnectorUIModel anchor) {
    IShapeUIModel note = anchor.getFromShape();
    IShapeUIModel target = anchor.getToShape();
    if (profileOf(diagram) != null || note == null || target == null) {
      return;
    }
    for (AbstractDiagramMcpTools family : families()) {
      if (family.routeAnchorOwn(diagram, anchor, note, target)) {
        return;
      }
    }
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getWidth() <= 0 || isHiddenStub(s)) {
        continue;
      }
      int[] extra = null;
      com.vp.plugin.diagram.ICaptionUIModel cap = s.getCaptionUIModel();
      if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
        int[] inside = {
          s.getX() + cap.getX(), s.getY() + cap.getY(), cap.getWidth(), cap.getHeight()
        };
        if (!insideRect(rect(s), inside)) {
          extra = new int[] {cap.getX(), cap.getY(), cap.getWidth(), cap.getHeight()};
        }
      }
      shapes.add(
          new OrthogonalRouter.Shape(
              s.getId(),
              rect(s),
              s.getParent() == null ? null : s.getParent().getId(),
              outlineOf(s),
              extra));
    }
    List<OrthogonalRouter.Drawn> drawn = new ArrayList<>();
    for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
      Point[] points = c.getPoints();
      if (c.getId().equals(anchor.getId())
          || c.getFromShape() == null
          || c.getToShape() == null
          || points == null
          || points.length < 2) {
        continue;
      }
      List<int[]> line = new ArrayList<>();
      for (Point p : points) {
        line.add(new int[] {p.x, p.y});
      }
      drawn.add(
          new OrthogonalRouter.Drawn(
              new OrthogonalRouter.Conn(
                  c.getId(), c.getFromShape().getId(), c.getToShape().getId()),
              line));
    }
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn(anchor.getId(), note.getId(), target.getId()));
    List<int[]> route = OrthogonalRouter.route(shapes, conns, drawn).get(anchor.getId());
    if (route == null || route.size() < 2) {
      return;
    }
    int n = route.size();
    ShapeOutline.snap(outlineOf(note), rect(note), route.get(0), route.get(1));
    ShapeOutline.snap(outlineOf(target), rect(target), route.get(n - 1), route.get(n - 2));
    VpGraphLayout.route(anchor, route);
  }

  /**
   * Draw the anchors of all notes of a diagram again (after shapes moved): see {@link
   * #routeNoteAnchor}.
   *
   * @param diagram the diagram
   */
  protected void routeNoteAnchors(IDiagramUIModel diagram) {
    for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
      if (c.getModelElement() instanceof com.vp.plugin.model.IAnchor) {
        routeNoteAnchor(diagram, c);
      }
    }
  }

  /** Characters per line of a note, and of the narrow variant used when a lane has no room. */
  private static final int NOTE_COLUMNS = 30;

  private static final int NOTE_NARROW_COLUMNS = 16;

  private static List<String> wrapNote(String text, int columns) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.trim().split("\\s+")) {
      if (line.length() > 0 && line.length() + word.length() + 1 > columns) {
        lines.add(line.toString());
        line.setLength(0);
      }
      line.append(line.length() > 0 ? " " : "").append(word);
    }
    lines.add(line.toString());
    return lines;
  }

  /** The widest line in the bold font Visual Paradigm draws a note in, with room for the corner. */
  private static int noteWidth(List<String> lines) {
    int widest = 0;
    for (String l : lines) {
      widest = Math.max(widest, TextMetrics.width(l, true));
    }
    return Math.max(100, widest + 40);
  }

  private static int noteHeight(List<String> lines) {
    return Math.max(40, lines.size() * 17 + 20);
  }

  /**
   * Top-left corner for a note of {@code w x h} beside {@code target}: the first of the spots right
   * of, left of, below and above it (near ones first) that overlaps no shape, no connector and no
   * label, and, for a shape in a lane, stays inside that lane. Falls back to the right of
   * everything when nothing is free.
   */
  private static int[] noteSpot(
      IDiagramUIModel diagram,
      IShapeUIModel note,
      IShapeUIModel target,
      IShapeUIModel laneShape,
      int w,
      int h,
      boolean inLane) {
    List<int[]> boxes = new ArrayList<>();
    List<int[]> lines = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getId().equals(note.getId()) || s.getWidth() <= 0 || isHiddenStub(s)) {
        continue;
      }
      IShapeUIModel[] children = s.toChildArray();
      boolean container = children != null && children.length > 0;
      // a lane, a pool, a boundary or a frame around the target only contribute what they hold
      if (!container || (isSolidContainer(s.getModelElement()) && !isInside(target, s))) {
        boxes.add(rect(s));
      }
      com.vp.plugin.diagram.ICaptionUIModel cap = s.getCaptionUIModel();
      if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
        // a caption inside its shape has coordinates relative to it; a detached one (the name of
        // an actor, a pseudo-state or a gateway) has diagram coordinates
        int[] relative = {
          s.getX() + cap.getX(), s.getY() + cap.getY(), cap.getWidth(), cap.getHeight()
        };
        if (!insideRect(rect(s), relative)) {
          boxes.add(new int[] {cap.getX(), cap.getY(), cap.getWidth(), cap.getHeight()});
        }
      }
    }
    for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
      Point[] points = c.getPoints();
      for (int i = 0; points != null && i + 1 < points.length; i++) {
        lines.add(new int[] {points[i].x, points[i].y, points[i + 1].x, points[i + 1].y});
      }
      com.vp.plugin.diagram.ICaptionUIModel cap = c.getCaptionUIModel();
      if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
        boxes.add(new int[] {cap.getX(), cap.getY(), cap.getWidth(), cap.getHeight()});
      }
    }
    int[] lane =
        laneShape != null && !isSolidContainer(laneShape.getModelElement())
            ? rect(laneShape)
            : null;
    int tx = target.getX();
    int ty = target.getY();
    int tw = target.getWidth();
    int th = target.getHeight();
    // a node in a lane: its note stays inside the lane (null when there is no room for it)
    for (int pass = lane == null || !inLane ? 1 : 0;
        pass < (lane != null && inLane ? 1 : 2);
        pass++) {
      for (int gap : new int[] {40, 70, 110, 160}) {
        int[][] candidates = {
          {tx + tw + gap, ty - 10},
          {tx + tw + gap, ty + th / 2 - h / 2},
          {tx - gap - w, ty - 10},
          {tx - gap - w, ty + th / 2 - h / 2},
          {tx, ty + th + gap},
          {tx + tw / 2 - w / 2, ty + th + gap},
          {tx, ty - gap - h},
          {tx + tw / 2 - w / 2, ty - gap - h},
          {tx + tw + gap, ty + th - h},
          {tx - gap - w, ty + th - h},
        };
        for (int[] c : candidates) {
          if (pass == 0) {
            // in a lane the box slides along the lane to fit it (a spot below a shape near the
            // lane's right edge is still the spot below it)
            c = new int[] {Math.max(lane[0] + 4, Math.min(c[0], lane[0] + lane[2] - w - 4)), c[1]};
          }
          int[] box = {c[0], c[1], w, h};
          if (c[0] < 5
              || c[1] < 5
              || (pass == 0 && !insideRect(lane, box))
              || noteBoxBlocked(box, boxes, lines)) {
            continue;
          }
          return c;
        }
      }
    }
    if (lane != null && inLane) {
      return null;
    }
    int right = tx + tw;
    for (int[] r : boxes) {
      right = Math.max(right, r[0] + r[2]);
    }
    return new int[] {right + 60, Math.max(5, ty - 10)};
  }

  private static boolean insideRect(int[] outer, int[] box) {
    return outer == null
        || (box[0] >= outer[0]
            && box[1] >= outer[1]
            && box[0] + box[2] <= outer[0] + outer[2]
            && box[1] + box[3] <= outer[1] + outer[3]);
  }

  private static boolean noteBoxBlocked(int[] box, List<int[]> boxes, List<int[]> lines) {
    for (int[] r : boxes) {
      if (box[0] < r[0] + r[2] + 6
          && r[0] < box[0] + box[2] + 6
          && box[1] < r[1] + r[3] + 6
          && r[1] < box[1] + box[3] + 6) {
        return true;
      }
    }
    for (int[] l : lines) {
      if (DiagramUtils.segmentHitsRect(l[0], l[1], l[2], l[3], box, 4)) {
        return true;
      }
    }
    return false;
  }

  /** Whether {@code shape} is nested, at any depth, in {@code container}. */
  private static boolean isInside(IShapeUIModel shape, IShapeUIModel container) {
    IDiagramElement parent = shape.getParent();
    for (int depth = 0; parent != null && depth < 50; depth++) {
      if (parent.getId().equals(container.getId())) {
        return true;
      }
      parent = parent instanceof IShapeUIModel ? ((IShapeUIModel) parent).getParent() : null;
    }
    return false;
  }

  /** Containers a connector between two shapes outside them must go around. */
  private static boolean isSolidContainer(IModelElement model) {
    return model instanceof com.vp.plugin.model.INode
        || model instanceof com.vp.plugin.model.IArtifact
        || model instanceof com.vp.plugin.model.IComponent
        || model instanceof com.vp.plugin.model.IPackage;
  }

  private static List<IConnectorUIModel> pairConnectors(IShapeUIModel a, IShapeUIModel b) {
    List<IConnectorUIModel> result = new ArrayList<>();
    IConnectorUIModel[] outgoing = a.toFromConnectorArray();
    if (outgoing != null) {
      for (IConnectorUIModel c : outgoing) {
        if (c.getToShape() != null && c.getToShape().getId().equals(b.getId())) {
          result.add(c);
        }
      }
    }
    IConnectorUIModel[] incoming = a.toToConnectorArray();
    if (incoming != null) {
      for (IConnectorUIModel c : incoming) {
        if (c.getFromShape() != null && c.getFromShape().getId().equals(b.getId())) {
          result.add(c);
        }
      }
    }
    return result;
  }

  /**
   * Whether an element is one of the invisible helper shapes of a sequence diagram: the small
   * per-message activations VP draws, which the tools make fully transparent under the long bar of
   * their lifeline. They would bury the lifelines and messages in the element list.
   */
  private static boolean isHiddenStub(IDiagramElement de) {
    return de instanceof com.vp.plugin.diagram.shape.IActivationUIModel
        && ((IShapeUIModel) de).getFillColor() != null
        && ((IShapeUIModel) de).getFillColor().getTransparency() >= 100;
  }

  @Tool(
      name = "autoLayoutDiagram",
      description =
          "Apply structured layout to a diagram. Call it after adding elements (every tool also"
              + " lays the diagram out as it adds). Every diagram type except sequence and BPMN:"
              + " layered, orthogonal layout of shapes, lines and labels: every line horizontal"
              + " or vertical, no shape overlap, no line through a shape, no two lines on top of"
              + " each other, labels beside their lines; shapes placed with x/y are not pinned any"
              + " more. Class: generalizations decide the"
              + " layers; UC: actors left, use cases inside the system boundary; state: initial at"
              + " the top, final at the bottom, loops and opposite transitions side by side;"
              + " activity: rows by the flow, nodes in the column of their lane, bars spanning"
              + " their branches, page frames around their nodes; flowchart: top to bottom, a"
              + " vertex of the decision per branch; communication: actor row, boundaries,"
              + " controls, entities, message arrows beside their links. ERD: referenced tables left of"
              + " the tables with their foreign keys. DFD: entities left, processes in the middle,"
              + " data stores right. Component: layers and containers around their components,"
              + " client above supplier, ports on the side their lines lead away to. Package:"
              + " frames around what they hold, a package above the ones it depends on. Object:"
              + " layers by the links. Deployment: nodes from left to right by their communication"
              + " paths (stacked when that gives a rounder drawing), nested nodes, artifacts and"
              + " components inside their node. BPMN: the auto-placed elements move left next to"
              + " their sources and the pools narrow to their content (rows stay). Sequence"
              + " diagrams are laid out as you add elements: nothing is moved (use setElementBounds"
              + " and rerouteConnectors)")
  public String autoLayoutDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            for (AbstractDiagramMcpTools family : families()) {
              String done = family.layoutOwnDiagram(diagram);
              if (done != null) {
                return done;
              }
            }
            LayoutProfile profile = profileOf(diagram);
            if (profile != null) {
              activate(diagram);
              PINNED.remove(diagram.getId());
              MANAGED.add(diagram.getId());
              VpGraphLayout.Summary summary = VpGraphLayout.layout(diagram, profile);
              settleReopen(diagram, profile);
              return "Auto-layout (layered, orthogonal) applied to diagram: "
                  + diagramName
                  + " ("
                  + summary
                  + ")";
            }
            return "Diagram '"
                + diagramName
                + "' is a "
                + diagram.getType()
                + ": the plugin has no layout for that diagram type, nothing was moved";
          });
    } catch (Exception e) {
      return "Error applying auto-layout: " + e.getMessage();
    }
  }

  /**
   * The answer of {@code autoLayoutDiagram} for a diagram type whose tools place every element as
   * it is added: nothing is moved.
   *
   * @param diagram the diagram
   * @param kind what the diagram is, e.g. "BPMN diagram"
   * @return the result text
   */
  protected static String laidOutWhileAdding(IDiagramUIModel diagram, String kind) {
    return "Nothing to lay out: the elements of the "
        + kind
        + " '"
        + diagram.getName()
        + "' are placed where they belong as they are added and were not moved (move one with"
        + " setElementBounds, re-route its connectors with rerouteConnectors)";
  }

  @Tool(
      name = "removeDiagramElement",
      description =
          "Remove an element (shape or connector) from a diagram by its model element name; a"
              + " connector is also found by what getDiagramElements prints for it (\"A -> B\","
              + " \"A -> B [guard]\") and a message by its number (\"5: tra ve\"). A text that fits"
              + " several connectors removes none and lists them. Only"
              + " the view goes (with the connectors attached to a removed shape and the shapes"
              + " nested in it); the model element stays in the project, except lifelines, links,"
              + " messages, activity flows and state transitions, which exist on their diagram"
              + " only")
  public String removeDiagramElement(String diagramName, String elementName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDiagramElement element = findDiagramElementByName(diagram, elementName);
            // a connector is also known by what the listings print: "A -> B", "A -> B [guard]",
            // "N: label"; a text that fits several connectors removes none of them
            boolean connectorWanted =
                element == null
                    || element instanceof IConnectorUIModel
                    || element.getModelElement() instanceof IMessage;
            List<IDiagramElement> printed =
                connectorWanted ? connectorsPrintedAs(diagram, elementName) : new ArrayList<>();
            if (!printed.isEmpty()) {
              if (printed.size() > 1) {
                StringBuilder candidates = new StringBuilder();
                for (IDiagramElement c : printed) {
                  candidates.append("\n  - ").append(connectorHandle(c));
                }
                return "Error removing element: '"
                    + elementName
                    + "' fits "
                    + printed.size()
                    + " connectors; none was removed. Name one of:"
                    + candidates;
              }
              element = printed.get(0);
            }
            if (element == null) {
              return "Element not found on diagram: " + elementName;
            }
            // IDiagramUIModel.removeDiagramElement leaves the shape on the diagram
            if (!(element instanceof IBaseDiagramElement)) {
              return "Error removing element: cannot remove '" + elementName + "'";
            }
            IBaseDiagramElement target = (IBaseDiagramElement) element;
            boolean owned = isDiagramOwned(element.getModelElement());
            // the flows and transitions of a removed node exist on this diagram only: they go with
            // it instead of staying behind in the project as models without a view
            List<IModelElement> boundFlows = new ArrayList<>();
            if (element instanceof IShapeUIModel) {
              for (IConnectorUIModel c : connectorsOfTree(element)) {
                if (isDiagramOwned(c.getModelElement())) {
                  boundFlows.add(c.getModelElement());
                }
              }
            }
            // Visual Paradigm asks "X has no referenced view. Delete X from the repository?" in a
            // modal dialog for every model left without a view (connectors of a removed shape,
            // nested shapes, ports, lifelines...), which would block this call for good. The
            // dialogs are answered "No to all": only the views go.
            List<String> prompts = new ArrayList<>();
            answeringDeletePrompts(
                () -> {
                  for (IModelElement flow : boundFlows) {
                    flow.delete();
                  }
                  if (owned) {
                    // Lifelines, links and messages live on their diagram only. The classifier a
                    // lifeline stands for stays.
                    target.deleteModel();
                  } else {
                    target.deleteViewOnly();
                  }
                  return null;
                },
                prompts);
            for (IDiagramElement left : getDiagramElementsList(diagram)) {
              if (left.getId().equals(element.getId())) {
                return "Error removing element: Visual Paradigm kept '" + elementName + "'";
              }
            }
            relayout(diagram); // the rest of the diagram closes up around the gap
            return "Removed element '"
                + elementName
                + "' from diagram '"
                + diagramName
                + "'"
                + (prompts.isEmpty()
                    ? ""
                    : " (Visual Paradigm asked to delete "
                        + prompts.size()
                        + " model(s) left without a view, answered No: "
                        + String.join(", ", prompts)
                        + " stay in the project)");
          });
    } catch (Exception e) {
      return "Error removing element: " + e.getMessage();
    }
  }

  /**
   * The connectors (and message shapes) a text names: the label of the connector ("goi"), what the
   * listings print for it ("A -> B", "A -> B [guard]", "Type 'name': A -> B") or, for a message,
   * its number and label ("5: tra ve").
   */
  private List<IDiagramElement> connectorsPrintedAs(IDiagramUIModel diagram, String text) {
    List<IDiagramElement> hits = new ArrayList<>();
    String wanted = text == null ? "" : text.trim();
    if (wanted.isEmpty()) {
      return hits;
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (!(de instanceof IConnectorUIModel) && !(model instanceof IMessage)) {
        continue;
      }
      if (connectorForms(de).contains(wanted)) {
        hits.add(de);
      }
    }
    return hits;
  }

  /** Index of the ": " in front of "A -> B" in a connector's line (a name may hold one too). */
  private static int endsStart(String line) {
    int arrow = line.indexOf(" -> ");
    return arrow < 0 ? line.indexOf(": ") : line.lastIndexOf(": ", arrow);
  }

  /** Every text that names a connector. */
  private static List<String> connectorForms(IDiagramElement de) {
    IModelElement model = de.getModelElement();
    List<String> forms = new ArrayList<>();
    String type = model != null ? getSemanticTypeName(model) : de.getShapeType();
    String line = describeConnector(de, model, type);
    forms.add(line);
    int colon = endsStart(line);
    if (colon >= 0) {
      forms.add(line.substring(colon + 2)); // "A -> B [guard]"
      int guard = line.lastIndexOf(" [");
      if (guard > colon && line.endsWith("]")) {
        forms.add(line.substring(colon + 2, guard)); // "A -> B"
      }
    }
    String name = model == null ? null : model.getName();
    if (name != null && !name.isEmpty()) {
      forms.add(name);
      if (model instanceof IMessage) {
        String seq = ((IMessage) model).getSequenceNumber();
        if (seq != null && !seq.isEmpty()) {
          forms.add(seq + ": " + name);
        }
      }
    }
    return forms;
  }

  /** The text that names one connector among several: "5: label" for a numbered message. */
  private static String connectorHandle(IDiagramElement de) {
    IModelElement model = de.getModelElement();
    String type = model != null ? getSemanticTypeName(model) : de.getShapeType();
    String line = describeConnector(de, model, type);
    int colon = endsStart(line);
    String ends = colon >= 0 ? line.substring(colon + 2) : line;
    if (model instanceof IMessage) {
      String seq = ((IMessage) model).getSequenceNumber();
      String name = model.getName();
      if (seq != null && !seq.isEmpty() && name != null && !name.isEmpty()) {
        return "\"" + seq + ": " + name + "\" (" + ends + ")";
      }
    }
    return "\"" + ends + "\"";
  }

  /**
   * Remove the connectors between two shapes of any diagram (either direction) that match a kind,
   * stereotype or label, with their model elements.
   *
   * @param diagram the diagram
   * @param fromName name of one end shape
   * @param toName name of the other end shape
   * @param kind connector kind, stereotype or label to match; empty or {@code *} matches all
   * @return the result text
   */
  protected String removeConnectorsBetween(
      IDiagramUIModel diagram, String fromName, String toName, String kind) {
    IDiagramElement a = resolveShape(diagram, fromName == null ? null : fromName.trim());
    if (!(a instanceof IShapeUIModel)) {
      return "Element not on diagram: " + fromName;
    }
    IDiagramElement b = resolveShape(diagram, toName == null ? null : toName.trim());
    if (!(b instanceof IShapeUIModel)) {
      return "Element not on diagram: " + toName;
    }
    String wanted = kind == null ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT);
    boolean any = wanted.isEmpty() || "*".equals(wanted);
    List<IConnectorUIModel> matches = new ArrayList<>();
    for (IConnectorUIModel c : pairConnectors((IShapeUIModel) a, (IShapeUIModel) b)) {
      IModelElement model = c.getModelElement();
      if (any || model == null || connectorMatches(model, wanted)) {
        matches.add(c);
      }
    }
    if (matches.isEmpty()) {
      return "No "
          + (any ? "connector" : kind.trim())
          + " found between '"
          + fromName
          + "' and '"
          + toName
          + "' on diagram '"
          + diagram.getName()
          + "'";
    }
    List<String> prompts = new ArrayList<>();
    try {
      answeringDeletePrompts(
          () -> {
            for (IConnectorUIModel c : matches) {
              if (c.getModelElement() != null) {
                c.getModelElement().delete();
              } else {
                c.deleteViewOnly();
              }
            }
            return null;
          },
          prompts);
    } catch (Exception e) {
      return "Error removing relationship: " + e.getMessage();
    }
    relayout(diagram);
    return "Removed "
        + matches.size()
        + " connector(s) between '"
        + fromName
        + "' and '"
        + toName
        + "' on diagram '"
        + diagram.getName()
        + "'";
  }

  /**
   * Hook: a shape by a handle other than its name (a DFD element by its ID: "1" or "D1"). The
   * default knows none.
   *
   * @param diagram the diagram
   * @param handle what the caller wrote
   * @return the shape, or null
   */
  protected IShapeUIModel shapeByHandle(IDiagramUIModel diagram, String handle) {
    return null;
  }

  /** The shape a caller means by a name or by the handle a family gives its shapes. */
  private IDiagramElement resolveShape(IDiagramUIModel diagram, String name) {
    IDiagramElement byName = findDiagramElementByName(diagram, name);
    if (byName instanceof IShapeUIModel || name == null) {
      return byName;
    }
    for (AbstractDiagramMcpTools family : families()) {
      IShapeUIModel shape = family.shapeByHandle(diagram, name);
      if (shape != null) {
        return shape;
      }
    }
    return byName;
  }

  private static boolean connectorMatches(IModelElement model, String wanted) {
    if (getSemanticTypeName(model).toLowerCase(java.util.Locale.ROOT).contains(wanted)
        || model.getModelType().toLowerCase(java.util.Locale.ROOT).contains(wanted)
        || cleanName(model).toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
      return true;
    }
    for (String st : stereotypeNames(model)) {
      if (st.toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Run a Visual Paradigm call that may open its "has no referenced view. Delete from the
   * repository?" prompt and answer every such prompt "No to all" from the EDT's own event loop (the
   * prompt is modal: the call would otherwise never return). Other dialogs are left alone.
   *
   * @param call the call
   * @param prompts receives the name of each model the prompt asked about
   * @param <T> the result type
   * @return the call's result
   * @throws Exception whatever the call throws
   */
  protected <T> T answeringDeletePrompts(Callable<T> call, List<String> prompts) throws Exception {
    javax.swing.Timer watch = new javax.swing.Timer(100, e -> answerDeletePrompts(prompts));
    watch.start();
    try {
      return call.call();
    } finally {
      watch.stop();
    }
  }

  private static void answerDeletePrompts(List<String> prompts) {
    for (java.awt.Window window : java.awt.Window.getWindows()) {
      if (!window.isShowing()
          || !(window instanceof java.awt.Dialog)
          || !((java.awt.Dialog) window).isModal()) {
        continue;
      }
      List<javax.swing.AbstractButton> buttons = new ArrayList<>();
      StringBuilder text = new StringBuilder();
      collectDialogParts(window, buttons, text);
      String message = text.toString();
      // "X has no referenced view. Delete X from the repository?": No (the view goes, the model
      // stays); "Do you want to mark X stopped?" after a destroy message: Yes (the X marker)
      // "Do you want to save the project?" while newProject closes the open one: No (the tool
      // discards unsaved changes, as its description says)
      boolean unreferenced =
          message.contains("no referenced view") || message.contains("save the project");
      boolean stopped = message.contains("stopped");
      if (!unreferenced && !stopped) {
        continue;
      }
      javax.swing.AbstractButton answer = null;
      for (javax.swing.AbstractButton b : buttons) {
        String label = b.getText() == null ? "" : b.getText().trim();
        if (unreferenced && label.equalsIgnoreCase("No to all")) {
          answer = b;
          break;
        }
        if (label.equalsIgnoreCase(unreferenced ? "No" : "Yes") && answer == null) {
          answer = b;
        }
      }
      if (answer == null && stopped && !buttons.isEmpty()) {
        answer = buttons.get(0); // the first button of the dialog is its "Yes"
      }
      int quote = message.indexOf('"');
      int end = message.indexOf('"', quote + 1);
      prompts.add(quote >= 0 && end > quote ? message.substring(quote + 1, end) : "?");
      if (answer != null) {
        answer.doClick();
      } else {
        window.dispose();
      }
    }
  }

  private static void collectDialogParts(
      java.awt.Component component, List<javax.swing.AbstractButton> buttons, StringBuilder text) {
    if (component instanceof javax.swing.AbstractButton) {
      buttons.add((javax.swing.AbstractButton) component);
    } else if (component instanceof javax.swing.JLabel) {
      text.append(((javax.swing.JLabel) component).getText()).append(' ');
    } else if (component instanceof javax.swing.text.JTextComponent) {
      text.append(((javax.swing.text.JTextComponent) component).getText()).append(' ');
    }
    if (component instanceof java.awt.Container) {
      for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
        collectDialogParts(child, buttons, text);
      }
    }
  }

  /** True for interaction models that exist only on the diagram that draws them. */
  private static boolean isDiagramOwned(IModelElement model) {
    return model instanceof IInteractionLifeLine
        || model instanceof IInteractionLifeLineLink
        || model instanceof IMessage
        || model instanceof IControlFlow
        || model instanceof IActivityObjectFlow
        || model instanceof com.vp.plugin.model.ITransition2;
  }

  @Tool(
      name = "renameElement",
      description =
          "Rename the element a shape of a diagram shows (class, package, node, state, action,"
              + " use case, actor, object, lifeline, table...): elementName is its current name on"
              + " the diagram, newName the new one (unique on the diagram). Every diagram that"
              + " shows the element follows; the shape is fitted to the new name. Not for"
              + " connectors (re-add them) or ERD columns (renameColumn)")
  public String renameElement(String diagramName, String elementName, String newName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (newName == null || newName.trim().isEmpty()) {
              return "New name must not be empty";
            }
            String wanted = newName.trim();
            IDiagramElement element =
                findDiagramElementByName(diagram, elementName == null ? null : elementName.trim());
            if (!(element instanceof IShapeUIModel) || element.getModelElement() == null) {
              return "Element not on diagram: " + elementName;
            }
            IDiagramElement clash = findDiagramElementByName(diagram, wanted);
            if (clash != null && !clash.getId().equals(element.getId())) {
              return "Diagram '" + diagramName + "' already has an element named '" + wanted + "'";
            }
            IModelElement model = element.getModelElement();
            String old = model.getName();
            model.setName(wanted);
            if (!wanted.equals(model.getName())) {
              String given = model.getName();
              model.setName(old);
              return "Visual Paradigm renamed '" + wanted + "' to '" + given + "': name taken";
            }
            IDiagramElement[] views = model.getDiagramElements();
            for (IDiagramElement view : views == null ? new IDiagramElement[0] : views) {
              if (view instanceof IShapeUIModel) {
                IShapeUIModel shape = (IShapeUIModel) view;
                if (old != null && old.equals(shape.getCustomText())) {
                  shape.setCustomText(wanted); // use cases and actors show a custom text
                }
                shape.resetCaption();
                fitShape(shape);
                relayout(shape.getDiagramUIModel()); // the box may be wider: lay out again
              }
            }
            return "Renamed '" + old + "' to '" + wanted + "' on diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error renaming element: " + e.getMessage();
    }
  }

  @Tool(
      name = "getElementCounts",
      description =
          "Get a summary of element types on a diagram (actors, use cases, classes, tables, etc.)")
  public String getElementCounts(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            HashMap<String, Integer> counts = new HashMap<>();
            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model != null && !isHiddenStub(de)) {
                  String typeName = getSemanticTypeName(model);
                  counts.merge(typeName, 1, Integer::sum);
                }
              }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Element counts for '").append(diagramName).append("':\n");
            counts.forEach(
                (type, count) ->
                    sb.append("  ").append(type).append(": ").append(count).append("\n"));
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error getting element counts: " + e.getMessage();
    }
  }

  /**
   * One-line description of a connector: {@code Type <<stereotypes>> name: from -> to [guard]}.
   * Ends come from the relationship model, falling back to the shapes the connector joins.
   */
  private static String describeConnector(IDiagramElement de, IModelElement model, String type) {
    StringBuilder sb = new StringBuilder(type);
    if (model != null) {
      appendStereotypes(sb, model, type);
      String name = model.getName();
      if (name != null && !name.isEmpty()) {
        String number = model instanceof IMessage ? ((IMessage) model).getSequenceNumber() : null;
        sb.append(" '")
            .append(number != null && !number.isEmpty() ? number + ": " : "")
            .append(name)
            .append("'");
      }
    }
    String from = null;
    String to = null;
    if (model instanceof IRelationship) {
      from = displayName(((IRelationship) model).getFrom());
      to = displayName(((IRelationship) model).getTo());
    }
    if (de instanceof IConnectorUIModel) {
      IConnectorUIModel conn = (IConnectorUIModel) de;
      if (from == null && conn.getFromShape() != null) {
        from = displayName(conn.getFromShape().getModelElement());
      }
      if (to == null && conn.getToShape() != null) {
        to = displayName(conn.getToShape().getModelElement());
      }
    }
    if (isStoredReversed(model)) {
      // print the direction the arrow is drawn in: child -> parent, extension -> base,
      // implementation -> interface
      String swap = from;
      from = to;
      to = swap;
    }
    sb.append(": ").append(from != null ? from : "?").append(" -> ").append(to != null ? to : "?");
    String guard = null;
    if (model instanceof IControlFlow) {
      guard = ((IControlFlow) model).getGuard();
    } else if (model instanceof IActivityObjectFlow) {
      guard = ((IActivityObjectFlow) model).getGuard();
    }
    if (guard != null && !guard.trim().isEmpty()) {
      sb.append(" [").append(guard.trim()).append("]");
    }
    if (model instanceof IAssociation) {
      IAssociationEnd fromEnd = (IAssociationEnd) ((IAssociation) model).getFromEnd();
      IAssociationEnd toEnd = (IAssociationEnd) ((IAssociation) model).getToEnd();
      String ma = fromEnd != null ? multiplicityText(fromEnd.getMultiplicity()) : "";
      String mb = toEnd != null ? multiplicityText(toEnd.getMultiplicity()) : "";
      if (!ma.isEmpty() || !mb.isEmpty()) {
        sb.append(" mult ")
            .append(ma != null ? ma : "")
            .append(" .. ")
            .append(mb != null ? mb : "");
      }
      // aggregation kind and the whole end (the diamond)
      for (IAssociationEnd end : new IAssociationEnd[] {fromEnd, toEnd}) {
        String kind = aggregationOf(end);
        if (kind != null) {
          sb.append(" ")
              .append(kind)
              .append(" (whole: ")
              .append(displayName(end.getModelElement()))
              .append(")");
        }
      }
    }
    if (model instanceof IInteractionLifeLineLink) {
      List<String> msgs = new ArrayList<>();
      for (IModelElement child : model.toChildArray()) {
        if (child instanceof IMessage) {
          String seq = ((IMessage) child).getSequenceNumber();
          msgs.add((seq != null && !seq.isEmpty() ? seq + ": " : "") + child.getName());
        }
      }
      if (!msgs.isEmpty()) {
        sb.append(" messages ").append(msgs);
      }
    }
    return sb.toString();
  }

  /**
   * The aggregation an association end carries: "composite", "shared" or null for none. VP reports
   * "composite"/"shared" although the kind is set with "Composited"/"Aggregation", so both
   * spellings are accepted.
   *
   * @param end the association end, may be null
   * @return "composite", "shared" or null
   */
  protected static String aggregationOf(IAssociationEnd end) {
    String kind = end != null ? end.getAggregationKind() : null;
    if (IAssociationEnd.AGGREGATION_KIND_composite.equalsIgnoreCase(kind)
        || IAssociationEnd.AGGREGATION_KIND_COMPOSITED.equalsIgnoreCase(kind)) {
      return "composite";
    }
    if (IAssociationEnd.AGGREGATION_KIND_shared.equalsIgnoreCase(kind)
        || IAssociationEnd.AGGREGATION_KIND_AGGREGATION.equalsIgnoreCase(kind)) {
      return "shared";
    }
    return null;
  }

  /**
   * Type-specific details for the element list: an object's classifier and slot values, the
   * component a port sits on.
   */
  private static String extraDetails(IDiagramElement de, IModelElement model) {
    StringBuilder sb = new StringBuilder();
    if (model instanceof com.vp.plugin.model.IInstanceSpecification) {
      com.vp.plugin.model.IInstanceSpecification instance =
          (com.vp.plugin.model.IInstanceSpecification) model;
      IModelElement[] classifiers = instance.toClassifierArray();
      if (classifiers != null && classifiers.length > 0) {
        sb.append(" : ").append(classifiers[0].getName());
      }
      com.vp.plugin.model.ISlot[] slots = instance.toSlotArray();
      if (slots != null && slots.length > 0) {
        List<String> values = new ArrayList<>();
        for (com.vp.plugin.model.ISlot slot : slots) {
          String feature = slot.getFeature() != null ? slot.getFeature().getName() : "?";
          List<String> parts = new ArrayList<>();
          com.vp.plugin.model.ICompositeValueSpecification[] specs = slot.toValueArray();
          if (specs != null) {
            for (com.vp.plugin.model.ICompositeValueSpecification spec : specs) {
              parts.add(String.valueOf(spec.getValue()));
            }
          }
          values.add(feature + "=" + String.join(",", parts));
        }
        sb.append(" {").append(String.join(", ", values)).append("}");
      }
    } else if (model instanceof com.vp.plugin.model.IPort
        && de instanceof IShapeUIModel
        && ((IShapeUIModel) de).getParent() != null
        && ((IShapeUIModel) de).getParent().getModelElement() != null) {
      sb.append(" on ").append(((IShapeUIModel) de).getParent().getModelElement().getName());
    }
    return sb.toString();
  }

  /**
   * Whether Visual Paradigm stores the relationship against the direction its arrow is drawn in: a
   * generalization as general -> specific, an extend as base -> extension and a realization as
   * interface -> implementation.
   *
   * @param model the relationship model, may be null
   * @return true for generalizations, extends and realizations
   */
  protected static boolean isStoredReversed(IModelElement model) {
    return model instanceof IGeneralization
        || model instanceof IExtend
        || model instanceof com.vp.plugin.model.IRealization;
  }

  /** Stereotypes of an element, except VP's built-in one named like the element type. */
  protected static void appendStereotypes(StringBuilder sb, IModelElement model, String type) {
    IStereotype[] stereotypes = model.toStereotypeModelArray();
    if (stereotypes != null) {
      for (IStereotype st : stereotypes) {
        if (!st.getName().equalsIgnoreCase(type)) {
          sb.append(" <<").append(st.getName()).append(">>");
        }
      }
    }
  }

  /** A multiplicity worth printing: empty for unset or VP's "Unspecified". */
  protected static String multiplicityText(String multiplicity) {
    return multiplicity == null || "Unspecified".equalsIgnoreCase(multiplicity) ? "" : multiplicity;
  }

  /** UML visibility symbol (+ - # ~) for VP's visibility keyword, empty when unset. */
  protected static String visibilitySymbol(String visibility) {
    if (visibility == null) {
      return "";
    }
    switch (visibility) {
      case "public":
        return "+";
      case "private":
        return "-";
      case "protected":
        return "#";
      case "package":
        return "~";
      default:
        return "";
    }
  }

  /**
   * The name a listing prints for an element: its own name, ":Classifier" for an anonymous object,
   * the first words of a note's text; what the tools accept as its handle.
   */
  private static String listName(IModelElement element) {
    String name = element.getName();
    if (name != null && !name.isEmpty()) {
      return name;
    }
    if (element instanceof com.vp.plugin.model.INOTE) {
      String text = ((com.vp.plugin.model.INOTE) element).getDescription();
      if (text != null && !text.trim().isEmpty()) {
        String line = text.trim().replaceAll("\\s+", " ");
        return "\"" + (line.length() > 40 ? line.substring(0, 40) + "..." : line) + "\"";
      }
    }
    return displayName(element);
  }

  private static String displayName(IModelElement element) {
    if (element == null) {
      return null;
    }
    String name = element.getName();
    if (name != null && !name.isEmpty()) {
      return name;
    }
    if (element instanceof IInteractionLifeLine) {
      Object classifier = ((IInteractionLifeLine) element).getBaseClassifier();
      if (classifier instanceof IModelElement) {
        return ":" + ((IModelElement) classifier).getName(); // anonymous object
      }
    }
    if (element instanceof com.vp.plugin.model.IInstanceSpecification) {
      // an anonymous object of an object diagram: ":Class"
      IModelElement[] classifiers =
          ((com.vp.plugin.model.IInstanceSpecification) element).toClassifierArray();
      if (classifiers != null && classifiers.length > 0 && classifiers[0] != null) {
        return ":" + classifiers[0].getName();
      }
    }
    return "(" + element.getModelType() + ")";
  }

  // --- Type Name Helper ---

  private static String getSemanticTypeName(IModelElement model) {
    if (model == null) {
      return "unknown";
    }
    if (model instanceof IActor) {
      return "Actor";
    }
    if (model instanceof IUseCase) {
      return "UseCase";
    }
    if (model instanceof IInclude) {
      return "Include";
    }
    if (model instanceof IExtend) {
      return "Extend";
    }
    if (model instanceof IGeneralization) {
      return "Generalization";
    }
    if (model instanceof IAssociation) {
      return "Association";
    }
    if (model instanceof IClass) {
      // Check for Interface stereotype
      Iterator<?> stereotypes = ((IClass) model).stereotypeIterator();
      while (stereotypes.hasNext()) {
        if ("Interface".equals(stereotypes.next())) {
          return "Interface";
        }
      }
      return "Class";
    }
    if (model instanceof IDBTable) {
      return "Table";
    }
    if (model instanceof IDBForeignKey) {
      return "ForeignKey";
    }
    if (model instanceof IInteractionLifeLine) {
      return "Lifeline";
    }
    if (model instanceof IMessage) {
      return "Message";
    }
    if (model instanceof com.vp.plugin.model.IInitialPseudoState) {
      return "InitialState";
    }
    if (model instanceof com.vp.plugin.model.IFinalState2) {
      return "FinalState";
    }
    if (model instanceof com.vp.plugin.model.IState2) {
      return "State";
    }
    if (model instanceof com.vp.plugin.model.ITransition2) {
      return "Transition";
    }
    return model.getModelType();
  }

  // --- VP API Accessors ---

  protected IProject requireProject() {
    IProject project = ApplicationManager.instance().getProjectManager().getProject();
    if (project == null) {
      throw new IllegalStateException("No project is open");
    }
    return project;
  }

  protected DiagramManager getDiagramManager() {
    return ApplicationManager.instance().getDiagramManager();
  }

  protected IModelElementFactory getModelElementFactory() {
    return IModelElementFactory.instance();
  }
}
