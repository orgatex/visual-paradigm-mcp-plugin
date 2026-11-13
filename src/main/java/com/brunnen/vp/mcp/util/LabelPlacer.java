package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Places connector labels (multiplicities, roles, names, guards, keywords) on a diagram whose
 * shapes and horizontal/vertical routes are final. A label sits beside its connector, never on a
 * shape, never on a line and never on another label: a tail label near the start of the route, a
 * head label near the end, a centre label beside the longest leg.
 *
 * <p>Pure geometry: rectangles are {x, y, w, h}, routes are lists of {x, y}.
 */
public final class LabelPlacer {

  /** Clearance between a label and its line: the nearest first, then further out. */
  private static final int[] GAPS = {3, 14, 26, 50, 80, 110, 150};

  /** Where a label belongs on its connector. */
  public enum Slot {
    TAIL,
    CENTER,
    HEAD
  }

  /** A label to place. */
  public static final class Spec {
    final String connector;
    final int index;
    final int w;
    final int h;
    final Slot slot;
    int verticalW;
    int verticalH;
    int side;
    int altW;
    int altH;

    /**
     * A label.
     *
     * @param connector id of the connector it belongs to
     * @param index position of the label in the connector's label list
     * @param w width
     * @param h height
     * @param slot where it sits
     */
    public Spec(String connector, int index, int w, int h, Slot slot) {
      this.connector = connector;
      this.index = index;
      this.w = w;
      this.h = h;
      this.slot = slot;
      this.verticalW = w;
      this.verticalH = h;
    }

    /**
     * The id of the connector the label belongs to.
     *
     * @return the id
     */
    public String connector() {
      return connector;
    }

    /**
     * A label that needs another size beside a vertical leg (an arrow with its text stacked above a
     * horizontal leg, side by side along a vertical one).
     *
     * @param vw width beside a vertical leg
     * @param vh height beside a vertical leg
     * @return this
     */
    public Spec verticalSize(int vw, int vh) {
      this.verticalW = vw;
      this.verticalH = vh;
      return this;
    }

    /**
     * A narrower, taller box the label may take when its own box finds no clear place (the text
     * wraps inside it).
     *
     * @param w width
     * @param h height
     * @return this
     */
    public Spec wrappedTo(int w, int h) {
      this.altW = w;
      this.altH = h;
      return this;
    }

    /**
     * The side of the line the label prefers: -1 above a horizontal leg or left of a vertical one,
     * 1 below or right, 0 none.
     *
     * @param value the side
     * @return this
     */
    public Spec preferSide(int value) {
      this.side = value;
      return this;
    }
  }

  private LabelPlacer() {}

  /**
   * Place every label.
   *
   * @param shapes rectangles of the shapes labels must keep off (leaf shapes)
   * @param routes the final route of every connector by id (first point at its tail)
   * @param specs the labels
   * @return for every connector id the label rectangles by label index
   */
  public static Map<String, Map<Integer, int[]>> place(
      List<int[]> shapes, Map<String, List<int[]>> routes, List<Spec> specs) {
    return place(shapes, routes, specs, new ArrayList<int[]>());
  }

  /**
   * Place some labels around labels that are already on the canvas.
   *
   * @param shapes rectangles of the shapes labels must keep off (leaf shapes)
   * @param routes the final route of every connector by id (first point at its tail)
   * @param specs the labels to place
   * @param fixed rectangles of labels that stay where they are
   * @return for every connector id the label rectangles by label index
   */
  public static Map<String, Map<Integer, int[]>> place(
      List<int[]> shapes, Map<String, List<int[]>> routes, List<Spec> specs, List<int[]> fixed) {
    return place(shapes, routes, specs, fixed, CANVAS_MIN);
  }

