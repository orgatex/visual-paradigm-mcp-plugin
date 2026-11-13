package com.brunnen.vp.mcp.util;

import java.util.Locale;

/**
 * Pure helpers for UML activity diagrams: control node kind parsing, guard and object node label
 * formatting, flow validation, default node sizes and the swimlane/grid placement arithmetic.
 */
public final class ActivityDiagramUtils {

  /** Initial node (filled circle). */
  public static final String KIND_INITIAL = "initial";

  /** Activity final node (bullseye). */
  public static final String KIND_FINAL = "final";

  /** Flow final node (circled X). */
  public static final String KIND_FLOW_FINAL = "flowFinal";

  /** Decision node (diamond with one incoming flow). */
  public static final String KIND_DECISION = "decision";

  /** Merge node (diamond with one outgoing flow). */
  public static final String KIND_MERGE = "merge";

  /** Fork node (bar splitting into concurrent flows). */
  public static final String KIND_FORK = "fork";

  /** Join node (bar synchronising concurrent flows). */
  public static final String KIND_JOIN = "join";

  /** Action node (rounded box). */
  public static final String KIND_ACTION = "action";

  /** Object node (rectangle). */
  public static final String KIND_OBJECT = "object";

  /** The control node kinds accepted by {@link #parseControlNodeKind(String)}, for messages. */
  public static final String CONTROL_NODE_KINDS =
      "initial, final, flowFinal, decision, merge, fork, join";

  /** Height of a partition header (the lane title bar). */
  public static final int HEADER_HEIGHT = 24;

  /** Lane width used when the caller does not give one. */
  public static final int DEFAULT_LANE_WIDTH = 240;

  /** Lane body height used when the caller does not give one. */
  public static final int DEFAULT_LANE_HEIGHT = 480;

  /** Free space kept between a lane border and the nodes inside it. */
  public static final int LANE_PADDING = 20;

  /** Vertical gap between two consecutive rows of auto-placed nodes. */
  public static final int ROW_GAP = 40;

  /** Top-left corner of the swimlane frame when the caller does not place it. */
  public static final int SWIMLANE_X = 40;

  public static final int SWIMLANE_Y = 40;

  /** Horizontal fork/join bar size. */
  private static final int BAR_WIDTH = 120;

  private static final int BAR_HEIGHT = 10;

  /** Rough width of one character of the default diagram font. */
  private static final int CHAR_WIDTH = 8;

  private ActivityDiagramUtils() {}

  /**
   * Check whether a name can be used for a node or partition. Names are the handles the flow tool
   * looks nodes up by, so they must not be blank.
   *
   * @param name the name
   * @return true when the name is usable
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Parse a control node kind. Case, spaces, '-' and '_' are ignored and a few common synonyms are
   * accepted (start/end, activityFinal, branch, ...).
   *
   * @param kind the raw kind
   * @return one of the {@code KIND_*} control node constants, or null when unknown
   */
  public static String parseControlNodeKind(String kind) {
    if (kind == null) {
      return null;
    }
    String key = kind.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    switch (key) {
      case "initial":
      case "initialnode":
      case "start":
        return KIND_INITIAL;
      case "final":
      case "finalnode":
      case "activityfinal":
      case "activityfinalnode":
      case "end":
        return KIND_FINAL;
      case "flowfinal":
      case "flowfinalnode":
        return KIND_FLOW_FINAL;
      case "decision":
      case "decisionnode":
      case "branch":
        return KIND_DECISION;
      case "merge":
      case "mergenode":
        return KIND_MERGE;
      case "fork":
      case "forknode":
        return KIND_FORK;
      case "join":
      case "joinnode":
        return KIND_JOIN;
      default:
        return null;
    }
  }

  /**
   * Whether the kind is drawn as a fork/join bar.
   *
   * @param kind a {@code KIND_*} constant
   * @return true for fork and join
   */
  public static boolean isBar(String kind) {
    return KIND_FORK.equals(kind) || KIND_JOIN.equals(kind);
  }

  /**
   * Whether the node's name is drawn on the diagram: a decision's question, and the "Bat dau" /
   * "Ket thuc" of initial and final nodes, as in the course book. Merge, fork and join nodes are
   * unlabelled in UML; their name only serves as the handle for flows.
   *
   * @param kind a {@code KIND_*} constant
   * @return true when the caption should stay visible
   */
  public static boolean showsLabel(String kind) {
    return KIND_DECISION.equals(kind)
        || KIND_INITIAL.equals(kind)
        || KIND_FINAL.equals(kind)
        || KIND_FLOW_FINAL.equals(kind);
  }

