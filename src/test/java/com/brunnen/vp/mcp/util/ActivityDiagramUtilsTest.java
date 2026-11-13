package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for ActivityDiagramUtils. */
public class ActivityDiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Named node is valid", ActivityDiagramUtils.isValidName("Approve order"));
    assertFalse("Null name is invalid", ActivityDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", ActivityDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", ActivityDiagramUtils.isValidName("  "));
  }

  @Test
  public void testParseControlNodeKindCanonicalNames() {
    assertEquals("initial", ActivityDiagramUtils.parseControlNodeKind("initial"));
    assertEquals("final", ActivityDiagramUtils.parseControlNodeKind("final"));
    assertEquals("flowFinal", ActivityDiagramUtils.parseControlNodeKind("flowFinal"));
    assertEquals("decision", ActivityDiagramUtils.parseControlNodeKind("decision"));
    assertEquals("merge", ActivityDiagramUtils.parseControlNodeKind("merge"));
    assertEquals("fork", ActivityDiagramUtils.parseControlNodeKind("fork"));
    assertEquals("join", ActivityDiagramUtils.parseControlNodeKind("join"));
  }

  @Test
  public void testParseControlNodeKindSynonymsAndSpelling() {
    assertEquals(
        "Case and padding are ignored",
        ActivityDiagramUtils.KIND_FLOW_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("  FLOW_FINAL "));
    assertEquals(
        "Dashes are ignored",
        ActivityDiagramUtils.KIND_FLOW_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("flow-final"));
    assertEquals(
        "start means initial",
        ActivityDiagramUtils.KIND_INITIAL,
        ActivityDiagramUtils.parseControlNodeKind("Start"));
    assertEquals(
        "end means activity final",
        ActivityDiagramUtils.KIND_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("end"));
    assertEquals(
        "activity final node is final",
        ActivityDiagramUtils.KIND_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("Activity Final Node"));
    assertEquals(
        "branch means decision",
        ActivityDiagramUtils.KIND_DECISION,
        ActivityDiagramUtils.parseControlNodeKind("branch"));
    assertEquals(
        "Node suffix is accepted",
        ActivityDiagramUtils.KIND_JOIN,
        ActivityDiagramUtils.parseControlNodeKind("JoinNode"));
  }

  @Test
  public void testParseControlNodeKindUnknown() {
    assertNull("Null kind", ActivityDiagramUtils.parseControlNodeKind(null));
    assertNull("Blank kind", ActivityDiagramUtils.parseControlNodeKind(" "));
    assertNull("Action is not a control node", ActivityDiagramUtils.parseControlNodeKind("action"));
    assertNull("Typo is rejected", ActivityDiagramUtils.parseControlNodeKind("forkk"));
  }

  @Test
  public void testBarsAndLabels() {
    assertTrue("Fork is a bar", ActivityDiagramUtils.isBar(ActivityDiagramUtils.KIND_FORK));
    assertTrue("Join is a bar", ActivityDiagramUtils.isBar(ActivityDiagramUtils.KIND_JOIN));
    assertFalse("Merge is a diamond", ActivityDiagramUtils.isBar(ActivityDiagramUtils.KIND_MERGE));
    assertTrue(
        "Decision shows its question",
        ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_DECISION));
    assertTrue(
        "Initial node shows its name (Bat dau)",
        ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_INITIAL));
    assertTrue(
        "Final node shows its name (Ket thuc)",
        ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_FINAL));
    assertFalse(
        "Merge is unlabelled", ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_MERGE));
    assertFalse(
        "Fork bar is unlabelled", ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_FORK));
  }

  @Test
  public void testDefaultControlNodeSize() {
    int[] fork = ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_FORK);
    assertTrue("Fork bar is horizontal", fork[0] > fork[1]);
    int[] join = ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_JOIN);
    assertArrayEquals("Join bar matches fork bar", fork, join);
    int[] decision =
        ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_DECISION);
    assertEquals("Decision diamond is square", decision[0], decision[1]);
    int[] initial = ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_INITIAL);
    assertEquals("Initial circle is round", initial[0], initial[1]);
    assertTrue("Initial circle is small", initial[0] <= 30);
  }

  @Test
  public void testNormalizeGuard() {
    assertEquals("Bare guard is kept", "valid", ActivityDiagramUtils.normalizeGuard("valid"));
    assertEquals(
        "Brackets are stripped",
        "stock > 0",
        ActivityDiagramUtils.normalizeGuard(" [ stock > 0 ] "));
    assertNull("Null guard", ActivityDiagramUtils.normalizeGuard(null));
    assertNull("Blank guard", ActivityDiagramUtils.normalizeGuard("   "));
    assertNull("Empty brackets", ActivityDiagramUtils.normalizeGuard("[ ]"));
    assertEquals(
        "Only one outer pair is stripped",
        "[a] or [b]",
        ActivityDiagramUtils.normalizeGuard("[[a] or [b]]"));
    assertEquals(
        "Unbalanced bracket is kept", "[open", ActivityDiagramUtils.normalizeGuard("[open"));
  }

  @Test
  public void testFormatGuard() {
    assertEquals("[approved]", ActivityDiagramUtils.formatGuard("approved"));
    assertEquals("[approved]", ActivityDiagramUtils.formatGuard("[approved]"));
    assertEquals("No guard formats to empty", "", ActivityDiagramUtils.formatGuard(""));
    assertEquals("Null guard formats to empty", "", ActivityDiagramUtils.formatGuard(null));
  }

  @Test
  public void testValidateFlow() {
    assertNull(
        "Action to action is fine",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_ACTION));
    assertNull(
        "Initial to action is fine",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_INITIAL, ActivityDiagramUtils.KIND_ACTION));
    assertNull(
        "Action to final is fine",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_FINAL));
    assertNotNull(
        "Nothing flows into an initial node",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_INITIAL));
    assertNotNull(
        "Nothing leaves a final node",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_FINAL, ActivityDiagramUtils.KIND_ACTION));
    assertNotNull(
        "Nothing leaves a flow final node",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_FLOW_FINAL, ActivityDiagramUtils.KIND_MERGE));
  }

  @Test
  public void testIsObjectFlow() {
    assertTrue(
        "Action to object is an object flow",
        ActivityDiagramUtils.isObjectFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_OBJECT));
    assertTrue(
        "Object to action is an object flow",
        ActivityDiagramUtils.isObjectFlow(
            ActivityDiagramUtils.KIND_OBJECT, ActivityDiagramUtils.KIND_ACTION));
    assertFalse(
        "Decision to action is a control flow",
        ActivityDiagramUtils.isObjectFlow(
            ActivityDiagramUtils.KIND_DECISION, ActivityDiagramUtils.KIND_ACTION));
  }

  @Test
  public void testLaneWidth() {
    assertEquals("Explicit width wins", 300, ActivityDiagramUtils.laneWidth("Sale staff", 300));
    assertEquals(
        "Short title gets the default width",
        ActivityDiagramUtils.DEFAULT_LANE_WIDTH,
        ActivityDiagramUtils.laneWidth("Clerk", 0));
    String longTitle = "Warehouse management department staff member";
    assertTrue(
        "Long title widens the lane",
        ActivityDiagramUtils.laneWidth(longTitle, 0) > ActivityDiagramUtils.DEFAULT_LANE_WIDTH);
    assertEquals(
        "Auto width equals the header estimate for long titles",
        ActivityDiagramUtils.estimateTextWidth(longTitle),
        ActivityDiagramUtils.laneWidth(longTitle, -1));
  }

  @Test
  public void testEstimateTextWidthUsesLongestLine() {
    assertEquals(
        "Multi-line text is as wide as its longest line",
        ActivityDiagramUtils.estimateTextWidth("abcdef"),
        ActivityDiagramUtils.estimateTextWidth("abc\nabcdef\nab"));
    assertTrue("Empty text still has padding", ActivityDiagramUtils.estimateTextWidth(null) > 0);
  }

  @Test
  public void testFirstClearSpotAndGuardCandidates() {
    java.util.List<int[]> candidates =
        ActivityDiagramUtils.guardCandidates(100, 100, 100, 300, 60, 14);
    // downward flow: the first candidate is right of the line, the 4th left of it
    assertTrue(candidates.get(0)[0] > 100);
    assertTrue(candidates.get(3)[0] + 60 < 100);
    // then the same spots further off the line
    assertEquals(12, candidates.size());
    assertTrue(candidates.get(6)[0] > candidates.get(0)[0]);
    assertTrue(candidates.get(9)[0] < candidates.get(3)[0]);
    java.util.List<int[]> obstacles = new java.util.ArrayList<>();
    obstacles.add(new int[] {100, 100, 200, 200}); // everything right of the line is taken
    int[] spot = ActivityDiagramUtils.firstClearSpot(candidates, 60, 14, obstacles);
    assertTrue("falls back to the left side", spot[0] + 60 < 100);
  }

  @Test
  public void testLaneIndexAt() {
    int[] xs = {40, 280, 520};
    int[] widths = {240, 240, 300};
    assertEquals("First lane", 0, ActivityDiagramUtils.laneIndexAt(40, xs, widths));
    assertEquals("Second lane", 1, ActivityDiagramUtils.laneIndexAt(400, xs, widths));
    assertEquals(
        "Right edge belongs to the next lane",
        1,
        ActivityDiagramUtils.laneIndexAt(280, xs, widths));
    assertEquals("Last lane", 2, ActivityDiagramUtils.laneIndexAt(819, xs, widths));
    assertEquals("Right of all lanes", -1, ActivityDiagramUtils.laneIndexAt(820, xs, widths));
    assertEquals("Left of all lanes", -1, ActivityDiagramUtils.laneIndexAt(10, xs, widths));
    assertEquals("No lanes", -1, ActivityDiagramUtils.laneIndexAt(10, new int[0], new int[0]));
  }

  @Test
  public void testOnCanvasDropsSpotsLeftOrAboveTheCanvas() {
    java.util.List<int[]> spots = new java.util.ArrayList<>();
    spots.add(new int[] {-20, 50});
    spots.add(new int[] {30, 2});
    spots.add(new int[] {30, 50});
    java.util.List<int[]> kept = ActivityDiagramUtils.onCanvas(spots);
    assertEquals(1, kept.size());
    assertArrayEquals(new int[] {30, 50}, kept.get(0));
    java.util.List<int[]> none = new java.util.ArrayList<>();
    none.add(new int[] {-5, -5});
    assertEquals(
        "all kept when none is on the canvas", 1, ActivityDiagramUtils.onCanvas(none).size());
  }

  @Test
  public void testClampIntoLane() {
    int pad = ActivityDiagramUtils.LANE_PADDING;
    assertEquals("Inside stays put", 150, ActivityDiagramUtils.clampIntoLane(150, 80, 100, 240));
    assertEquals(
        "Too far left is pushed in",
        100 + pad,
        ActivityDiagramUtils.clampIntoLane(0, 80, 100, 240));
    assertEquals(
        "Too far right is pulled in",
        100 + 240 - pad - 80,
        ActivityDiagramUtils.clampIntoLane(900, 80, 100, 240));
    assertEquals(
        "Wider than the lane is centered",
        100 + 120 - 150,
        ActivityDiagramUtils.clampIntoLane(150, 300, 100, 240));
  }
}
