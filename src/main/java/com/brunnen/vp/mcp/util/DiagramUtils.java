package com.brunnen.vp.mcp.util;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Shared utility for finding diagrams and model elements in a Visual Paradigm project. */
public final class DiagramUtils {

  private DiagramUtils() {}

  public static IProject getProject() {
    return ApplicationManager.instance().getProjectManager().getProject();
  }

  public static DiagramManager getDiagramManager() {
    return ApplicationManager.instance().getDiagramManager();
  }

  /** Find a diagram by name and type. Returns the LAST match (most recently created). */
  public static IDiagramUIModel findDiagramByName(String name, Class<?> diagramType) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    IDiagramUIModel result = null;
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel) {
        IDiagramUIModel diagram = (IDiagramUIModel) obj;
        if (name.equals(diagram.getName()) && diagramType.isInstance(diagram)) {
          result = diagram;
        }
      }
    }
    return result;
  }

  /** Find any diagram by name (any type). Returns the LAST match (most recently created). */
  public static IDiagramUIModel findDiagramByName(String name) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    IDiagramUIModel result = null;
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel) {
        IDiagramUIModel diagram = (IDiagramUIModel) obj;
        if (name.equals(diagram.getName())) {
          result = diagram;
        }
      }
    }
    return result;
  }

  /** Find a model element by name and type. */
  public static <T extends IModelElement> T findModelElementByName(String name, Class<T> type) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (type.isInstance(obj)) {
        T element = type.cast(obj);
        if (name.equals(element.getName())) {
          return element;
        }
      }
    }
    return null;
  }

  /** Find all model elements of a given type. */
  public static <T extends IModelElement> List<T> findAllModelElements(Class<T> type) {
    List<T> result = new ArrayList<>();
    IProject project = getProject();
    if (project == null) {
      return result;
    }
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (type.isInstance(obj)) {
        result.add(type.cast(obj));
      }
    }
    return result;
  }

  /** Find all diagrams of a given type. */
  public static List<IDiagramUIModel> findAllDiagrams(Class<?> diagramType) {
    List<IDiagramUIModel> result = new ArrayList<>();
    IProject project = getProject();
    if (project == null) {
      return result;
    }
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel && diagramType.isInstance(obj)) {
        result.add((IDiagramUIModel) obj);
      }
    }
    return result;
  }

  public static void openDiagram(IDiagramUIModel diagram) {
    getDiagramManager().openDiagram(diagram);
  }

  private static final java.util.Map<String, String[]> TYPE_ALIASES = typeAliases();

  private static java.util.Map<String, String[]> typeAliases() {
    java.util.Map<String, String[]> m = new java.util.LinkedHashMap<>();
    m.put("usecase", new String[] {"UseCaseDiagram"});
    m.put("uc", new String[] {"UseCaseDiagram"});
    m.put("class", new String[] {"ClassDiagram"});
    m.put("sequence", new String[] {"InteractionDiagram"});
    m.put("interaction", new String[] {"InteractionDiagram"});
    m.put("communication", new String[] {"CommunicationDiagram"});
    m.put("collaboration", new String[] {"CommunicationDiagram"});
    m.put("state", new String[] {"StateDiagram"});
    m.put("statemachine", new String[] {"StateDiagram"});
    m.put("activity", new String[] {"ActivityDiagram"});
    m.put("component", new String[] {"ComponentDiagram"});
    m.put("deployment", new String[] {"DeploymentDiagram"});
    m.put("package", new String[] {"PackageDiagram"});
    m.put("object", new String[] {"ObjectDiagram"});
    m.put("er", new String[] {"ERDiagram"});
    m.put("erd", new String[] {"ERDiagram"});
    m.put("entityrelationship", new String[] {"ERDiagram"});
    m.put("bpmn", new String[] {"BusinessProcessDiagram"});
    m.put("businessprocess", new String[] {"BusinessProcessDiagram"});
    m.put("flowchart", new String[] {"Flowchart"});
    m.put(
        "dfd",
        new String[] {
          "DataFlowDiagram", "GaneSarsonDFD", "YourdonAndCoadDFD", "YourdonDeMarcoDFD"
        });
    m.put(
        "dataflow",
        new String[] {
          "DataFlowDiagram", "GaneSarsonDFD", "YourdonAndCoadDFD", "YourdonDeMarcoDFD"
        });
    return java.util.Collections.unmodifiableMap(m);
  }

  /**
   * The exact Visual Paradigm diagram type names a {@code listDiagrams} filter stands for. A
   * friendly name (sequence, state machine, ER, communication, BPMN, DFD...), a type name with or
   * without its "Diagram" suffix, case, spaces and dashes ignored. Never a substring match: "ER" is
   * the entity relationship diagram only, not every type that contains the letters.
   *
   * @param filter the filter text
   * @return the matching type names; empty for a blank filter (no filtering) or an unknown name
   */
  public static List<String> diagramTypesFor(String filter) {
    List<String> result = new ArrayList<>();
    if (filter == null) {
      return result;
    }
    String key = filter.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
    if (key.isEmpty()) {
      return result;
    }
    String[] alias = TYPE_ALIASES.get(key);
    if (alias == null && key.endsWith("diagram")) {
      alias = TYPE_ALIASES.get(key.substring(0, key.length() - "diagram".length()));
    }
    if (alias != null) {
      result.addAll(java.util.Arrays.asList(alias));
    }
    return result;
  }

  /**
   * Whether a diagram of Visual Paradigm type {@code diagramType} passes a {@code listDiagrams}
   * filter: a blank filter passes everything, a known friendly name passes its exact types, any
   * other text must equal the type name (ignoring case and a trailing "Diagram").
   *
   * @param diagramType VP's type name of the diagram
   * @param filter the filter text, may be blank
   * @return true when the diagram is listed
   */
  public static boolean matchesTypeFilter(String diagramType, String filter) {
    if (filter == null || filter.trim().isEmpty()) {
      return true;
    }
    List<String> exact = diagramTypesFor(filter);
    if (!exact.isEmpty()) {
      return exact.contains(diagramType);
    }
    String key = filter.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
    String type = diagramType == null ? "" : diagramType.toLowerCase(java.util.Locale.ROOT);
    return type.equals(key) || type.equals(key + "diagram");
  }

  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * How far to move rectangle {@code b} so it no longer collides with rectangle {@code a} (which
   * grew or was placed over it). A shape below a's middle goes down, one in the same row right of
   * a's left edge goes right; otherwise the shorter of the two moves wins. Shapes are never moved
   * left or up.
   *
   * @param a the fixed rectangle {x, y, w, h}
   * @param b the rectangle to move {x, y, w, h}
   * @param gap free space to keep between them
   * @return {dx, dy}, {0, 0} when b is already clear of a
   */
  public static int[] pushVector(int[] a, int[] b, int gap) {
    int needRight = a[0] + a[2] + gap - b[0];
    int needDown = a[1] + a[3] + gap - b[1];
    boolean clear =
        needRight <= 0 || needDown <= 0 || b[0] + b[2] + gap <= a[0] || b[1] + b[3] + gap <= a[1];
    if (clear) {
      return new int[] {0, 0};
    }
    if (b[1] >= a[1] + a[3] / 2) {
      return new int[] {0, needDown}; // below a's middle: a grew down over it
    }
    if (b[0] >= a[0]) {
      return new int[] {needRight, 0}; // same row, right of a's left edge: a grew right over it
    }
    return needRight <= needDown ? new int[] {needRight, 0} : new int[] {0, needDown};
  }

  /**
   * First free spot for a w x h shape, scanning rows left to right from (x0, y0): a candidate that
   * collides with an occupied rectangle jumps to that rectangle's right edge; a row that runs past
   * {@code maxRight} continues on the next row, just below the nearest shape that blocked it.
   *
   * @param occupied rectangles {x, y, w, h} already on the diagram
   * @param x0 left edge of every row
   * @param y0 top of the first row
   * @param w width of the new shape
   * @param h height of the new shape
   * @param maxRight right edge a row should not cross
   * @param gap free space kept around shapes
   * @return {x, y}
   */
  public static int[] firstFreeSpot(
      List<int[]> occupied, int x0, int y0, int w, int h, int maxRight, int gap) {
    int y = y0;
    for (int row = 0; row < 200; row++) {
      int x = x0;
      int nextY = Integer.MAX_VALUE;
      while (x == x0 || x + w <= maxRight) {
        int[] hit = null;
        for (int[] r : occupied) {
          if (x < r[0] + r[2] + gap
              && r[0] < x + w + gap
              && y < r[1] + r[3] + gap
              && r[1] < y + h + gap) {
            if (hit == null || r[0] + r[2] > hit[0] + hit[2]) {
              hit = r;
            }
          }
        }
        if (hit == null) {
          return new int[] {x, y};
        }
        nextY = Math.min(nextY, hit[1] + hit[3] + gap);
        x = hit[0] + hit[2] + gap;
      }
      y = nextY > y && nextY != Integer.MAX_VALUE ? nextY : y + h + gap;
    }
    return new int[] {x0, y};
  }

  /**
   * Candidate top-left corners for a connector label beside its line, best first: on the longest
   * leg, above a horizontal or shallow one and to the right of a steep one (a label centered on the
   * line is struck through by it), in the middle, then towards either end, then on the other side.
   *
   * @param points the connector points {x, y}
   * @param w label width
   * @param h label height
   * @return candidate corners, best first
   */
  public static List<int[]> labelSpotsBeside(int[][] points, int w, int h) {
    int best = 0;
    double longest = -1;
    for (int i = 0; i + 1 < points.length; i++) {
      double len = Math.hypot(points[i + 1][0] - points[i][0], points[i + 1][1] - points[i][1]);
      if (len > longest) {
        longest = len;
        best = i;
      }
    }
    int[] a = points[best];
    int[] b = points[Math.min(best + 1, points.length - 1)];
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    double len = Math.max(1, Math.hypot(dx, dy));
    boolean shallow = Math.abs(dx) >= Math.abs(dy);
    // the normal pointing up (shallow line) or right (steep line) first
    double nx = -dy / len;
    double ny = dx / len;
    if (shallow ? ny > 0 : nx < 0) {
      nx = -nx;
      ny = -ny;
    }
    double clearance = shallow ? h / 2.0 + 6 : w / 2.0 + 8;
    List<int[]> candidates = new ArrayList<>();
    for (double t : new double[] {0.5, 0.35, 0.65, 0.2, 0.8}) {
      for (double side : new double[] {1, -1}) {
        double cx = a[0] + dx * t + nx * clearance * side;
        double cy = a[1] + dy * t + ny * clearance * side;
        candidates.add(new int[] {(int) Math.round(cx - w / 2.0), (int) Math.round(cy - h / 2.0)});
      }
    }
    return candidates;
  }

  /**
   * Whether the segment (x1, y1)-(x2, y2) passes through the rectangle grown by {@code margin}
   * (Liang-Barsky clipping).
   *
   * @param x1 start x
   * @param y1 start y
   * @param x2 end x
   * @param y2 end y
   * @param r rectangle {x, y, w, h}
   * @param margin extra space around the rectangle
   * @return true when the segment touches the grown rectangle
   */
  public static boolean segmentHitsRect(
      double x1, double y1, double x2, double y2, int[] r, int margin) {
    double left = r[0] - margin;
    double top = r[1] - margin;
    double right = r[0] + r[2] + margin;
    double bottom = r[1] + r[3] + margin;
    double dx = x2 - x1;
    double dy = y2 - y1;
    double[] p = {-dx, dx, -dy, dy};
    double[] q = {x1 - left, right - x1, y1 - top, bottom - y1};
    double t0 = 0;
    double t1 = 1;
    for (int i = 0; i < 4; i++) {
      if (Math.abs(p[i]) < 1e-12) {
        if (q[i] < 0) {
          return false;
        }
      } else {
        double t = q[i] / p[i];
        if (p[i] < 0) {
          t0 = Math.max(t0, t);
        } else {
          t1 = Math.min(t1, t);
        }
        if (t0 > t1) {
          return false;
        }
      }
    }
    return true;
  }
}
