package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Routes connectors between shapes that already have their places: every route is one straight
 * horizontal or vertical line or an orthogonal polyline with right-angle elbows. A route keeps out
 * of every shape that is not one of its ends, leaves and enters a shape through a point on its
 * border (ends on one side are at least {@link #PORT_GAP} apart), runs at least {@link #LANE_GAP}
 * away from the routes already drawn where they are parallel, and crosses them as little as
 * possible.
 *
 * <p>The router is pure geometry (rectangles are {x, y, w, h}); {@link VpGraphLayout} feeds it.
 */
public final class OrthogonalRouter {

  /** Least distance between two connector ends on one side of a shape. */
  public static final int PORT_GAP = 12;

  /** Least distance between two parallel route segments that overlap. */
  public static final int LANE_GAP = 12;

  /** Length of the stub every route has at a shape before it may turn. */
  private static final int STUB = 16;

  /** Distance from a corner a connector end keeps. */
  private static final int CORNER = 8;

  /** Two collinear routes keep this far apart end to end. */
  private static final int END_GAP = 12;

  private static final long BEND_COST = 30;
  private static final long CROSS_COST = 70;
  private static final long CLASH_COST = 20000;
  private static final long CONTAINER_FACTOR = 3;

  private OrthogonalRouter() {}

  /** A shape the router knows. */
  public static final class Shape {
    final String id;
    final int[] rect;
    final String parent;
    final ShapeOutline.Kind outline;
    final int[] extra;

    /**
     * A shape.
     *
     * @param id unique id
     * @param rect {x, y, w, h}
     * @param parent id of the container it sits in, or null
     * @param outline its outline
     */
    public Shape(String id, int[] rect, String parent, ShapeOutline.Kind outline) {
      this(id, rect, parent, outline, null);
    }

    /**
     * A shape with something hanging outside its box (an actor's name): routes keep off that too.
     *
     * @param id unique id
     * @param rect {x, y, w, h}
     * @param parent id of the container it sits in, or null
     * @param outline its outline
     * @param extra {x, y, w, h} of what hangs outside the box, or null
     */
    public Shape(String id, int[] rect, String parent, ShapeOutline.Kind outline, int[] extra) {
      this.id = id;
      this.rect = rect;
      this.parent = parent;
      this.outline = outline;
      this.extra = extra;
    }
  }

  /** A connector to route. */
  public static final class Conn {
    final String id;
    final String src;
    final String tgt;
    int srcSide = -1;
    int tgtSide = -1;
    int srcPrefer = -1;
    int tgtPrefer = -1;

    /**
     * A connector.
     *
     * @param id unique id
     * @param src id of the shape its first point lies on
     * @param tgt id of the shape its last point lies on
     */
    public Conn(String id, String src, String tgt) {
      this.id = id;
      this.src = src;
      this.tgt = tgt;
    }

    /**
     * Restrict the sides the route may leave and enter the shapes by.
     *
     * @param srcSide side of the source shape: 0 north, 1 east, 2 south, 3 west, -1 any
     * @param tgtSide side of the target shape, same values
     * @return this
     */
    public Conn sides(int srcSide, int tgtSide) {
      this.srcSide = srcSide;
      this.tgtSide = tgtSide;
      return this;
    }

    /**
     * Prefer (not require) a side at each end: a generalization leaves the bottom of the general
     * class and enters the top of the specialization.
     *
     * @param srcPrefer side of the source shape, -1 for none
     * @param tgtPrefer side of the target shape, -1 for none
     * @return this
     */
    public Conn prefer(int srcPrefer, int tgtPrefer) {
      this.srcPrefer = srcPrefer;
      this.tgtPrefer = tgtPrefer;
      return this;
    }
  }

  /** A route that is already drawn: the router keeps its lanes and its connector ends free. */
  public static final class Drawn {
    final Conn conn;
    final List<int[]> route;

    /**
     * A drawn route.
     *
     * @param conn the connector (its shapes)
     * @param route its points
     */
    public Drawn(Conn conn, List<int[]> route) {
      this.conn = conn;
      this.route = route;
    }
  }

  /**
   * Route every connector.
   *
   * @param shapes the shapes
   * @param conns the connectors, routed shortest first
   * @return the routes by connector id, points from the source to the target
   */
  public static Map<String, List<int[]>> route(List<Shape> shapes, List<Conn> conns) {
    return route(shapes, conns, new ArrayList<Drawn>());
  }

  /**
   * Route connectors around shapes and around routes that are already drawn.
   *
   * @param shapes the shapes
   * @param conns the connectors to route, shortest first
   * @param drawn routes that stay as they are
   * @return the new routes by connector id, points from the source to the target
   */
  public static Map<String, List<int[]>> route(
      List<Shape> shapes, List<Conn> conns, List<Drawn> drawn) {
    return route(shapes, conns, drawn, LANE_GAP);
  }

  /**
   * Route connectors around shapes and around routes that are already drawn, keeping parallel
   * routes at least {@code laneGap} apart.
   *
   * @param shapes the shapes
   * @param conns the connectors to route, shortest first
   * @param drawn routes that stay as they are
   * @param laneGap least distance between two parallel routes that overlap
   * @return the new routes by connector id, points from the source to the target
   */
  public static Map<String, List<int[]>> route(
      List<Shape> shapes, List<Conn> conns, List<Drawn> drawn, int laneGap) {
    return route(shapes, conns, drawn, laneGap, CROSS_COST);
  }

  /**
   * Route connectors around shapes and around routes that are already drawn, with a price for every
   * crossing of a drawn route (a higher price makes longer detours worth their length).
   *
   * @param shapes the shapes
   * @param conns the connectors to route, shortest first
   * @param drawn routes that stay as they are
   * @param laneGap least distance between two parallel routes that overlap
   * @param crossCost cost of one crossing, in pixels of route
   * @return the new routes by connector id, points from the source to the target
   */
  public static Map<String, List<int[]>> route(
      List<Shape> shapes, List<Conn> conns, List<Drawn> drawn, int laneGap, long crossCost) {
    Context ctx = new Context(shapes, laneGap);
    ctx.crossCost = crossCost;
    for (Drawn d : drawn) {
      ctx.add(d.conn, d.route);
    }
    for (Conn c : conns) {
      ctx.batch.add(c.src + "|" + c.tgt);
    }
    List<Conn> order = new ArrayList<>(conns);
    order.sort((a, b) -> Integer.compare(ctx.distance(a.src, a.tgt), ctx.distance(b.src, b.tgt)));
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    for (Conn c : order) {
      List<int[]> r = ctx.route(c);
      if (r != null) {
        routes.put(c.id, r);
        ctx.add(c, r);
      }
    }
    Map<String, List<int[]>> ordered = new LinkedHashMap<>();
    for (Conn c : conns) {
      if (routes.containsKey(c.id)) {
        ordered.put(c.id, routes.get(c.id));
      }
    }
    return ordered;
  }

  /** The state of one routing run: the shapes and what is already drawn. */
  private static final class Context {
    final Map<String, Shape> shapes = new LinkedHashMap<>();
    final Map<String, List<String>> children = new HashMap<>();
    final List<int[]> segments = new ArrayList<>(); // {x1, y1, x2, y2}
    final List<Object[]> ports = new ArrayList<>(); // {shapeId, side, position}
    final Map<String, List<int[]>> byPair = new HashMap<>(); // "src|tgt" -> route
    final Set<String> batch = new HashSet<>(); // "src|tgt" of the connectors being routed
    boolean centered; // the first of an opposite pair: ports in the middle, room for the second
    final int lane;
    long crossCost = CROSS_COST;
    boolean sharePorts;
    boolean ignoreExtra;

    Context(List<Shape> list, int laneGap) {
      this.lane = laneGap;
      for (Shape s : list) {
        shapes.put(s.id, s);
      }
      for (Shape s : list) {
        if (s.parent != null && shapes.containsKey(s.parent)) {
          children.computeIfAbsent(s.parent, k -> new ArrayList<>()).add(s.id);
        }
      }
    }

    int distance(String a, String b) {
      Shape sa = shapes.get(a);
      Shape sb = shapes.get(b);
      if (sa == null || sb == null) {
        return 0;
      }
      return Math.abs(cx(sa.rect) - cx(sb.rect)) + Math.abs(cy(sa.rect) - cy(sb.rect));
    }

    boolean isContainer(String id) {
      return children.containsKey(id);
    }

    boolean isAncestor(String ancestor, String id) {
      String current = shapes.get(id) == null ? null : shapes.get(id).parent;
      Set<String> seen = new HashSet<>();
      while (current != null && seen.add(current)) {
        if (current.equals(ancestor)) {
          return true;
        }
        Shape s = shapes.get(current);
        current = s == null ? null : s.parent;
      }
      return false;
    }

    void add(Conn c, List<int[]> route) {
      byPair.put(c.src + "|" + c.tgt, route);
      for (int i = 0; i + 1 < route.size(); i++) {
        int[] a = route.get(i);
        int[] b = route.get(i + 1);
        segments.add(new int[] {a[0], a[1], b[0], b[1]});
      }
      Shape s = shapes.get(c.src);
      Shape t = shapes.get(c.tgt);
      int[] first = route.get(0);
      int[] last = route.get(route.size() - 1);
      if (s != null) {
        ports.add(new Object[] {c.src, sideOf(first, s.rect), position(first, s.rect)});
      }
      if (t != null) {
        ports.add(new Object[] {c.tgt, sideOf(last, t.rect), position(last, t.rect)});
      }
    }

    List<int[]> route(Conn c) {
      Shape s = shapes.get(c.src);
      Shape t = shapes.get(c.tgt);
      if (s == null || t == null) {
        return null;
      }
      if (s == t) {
        return loop(s);
      }
      if (isAncestor(s.id, t.id) || isAncestor(t.id, s.id)) {
        return nested(s, t);
      }
      // the connector back from the other shape is drawn already: this one runs beside it, the
      // same route a lane away (the two elbows nest instead of crossing)
      List<int[]> opposite = byPair.get(t.id + "|" + s.id);
      if (opposite != null && c.srcSide < 0 && c.tgtSide < 0) {
        List<int[]> beside = mirror(s, t, opposite);
        if (beside != null) {
          return beside;
        }
      }
      // the first of two opposite connectors keeps to the middle of its sides: the second runs a
      // lane to either side of it and still has ports on its sides
      centered = opposite == null && batch.contains(t.id + "|" + s.id);
      List<int[]> r;
      try {
        r = search(c, s, t, true, STUB - 2);
      } finally {
        centered = false;
      }
      if (r == null) {
        r = search(c, s, t, false, STUB - 2);
      }
      if (r == null) {
        r = search(c, s, t, false, 3);
      }
      if (r == null) {
        // every port of a small shape is in use: a shared end beats a line through a shape
        sharePorts = true;
        r = search(c, s, t, false, STUB - 2);
        if (r == null) {
          r = search(c, s, t, false, 3);
        }
        sharePorts = false;
      }
      if (r == null) {
        // a name hanging outside a shape is a lesser evil than a line through a shape
        ignoreExtra = true;
        r = search(c, s, t, false, STUB - 2);
        if (r == null) {
          r = search(c, s, t, false, 3);
        }
        ignoreExtra = false;
      }
      return r != null ? r : direct(s, t);
    }

    /**
     * The route from {@code s} to {@code t} that runs a lane beside the route {@code opposite}
     * (drawn from {@code t} to {@code s}), on the side with the lower cost, or null when neither
     * side is free (ends off their sides, a shape in the way).
     */
    List<int[]> mirror(Shape s, Shape t, List<int[]> opposite) {
      List<int[]> base = new ArrayList<>();
      for (int i = opposite.size() - 1; i >= 0; i--) {
        base.add(new int[] {opposite.get(i)[0], opposite.get(i)[1]});
      }
      List<int[]> best = null;
      long bestCost = Long.MAX_VALUE;
      for (int sign : new int[] {1, -1}) {
        List<int[]> r = offset(base, sign * Math.max(lane, PORT_GAP));
        if (r == null || !endOnSide(r.get(0), s) || !endOnSide(r.get(r.size() - 1), t)) {
          continue;
        }
        long cost = 0;
        boolean clear = true;
        List<int[]> hard = new ArrayList<>();
        for (Shape x : shapes.values()) {
          if (x == s
              || x == t
              || isContainer(x.id)
              || isAncestor(x.id, s.id)
              || isAncestor(x.id, t.id)) {
            continue;
          }
          hard.add(inflate(x.rect, 2));
          if (x.extra != null) {
            hard.add(inflate(x.extra, 2));
          }
        }
        for (int i = 0; i + 1 < r.size(); i++) {
          int[] a = r.get(i);
          int[] b = r.get(i + 1);
          if (blocked(a[0], a[1], b[0], b[1], hard)) {
            clear = false;
            break;
          }
          cost += conflict(a[0], a[1], b[0], b[1]);
        }
        if (clear && cost < bestCost) {
          bestCost = cost;
          best = r;
        }
      }
      // a route that clashes with something is left to the search
      return bestCost >= CLASH_COST ? null : best;
    }

    /**
     * The route shifted sideways by {@code delta}, every segment by the same side; null if it
     * folds.
     */
    List<int[]> offset(List<int[]> route, int delta) {
      int n = route.size();
      if (n < 2) {
        return null;
      }
      int[][] shift = new int[n - 1][];
      for (int i = 0; i + 1 < n; i++) {
        int[] a = route.get(i);
        int[] b = route.get(i + 1);
        int sx = Integer.signum(b[0] - a[0]);
        int sy = Integer.signum(b[1] - a[1]);
        if ((sx == 0) == (sy == 0)) {
          return null; // a point or a slanted segment
        }
        shift[i] = new int[] {-sy * delta, sx * delta};
      }
      List<int[]> result = new ArrayList<>();
      result.add(new int[] {route.get(0)[0] + shift[0][0], route.get(0)[1] + shift[0][1]});
      for (int k = 1; k + 1 < n; k++) {
        int[] before = route.get(k - 1);
        int[] here = route.get(k);
        boolean beforeHorizontal = before[1] == here[1];
        int[] sb = shift[k - 1];
        int[] sa = shift[k];
        result.add(
            beforeHorizontal
                ? new int[] {here[0] + sa[0], here[1] + sb[1]}
                : new int[] {here[0] + sb[0], here[1] + sa[1]});
      }
      int[] last = route.get(n - 1);
      result.add(new int[] {last[0] + shift[n - 2][0], last[1] + shift[n - 2][1]});
      for (int i = 0; i + 1 < n; i++) {
        int[] a = route.get(i);
        int[] b = route.get(i + 1);
        int[] c = result.get(i);
        int[] d = result.get(i + 1);
        if (Integer.signum(b[0] - a[0]) != Integer.signum(d[0] - c[0])
            || Integer.signum(b[1] - a[1]) != Integer.signum(d[1] - c[1])) {
          return null; // a short segment folded over
        }
      }
      return result;
    }

    /** Whether an end lies on a free port position of its shape's side (clear of the corners). */
    boolean endOnSide(int[] p, Shape shape) {
      int[] r = shape.rect;
      int side = sideOf(p, r);
      boolean horizontalSide = side == 0 || side == 2;
      int length = horizontalSide ? r[2] : r[3];
      int offset = horizontalSide ? p[0] - r[0] : p[1] - r[1];
      double inner =
          shape.outline == ShapeOutline.Kind.ELLIPSE || shape.outline == ShapeOutline.Kind.DIAMOND
              ? 0.25
              : 0;
      int corner = cornerOf(shape, length);
      int lo = (int) Math.round(length * inner) + corner;
      int hi = length - (int) Math.round(length * inner) - corner;
      if (shape.outline == ShapeOutline.Kind.FOLDER && !horizontalSide) {
        lo = Math.max(lo, ShapeOutline.TAB_HEIGHT + CORNER);
      }
      return offset >= lo && offset <= hi && !taken(shape.id, side, horizontalSide ? p[0] : p[1]);
    }

    /** Last resort when nothing fits between the shapes: an L from the facing sides. */
    List<int[]> direct(Shape s, Shape t) {
      int[] a = s.rect;
      int[] b = t.rect;
      List<int[]> route = new ArrayList<>();
      int ax = cx(a);
      int ay = cy(a);
      int bx = cx(b);
      int by = cy(b);
      boolean horizontalFirst = Math.abs(bx - ax) >= Math.abs(by - ay);
      if (horizontalFirst) {
        route.add(new int[] {bx > ax ? a[0] + a[2] : a[0], ay});
        route.add(new int[] {bx, ay});
        route.add(new int[] {bx, by > ay ? b[1] : b[1] + b[3]});
      } else {
        route.add(new int[] {ax, by > ay ? a[1] + a[3] : a[1]});
        route.add(new int[] {ax, by});
        route.add(new int[] {bx > ax ? b[0] : b[0] + b[2], by});
      }
      RouteTidy.removeCollinear(route);
      return route;
    }

    // ---- ports

    /** Free port positions along a side, best first. */
    List<int[]> portsOf(Shape shape, int[] other, int onlySide, int prefer) {
      int[] r = shape.rect;
      List<int[]> result = new ArrayList<>(); // {x, y, side, cost}
      double inner =
          shape.outline == ShapeOutline.Kind.ELLIPSE || shape.outline == ShapeOutline.Kind.DIAMOND
              ? 0.25
              : 0;
      for (int side = 0; side < 4; side++) {
        if (onlySide >= 0 && side != onlySide) {
          continue;
        }
        boolean horizontalSide = side == 0 || side == 2; // N, S
        int length = horizontalSide ? r[2] : r[3];
        int corner = cornerOf(shape, length);
        int lo = (int) Math.round(length * inner) + corner;
        int hi = length - (int) Math.round(length * inner) - corner;
        if (shape.outline == ShapeOutline.Kind.FOLDER && !horizontalSide) {
          lo = Math.max(lo, ShapeOutline.TAB_HEIGHT + CORNER); // nothing east of the tab
        }
        if (hi < lo) {
          lo = hi = length / 2;
        }
        Set<Integer> offsets = new TreeSet<>();
        offsets.add(length / 2);
        for (int o = lo; o <= hi; o += PORT_GAP) {
          offsets.add(o);
        }
        // line up with the other shape: its centre and its own grid, so a straight line exists
        if (other != null) {
          int base = horizontalSide ? r[0] : r[1];
          int oc = horizontalSide ? cx(other) : cy(other);
          int oStart = horizontalSide ? other[0] : other[1];
          int oLen = horizontalSide ? other[2] : other[3];
          offsets.add(oc - base);
          for (int o = PORT_GAP; o < oLen; o += PORT_GAP) {
            offsets.add(oStart + o - base);
          }
          // the middle of the overlap of the two extents
          int overlapLo = Math.max(base, oStart);
          int overlapHi = Math.min(base + length, oStart + oLen);
          if (overlapHi > overlapLo) {
            offsets.add((overlapLo + overlapHi) / 2 - base);
          }
        }
        for (int o : offsets) {
          if (o < lo || o > hi || (centered && Math.abs(o - length / 2) > PORT_GAP / 2)) {
            continue;
          }
          int x;
          int y;
          switch (side) {
            case 0:
              x = r[0] + o;
              y = r[1];
              break;
            case 2:
              x = r[0] + o;
              y = r[1] + r[3];
              break;
            case 1:
              x = r[0] + r[2];
              y = r[1] + o;
              break;
            default:
              x = r[0];
              y = r[1] + o;
              break;
          }
          if (taken(shape.id, side, horizontalSide ? x : y)) {
            continue;
          }
          int cost = Math.abs(o - length / 2) / 8;
          if (prefer >= 0) {
            cost += side == prefer ? 0 : 60;
          } else if (other != null && !facing(side, r, other)) {
            cost += 40;
          }
          result.add(new int[] {x, y, side, cost});
        }
      }
      return result;
    }

    boolean taken(String shapeId, int side, int position) {
      if (sharePorts) {
        return false;
      }
      for (Object[] p : ports) {
        if (p[0].equals(shapeId)
            && (Integer) p[1] == side
            && Math.abs((Integer) p[2] - (position)) < PORT_GAP) {
          return true;
        }
      }
      return false;
    }

    // ---- search

    List<int[]> search(Conn c, Shape s, Shape t, boolean softContainers, int inflation) {
      List<int[]> starts = portsOf(s, t.rect, c.srcSide, c.srcPrefer);
      List<int[]> goals = portsOf(t, s.rect, c.tgtSide, c.tgtPrefer);
      if (starts.isEmpty() || goals.isEmpty()) {
        return null;
      }
      List<int[]> hard = new ArrayList<>();
      List<int[]> soft = new ArrayList<>();
      for (Shape x : shapes.values()) {
        if (x == s
            || x == t
            || isAncestor(x.id, s.id)
            || isAncestor(x.id, t.id)
            || isAncestor(s.id, x.id)
            || isAncestor(t.id, x.id)) {
          continue;
        }
        if (isContainer(x.id)) {
          if (softContainers) {
            soft.add(x.rect);
          } else {
            continue;
          }
        } else {
          hard.add(inflate(x.rect, inflation));
          if (x.extra != null && !ignoreExtra) {
            hard.add(inflate(x.extra, inflation));
          }
        }
      }
      hard.add(s.rect);
      hard.add(t.rect);
      if (s.extra != null && !ignoreExtra) {
        hard.add(s.extra);
      }
      if (t.extra != null && !ignoreExtra) {
        hard.add(t.extra);
      }
      // grid lines
      TreeSet<Integer> xs = new TreeSet<>();
      TreeSet<Integer> ys = new TreeSet<>();
      int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
      for (Shape x : shapes.values()) {
        int[] r = x.rect;
        bounds[0] = Math.min(bounds[0], r[0]);
        bounds[1] = Math.min(bounds[1], r[1]);
        bounds[2] = Math.max(bounds[2], r[0] + r[2]);
        bounds[3] = Math.max(bounds[3], r[1] + r[3]);
        for (int m : new int[] {0, 3, STUB - 2, STUB}) {
          xs.add(r[0] - m);
          xs.add(r[0] + r[2] + m);
          ys.add(r[1] - m);
          ys.add(r[1] + r[3] + m);
        }
        xs.add(r[0]);
        xs.add(r[0] + r[2]);
        ys.add(r[1]);
        ys.add(r[1] + r[3]);
        if (x.extra != null) {
          // what hangs outside the shape (a name) has lines beside it as the shape has
          for (int m : new int[] {0, 3, STUB - 2, STUB}) {
            xs.add(x.extra[0] - m);
            xs.add(x.extra[0] + x.extra[2] + m);
            ys.add(x.extra[1] - m);
            ys.add(x.extra[1] + x.extra[3] + m);
          }
        }
      }
      xs.add(Math.max(bounds[0] - 40, 5));
      xs.add(bounds[2] + 40);
      ys.add(Math.max(bounds[1] - 40, 5));
      ys.add(bounds[3] + 40);
      for (int[] st : starts) {
        addStub(st, xs, ys);
      }
      for (int[] g : goals) {
        addStub(g, xs, ys);
      }
      // channels between neighbouring shapes: the middle of every gap
      addGapMiddles(xs, true);
      addGapMiddles(ys, false);
      // lanes beside the routes drawn already (near the two shapes only)
      int[] window =
          inflate(
              new int[] {
                Math.min(s.rect[0], t.rect[0]),
                Math.min(s.rect[1], t.rect[1]),
                Math.max(s.rect[0] + s.rect[2], t.rect[0] + t.rect[2])
                    - Math.min(s.rect[0], t.rect[0]),
                Math.max(s.rect[1] + s.rect[3], t.rect[1] + t.rect[3])
                    - Math.min(s.rect[1], t.rect[1])
              },
              160);
      for (int[] seg : segments) {
        if (Math.max(seg[0], seg[2]) < window[0]
            || Math.min(seg[0], seg[2]) > window[0] + window[2]
            || Math.max(seg[1], seg[3]) < window[1]
            || Math.min(seg[1], seg[3]) > window[1] + window[3]) {
          continue;
        }
        for (int d : new int[] {-lane, lane}) {
          xs.add(seg[0] + d);
          xs.add(seg[2] + d);
          ys.add(seg[1] + d);
          ys.add(seg[3] + d);
        }
      }
      int[] xa = toArray(xs);
      int[] ya = toArray(ys);
      return dijkstra(s, t, starts, goals, xa, ya, hard, soft);
    }

    void addStub(int[] port, TreeSet<Integer> xs, TreeSet<Integer> ys) {
      int[] end = stubEnd(port);
      xs.add(end[0]);
      ys.add(end[1]);
      xs.add(port[0]);
      ys.add(port[1]);
    }

    int[] stubEnd(int[] port) {
      switch (port[2]) {
        case 0:
          return new int[] {port[0], port[1] - STUB};
        case 2:
          return new int[] {port[0], port[1] + STUB};
        case 1:
          return new int[] {port[0] + STUB, port[1]};
        default:
          return new int[] {port[0] - STUB, port[1]};
      }
    }

    void addGapMiddles(TreeSet<Integer> lines, boolean xAxis) {
      List<int[]> spans = new ArrayList<>();
      for (Shape x : shapes.values()) {
        int[] r = x.rect;
        spans.add(xAxis ? new int[] {r[0], r[0] + r[2]} : new int[] {r[1], r[1] + r[3]});
      }
      spans.sort((a, b) -> Integer.compare(a[0], b[0]));
      int reach = Integer.MIN_VALUE;
      for (int[] span : spans) {
        if (reach != Integer.MIN_VALUE && span[0] - reach > 2 * STUB) {
          lines.add((reach + span[0]) / 2);
        }
        reach = Math.max(reach, span[1]);
      }
    }

    List<int[]> dijkstra(
        Shape s,
        Shape t,
        List<int[]> starts,
        List<int[]> goals,
        int[] xa,
        int[] ya,
        List<int[]> hard,
        List<int[]> soft) {
      int nx = xa.length;
      int ny = ya.length;
      long[] dist = new long[nx * ny * 2];
      Arrays.fill(dist, Long.MAX_VALUE);
      int[] prev = new int[nx * ny * 2];
      Arrays.fill(prev, -1);
      PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
      Map<Integer, int[]> goalAt = new HashMap<>(); // node index -> goal port
      for (int[] g : goals) {
        int[] e = stubEnd(g);
        int node = index(xa, e[0]) * ny + index(ya, e[1]);
        int[] best = goalAt.get(node);
        if (best == null || g[3] < best[3]) {
          goalAt.put(node, g);
        }
      }
      Map<Integer, int[]> startAt = new HashMap<>();
      for (int[] st : starts) {
        int[] e = stubEnd(st);
        int ix = index(xa, e[0]);
        int iy = index(ya, e[1]);
        int dir = st[2] == 0 || st[2] == 2 ? 1 : 0; // arrived vertically for N/S ports
        int state = (ix * ny + iy) * 2 + dir;
        long cost = STUB + st[3];
        int node = ix * ny + iy;
        int[] best = startAt.get(node * 2 + dir);
        if (best == null || st[3] < best[3]) {
          startAt.put(node * 2 + dir, st);
        }
        if (cost < dist[state] && startAt.get(node * 2 + dir) == st) {
          dist[state] = cost;
          queue.add(new long[] {cost, state});
        }
      }
      int found = -1;
      long bestTotal = Long.MAX_VALUE;
      while (!queue.isEmpty()) {
        long[] top = queue.poll();
        if (top[0] >= bestTotal) {
          break;
        }
        int state = (int) top[1];
        if (top[0] > dist[state]) {
          continue;
        }
        int node = state / 2;
        int dir = state % 2;
        int ix = node / ny;
        int iy = node % ny;
        int[] goal = goalAt.get(node);
        if (goal != null) {
          // the last stub joins the target port along its normal: a turn costs a bend
          boolean normalVertical = goal[2] == 0 || goal[2] == 2;
          long total = top[0] + goal[3] + STUB + (normalVertical == (dir == 1) ? 0 : BEND_COST);
          if (total < bestTotal) {
            bestTotal = total;
            found = state;
          }
        }
        // horizontal and vertical neighbours
        for (int step = 0; step < 4; step++) {
          int jx = ix + (step == 0 ? -1 : step == 1 ? 1 : 0);
          int jy = iy + (step == 2 ? -1 : step == 3 ? 1 : 0);
          if (jx < 0 || jx >= nx || jy < 0 || jy >= ny) {
            continue;
          }
          int nd = step < 2 ? 0 : 1;
          int x1 = xa[ix];
          int y1 = ya[iy];
          int x2 = xa[jx];
          int y2 = ya[jy];
          if (blocked(x1, y1, x2, y2, hard)) {
            continue;
          }
          long cost = Math.abs(x2 - x1) + Math.abs(y2 - y1);
          if (nd != dir) {
            cost += BEND_COST;
          }
          cost += conflict(x1, y1, x2, y2);
          if (inside(x1, y1, x2, y2, soft)) {
            cost *= CONTAINER_FACTOR;
          }
          int next = (jx * ny + jy) * 2 + nd;
          long total = top[0] + cost;
          if (total < dist[next]) {
            dist[next] = total;
            prev[next] = state;
            queue.add(new long[] {total, next});
          }
        }
      }
      if (found < 0) {
        return null;
      }
      // reconstruct
      List<int[]> pts = new ArrayList<>();
      int state = found;
      int[] goal = goalAt.get(state / 2);
      pts.add(new int[] {goal[0], goal[1]});
      int[] startPort = null;
      while (state >= 0) {
        int node = state / 2;
        pts.add(new int[] {xa[node / ny], ya[node % ny]});
        if (prev[state] < 0) {
          startPort = startAt.get(state);
        }
        state = prev[state];
      }
      pts.add(new int[] {startPort[0], startPort[1]});
      Collections.reverse(pts);
      RouteTidy.removeCollinear(pts);
      return pts;
    }

    /** Whether a unit of the grid runs through the interior of a hard obstacle. */
    boolean blocked(int x1, int y1, int x2, int y2, List<int[]> hard) {
      for (int[] r : hard) {
        int rx0 = r[0];
        int ry0 = r[1];
        int rx1 = r[0] + r[2];
        int ry1 = r[1] + r[3];
        if (y1 == y2) {
          if (ry0 < y1 && y1 < ry1 && Math.min(x1, x2) < rx1 && Math.max(x1, x2) > rx0) {
            return true;
          }
        } else if (rx0 < x1 && x1 < rx1 && Math.min(y1, y2) < ry1 && Math.max(y1, y2) > ry0) {
          return true;
        }
      }
      return false;
    }

    boolean inside(int x1, int y1, int x2, int y2, List<int[]> soft) {
      int mx = (x1 + x2) / 2;
      int my = (y1 + y2) / 2;
      for (int[] r : soft) {
        if (r[0] < mx && mx < r[0] + r[2] && r[1] < my && my < r[1] + r[3]) {
          return true;
        }
      }
      return false;
    }

    /** Cost of a unit against the routes already drawn: crossings and parallel clashes. */
    long conflict(int x1, int y1, int x2, int y2) {
      long cost = 0;
      boolean horizontal = y1 == y2;
      for (int[] seg : segments) {
        boolean segHorizontal = seg[1] == seg[3];
        if (horizontal == segHorizontal) {
          int off = horizontal ? Math.abs(y1 - seg[1]) : Math.abs(x1 - seg[0]);
          if (off < lane) {
            int lo = horizontal ? Math.min(x1, x2) : Math.min(y1, y2);
            int hi = horizontal ? Math.max(x1, x2) : Math.max(y1, y2);
            int slo = horizontal ? Math.min(seg[0], seg[2]) : Math.min(seg[1], seg[3]);
            int shi = horizontal ? Math.max(seg[0], seg[2]) : Math.max(seg[1], seg[3]);
            // two lines on one line that stop a few pixels short of each other read as one line
            int pad = off < 3 ? END_GAP : 0;
            int overlap = Math.min(hi, shi + pad) - Math.max(lo, slo - pad);
            if (overlap > 0) {
              cost += CLASH_COST * Math.min(overlap, 50) / 50 + CLASH_COST / 10;
            }
          }
        } else if (horizontal) {
          if (Math.min(x1, x2) < seg[0]
              && seg[0] < Math.max(x1, x2)
              && Math.min(seg[1], seg[3]) < y1
              && y1 < Math.max(seg[1], seg[3])) {
            cost += crossCost;
          }
        } else if (Math.min(y1, y2) < seg[1]
            && seg[1] < Math.max(y1, y2)
            && Math.min(seg[0], seg[2]) < x1
            && x1 < Math.max(seg[0], seg[2])) {
          cost += crossCost;
        }
      }
      return cost;
    }

    // ---- loops and nested ends

    List<int[]> loop(Shape s) {
      int[] r = s.rect;
      int extent = 26;
      List<int[]> best = null;
      long bestCost = Long.MAX_VALUE;
      for (int sd : new int[] {1, 2, 3, 0}) { // east, south, west, north
        List<int[]> route = loopOn(r, sd, extent, s.id);
        if (route == null) {
          continue;
        }
        long cost = 0;
        for (int i = 0; i + 1 < route.size(); i++) {
          int[] a = route.get(i);
          int[] b = route.get(i + 1);
          cost += conflict(a[0], a[1], b[0], b[1]);
          for (Shape x : shapes.values()) {
            if (x != s
                && !isContainer(x.id)
                && blocked(a[0], a[1], b[0], b[1], Collections.singletonList(x.rect))) {
              cost += CLASH_COST;
            }
          }
        }
        if (cost < bestCost) {
          bestCost = cost;
          best = route;
        }
        if (cost == 0) {
          break;
        }
      }
      return best;
    }

    /** A rectangular loop on one side: out from one port, along, back at a second port. */
    List<int[]> loopOn(int[] r, int side, int extent, String id) {
      boolean horizontalSide = side == 0 || side == 2;
      int length = horizontalSide ? r[2] : r[3];
      int gap = Math.max(PORT_GAP + 4, 16);
      int a = length / 2 - gap / 2;
      int b = a + gap;
      // keep clear of ports that are in use
      for (int guard = 0; guard < 8; guard++) {
        boolean clash = false;
        for (int o : new int[] {a, b}) {
          if (taken(id, side, o + (horizontalSide ? r[0] : r[1]))) {
            clash = true;
          }
        }
        if (!clash) {
          break;
        }
        a += gap;
        b += gap;
      }
      if (b > length - CORNER) {
        a = Math.max(CORNER, length / 4);
        b = Math.min(length - CORNER, a + gap);
      }
      List<int[]> route = new ArrayList<>();
      switch (side) {
        case 1: // east
          route.add(new int[] {r[0] + r[2], r[1] + a});
          route.add(new int[] {r[0] + r[2] + extent, r[1] + a});
          route.add(new int[] {r[0] + r[2] + extent, r[1] + b});
          route.add(new int[] {r[0] + r[2], r[1] + b});
          break;
        case 3: // west
          route.add(new int[] {r[0], r[1] + a});
          route.add(new int[] {r[0] - extent, r[1] + a});
          route.add(new int[] {r[0] - extent, r[1] + b});
          route.add(new int[] {r[0], r[1] + b});
          break;
        case 2: // south
          route.add(new int[] {r[0] + a, r[1] + r[3]});
          route.add(new int[] {r[0] + a, r[1] + r[3] + extent});
          route.add(new int[] {r[0] + b, r[1] + r[3] + extent});
          route.add(new int[] {r[0] + b, r[1] + r[3]});
          break;
        default: // north
          route.add(new int[] {r[0] + a, r[1]});
          route.add(new int[] {r[0] + a, r[1] - extent});
          route.add(new int[] {r[0] + b, r[1] - extent});
          route.add(new int[] {r[0] + b, r[1]});
          break;
      }
      return route;
    }

    /** A connector between a container and a shape inside it: straight out to the border. */
    List<int[]> nested(Shape s, Shape t) {
      boolean srcOuter = isAncestor(s.id, t.id);
      Shape outer = srcOuter ? s : t;
      Shape inner = srcOuter ? t : s;
      int[] o = outer.rect;
      int[] i = inner.rect;
      int left = i[0] - o[0];
      int right = o[0] + o[2] - (i[0] + i[2]);
      int top = i[1] - o[1];
      int bottom = o[1] + o[3] - (i[1] + i[3]);
      int min = Math.min(Math.min(left, right), Math.min(top, bottom));
      List<int[]> route = new ArrayList<>();
      if (min == left) {
        int y = i[1] + i[3] / 2;
        route.add(new int[] {i[0], y});
        route.add(new int[] {o[0], y});
      } else if (min == right) {
        int y = i[1] + i[3] / 2;
        route.add(new int[] {i[0] + i[2], y});
        route.add(new int[] {o[0] + o[2], y});
      } else if (min == top) {
        int x = i[0] + i[2] / 2;
        route.add(new int[] {x, i[1]});
        route.add(new int[] {x, o[1]});
      } else {
        int x = i[0] + i[2] / 2;
        route.add(new int[] {x, i[1] + i[3]});
        route.add(new int[] {x, o[1] + o[3]});
      }
      if (srcOuter) {
        Collections.reverse(route);
      }
      return route;
    }
  }

  // ---- helpers

  /**
   * How far from a corner of a side a connector end keeps: the curve of a rounded shape, and an
   * eighth of the side (at most 20) on a large box, so a line does not look attached to its corner.
   */
  private static int cornerOf(Shape shape, int sideLength) {
    if (shape.outline == ShapeOutline.Kind.ROUNDED) {
      return ShapeOutline.ROUND_CORNER;
    }
    if (shape.outline == ShapeOutline.Kind.FIGURE) {
      return ShapeOutline.FIGURE_CORNER; // five ends fit the 60 px side of a figure
    }
    return Math.max(CORNER, Math.min(20, sideLength / 8));
  }

  private static int cx(int[] r) {
    return r[0] + r[2] / 2;
  }

  private static int cy(int[] r) {
    return r[1] + r[3] / 2;
  }

  private static int[] inflate(int[] r, int m) {
    return new int[] {r[0] - m, r[1] - m, r[2] + 2 * m, r[3] + 2 * m};
  }

  private static int[] toArray(Set<Integer> set) {
    int[] a = new int[set.size()];
    int i = 0;
    for (int v : set) {
      a[i++] = v;
    }
    return a;
  }

  private static int index(int[] sorted, int value) {
    int i = Arrays.binarySearch(sorted, value);
    if (i < 0) {
      throw new IllegalStateException("grid line missing: " + value);
    }
    return i;
  }

  /** Side of a rectangle a point lies on: 0 north, 1 east, 2 south, 3 west. */
  private static int sideOf(int[] p, int[] r) {
    if (p[1] == r[1]) {
      return 0;
    }
    if (p[0] == r[0] + r[2]) {
      return 1;
    }
    if (p[1] == r[1] + r[3]) {
      return 2;
    }
    return 3;
  }

  private static int position(int[] p, int[] r) {
    int side = sideOf(p, r);
    return side == 0 || side == 2 ? p[0] : p[1];
  }

  /** Whether a side of {@code r} looks towards the shape {@code other} (along the main axis). */
  private static boolean facing(int side, int[] r, int[] other) {
    int dx = cx(other) - cx(r);
    int dy = cy(other) - cy(r);
    boolean horizontal = Math.abs(dx) >= Math.abs(dy);
    switch (side) {
      case 0:
        return !horizontal && dy < 0;
      case 2:
        return !horizontal && dy > 0;
      case 1:
        return horizontal && dx > 0;
      default:
        return horizontal && dx < 0;
    }
  }
}
