package com.brunnen.vp.mcp.util;

/**
 * Where a horizontal or vertical connector really meets a shape. A route ends on the bounding box
 * of its shape; for a shape whose outline is not that box (an ellipse, a diamond) the last stub is
 * lengthened so that it ends on the outline itself, and the end stays on the same horizontal or
 * vertical line.
 */
public final class ShapeOutline {

  /** The outline of a shape. */
  public enum Kind {
    /** The bounding box itself. */
    RECT,
    /** The bounding box with rounded corners: ends keep clear of the curve of the corners. */
    ROUNDED,
    /** A stick figure (an actor): the whole side of its box takes ends, nearly to the corners. */
    FIGURE,
    /** The ellipse inscribed in the box (use cases, circles). */
    ELLIPSE,
    /** The diamond through the middle of the four sides (decisions, gateways). */
    DIAMOND,
    /**
     * A package: a body {@link #TAB_HEIGHT} below the top of the box, with a tab on the top left
     * that is a fifth of the width. Above the body beside the tab there is nothing to meet.
     */
    FOLDER
  }

  /** Distance from a corner of a stick figure's box that a connector end keeps. */
  public static final int FIGURE_CORNER = 4;

  /** Distance from a corner of a rounded shape that a connector end keeps. */
  public static final int ROUND_CORNER = 14;

  /** Height of the tab of a package. */
  public static final int TAB_HEIGHT = 20;

  /** Width of the tab of a package of the given width (a fifth of it). */
  public static int tabWidth(int width) {
    return width / 5;
  }

  private ShapeOutline() {}

  /**
   * Move the end of a route onto the outline of the shape.
   *
   * @param kind the outline
   * @param rect the shape's bounding box {x, y, w, h}
   * @param end the last point of the route, on the bounding box (it is modified)
   * @param next the point before it (gives the direction of the last segment)
   */
  public static void snap(Kind kind, int[] rect, int[] end, int[] next) {
    if (kind == Kind.RECT || kind == Kind.ROUNDED || kind == Kind.FIGURE) {
      return;
    }
    if (kind == Kind.FOLDER) {
      // a stub that comes down on the top of the box beside the tab ends on the body, TAB_HEIGHT
      // lower
      boolean onTop = end[0] == next[0] && end[1] == rect[1] && next[1] < end[1];
      if (onTop && end[0] > rect[0] + tabWidth(rect[2]) + 2) {
        end[1] += TAB_HEIGHT;
      }
      return;
    }
    double cx = rect[0] + rect[2] / 2.0;
    double cy = rect[1] + rect[3] / 2.0;
    double a = rect[2] / 2.0;
    double b = rect[3] / 2.0;
    boolean vertical = end[0] == next[0]; // the stub runs along y
    if (vertical) {
      double t = Math.min(1.0, Math.abs(end[0] - cx) / a);
      double depth = kind == Kind.ELLIPSE ? b * (1 - Math.sqrt(1 - t * t)) : b * t;
      // inwards: from the side the stub comes from towards the middle
      end[1] += (int) Math.round(next[1] < end[1] ? depth : -depth);
    } else {
      double t = Math.min(1.0, Math.abs(end[1] - cy) / b);
      double depth = kind == Kind.ELLIPSE ? a * (1 - Math.sqrt(1 - t * t)) : a * t;
      end[0] += (int) Math.round(next[0] < end[0] ? depth : -depth);
    }
  }
}