  /**
   * Whether a node name is only a number marker ("#1"): such a node shows no text (a decision or
   * merge diamond without a question), the number only keeps the handle unique.
   *
   * @param name the node's name
   * @return true when the name is nothing but "#n"
   */
  public static boolean hiddenLabel(String name) {
    return name != null && name.trim().matches("#\\d+");
  }

  /**
   * Default size of a control node shape. Fork and join are horizontal bars, decision and merge
   * square diamonds, the initial/final nodes small circles.
   *
   * @param kind a {@code KIND_*} control node constant
   * @return {width, height}
   */
  public static int[] defaultControlNodeSize(String kind) {
    if (isBar(kind)) {
      return new int[] {BAR_WIDTH, BAR_HEIGHT};
    }
    if (KIND_DECISION.equals(kind) || KIND_MERGE.equals(kind)) {
      return new int[] {32, 32};
    }
    if (KIND_FINAL.equals(kind)) {
      return new int[] {26, 26};
    }
    return new int[] {24, 24};
  }

  /**
   * Normalize a guard condition. Surrounding square brackets are dropped because Visual Paradigm
   * adds them itself when it draws a guard; blank guards become null.
   *
   * @param guard the raw guard, with or without brackets
   * @return the bare guard text, or null when nothing was supplied
   */
  public static String normalizeGuard(String guard) {
    if (guard == null) {
      return null;
    }
    String trimmed = guard.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Format a guard the way UML shows it on a flow.
   *
   * @param guard the raw guard, with or without brackets
   * @return {@code [guard]}, or an empty string when there is no guard
   */
  public static String formatGuard(String guard) {
    String normalized = normalizeGuard(guard);
    return normalized == null ? "" : "[" + normalized + "]";
  }

  /**
   * Check a flow against the UML rules on its ends: an initial node has no incoming flow, a final
   * or flow final node no outgoing flow.
   *
   * @param fromKind kind of the source node ({@code KIND_*})
   * @param toKind kind of the target node ({@code KIND_*})
   * @return an error message, or null when the flow is allowed
   */
  public static String validateFlow(String fromKind, String toKind) {
    if (KIND_FINAL.equals(fromKind) || KIND_FLOW_FINAL.equals(fromKind)) {
      return "A " + fromKind + " node cannot have outgoing flows";
    }
    if (KIND_INITIAL.equals(toKind)) {
      return "An initial node cannot have incoming flows";
    }
    return null;
  }

  /**
   * Whether a flow between the two kinds is an object flow (one end is an object node).
   *
   * @param fromKind kind of the source node
   * @param toKind kind of the target node
   * @return true for an object flow, false for a control flow
   */
  public static boolean isObjectFlow(String fromKind, String toKind) {
    return KIND_OBJECT.equals(fromKind) || KIND_OBJECT.equals(toKind);
  }

  /**
   * Estimate the rendered width of a single-line label, used where Visual Paradigm cannot fit the
   * shape itself (partition headers).
   *
   * @param text the label
   * @return an approximate width in pixels including some padding
   */
  public static int estimateTextWidth(String text) {
    int longest = 0;
    if (text != null) {
      for (String line : text.split("\n", -1)) {
        longest = Math.max(longest, line.length());
      }
    }
    return longest * CHAR_WIDTH + 2 * LANE_PADDING;
  }

  /**
   * Width of a new lane: the requested width when given, otherwise the default widened to the
   * header text.
   *
   * @param partitionName the lane title
   * @param requestedWidth the caller's width, &lt;= 0 for automatic
   * @return the lane width
   */
  public static int laneWidth(String partitionName, int requestedWidth) {
    if (requestedWidth > 0) {
      return requestedWidth;
    }
    return Math.max(DEFAULT_LANE_WIDTH, estimateTextWidth(partitionName));
  }

  /**
   * Clamp a shape's x so it lies inside a lane, keeping the lane padding. A shape wider than the
   * lane is centered.
   *
   * @param x the wanted left x
   * @param width the shape width
   * @param laneX the lane's left x
   * @param laneWidth the lane width
   * @return the clamped left x
   */
  public static int clampIntoLane(int x, int width, int laneX, int laneWidth) {
    int min = laneX + LANE_PADDING;
    int max = laneX + laneWidth - LANE_PADDING - width;
    if (max < min) {
      return laneX + laneWidth / 2 - width / 2;
    }
    return Math.max(min, Math.min(max, x));
  }

  /**
   * Top-left corner of a w x h label for the first candidate spot that touches none of the
   * obstacles (other labels, shapes, flow segments as thin boxes); the first candidate when all of
   * them collide.
   *
   * @param candidates candidate top-left corners, best first
   * @param w label width
   * @param h label height
   * @param obstacles rectangles {x, y, w, h} to keep clear of
   * @return the chosen corner {x, y}
   */
  public static int[] firstClearSpot(
      java.util.List<int[]> candidates, int w, int h, java.util.List<int[]> obstacles) {
    int[] best = candidates.get(0);
    int bestHits = Integer.MAX_VALUE;
    for (int[] c : candidates) {
      int hits = hits(c, w, h, obstacles);
      if (hits == 0) {
        return c;
      }
      if (hits < bestHits) {
        bestHits = hits;
        best = c;
      }
    }
    return best;
  }

  /**
   * Whether a w x h label at a corner touches none of the obstacles.
   *
   * @param c top-left corner
   * @param w label width
   * @param h label height
   * @param obstacles rectangles {x, y, w, h}
   * @return true when the spot is clear
   */
  public static boolean isClear(int[] c, int w, int h, java.util.List<int[]> obstacles) {
    return hits(c, w, h, obstacles) == 0;
  }

  private static int hits(int[] c, int w, int h, java.util.List<int[]> obstacles) {
    int hits = 0;
    for (int[] o : obstacles) {
      if (o[0] < c[0] + w + 2
          && c[0] < o[0] + o[2] + 2
          && o[1] < c[1] + h + 2
          && c[1] < o[1] + o[3] + 2) {
        hits++;
      }
    }
    return hits;
  }

  /**
   * Candidate spots for a flow's [guard] along the flow's first segment, best first: beside the
   * line at 25%, 45% and 65% of the segment (capped near the source), on the right of the direction
   * of travel first, then on the left; then the same spots further off the line (a slanted line's
   * clearance box needs more room).
   *
   * @param sx segment start x (source end)
   * @param sy segment start y
   * @param ex segment end x
   * @param ey segment end y
   * @param w label width
   * @param h label height
   * @return candidate top-left corners
   */
  public static java.util.List<int[]> guardCandidates(
      int sx, int sy, int ex, int ey, int w, int h) {
    java.util.List<int[]> result = new java.util.ArrayList<>();
    double len = Math.max(1, Math.hypot(ex - sx, ey - sy));
    double ux = (ex - sx) / len;
    double uy = (ey - sy) / len;
    double[] fractions = {0.25, 0.45, 0.65};
    for (int pass = 0; pass < 2; pass++) {
      int off = pass == 0 ? 6 : 16;
      int offV = pass == 0 ? 4 : 12;
      for (int side = 0; side < 2; side++) {
        for (double f : fractions) {
          double along = Math.min(len * f, 30 + 60 * f);
          double ax = sx + ux * along;
          double ay = sy + uy * along;
          // "right" of a downward flow is +x; of a rightward flow it is +y (below)
          boolean right = side == 0;
          int x;
          int y;
          if (Math.abs(uy) >= Math.abs(ux)) {
            x = (int) Math.round(right == (uy > 0) ? ax + off : ax - w - off);
            y = (int) Math.round(ay - h / 2.0);
          } else {
            x = (int) Math.round(ax - w / 2.0);
            y = (int) Math.round(right == (ux < 0) ? ay - h - offV : ay + offV);
          }
          result.add(new int[] {x, y});
        }
      }
    }
    return result;
  }

  /** Smallest coordinate a label may have: the export crops at 0, VP drops negative positions. */
  private static final int MIN_LABEL_COORD = 5;

  /**
   * The candidate spots that lie on the canvas (left and top edge at least 5), or all of them when
   * none does.
   *
   * @param candidates top-left corners
   * @return the candidates on the canvas
   */
  public static java.util.List<int[]> onCanvas(java.util.List<int[]> candidates) {
    java.util.List<int[]> result = new java.util.ArrayList<>();
    for (int[] c : candidates) {
      if (c[0] >= MIN_LABEL_COORD && c[1] >= MIN_LABEL_COORD) {
        result.add(c);
      }
    }
    return result.isEmpty() ? candidates : result;
  }

  /**
   * Index of the lane whose horizontal extent contains {@code px}.
   *
   * @param px the x to test
   * @param laneXs left x of each lane
   * @param laneWidths width of each lane
   * @return the lane index, or -1 when no lane contains the point
   */
  public static int laneIndexAt(int px, int[] laneXs, int[] laneWidths) {
    for (int i = 0; i < laneXs.length && i < laneWidths.length; i++) {
      if (px >= laneXs[i] && px < laneXs[i] + laneWidths[i]) {
        return i;
      }
    }
    return -1;
  }
}
