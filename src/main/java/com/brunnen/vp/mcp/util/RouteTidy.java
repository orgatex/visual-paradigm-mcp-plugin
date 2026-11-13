package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tidies horizontal/vertical routes without ever making a segment slanted: removes the small jogs a
 * layered router leaves at the ends of a connector (a stub, a few pixels sideways, another stub) by
 * sliding the end along its shape's side, and drops points that lie on a straight line.
 */
public final class RouteTidy {

  /** Keep ends this far from a corner of their shape. */
  private static final int CORNER_MARGIN = 8;

  private RouteTidy() {}

  /** A route to tidy: its ends' shapes and the mutable point list. */
  public static final class Item {
    final String src;
    final String tgt;
    final List<int[]> points;

    /**
     * A route.
     *
     * @param src id of the shape the first point lies on
     * @param tgt id of the shape the last point lies on
     * @param points the points, every segment horizontal or vertical
     */
    public Item(String src, String tgt, List<int[]> points) {
      this.src = src;
      this.tgt = tgt;
      this.points = points;
    }
  }

  /**
   * Remove short jogs at the ends of the routes and merge collinear points.
   *
   * @param items the routes (modified in place)
   * @param rects the rectangles {x, y, w, h} of the shapes by id; a fifth element is the distance
   *     from a corner that an end keeps (default 8)
   * @param portGap least distance between two connector ends on one side of a shape
   * @param maxJog largest sideways jump that is removed
   */
  public static void straighten(
      List<Item> items, Map<String, int[]> rects, int portGap, int maxJog) {
    for (Item item : items) {
      removeCollinear(item.points);
      removeMiddleJogs(item.points, MIDDLE_JOG);
    }
    boolean changed = true;
    int rounds = 0;
    while (changed && rounds++ < 6) {
      changed = false;
      for (Item item : items) {
        if (item.src.equals(item.tgt)) {
          continue; // a self loop keeps its rectangle
        }
        changed |= straightenStart(item, items, rects, portGap, maxJog);
        changed |= straightenEnd(item, items, rects, portGap, maxJog);
        removeCollinear(item.points);
      }
    }
  }

  /** Largest sideways step in the middle of a route that is taken out. */
  private static final int MIDDLE_JOG = 6;