  /**
   * Place some labels around labels that are already on the canvas.
   *
   * @param shapes rectangles of the shapes labels must keep off (leaf shapes)
   * @param routes the final route of every connector by id (first point at its tail)
   * @param specs the labels to place
   * @param fixed rectangles of labels that stay where they are
   * @param canvasMin the least x and y a label may have (the export clips what lies left of or
   *     above the canvas); a layout that may move everything afterwards passes a very small value
   * @return for every connector id the label rectangles by label index
   */
  public static Map<String, Map<Integer, int[]>> place(
      List<int[]> shapes,
      Map<String, List<int[]>> routes,
      List<Spec> specs,
      List<int[]> fixed,
      int canvasMin) {
    List<int[]> lines = new ArrayList<>(); // {x1, y1, x2, y2, index of the route}
    Map<String, Integer> owner = new LinkedHashMap<>();
    for (Map.Entry<String, List<int[]>> entry : routes.entrySet()) {
      List<int[]> route = entry.getValue();
      int index = owner.size();
      owner.put(entry.getKey(), index);
      for (int i = 0; i + 1 < route.size(); i++) {
        int[] a = route.get(i);
        int[] b = route.get(i + 1);
        lines.add(new int[] {a[0], a[1], b[0], b[1], index});
      }
    }
    List<int[]> placed = new ArrayList<>(fixed);
    Map<String, Map<Integer, int[]>> result = new LinkedHashMap<>();
    // ends first (they are tied to a short stretch), then centre labels
    for (Slot pass : new Slot[] {Slot.TAIL, Slot.HEAD, Slot.CENTER}) {
      for (Spec spec : specs) {
        if (spec.slot != pass) {
          continue;
        }
        List<int[]> route = routes.get(spec.connector);
        if (route == null || route.size() < 2) {
          continue;
        }
        int[] rect =
            choose(spec, route, shapes, lines, placed, owner.get(spec.connector), canvasMin);
        placed.add(rect);
        result.computeIfAbsent(spec.connector, k -> new LinkedHashMap<>()).put(spec.index, rect);
      }
    }
    return result;
  }

  private static int[] choose(
      Spec spec,
      List<int[]> route,
      List<int[]> shapes,
      List<int[]> lines,
      List<int[]> placed,
      int own,
      int canvasMin) {
    List<int[]> candidates = new ArrayList<>();
    for (int gap : GAPS) {
      candidates.addAll(candidates(spec, route, gap, lines, own));
    }
    int[] best = null;
    long bestScore = Long.MAX_VALUE;
    for (int i = 0; i < candidates.size(); i++) {
      int[] c = candidates.get(i);
      int collisions = collisions(c, shapes, lines, placed, own, canvasMin);
      int doubt = doubt(c, lines, own);
      long score = ((long) collisions * 10 + doubt) * 100000L + i;
      if (score < bestScore) {
        bestScore = score;
        best = c;
        if (collisions == 0 && doubt == 0) {
          break;
        }
      }
    }
    if (spec.altW > 0 && bestScore >= 2L * 100000L) {
      // no clear place beside the line for the label as it is: try it wrapped into a narrower box
      Spec narrow = new Spec(spec.connector, spec.index, spec.altW, spec.altH, spec.slot);
      narrow.side = spec.side;
      List<int[]> alternatives = new ArrayList<>();
      for (int gap : GAPS) {
        alternatives.addAll(candidates(narrow, route, gap, lines, own));
      }
      for (int i = 0; i < alternatives.size(); i++) {
        int[] c = alternatives.get(i);
        int collisions = collisions(c, shapes, lines, placed, own, canvasMin);
        int doubt = doubt(c, lines, own);
        // wrapping costs a little: it is used when the one-line label has no clear place near
        // its line
        long score = ((long) collisions * 10 + doubt + 1) * 100000L + i;
        if (score < bestScore) {
          bestScore = score;
          best = c;
        }
      }
    }
    if (best == null) {
      int[] a = route.get(0);
      best = new int[] {a[0] + 3, a[1] + 3, spec.w, spec.h}; // a route of no length
    }
    return best;
  }

