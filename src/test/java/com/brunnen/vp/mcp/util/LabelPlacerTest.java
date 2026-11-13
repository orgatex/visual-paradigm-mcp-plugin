package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class LabelPlacerTest {

  private static List<int[]> route(int... xy) {
    List<int[]> list = new ArrayList<>();
    for (int i = 0; i < xy.length; i += 2) {
      list.add(new int[] {xy[i], xy[i + 1]});
    }
    return list;
  }

  @Test
  public void labelsSitBesideTheirEndsAndMiddle() {
    List<int[]> shapes = new ArrayList<>();
    shapes.add(new int[] {0, 0, 100, 60});
    shapes.add(new int[] {300, 0, 100, 60});
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("c", route(100, 30, 300, 30));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("c", 0, 24, 14, LabelPlacer.Slot.TAIL));
    specs.add(new LabelPlacer.Spec("c", 1, 24, 14, LabelPlacer.Slot.HEAD));
    specs.add(new LabelPlacer.Spec("c", 2, 80, 14, LabelPlacer.Slot.CENTER));
    Map<String, Map<Integer, int[]>> placed = LabelPlacer.place(shapes, routes, specs);
    int[] tail = placed.get("c").get(0);
    int[] head = placed.get("c").get(1);
    int[] mid = placed.get("c").get(2);
    assertTrue(tail[0] >= 100 && tail[0] < 140);
    assertTrue(head[0] + head[2] <= 300 && head[0] + head[2] > 260);
    assertTrue(mid[0] > 130 && mid[0] + mid[2] < 270);
    assertFalse(LabelPlacer.intersects(tail, mid));
    assertFalse(LabelPlacer.intersects(head, mid));
    for (int[] r : new int[][] {tail, head, mid}) {
      // beside the line, never on it
      assertTrue(r[1] + r[3] <= 30 || r[1] >= 30);
      for (int[] s : shapes) {
        assertFalse(LabelPlacer.intersects(r, s));
      }
    }
  }

  @Test
  public void labelLongerThanItsLegIsStillPlaced() {
    List<int[]> shapes = new ArrayList<>();
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("c", route(0, 0, 40, 0));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("c", 0, 94, 14, LabelPlacer.Slot.CENTER));
    Map<String, Map<Integer, int[]>> placed = LabelPlacer.place(shapes, routes, specs);
    assertNotNull(placed.get("c").get(0));
  }

  @Test
  public void aLabelAvoidsAnotherLabel() {
    List<int[]> shapes = new ArrayList<>();
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("a", route(0, 0, 200, 0));
    routes.put("b", route(0, 10, 200, 10));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("a", 0, 60, 14, LabelPlacer.Slot.CENTER));
    specs.add(new LabelPlacer.Spec("b", 0, 60, 14, LabelPlacer.Slot.CENTER));
    Map<String, Map<Integer, int[]>> placed = LabelPlacer.place(shapes, routes, specs);
    assertFalse(LabelPlacer.intersects(placed.get("a").get(0), placed.get("b").get(0)));
  }

  @Test
  public void labelBesideAVerticalLegUsesTheVerticalSizeAndPreferredSide() {
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("c", route(200, 0, 200, 300));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    // 100 x 40 above a horizontal leg, 130 x 24 beside a vertical one
    specs.add(
        new LabelPlacer.Spec("c", 0, 100, 40, LabelPlacer.Slot.CENTER)
            .verticalSize(130, 24)
            .preferSide(1));
    int[] r = LabelPlacer.place(new ArrayList<int[]>(), routes, specs).get("c").get(0);
    assertEquals(130, r[2]);
    assertEquals(24, r[3]);
    assertTrue("right of the line", r[0] >= 200);
  }

  @Test
  public void labelsStayOnTheCanvas() {
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("c", route(30, 40, 30, 200));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("c", 0, 120, 14, LabelPlacer.Slot.CENTER));
    int[] r = LabelPlacer.place(new ArrayList<int[]>(), routes, specs).get("c").get(0);
    assertTrue("not left of the canvas: " + r[0], r[0] >= 2);
  }

  @Test
  public void labelsGoOutsideABundleOfParallelLines() {
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("left", route(100, 0, 100, 300));
    routes.put("right", route(114, 300, 114, 0));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("left", 0, 80, 14, LabelPlacer.Slot.CENTER));
    specs.add(new LabelPlacer.Spec("right", 0, 80, 14, LabelPlacer.Slot.CENTER));
    Map<String, Map<Integer, int[]>> placed =
        LabelPlacer.place(new ArrayList<int[]>(), routes, specs);
    int[] l = placed.get("left").get(0);
    int[] r = placed.get("right").get(0);
    assertTrue("left label left of its line", l[0] + l[2] <= 100);
    assertTrue("right label right of its line", r[0] >= 114);
  }

  @Test
  public void aLayoutThatMayShiftPutsTheLabelLeftOfTheLineEvenOffTheCanvas() {
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("c", route(30, 40, 30, 200));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("c", 0, 120, 14, LabelPlacer.Slot.CENTER));
    int[] r =
        LabelPlacer.place(
                new ArrayList<int[]>(),
                routes,
                specs,
                new ArrayList<int[]>(),
                Integer.MIN_VALUE / 4)
            .get("c")
            .get(0);
    assertTrue("left of the line, as the first choice: " + r[0], r[0] + r[2] <= 30);
  }

  /**
   * A label never gets a box smaller than its spec: the rectangle has the size of the label (or of
   * the sizes it was given for a vertical leg or a wrapped text), whatever room there is. A label
   * that finds no clear place moves further out, it does not shrink to squeeze in.
   */
  @Test
  public void aLabelNeverComesOutSmallerThanItsSpec() {
    java.util.Random random = new java.util.Random(7);
    for (int round = 0; round < 300; round++) {
      List<int[]> shapes = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        shapes.add(
            new int[] {random.nextInt(500), random.nextInt(400), 60 + random.nextInt(120), 40});
      }
      Map<String, List<int[]>> routes = new LinkedHashMap<>();
      List<LabelPlacer.Spec> specs = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        int x = random.nextInt(600);
        int y = random.nextInt(450);
        List<int[]> r =
            random.nextBoolean()
                ? route(x, y, x + 30 + random.nextInt(250), y)
                : route(x, y, x, y + 30 + random.nextInt(250));
        if (random.nextBoolean()) {
          int[] last = r.get(1);
          r.add(
              random.nextBoolean()
                  ? new int[] {last[0], last[1] + 20 + random.nextInt(200)}
                  : new int[] {last[0] + 20 + random.nextInt(200), last[1]});
        }
        routes.put("c" + i, r);
        int w = 46 + random.nextInt(90);
        LabelPlacer.Spec spec =
            new LabelPlacer.Spec("c" + i, 0, w, 14, LabelPlacer.Slot.values()[random.nextInt(3)]);
        if (random.nextBoolean()) {
          spec.verticalSize(w + 3, 16);
        }
        if (random.nextBoolean()) {
          spec.wrappedTo(w / 2 + 14, 28);
        }
        specs.add(spec);
      }
      Map<String, Map<Integer, int[]>> placed = LabelPlacer.place(shapes, routes, specs);
      for (LabelPlacer.Spec spec : specs) {
        int[] rect = placed.get(spec.connector()).get(0);
        boolean oneLine = rect[2] == spec.w && rect[3] == spec.h;
        boolean vertical = rect[2] == spec.verticalW && rect[3] == spec.verticalH;
        boolean wrapped = spec.altW > 0 && rect[2] == spec.altW && rect[3] == spec.altH;
        assertTrue(
            "the box of "
                + spec.connector()
                + " has no size it was given: "
                + java.util.Arrays.toString(rect),
            oneLine || vertical || wrapped);
        if (spec.altW == 0) {
          assertTrue(
              "a label that may not wrap keeps its width: " + java.util.Arrays.toString(rect),
              rect[2] >= Math.min(spec.w, spec.verticalW));
        }
      }
    }
  }
}