  /**
   * Take out a small step in the middle of a route: a line that runs on, jumps a few pixels
   * sideways and runs on again becomes one straight line (the later part of the route moves by the
   * step; the ends of the route stay on their shapes).
   *
   * @param pts the route, modified in place
   * @param max largest step that is removed
   */
  public static void removeMiddleJogs(List<int[]> pts, int max) {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (int i = 0; i + 3 < pts.size(); i++) {
        int[] a = pts.get(i);
        int[] b = pts.get(i + 1);
        int[] c = pts.get(i + 2);
        int[] d = pts.get(i + 3);
        boolean vertical = a[0] == b[0] && c[0] == d[0] && b[1] == c[1];
        boolean horizontal = a[1] == b[1] && c[1] == d[1] && b[0] == c[0];
        if (vertical && b[0] != c[0] && Math.abs(c[0] - b[0]) <= max && i + 3 < pts.size() - 1) {
          // d is not the end of the route: its leg moves with the step
          int step = b[0] - c[0];
          c[0] += step;
          d[0] += step;
          changed = true;
          break;
        }
        if (horizontal && b[1] != c[1] && Math.abs(c[1] - b[1]) <= max && i + 3 < pts.size() - 1) {
          int step = b[1] - c[1];
          c[1] += step;
          d[1] += step;
          changed = true;
          break;
        }
        if (vertical && b[0] != c[0] && Math.abs(c[0] - b[0]) <= max && i > 0) {
          // a is not the start: the first part moves
          int step = c[0] - b[0];
          a[0] += step;
          b[0] += step;
          changed = true;
          break;
        }
        if (horizontal && b[1] != c[1] && Math.abs(c[1] - b[1]) <= max && i > 0) {
          int step = c[1] - b[1];
          a[1] += step;
          b[1] += step;
          changed = true;
          break;
        }
      }
      if (changed) {
        removeCollinear(pts);
      }
    }
  }

  /** Drop zero-length segments and middle points of straight runs. */
  public static void removeCollinear(List<int[]> pts) {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (int i = 1; i < pts.size(); i++) {
        if (pts.get(i)[0] == pts.get(i - 1)[0] && pts.get(i)[1] == pts.get(i - 1)[1]) {
          pts.remove(i);
          changed = true;
          break;
        }
      }
      for (int i = 1; i + 1 < pts.size() && !changed; i++) {
        int[] a = pts.get(i - 1);
        int[] b = pts.get(i);
        int[] c = pts.get(i + 1);
        if ((a[0] == b[0] && b[0] == c[0]) || (a[1] == b[1] && b[1] == c[1])) {
          pts.remove(i);
          changed = true;
        }
      }
    }
  }

  private static boolean straightenStart(
      Item item, List<Item> all, Map<String, int[]> rects, int portGap, int maxJog) {
    List<int[]> p = item.points;
    if (p.size() < 4) {
      return false;
    }
    return slide(item, 0, 1, 2, 3, item.src, all, rects, portGap, maxJog);
  }

  private static boolean straightenEnd(
      Item item, List<Item> all, Map<String, int[]> rects, int portGap, int maxJog) {
    List<int[]> p = item.points;
    int n = p.size();
    if (n < 4) {
      return false;
    }
    return slide(item, n - 1, n - 2, n - 3, n - 4, item.tgt, all, rects, portGap, maxJog);
  }

  /**
   * Points e (the end on the shape), s1, s2, s3: the end stub e-s1, the jog s1-s2 and the next leg
   * s2-s3. When the jog is small the end (and s1) slide to the line of s2-s3.
   */
  private static boolean slide(
      Item item,
      int e,
      int s1,
      int s2,
      int s3,
      String shapeId,
      List<Item> all,
      Map<String, int[]> rects,
      int portGap,
      int maxJog) {
    List<int[]> p = item.points;
    int[] pe = p.get(e);
    int[] p1 = p.get(s1);
    int[] p2 = p.get(s2);
    int[] p3 = p.get(s3);
    int[] rect = rects.get(shapeId);
    if (rect == null) {
      return false;
    }
    boolean stubHorizontal = pe[1] == p1[1] && pe[0] != p1[0];
    boolean stubVertical = pe[0] == p1[0] && pe[1] != p1[1];
    if (stubHorizontal) {
      // jog is vertical, the next leg horizontal again
      if (p1[0] != p2[0] || p2[1] != p3[1] || p2[1] == p1[1]) {
        return false;
      }
      int target = p2[1];
      if (Math.abs(target - p1[1]) > maxJog
          || !portFree(item, pe, target, true, rect, all, rects, portGap)) {
        return false;
      }
      pe[1] = target;
      p1[1] = target;
      return true;
    }
    if (stubVertical) {
      if (p1[1] != p2[1] || p2[0] != p3[0] || p2[0] == p1[0]) {
        return false;
      }
      int target = p2[0];
      if (Math.abs(target - p1[0]) > maxJog
          || !portFree(item, pe, target, false, rect, all, rects, portGap)) {
        return false;
      }
      pe[0] = target;
      p1[0] = target;
      return true;
    }
    return false;
  }

  /**
   * Whether the end {@code pe} may move along its side of {@code rect} to {@code target}: it stays
   * inside the side (clear of the corners) and {@code portGap} away from every other connector end
   * on that side.
   */
  private static boolean portFree(
      Item item,
      int[] pe,
      int target,
      boolean verticalSide,
      int[] rect,
      List<Item> all,
      Map<String, int[]> rects,
      int portGap) {
    int margin = rect.length > 4 ? rect[4] : CORNER_MARGIN;
    int lo = (verticalSide ? rect[1] : rect[0]) + margin;
    int hi = (verticalSide ? rect[1] + rect[3] : rect[0] + rect[2]) - margin;
    if (target < lo || target > hi) {
      return false;
    }
    for (Item other : all) {
      for (int[] end : new int[][] {first(other), last(other)}) {
        if (end == pe || end == null) {
          continue;
        }
        boolean sameSide =
            verticalSide
                ? end[0] == pe[0] && end[1] >= rect[1] && end[1] <= rect[1] + rect[3]
                : end[1] == pe[1] && end[0] >= rect[0] && end[0] <= rect[0] + rect[2];
        if (sameSide && Math.abs((verticalSide ? end[1] : end[0]) - target) < portGap) {
          return false;
        }
      }
    }
    return true;
  }

  private static int[] first(Item item) {
    return item.points.isEmpty() ? null : item.points.get(0);
  }

  private static int[] last(Item item) {
    return item.points.isEmpty() ? null : item.points.get(item.points.size() - 1);
  }

  /**
   * Copy of the route as a new list (so a caller can keep the original).
   *
   * @param route points
   * @return a deep copy
   */
  public static List<int[]> copy(List<int[]> route) {
    List<int[]> copy = new ArrayList<>();
    for (int[] p : route) {
      copy.add(new int[] {p[0], p[1]});
    }
    return copy;
  }
}
