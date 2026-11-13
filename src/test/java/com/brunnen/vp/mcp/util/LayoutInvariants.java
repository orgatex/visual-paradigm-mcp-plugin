package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The measurable layout rules of the project (the same ones scratchpad/layout_check.py applies to a
 * live diagram): every segment horizontal or vertical, no overlapping siblings, no connector
 * through a shape that is not one of its ends, no two connectors on top of each other, labels clear
 * of shapes and of each other.
 */
final class LayoutInvariants {

  private LayoutInvariants() {}

  /** A node for the checks: {x, y, w, h} plus its container id and ends. */
  static final class Shape {
    final String id;
    final int[] rect;
    final String parent;

    Shape(String id, int[] rect, String parent) {
      this.id = id;
      this.rect = rect;
      this.parent = parent;
    }
  }

  /** A route for the checks. */
  static final class Route {
    final String id;
    final String src;
    final String tgt;
    final List<int[]> points;

    Route(String id, String src, String tgt, List<int[]> points) {
      this.id = id;
      this.src = src;
      this.tgt = tgt;
      this.points = points;
    }
  }

  static List<String> check(
      List<Shape> shapes, List<Route> routes, List<int[]> labels, int minParallelGap) {
    List<String> problems = new ArrayList<>();
    for (Route r : routes) {
      for (int i = 0; i + 1 < r.points.size(); i++) {
        int[] a = r.points.get(i);
        int[] b = r.points.get(i + 1);
        if (a[0] != b[0] && a[1] != b[1]) {
          problems.add(
              "SLANT " + r.id + " (" + a[0] + "," + a[1] + ")->(" + b[0] + "," + b[1] + ")");
        }
      }
    }
    for (int i = 0; i < shapes.size(); i++) {
      for (int j = i + 1; j < shapes.size(); j++) {
        Shape a = shapes.get(i);
        Shape b = shapes.get(j);
        if (java.util.Objects.equals(a.parent, b.parent) && intersects(a.rect, b.rect, 0)) {
          problems.add("OVERLAP " + a.id + " x " + b.id);
        }
      }
    }
    for (Route r : routes) {
      for (Shape s : shapes) {
        if (isContainer(s, shapes)
            || s.id.equals(r.src)
            || s.id.equals(r.tgt)
            || within(r.src, s, shapes)
            || within(r.tgt, s, shapes)) {
          continue;
        }
        for (int i = 0; i + 1 < r.points.size(); i++) {
          if (segmentHits(r.points.get(i), r.points.get(i + 1), s.rect, 2)) {
            problems.add("THROUGH " + r.id + " crosses " + s.id);
            break;
          }
        }
      }
    }
    for (int i = 0; i < routes.size(); i++) {
      for (int j = i + 1; j < routes.size(); j++) {
        if (sharesSegment(routes.get(i).points, routes.get(j).points, minParallelGap)) {
          problems.add("SHARED " + routes.get(i).id + " || " + routes.get(j).id);
        }
      }
    }
    for (int i = 0; i < labels.size(); i++) {
      for (Shape s : shapes) {
        if (!isContainer(s, shapes) && intersects(labels.get(i), s.rect, 0)) {
          problems.add("LABEL " + i + " on " + s.id);
        }
      }
      for (int j = i + 1; j < labels.size(); j++) {
        if (intersects(labels.get(i), labels.get(j), 0)) {
          problems.add("LABEL " + i + " on label " + j);
        }
      }
    }
    return problems;
  }

  static List<Shape> shapesOf(Map<String, int[]> rects, Map<String, String> parents) {
    List<Shape> shapes = new ArrayList<>();
    for (Map.Entry<String, int[]> e : rects.entrySet()) {
      shapes.add(new Shape(e.getKey(), e.getValue(), parents.get(e.getKey())));
    }
    return shapes;
  }

  private static boolean isContainer(Shape s, List<Shape> shapes) {
    for (Shape o : shapes) {
      if (s.id.equals(o.parent)) {
        return true;
      }
    }
    return false;
  }

  /** True when {@code id} is {@code container} or sits somewhere inside it. */
  private static boolean within(String id, Shape container, List<Shape> shapes) {
    String current = id;
    while (current != null) {
      if (current.equals(container.id)) {
        return true;
      }
      String next = null;
      for (Shape s : shapes) {
        if (s.id.equals(current)) {
          next = s.parent;
        }
      }
      current = next;
    }
    return false;
  }

  static boolean intersects(int[] a, int[] b, int shrink) {
    return a[0] + shrink < b[0] + b[2] - shrink
        && b[0] + shrink < a[0] + a[2] - shrink
        && a[1] + shrink < b[1] + b[3] - shrink
        && b[1] + shrink < a[1] + a[3] - shrink;
  }

  static boolean segmentHits(int[] p, int[] q, int[] r, int shrink) {
    int x0 = r[0] + shrink;
    int y0 = r[1] + shrink;
    int x1 = r[0] + r[2] - shrink;
    int y1 = r[1] + r[3] - shrink;
    if (x1 <= x0 || y1 <= y0) {
      return false;
    }
    if (p[1] == q[1]) {
      return y0 < p[1] && p[1] < y1 && Math.min(p[0], q[0]) < x1 && Math.max(p[0], q[0]) > x0;
    }
    return x0 < p[0] && p[0] < x1 && Math.min(p[1], q[1]) < y1 && Math.max(p[1], q[1]) > y0;
  }

  /**
   * Two routes run on top of each other: parallel segments closer than {@code gap} that overlap.
   */
  static boolean sharesSegment(List<int[]> a, List<int[]> b, int gap) {
    for (int i = 0; i + 1 < a.size(); i++) {
      for (int j = 0; j + 1 < b.size(); j++) {
        int[] p = a.get(i);
        int[] q = a.get(i + 1);
        int[] s = b.get(j);
        int[] t = b.get(j + 1);
        boolean ah = p[1] == q[1];
        boolean bh = s[1] == t[1];
        if (ah != bh) {
          continue;
        }
        if (ah) {
          if (Math.abs(p[1] - s[1]) < gap
              && Math.min(Math.max(p[0], q[0]), Math.max(s[0], t[0]))
                      - Math.max(Math.min(p[0], q[0]), Math.min(s[0], t[0]))
                  > 3) {
            return true;
          }
        } else if (Math.abs(p[0] - s[0]) < gap
            && Math.min(Math.max(p[1], q[1]), Math.max(s[1], t[1]))
                    - Math.max(Math.min(p[1], q[1]), Math.min(s[1], t[1]))
                > 3) {
          return true;
        }
      }
    }
    return false;
  }
}
