package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The fixes of the round 6 audit: package folders, note anchors, nested routes, labels. */
public class Round6Test {

  private static List<int[]> route(int... xy) {
    List<int[]> list = new ArrayList<>();
    for (int i = 0; i < xy.length; i += 2) {
      list.add(new int[] {xy[i], xy[i + 1]});
    }
    return list;
  }

  private static void assertOrthogonal(List<int[]> route) {
    for (int i = 0; i + 1 < route.size(); i++) {
      int[] a = route.get(i);
      int[] b = route.get(i + 1);
      assertTrue("segment " + i + " is horizontal or vertical", a[0] == b[0] || a[1] == b[1]);
    }
  }

  // ---- package folder

  @Test
  public void aLineBesideTheTabEndsOnTheBodyOfAPackage() {
    int[] rect = {100, 100, 100, 50};
    int[] end = {150, 100}; // right of the tab (the tab is 20 wide)
    ShapeOutline.snap(ShapeOutline.Kind.FOLDER, rect, end, new int[] {150, 40});
    assertEquals(100 + ShapeOutline.TAB_HEIGHT, end[1]);
    int[] onTab = {110, 100};
    ShapeOutline.snap(ShapeOutline.Kind.FOLDER, rect, onTab, new int[] {110, 40});
    assertEquals("on the tab the top of the tab is the border", 100, onTab[1]);
    int[] fromBelow = {150, 150};
    ShapeOutline.snap(ShapeOutline.Kind.FOLDER, rect, fromBelow, new int[] {150, 220});
    assertEquals(150, fromBelow[1]);
  }