  /** Candidate rectangles in order of preference. */
  private static List<int[]> candidates(
      Spec spec, List<int[]> route, int gap, List<int[]> lines, int own) {
    List<int[]> result = new ArrayList<>();
    int legs = route.size() - 1;
    List<Integer> order = new ArrayList<>();
    if (spec.slot == Slot.TAIL) {
      for (int i = 0; i < legs; i++) {
        order.add(i);
      }
    } else if (spec.slot == Slot.HEAD) {
      for (int i = legs - 1; i >= 0; i--) {
        order.add(i);
      }
    } else {
      for (int i = 0; i < legs; i++) {
        order.add(i);
      }
      order.sort((a, b) -> Integer.compare(length(route, b), length(route, a)));
    }
    for (int leg : order) {
      int[] a = route.get(leg);
      int[] b = route.get(leg + 1);
      boolean horizontal = a[1] == b[1];
      int len = length(route, leg);
      if (len == 0) {
        continue;
      }
      int sign = horizontal ? Integer.signum(b[0] - a[0]) : Integer.signum(b[1] - a[1]);
      int lw = horizontal ? spec.w : spec.verticalW;
      int lh = horizontal ? spec.h : spec.verticalH;
      int[] sides = sidesOf(spec, a, b, horizontal, lines, own);
      int extent = horizontal ? lw : lh;
      if (spec.slot == Slot.CENTER) {
        // the middle of the leg first (even when the label is longer than the leg), then sliding
        // outwards: +6, -6, +12, ... while the label stays within the leg
        List<Integer> alongs = new ArrayList<>();
        alongs.add(len / 2);
        for (int k = 1; k * 6 <= (len - extent) / 2; k++) {
          alongs.add(len / 2 + k * 6);
          alongs.add(len / 2 - k * 6);
        }
        // a leg too short for the label (a stub of a self loop): beside the line, past either end
        alongs.add(len + extent / 2 + 4);
        alongs.add(-extent / 2 - 4);
        for (int along : alongs) {
          for (int side : sides) {
            result.add(rect(lw, lh, a, sign, horizontal, along, side, true, gap));
          }
        }
        continue;
      }
      // a tail label slides from the start of the leg, a head label from its end
      int[] from = spec.slot == Slot.HEAD ? b : a;
      int dir = spec.slot == Slot.HEAD ? -sign : sign;
      int steps = Math.max(0, (len - extent) / 6);
      for (int step = 0; step <= steps; step++) {
        for (int side : sides) {
          result.add(rect(lw, lh, from, dir, horizontal, 3 + step * 6, side, false, gap));
        }
      }
    }
    if (spec.slot == Slot.CENTER) {
      // last resorts: clear of the whole route, on any of its four sides
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      for (int[] p : route) {
        minX = Math.min(minX, p[0]);
        minY = Math.min(minY, p[1]);
        maxX = Math.max(maxX, p[0]);
        maxY = Math.max(maxY, p[1]);
      }
      int cx = (minX + maxX) / 2 - spec.w / 2;
      int cy = (minY + maxY) / 2 - spec.h / 2;
      result.add(new int[] {minX - spec.w - gap, cy, spec.w, spec.h});
      result.add(new int[] {maxX + gap, cy, spec.w, spec.h});
      result.add(new int[] {cx, minY - spec.h - gap, spec.w, spec.h});
      result.add(new int[] {cx, maxY + gap, spec.w, spec.h});
    }
    return result;
  }

  /**
   * The sides of a leg to try, the preferred first: the side the spec asks for, else the one with
   * more room before the next line of another connector that runs along the leg (a label belongs
   * outside a bundle of parallel lines, beside its own).
   */
  private static int[] sidesOf(
      Spec spec, int[] a, int[] b, boolean horizontal, List<int[]> lines, int own) {
    if (spec.side != 0) {
      return spec.side > 0 ? new int[] {1, -1} : new int[] {-1, 1};
    }
    int lo = horizontal ? Math.min(a[0], b[0]) : Math.min(a[1], b[1]);
    int hi = horizontal ? Math.max(a[0], b[0]) : Math.max(a[1], b[1]);
    int at = horizontal ? a[1] : a[0];
    int before = ROOM;
    int after = ROOM;
    for (int[] l : lines) {
      if (l[4] == own || (l[1] == l[3]) != horizontal) {
        continue;
      }
      int llo = horizontal ? Math.min(l[0], l[2]) : Math.min(l[1], l[3]);
      int lhi = horizontal ? Math.max(l[0], l[2]) : Math.max(l[1], l[3]);
      if (Math.min(hi, lhi) - Math.max(lo, llo) <= 0) {
        continue;
      }
      int offset = (horizontal ? l[1] : l[0]) - at;
      if (offset < 0) {
        before = Math.min(before, -offset);
      } else if (offset > 0) {
        after = Math.min(after, offset);
      }
    }
    return before >= after ? new int[] {-1, 1} : new int[] {1, -1};
  }

