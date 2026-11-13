package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The dashed line from a note to the shape it explains, as one straight horizontal or vertical line
 * (or an L when the two do not face each other). Pure geometry; rectangles are {x, y, w, h}. Used
 * by diagram types whose shapes the shared layout does not place (a sequence diagram: the line ends
 * on the dashed line of a lifeline, not on the border of its tall box).
 */
public final class NoteAnchor {

  /** Distance from a corner of the note or the shape that the line keeps. */
  private static final int MARGIN = 8;

  private NoteAnchor() {}

  /**
   * The route from the note to the target.
   *
   * @param note the note's rectangle
   * @param target the target's rectangle
   * @param axisX x of a line the anchor should end on inside the target (the dashed line of a
   *     lifeline), or null to end on the border
   * @return the points from the note to the target, every segment horizontal or vertical
   */
  public static List<int[]> route(int[] note, int[] target, Integer axisX) {
    int noteRight = note[0] + note[2];
    int noteBottom = note[1] + note[3];
    int targetRight = target[0] + target[2];
    int targetBottom = target[1] + target[3];
    List<int[]> route = new ArrayList<>();
    // facing in x: one vertical line
    int loX = Math.max(note[0], target[0]) + MARGIN;
    int hiX = Math.min(noteRight, targetRight) - MARGIN;
    boolean noteBelow = note[1] >= targetBottom;
    boolean noteAbove = noteBottom <= target[1];
    if (axisX != null && axisX >= note[0] + MARGIN && axisX <= noteRight - MARGIN) {
      if (noteBelow) {
        return line(route, axisX, note[1], axisX, targetBottom);
      }
      if (noteAbove) {
        return line(route, axisX, noteBottom, axisX, target[1]);
      }
    }
    if ((noteBelow || noteAbove) && hiX >= loX) {
      int x = (loX + hiX) / 2;
      if (axisX != null && axisX >= loX && axisX <= hiX) {
        x = axisX;
      }
      return noteBelow
          ? line(route, x, note[1], x, targetBottom)
          : line(route, x, noteBottom, x, target[1]);
    }
    // facing in y: one horizontal line
    int loY = Math.max(note[1], target[1]) + MARGIN;
    int hiY = Math.min(noteBottom, targetBottom) - MARGIN;
    if (hiY >= loY) {
      int y = (loY + hiY) / 2;
      boolean noteRightOf = note[0] >= targetRight;
      int end = axisX != null ? axisX : noteRightOf ? targetRight : target[0];
      if (noteRightOf || note[0] + note[2] <= target[0]) {
        return line(route, noteRightOf ? note[0] : noteRight, y, end, y);
      }
    }
    // diagonal to each other: out of the note vertically, then along to the target's side
    int ncx = note[0] + note[2] / 2;
    int tcy = target[1] + target[3] / 2;
    boolean noteRightOf = ncx > target[0] + target[2] / 2;
    route.add(new int[] {ncx, noteBelow || !noteAbove ? note[1] : noteBottom});
    if (noteBelow || noteAbove) {
      route.add(new int[] {ncx, tcy});
      route.add(new int[] {axisX != null ? axisX : noteRightOf ? targetRight : target[0], tcy});
    } else {
      route.add(new int[] {ncx, tcy});
      route.add(new int[] {noteRightOf ? targetRight : target[0], tcy});
    }
    RouteTidy.removeCollinear(route);
    return route;
  }

  private static List<int[]> line(List<int[]> route, int x1, int y1, int x2, int y2) {
    route.add(new int[] {x1, y1});
    route.add(new int[] {x2, y2});
    return route;
  }

  /**
   * A spot for a note under the dashed line of a lifeline: the note's middle on the line, 40 px
   * below the lifeline's end, moved right past other notes that stand there.
   *
   * @param lifeline the lifeline's rectangle (head and dashed line)
   * @param axisX x of the dashed line
   * @param w the note's width
   * @param h the note's height
   * @param others rectangles the note must not overlap (other notes)
   * @return the note's top-left corner
   */
  public static int[] spotBelow(int[] lifeline, int axisX, int w, int h, List<int[]> others) {
    int x = Math.max(5, axisX - w / 2);
    int y = lifeline[1] + lifeline[3] + 40;
    boolean moved = true;
    for (int pass = 0; moved && pass < 20; pass++) {
      moved = false;
      for (int[] o : others) {
        if (x < o[0] + o[2] + 12
            && o[0] < x + w + 12
            && y < o[1] + o[3] + 12
            && o[1] < y + h + 12) {
          // another note under the line: the next one goes below it
          y = o[1] + o[3] + 24;
          moved = true;
        }
      }
    }
    return new int[] {x, y};
  }
}