  @Test
  public void aPortOnTheEastSideOfAPackageIsBelowTheTabLevel() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(
        new OrthogonalRouter.Shape(
            "p", new int[] {100, 100, 100, 50}, null, ShapeOutline.Kind.FOLDER));
    shapes.add(
        new OrthogonalRouter.Shape(
            "q", new int[] {400, 60, 100, 50}, null, ShapeOutline.Kind.RECT));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("pq", "p", "q"));
    List<int[]> r = OrthogonalRouter.route(shapes, conns).get("pq");
    assertNotNull(r);
    assertOrthogonal(r);
    int[] start = r.get(0);
    if (start[0] == 200) {
      assertTrue("east end below the tab strip", start[1] >= 100 + ShapeOutline.TAB_HEIGHT);
    }
  }

  // ---- opposite connectors

  @Test
  public void twoOppositeRoutesNestInsteadOfCrossing() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(
        new OrthogonalRouter.Shape(
            "trong", new int[] {391, 180, 100, 40}, null, ShapeOutline.Kind.RECT));
    shapes.add(
        new OrthogonalRouter.Shape(
            "dat", new int[] {226, 300, 100, 40}, null, ShapeOutline.Kind.RECT));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("ab", "trong", "dat"));
    conns.add(new OrthogonalRouter.Conn("ba", "dat", "trong"));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    assertNotNull(routes.get("ab"));
    assertNotNull(routes.get("ba"));
    assertOrthogonal(routes.get("ab"));
    assertOrthogonal(routes.get("ba"));
    assertEquals(0, RouteChecks.crossingsBetween(routes.get("ab"), routes.get("ba")));
    assertFalse(RouteChecks.shares(routes.get("ab"), routes.get("ba"), OrthogonalRouter.LANE_GAP));
  }

  @Test
  public void routesEndingOnOneLineKeepApart() {
    // a route that stops 2 px short of where another begins on the same line reads as one line
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(
        new OrthogonalRouter.Shape(
            "a", new int[] {200, 100, 100, 40}, null, ShapeOutline.Kind.RECT));
    shapes.add(
        new OrthogonalRouter.Shape(
            "b", new int[] {200, 300, 100, 40}, null, ShapeOutline.Kind.RECT));
    shapes.add(
        new OrthogonalRouter.Shape(
            "c", new int[] {200, 500, 100, 40}, null, ShapeOutline.Kind.RECT));
    List<OrthogonalRouter.Drawn> drawn = new ArrayList<>();
    // a drawn line from a to b on x = 250, so a line from b to c on the same x must move aside
    drawn.add(
        new OrthogonalRouter.Drawn(
            new OrthogonalRouter.Conn("ab", "a", "b"), route(250, 140, 250, 298)));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("bc", "b", "c"));
    List<int[]> r = OrthogonalRouter.route(shapes, conns, drawn).get("bc");
    assertNotNull(r);
    assertOrthogonal(r);
    for (int i = 0; i + 1 < r.size(); i++) {
      int[] p = r.get(i);
      int[] q = r.get(i + 1);
      if (p[0] == q[0] && p[0] == 250) {
        assertTrue(
            "no segment of its own on the line of the other, end to end",
            Math.min(p[1], q[1]) > 298 + OrthogonalRouter.LANE_GAP - 1
                || Math.max(p[1], q[1]) < 140 - OrthogonalRouter.LANE_GAP + 1);
      }
    }
  }

  // ---- note anchors

  @Test
  public void aNoteUnderALifelineIsAnchoredByAVerticalLine() {
    int[] lifeline = {285, 30, 101, 639};
    int axis = 335;
    int[] spot = NoteAnchor.spotBelow(lifeline, axis, 241, 54, new ArrayList<int[]>());
    int[] note = {spot[0], spot[1], 241, 54};
    List<int[]> r = NoteAnchor.route(note, lifeline, axis);
    assertEquals(2, r.size());
    assertEquals(axis, r.get(0)[0]);
    assertEquals(axis, r.get(1)[0]);
    assertEquals(note[1], r.get(0)[1]);
    assertEquals(lifeline[1] + lifeline[3], r.get(1)[1]);
  }

  @Test
  public void aSecondNoteGoesBelowTheFirstOne() {
    int[] lifeline = {285, 30, 101, 639};
    List<int[]> others = new ArrayList<>();
    int[] first = NoteAnchor.spotBelow(lifeline, 335, 200, 40, others);
    others.add(new int[] {first[0], first[1], 200, 40});
    int[] second = NoteAnchor.spotBelow(lifeline, 335, 200, 40, others);
    assertTrue(second[1] >= first[1] + 40);
  }

  @Test
  public void aNoteBesideALifelineIsAnchoredHorizontally() {
    int[] lifeline = {285, 30, 101, 639};
    int[] note = {450, 200, 150, 50};
    List<int[]> r = NoteAnchor.route(note, lifeline, 335);
    assertOrthogonal(r);
    assertEquals(2, r.size());
    assertEquals(r.get(0)[1], r.get(1)[1]);
    assertEquals(335, r.get(1)[0]);
  }

  @Test
  public void aNoteDiagonalToItsTargetGetsAnElbowNeverASlant() {
    int[] target = {100, 100, 80, 40};
    int[] note = {400, 400, 100, 40};
    List<int[]> r = NoteAnchor.route(note, target, null);
    assertOrthogonal(r);
    assertTrue(r.size() >= 3);
    int[] last = r.get(r.size() - 1);
    assertTrue(last[0] == 180 || last[0] == 100);
  }

  // ---- gateway with three branches

  @Test
  public void threeBranchesOfAGatewayLeaveAtThreePlaces() {
    Rectangle gateway = new Rectangle(465, 100, 40, 40);
    Rectangle cash = new Rectangle(673, 100, 100, 40);
    Rectangle card = new Rectangle(633, 190, 100, 40);
    Rectangle transfer = new Rectangle(705, 280, 100, 40);
    java.util.Set<Integer> used = new java.util.HashSet<>();
    List<List<Point>> others = new ArrayList<>();
    List<List<Point>> routes = new ArrayList<>();
    for (Rectangle target : Arrays.asList(cash, card, transfer)) {
      BpmnUtils.FlowRoute flow =
          BpmnUtils.routeFlow(
              gateway,
              true,
              true,
              target,
              false,
              used,
              new ArrayList<Rectangle>(),
              BpmnUtils.EXIT_TOP,
              others);
      assertNotNull(flow.points());
      used.add(flow.exit());
      others.add(flow.points());
      routes.add(flow.points());
    }
    assertEquals("three different places", 3, used.size());
    for (int i = 0; i < routes.size(); i++) {
      for (int j = i + 1; j < routes.size(); j++) {
        assertNotEquals(routes.get(i).get(0), routes.get(j).get(0));
        for (int a = 0; a + 1 < routes.get(i).size(); a++) {
          for (int b = 0; b + 1 < routes.get(j).size(); b++) {
            Point p = routes.get(i).get(a);
            Point q = routes.get(i).get(a + 1);
            Point r = routes.get(j).get(b);
            Point s = routes.get(j).get(b + 1);
            if (p.x == q.x && r.x == s.x && Math.abs(p.x - r.x) < 3) {
              assertTrue(
                  "no two branches on one vertical line over a stretch",
                  Math.min(Math.max(p.y, q.y), Math.max(r.y, s.y))
                          - Math.max(Math.min(p.y, q.y), Math.min(r.y, s.y))
                      <= 3);
            }
          }
        }
      }
    }
  }

  // ---- labels

  @Test
  public void aLabelNeverStaysFarFromItsOwnLineWhenThereIsRoomBesideIt() {
    List<int[]> shapes = new ArrayList<>();
    shapes.add(new int[] {200, 0, 100, 40});
    shapes.add(new int[] {200, 300, 100, 40});
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("c", route(250, 40, 250, 300));
    routes.put("other", route(150, 40, 150, 300));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("c", 0, 110, 14, LabelPlacer.Slot.CENTER));
    Map<String, Map<Integer, int[]>> placed = LabelPlacer.place(shapes, routes, specs);
    int[] r = placed.get("c").get(0);
    int distance = Math.max(250 - (r[0] + r[2]), r[0] - 250);
    assertTrue("label within 30 px of its line: " + Arrays.toString(r), distance <= 30);
  }

  @Test
  public void aLabelWithASpaceMayWrapWhenItsBoxFindsNoPlace() {
    // a narrow gap between two shapes: the one-line box does not fit beside the line
    List<int[]> shapes = new ArrayList<>();
    shapes.add(new int[] {0, 0, 100, 40});
    shapes.add(new int[] {0, 200, 100, 40});
    shapes.add(new int[] {110, 80, 400, 80});
    Map<String, List<int[]>> routes = new LinkedHashMap<>();
    routes.put("c", route(50, 40, 50, 200));
    List<LabelPlacer.Spec> specs = new ArrayList<>();
    specs.add(new LabelPlacer.Spec("c", 0, 120, 14, LabelPlacer.Slot.CENTER).wrappedTo(54, 28));
    Map<String, Map<Integer, int[]>> placed = LabelPlacer.place(shapes, routes, specs);
    int[] r = placed.get("c").get(0);
    for (int[] s : shapes) {
      assertFalse("the label is clear of every shape", LabelPlacer.intersects(r, s));
    }
  }

  // ---- results

  @Test
  public void aFailureStartsWithError() {
    assertEquals("Error: Diagram not found: X", ToolResults.normalize("Diagram not found: X"));
    assertEquals(
        "Error: Node not on diagram: Nope1", ToolResults.normalize("Node not on diagram: Nope1"));
    assertEquals(
        "Error: Message name must not be empty",
        ToolResults.normalize("Message name must not be empty"));
    assertEquals(
        "Error: Class already on diagram: A", ToolResults.normalize("Class already on diagram: A"));
    assertEquals("Error adding flow: x", ToolResults.normalize("Error adding flow: x"));
  }

  @Test
  public void aSuccessIsLeftAlone() {
    String added = "Added class 'A' to diagram 'D' (existing class of the project, drawn again)";
    assertEquals(added, ToolResults.normalize(added));
    String partial = "Added the flow 'a' -> 'b' but Visual Paradigm refused the flow back";
    assertEquals(partial, ToolResults.normalize(partial));
    String elements = "Elements on 'D' (2):\n  - Actor: Nobody not found";
    assertEquals(elements, ToolResults.normalize(elements));
    assertEquals(null, ToolResults.normalize(null));
  }

  @Test
  public void aNameThatIsOnlyANumberShowsNoText() {
    assertTrue(ActivityDiagramUtils.hiddenLabel("#1"));
    assertTrue(ActivityDiagramUtils.hiddenLabel(" #12 "));
    assertFalse(ActivityDiagramUtils.hiddenLabel("End #2"));
    assertFalse(ActivityDiagramUtils.hiddenLabel("Valid?"));
    assertFalse(ActivityDiagramUtils.hiddenLabel(null));
  }

  @Test
  public void crossingsBetweenTwoRoutesAreCounted() {
    assertEquals(1, RouteChecks.crossingsBetween(route(0, 50, 100, 50), route(50, 0, 50, 100)));
    assertEquals(0, RouteChecks.crossingsBetween(route(0, 50, 100, 50), route(150, 0, 150, 100)));
  }

  @Test
  public void aSmallStepInTheMiddleOfARouteIsTakenOut() {
    List<int[]> r = route(100, 0, 100, 50, 103, 50, 103, 100, 160, 100);
    RouteTidy.removeMiddleJogs(r, 6);
    assertOrthogonal(r);
    for (int i = 0; i + 1 < r.size(); i++) {
      int[] a = r.get(i);
      int[] b = r.get(i + 1);
      assertTrue(
          "no step of 3 px is left", Math.abs(a[0] - b[0]) != 3 && Math.abs(a[1] - b[1]) != 3);
    }
    assertEquals("the start stays", 100, r.get(0)[0]);
    assertEquals("the end stays", 160, r.get(r.size() - 1)[0]);
    assertEquals(100, r.get(r.size() - 1)[1]);
  }

  @Test
  public void fiveGeneralizationsFitTheWestSideOfAFigure() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(
        new OrthogonalRouter.Shape(
            "p", new int[] {101, 500, 30, 60}, null, ShapeOutline.Kind.FIGURE));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      shapes.add(
          new OrthogonalRouter.Shape(
              "c" + i, new int[] {101, 700 + i * 120, 30, 60}, null, ShapeOutline.Kind.FIGURE));
      conns.add(new OrthogonalRouter.Conn("g" + i, "p", "c" + i).sides(3, 3));
    }
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    List<Integer> ends = new ArrayList<>();
    for (OrthogonalRouter.Conn c : conns) {
      List<int[]> r = routes.get(c.id);
      assertNotNull(r);
      assertOrthogonal(r);
      assertEquals(101, r.get(0)[0]);
      ends.add(r.get(0)[1]);
    }
    for (int i = 0; i < ends.size(); i++) {
      for (int j = i + 1; j < ends.size(); j++) {
        assertTrue(
            "ends of two generalizations are 12 px apart: " + ends,
            Math.abs(ends.get(i) - ends.get(j)) >= 12);
      }
    }
  }
}
