package com.brunnen.vp.mcp.util;

import java.util.List;

/** Geometric tests on horizontal/vertical routes (points {x, y}) and rectangles {x, y, w, h}. */
public final class RouteChecks {

  private RouteChecks() {}

  /**
   * Whether the route runs through the inside of the rectangle (its outermost {@code shrink} pixels
   * do not count: an end stub touching the border is no crossing).
   *
   * @param route the route
   * @param rect the rectangle
   * @param shrink pixels of the rectangle's edge that do not count
   * @return true when a segment passes through the inside
   */
  public static boolean through(List<int[]> route, int[] rect, int shrink) {
    int x0 = rect[0] + shrink;
    int y0 = rect[1] + shrink;
    int x1 = rect[0] + rect[2] - shrink;
    int y1 = rect[1] + rect[3] - shrink;
    if (x1 <= x0 || y1 <= y0) {
      return false;
    }
    for (int i = 0; i + 1 < route.size(); i++) {
      int[] p = route.get(i);
      int[] q = route.get(i + 1);
      if (p[1] == q[1]) {
        if (y0 < p[1] && p[1] < y1 && Math.min(p[0], q[0]) < x1 && Math.max(p[0], q[0]) > x0) {
          return true;
        }
      } else if (x0 < p[0] && p[0] < x1 && Math.min(p[1], q[1]) < y1 && Math.max(p[1], q[1]) > y0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether two routes run on top of each other: parallel segments closer than {@code gap} that
   * overlap by more than a few pixels.
   *
   * @param a first route
   * @param b second route
   * @param gap least distance between parallel overlapping segments
   * @return true when the routes share a stretch of line
   */
  public static boolean shares(List<int[]> a, List<int[]> b, int gap) {
    for (int i = 0; i + 1 < a.size(); i++) {
      for (int j = 0; j + 1 < b.size(); j++) {
        int[] p = a.get(i);
        int[] q = a.get(i + 1);
        int[] s = b.get(j);
        int[] t = b.get(j + 1);
        boolean horizontal = p[1] == q[1];
        if (horizontal != (s[1] == t[1])) {
          continue;
        }
        int offset = horizontal ? Math.abs(p[1] - s[1]) : Math.abs(p[0] - s[0]);
        if (offset >= gap) {
          continue;
        }
        int lo = horizontal ? Math.min(p[0], q[0]) : Math.min(p[1], q[1]);
        int hi = horizontal ? Math.max(p[0], q[0]) : Math.max(p[1], q[1]);
        int slo = horizontal ? Math.min(s[0], t[0]) : Math.min(s[1], t[1]);
        int shi = horizontal ? Math.max(s[0], t[0]) : Math.max(s[1], t[1]);
        if (Math.min(hi, shi) - Math.max(lo, slo) > 3) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Number of places where a horizontal segment of one route crosses a vertical segment of another
   * (a crossing at an end of a segment does not count).
   *
   * @param routes the routes
   * @return the number of crossings
   */
  public static int crossings(java.util.Collection<List<int[]>> routes) {
    List<List<int[]>> list = new java.util.ArrayList<>(routes);
    int count = 0;
    for (int a = 0; a < list.size(); a++) {
      for (int b = a + 1; b < list.size(); b++) {
        count += crossings(list.get(a), list.get(b)) + crossings(list.get(b), list.get(a));
      }
    }
    return count;
  }

  /**
   * Number of crossings between two routes.
   *
   * @param a first route
   * @param b second route
   * @return the number of places where they cross
   */
  public static int crossingsBetween(List<int[]> a, List<int[]> b) {
    return crossings(a, b) + crossings(b, a);
  }

  /** Crossings of the horizontal segments of {@code h} with the vertical segments of {@code v}. */
  private static int crossings(List<int[]> h, List<int[]> v) {
    int count = 0;
    for (int i = 0; i + 1 < h.size(); i++) {
      int[] p = h.get(i);
      int[] q = h.get(i + 1);
      if (p[1] != q[1]) {
        continue;
      }
      for (int j = 0; j + 1 < v.size(); j++) {
        int[] s = v.get(j);
        int[] t = v.get(j + 1);
        if (s[0] == t[0]
            && Math.min(p[0], q[0]) < s[0]
            && s[0] < Math.max(p[0], q[0])
            && Math.min(s[1], t[1]) < p[1]
            && p[1] < Math.max(s[1], t[1])) {
          count++;
        }
      }
    }
    return count;
  }
}
