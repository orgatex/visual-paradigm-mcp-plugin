package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Puts the ports of a component on its border. A port stands on the side its connectors lead away
 * to (so the line leaves straight outwards), as near as it can to the coordinate of the shape at
 * the other end (so the line is straight when it can be), never closer to another port than its
 * name needs, and on another side when its own is full. A port that several lines end on is
 * stretched along the border so that every line has an end of its own on its outer face. Pure
 * geometry: rectangles are {x, y, w, h}.
 */
public final class PortPlacer {

  /** Distance from a corner of the owner to the nearest port. */
  private static final int CORNER = 6;

  /** Height of the name of a port. */
  private static final int LABEL_HEIGHT = 14;

  /** Distance between two line ends on one face of a port. */
  private static final int END_GAP = 12;

  /** Distance of the outermost line end from the end of the face. */
  private static final int END_MARGIN = 8;

  private PortPlacer() {}

  /** A port to place. */
  public static final class Port {
    final int thickness;
    final int length;
    final int labelW;
    final double towardX;
    final double towardY;
    final boolean known;

    /**
     * A port whose connectors lead to a place.
     *
     * @param size side length of the port's square
     * @param labelW width of the port's name, which hangs outside next to the square
     * @param ends number of lines that end on the port
     * @param towardX x of the shape at the other end of its connectors
     * @param towardY y of the shape at the other end of its connectors
     */
    public Port(int size, int labelW, int ends, double towardX, double towardY) {
      this(size, labelW, ends, towardX, towardY, true);
    }

    /**
     * A port without connectors: it goes on a side with room, east first.
     *
     * @param size side length of the port's square
     * @param labelW width of the port's name
     */
    public Port(int size, int labelW) {
      this(size, labelW, 0, 0, 0, false);
    }

    private Port(int size, int labelW, int ends, double towardX, double towardY, boolean known) {
      this.thickness = size;
      // the face the lines end on is long enough for an end of its own for every line
      this.length = ends <= 1 ? size : Math.max(size, 2 * END_MARGIN + END_GAP * (ends - 1));
      this.labelW = labelW;
      this.towardX = towardX;
      this.towardY = towardY;
      this.known = known;
    }
  }

  /** Where a port went. */
  public static final class Placed {
    private final int side;
    private final int[] rect;

    Placed(int side, int[] rect) {
      this.side = side;
      this.rect = rect;
    }

    /**
     * The side of the owner: 0 north, 1 east, 2 south, 3 west.
     *
     * @return the side
     */
    public int side() {
      return side;
    }

    /**
     * The port's rectangle {x, y, w, h}: it straddles the border, longer along the border when
     * several lines end on it.
     *
     * @return the rectangle
     */
    public int[] rect() {
      return rect;
    }
  }

