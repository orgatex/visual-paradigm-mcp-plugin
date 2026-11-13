package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShapeOutlineTest {

  @Test
  public void aRectangleIsLeftAlone() {
    int[] end = {100, 50};
    ShapeOutline.snap(ShapeOutline.Kind.RECT, new int[] {100, 0, 80, 100}, end, new int[] {60, 50});
    assertEquals(100, end[0]);
  }

  @Test
  public void anEllipseIsReachedOnItsOutline() {
    // 100x40 ellipse; a vertical stub from above at the centre reaches the top
    int[] rect = {0, 100, 100, 40};
    int[] centre = {50, 100};
    ShapeOutline.snap(ShapeOutline.Kind.ELLIPSE, rect, centre, new int[] {50, 60});
    assertEquals(100, centre[1]);
    // 30 px right of the centre the outline is lower: 20 * (1 - sqrt(1 - 0.36)) = 4
    int[] off = {80, 100};
    ShapeOutline.snap(ShapeOutline.Kind.ELLIPSE, rect, off, new int[] {80, 60});
    assertEquals(104, off[1]);
    assertEquals(80, off[0]);
  }

  @Test
  public void aHorizontalStubFromTheLeftMovesRight() {
    int[] rect = {100, 0, 100, 40};
    int[] end = {100, 30};
    ShapeOutline.snap(ShapeOutline.Kind.ELLIPSE, rect, end, new int[] {60, 30});
    // 10 px under the centre line: 50 * (1 - sqrt(1 - 0.25)) = 6.7
    assertEquals(107, end[0]);
    assertEquals(30, end[1]);
  }

  @Test
  public void aDiamondIsReachedOnItsEdge() {
    int[] rect = {0, 0, 100, 60};
    int[] end = {75, 0};
    ShapeOutline.snap(ShapeOutline.Kind.DIAMOND, rect, end, new int[] {75, -30});
    // 25 px right of the centre, half of the half-width: 30 * 0.5 = 15 down
    assertEquals(15, end[1]);
  }
}