  /**
   * The rectangle of a label beside a leg: {@code from} is where the slide starts, {@code dir} its
   * direction along the leg, {@code along} the distance to the label's near edge (to its centre for
   * a centred label) and {@code side} the side of the line.
   */
  private static int[] rect(
      int w,
      int h,
      int[] from,
      int dir,
      boolean horizontal,
      int along,
      int side,
      boolean centred,
      int gap) {
    int extent = horizontal ? w : h;
    int start;
    if (centred) {
      start = (horizontal ? from[0] : from[1]) + dir * along - extent / 2;
    } else {
      int near = (horizontal ? from[0] : from[1]) + dir * along;
      start = dir > 0 ? near : near - extent;
    }
    if (horizontal) {
      return new int[] {start, side < 0 ? from[1] - gap - h : from[1] + gap, w, h};
    }
    return new int[] {side < 0 ? from[0] - gap - w : from[0] + gap, start, w, h};
  }

  private static int length(List<int[]> route, int leg) {
    int[] a = route.get(leg);
    int[] b = route.get(leg + 1);
    return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
  }

  /** The least coordinate of a label on a canvas that stays where it is. */
  private static final int CANVAS_MIN = 2;

  /** Distance after which a neighbouring line does not matter for the side of a label. */
  private static final int ROOM = 400;

  /** How close a label may come to the line of another connector before it reads as its. */
  private static final int NEAR = 9;

  /**
   * Cost of a spot: a label on a shape or another label is the worst, on a line, off the canvas or
   * right beside the line of another connector (where it would read as that line's label) less so.
   */
  private static int collisions(
      int[] r, List<int[]> shapes, List<int[]> lines, List<int[]> placed, int own, int canvasMin) {
    int n = 0;
    for (int[] s : shapes) {
      if (intersects(r, s)) {
        n += 3;
      }
    }
    for (int[] p : placed) {
      if (intersects(r, p)) {
        n += 3;
      }
    }
    int[] wide = {r[0] - NEAR, r[1] - NEAR, r[2] + 2 * NEAR, r[3] + 2 * NEAR};
    for (int[] l : lines) {
      if (crosses(r, l) || (l[4] != own && crosses(wide, l))) {
        n++;
      }
    }
    if (r[0] < canvasMin || r[1] < canvasMin) {
      n += 5; // the export clips what lies left of or above the canvas
    }
    return n;
  }

  /** A label this far from its own line does not read as the label of that line. */
  private static final int FAR = 30;

  /**
   * How doubtful it is which connector a spot belongs to: 2 and more the farther it is from its own
   * line, 1 when another connector's line is nearer to it than its own (a label between two
   * parallel flows is read as the label of the other one), 0 otherwise.
   */
  private static int doubt(int[] r, List<int[]> lines, int own) {
    int near = Integer.MAX_VALUE;
    int foreign = Integer.MAX_VALUE;
    for (int[] l : lines) {
      int d = distance(r, l);
      if (l[4] == own) {
        near = Math.min(near, d);
      } else {
        foreign = Math.min(foreign, d);
      }
    }
    if (near == Integer.MAX_VALUE) {
      return 0;
    }
    if (near > FAR) {
      // the farther from its line, the less it reads as that line's label (never more than a
      // line through the label costs)
      return Math.min(9, 2 + (near - FAR) / 15) + (foreign < near ? 1 : 0);
    }
    return foreign < near ? 1 : 0;
  }

  /** Distance of the segment {x1, y1, x2, y2} from the rectangle, 0 when they touch. */
  private static int distance(int[] r, int[] l) {
    int dx =
        Math.max(Math.max(Math.min(l[0], l[2]) - (r[0] + r[2]), r[0] - Math.max(l[0], l[2])), 0);
    int dy =
        Math.max(Math.max(Math.min(l[1], l[3]) - (r[1] + r[3]), r[1] - Math.max(l[1], l[3])), 0);
    return (int) Math.round(Math.sqrt((double) dx * dx + (double) dy * dy));
  }

  static boolean intersects(int[] a, int[] b) {
    return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
  }

  /** Whether the segment {x1, y1, x2, y2} passes through the interior of the rectangle. */
  private static boolean crosses(int[] r, int[] l) {
    int x0 = r[0];
    int y0 = r[1];
    int x1 = r[0] + r[2];
    int y1 = r[1] + r[3];
    if (l[1] == l[3]) {
      return y0 < l[1] && l[1] < y1 && Math.min(l[0], l[2]) < x1 && Math.max(l[0], l[2]) > x0;
    }
    return x0 < l[0] && l[0] < x1 && Math.min(l[1], l[3]) < y1 && Math.max(l[1], l[3]) > y0;
  }
}