  /**
   * Place the ports of one owner.
   *
   * @param owner the owner's rectangle {x, y, w, h}
   * @param ports the ports in creation order
   * @return where each port went, in the order of {@code ports}
   */
  public static List<Placed> place(int[] owner, List<Port> ports) {
    int n = ports.size();
    int[] side = new int[n];
    List<List<Integer>> onSide = new ArrayList<>();
    for (int s = 0; s < 4; s++) {
      onSide.add(new ArrayList<>());
    }
    // the ports that lean most strongly to a side choose first
    Integer[] order = new Integer[n];
    double[] strength = new double[n];
    int[][] prefer = new int[n][];
    for (int i = 0; i < n; i++) {
      order[i] = i;
      prefer[i] = preference(owner, ports.get(i), i);
      strength[i] = ports.get(i).known ? lean(owner, ports.get(i)) : -1;
    }
    Arrays.sort(order, (a, b) -> Double.compare(strength[b], strength[a]));
    for (int i : order) {
      int chosen = prefer[i][0];
      for (int candidate : prefer[i]) {
        if (room(owner, onSide.get(candidate), candidate, ports, ports.get(i))) {
          chosen = candidate;
          break;
        }
      }
      side[i] = chosen;
      onSide.get(chosen).add(i);
    }
    int[][] rects = new int[n][];
    for (int s = 0; s < 4; s++) {
      List<Integer> list = onSide.get(s);
      if (!list.isEmpty()) {
        spread(owner, s, list, ports, rects);
      }
    }
    List<Placed> result = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      result.add(new Placed(side[i], rects[i]));
    }
    return result;
  }

  /** Put the ports of one side along it: near their wish, apart from each other. */
  private static void spread(
      int[] owner, int side, List<Integer> list, List<Port> ports, int[][] rects) {
    boolean vertical = side == 1 || side == 3; // the side runs along y
    int length = vertical ? owner[3] : owner[2];
    int start = vertical ? owner[1] : owner[0];
    list.sort(
        (a, b) ->
            Double.compare(
                wanted(owner, ports.get(a), vertical), wanted(owner, ports.get(b), vertical)));
    int count = list.size();
    double[] center = new double[count];
    double[] pitch = new double[count]; // distance to the next port
    double total = 0;
    for (int k = 0; k < count; k++) {
      Port p = ports.get(list.get(k));
      center[k] = wanted(owner, p, vertical);
      Port next = k + 1 < count ? ports.get(list.get(k + 1)) : p;
      pitch[k] = Math.max(pitch(side, p), (p.length + next.length) / 2.0 + 4);
      total += k + 1 < count ? pitch[k] : 0;
    }
    double lo = CORNER + ports.get(list.get(0)).length / 2.0;
    double hi = length - CORNER - ports.get(list.get(count - 1)).length / 2.0;
    if (hi - lo < total) {
      // more ports than the side holds at their wish: spread them evenly
      for (int k = 0; k < count; k++) {
        center[k] = length * (k + 1.0) / (count + 1);
      }
    } else {
      for (int k = 0; k < count; k++) {
        center[k] = Math.max(center[k], k == 0 ? lo : center[k - 1] + pitch[k - 1]);
      }
      for (int k = count - 1; k >= 0; k--) {
        center[k] = Math.min(center[k], k == count - 1 ? hi : center[k + 1] - pitch[k]);
      }
    }
    for (int k = 0; k < count; k++) {
      Port p = ports.get(list.get(k));
      int along = (int) Math.round(center[k]);
      int t = p.thickness;
      int l = p.length;
      int[] rect;
      if (side == 0) {
        rect = new int[] {start + along - l / 2, owner[1] - t / 2, l, t};
      } else if (side == 2) {
        rect = new int[] {start + along - l / 2, owner[1] + owner[3] - t / 2, l, t};
      } else if (side == 1) {
        rect = new int[] {owner[0] + owner[2] - t / 2, start + along - l / 2, t, l};
      } else {
        rect = new int[] {owner[0] - t / 2, start + along - l / 2, t, l};
      }
      rects[list.get(k)] = rect;
    }
  }

  /**
   * Distance between the centers of two ports on a side: the name of a port hangs above its square
   * (beside a vertical side) or to its right (along a horizontal side) and no line of the next port
   * may run through it.
   */
  private static int pitch(int side, Port port) {
    boolean vertical = side == 1 || side == 3;
    return vertical ? 2 * LABEL_HEIGHT + 4 : port.thickness + port.labelW + 24;
  }

  /**
   * Whether a side still has room for one more port next to those already on it: the room the ports
   * need, a pitch apart, with the margins at both corners (counted for the widest pitch, as the
   * order along the side is not known yet).
   */
  private static boolean room(
      int[] owner, List<Integer> placed, int side, List<Port> ports, Port added) {
    if (placed.isEmpty()) {
      return true;
    }
    boolean vertical = side == 1 || side == 3;
    int length = vertical ? owner[3] : owner[2];
    double widest = added.length;
    double pitch = pitch(side, added);
    for (int i : placed) {
      widest = Math.max(widest, ports.get(i).length);
      pitch = Math.max(pitch, pitch(side, ports.get(i)));
    }
    double need = 2.0 * CORNER + widest + pitch * placed.size();
    return need <= length;
  }

  /** The coordinate along the side the port's connectors lead to (the middle without any). */
  private static double wanted(int[] owner, Port port, boolean vertical) {
    if (!port.known) {
      return vertical ? owner[3] / 2.0 : owner[2] / 2.0;
    }
    return vertical ? port.towardY - owner[1] : port.towardX - owner[0];
  }

  /** How far past the owner's edge, in half-extents, the shape at the other end lies. */
  private static double lean(int[] owner, Port port) {
    double nx = (port.towardX - (owner[0] + owner[2] / 2.0)) / (owner[2] / 2.0);
    double ny = (port.towardY - (owner[1] + owner[3] / 2.0)) / (owner[3] / 2.0);
    return Math.max(Math.abs(nx), Math.abs(ny));
  }

  /** The sides a port would like, best first: where its connectors lead, the sides across, back. */
  private static int[] preference(int[] owner, Port port, int index) {
    if (!port.known) {
      int first = index % 2 == 0 ? 1 : 3;
      return new int[] {first, 4 - first, 0, 2};
    }
    double dx = port.towardX - (owner[0] + owner[2] / 2.0);
    double dy = port.towardY - (owner[1] + owner[3] / 2.0);
    double nx = dx / (owner[2] / 2.0);
    double ny = dy / (owner[3] / 2.0);
    int horizontal = nx >= 0 ? 1 : 3;
    int vertical = ny >= 0 ? 2 : 0;
    if (Math.abs(nx) >= Math.abs(ny)) {
      return new int[] {horizontal, vertical, (vertical + 2) % 4, (horizontal + 2) % 4};
    }
    return new int[] {vertical, horizontal, (horizontal + 2) % 4, (vertical + 2) % 4};
  }
}
