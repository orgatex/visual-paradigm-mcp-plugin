package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.BpmnUtils.EventPosition;
import com.brunnen.vp.mcp.util.BpmnUtils.EventTrigger;
import com.brunnen.vp.mcp.util.BpmnUtils.GatewayKind;
import com.brunnen.vp.mcp.util.BpmnUtils.NodeRole;
import com.brunnen.vp.mcp.util.BpmnUtils.TaskKind;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Test class for BpmnUtils. */
public class BpmnUtilsTest {

  // --- Kind parsing ---

  @Test
  public void testParseTaskKind() {
    assertEquals("Blank is a plain task", TaskKind.TASK, BpmnUtils.parseTaskKind(""));
    assertEquals("Null is a plain task", TaskKind.TASK, BpmnUtils.parseTaskKind(null));
    assertEquals("none is a plain task", TaskKind.TASK, BpmnUtils.parseTaskKind("none"));
    assertEquals("Case is ignored", TaskKind.USER, BpmnUtils.parseTaskKind("User"));
    assertEquals("Trailing task is ignored", TaskKind.USER, BpmnUtils.parseTaskKind("user task"));
    assertEquals(TaskKind.SERVICE, BpmnUtils.parseTaskKind("service"));
    assertEquals(TaskKind.MANUAL, BpmnUtils.parseTaskKind("manual"));
    assertEquals(TaskKind.SEND, BpmnUtils.parseTaskKind("SendTask"));
    assertEquals(TaskKind.RECEIVE, BpmnUtils.parseTaskKind("receive"));
    assertEquals(TaskKind.SCRIPT, BpmnUtils.parseTaskKind("script"));
    assertEquals("Dashes are ignored", TaskKind.SUBPROCESS, BpmnUtils.parseTaskKind("Sub-Process"));
    assertEquals(TaskKind.SUBPROCESS, BpmnUtils.parseTaskKind("subprocess"));
    assertNull("Unknown kind", BpmnUtils.parseTaskKind("businessRule"));
  }

  @Test
  public void testParseEventPosition() {
    assertEquals(EventPosition.START, BpmnUtils.parseEventPosition("start"));
    assertEquals(EventPosition.INTERMEDIATE, BpmnUtils.parseEventPosition(" Intermediate "));
    assertEquals(EventPosition.END, BpmnUtils.parseEventPosition("END"));
    assertNull("Position is required", BpmnUtils.parseEventPosition(""));
    assertNull("Null position", BpmnUtils.parseEventPosition(null));
    assertNull("Unknown position", BpmnUtils.parseEventPosition("begin"));
  }

  @Test
  public void testParseEventTrigger() {
    assertEquals("Blank is none", EventTrigger.NONE, BpmnUtils.parseEventTrigger(""));
    assertEquals("Null is none", EventTrigger.NONE, BpmnUtils.parseEventTrigger(null));
    assertEquals(EventTrigger.NONE, BpmnUtils.parseEventTrigger("none"));
    assertEquals(EventTrigger.MESSAGE, BpmnUtils.parseEventTrigger("Message"));
    assertEquals(EventTrigger.TIMER, BpmnUtils.parseEventTrigger("timer"));
    assertEquals(EventTrigger.ERROR, BpmnUtils.parseEventTrigger("error"));
    assertEquals(EventTrigger.SIGNAL, BpmnUtils.parseEventTrigger("signal"));
    assertEquals(EventTrigger.TERMINATE, BpmnUtils.parseEventTrigger("terminate"));
    assertNull("Unknown trigger", BpmnUtils.parseEventTrigger("cancel"));
  }

  @Test
  public void testParseGatewayKind() {
    assertEquals("Blank is exclusive", GatewayKind.EXCLUSIVE, BpmnUtils.parseGatewayKind(""));
    assertEquals(GatewayKind.EXCLUSIVE, BpmnUtils.parseGatewayKind("XOR"));
    assertEquals(GatewayKind.EXCLUSIVE, BpmnUtils.parseGatewayKind("exclusive gateway"));
    assertEquals(GatewayKind.PARALLEL, BpmnUtils.parseGatewayKind("parallel"));
    assertEquals(GatewayKind.PARALLEL, BpmnUtils.parseGatewayKind("AND"));
    assertEquals(GatewayKind.INCLUSIVE, BpmnUtils.parseGatewayKind("inclusive"));
    assertEquals(GatewayKind.INCLUSIVE, BpmnUtils.parseGatewayKind("or"));
    assertEquals(GatewayKind.EVENT_BASED, BpmnUtils.parseGatewayKind("eventBased"));
    assertEquals(GatewayKind.EVENT_BASED, BpmnUtils.parseGatewayKind("event-based"));
    assertNull("Unknown kind", BpmnUtils.parseGatewayKind("complex"));
  }

  @Test
  public void testKindNames() {
    assertEquals(
        "task, user, service, manual, send, receive, script, subprocess",
        BpmnUtils.taskKindNames());
    assertEquals("none, message, timer, error, signal, terminate", BpmnUtils.eventTriggerNames());
    assertEquals("exclusive, parallel, inclusive, eventBased", BpmnUtils.gatewayKindNames());
  }

  @Test
  public void testEventTriggerViolation() {
    assertNotNull(
        "End events cannot wait for a timer",
        BpmnUtils.eventTriggerViolation(EventPosition.END, EventTrigger.TIMER));
    assertNotNull(
        "Terminate is end-only",
        BpmnUtils.eventTriggerViolation(EventPosition.START, EventTrigger.TERMINATE));
    assertNotNull(
        "Terminate is end-only",
        BpmnUtils.eventTriggerViolation(EventPosition.INTERMEDIATE, EventTrigger.TERMINATE));
    assertNull(BpmnUtils.eventTriggerViolation(EventPosition.END, EventTrigger.TERMINATE));
    assertNull(BpmnUtils.eventTriggerViolation(EventPosition.START, EventTrigger.TIMER));
    assertNull(BpmnUtils.eventTriggerViolation(EventPosition.END, EventTrigger.MESSAGE));
  }

  @Test
  public void testDescribeEvent() {
    assertEquals(
        "message start event", BpmnUtils.describeEvent(EventPosition.START, EventTrigger.MESSAGE));
    assertEquals("end event", BpmnUtils.describeEvent(EventPosition.END, EventTrigger.NONE));
    assertEquals(
        "timer intermediate event",
        BpmnUtils.describeEvent(EventPosition.INTERMEDIATE, EventTrigger.TIMER));
  }

  // --- Names and labels ---

  @Test
  public void testNamesAndLabels() {
    assertTrue(BpmnUtils.isValidName("Approve order"));
    assertFalse(BpmnUtils.isValidName(null));
    assertFalse(BpmnUtils.isValidName("   "));
    assertEquals("Label is trimmed", "Yes", BpmnUtils.normalizeLabel("  Yes "));
    assertNull("Blank label becomes null", BpmnUtils.normalizeLabel(" "));
    assertNull("Null label stays null", BpmnUtils.normalizeLabel(null));
    assertEquals(" [No]", BpmnUtils.labelSuffix("No"));
    assertEquals(" [còn phòng]", BpmnUtils.labelSuffix("[còn phòng]"));
    assertEquals("", BpmnUtils.labelSuffix(null));
  }

  // --- Roles and connection rules ---

  @Test
  public void testRoles() {
    assertTrue(BpmnUtils.isFlowNode(NodeRole.TASK));
    assertTrue(BpmnUtils.isFlowNode(NodeRole.GATEWAY));
    assertFalse(BpmnUtils.isFlowNode(NodeRole.DATA_OBJECT));
    assertFalse(BpmnUtils.isFlowNode(NodeRole.POOL));
    assertFalse(BpmnUtils.isFlowNode(null));
    assertTrue("Events are circles", BpmnUtils.isRound(NodeRole.START_EVENT));
    assertTrue("Gateways are diamonds", BpmnUtils.isRound(NodeRole.GATEWAY));
    assertFalse(BpmnUtils.isRound(NodeRole.TASK));
    assertTrue(BpmnUtils.isDataAssociation(NodeRole.DATA_OBJECT, NodeRole.TASK));
    assertTrue(BpmnUtils.isDataAssociation(NodeRole.TASK, NodeRole.DATA_OBJECT));
    assertFalse(BpmnUtils.isDataAssociation(NodeRole.TASK, NodeRole.GATEWAY));
    assertEquals("start event", BpmnUtils.roleName(NodeRole.START_EVENT));
    assertEquals("pool", BpmnUtils.roleName(NodeRole.POOL));
    assertEquals("sub-process", BpmnUtils.roleName(NodeRole.SUBPROCESS));
  }

  @Test
  public void testSequenceFlowRules() {
    assertNull(
        "Task to task in one pool",
        BpmnUtils.sequenceFlowViolation(NodeRole.TASK, NodeRole.TASK, false, true));
    assertNull(
        "Start event to gateway",
        BpmnUtils.sequenceFlowViolation(NodeRole.START_EVENT, NodeRole.GATEWAY, false, true));
    assertNotNull(
        "No flow out of an end event",
        BpmnUtils.sequenceFlowViolation(NodeRole.END_EVENT, NodeRole.TASK, false, true));
    assertNotNull(
        "No flow into a start event",
        BpmnUtils.sequenceFlowViolation(NodeRole.TASK, NodeRole.START_EVENT, false, true));
    assertNotNull(
        "Same element twice",
        BpmnUtils.sequenceFlowViolation(NodeRole.TASK, NodeRole.TASK, true, true));
    assertNotNull(
        "Pools are not flow nodes",
        BpmnUtils.sequenceFlowViolation(NodeRole.POOL, NodeRole.TASK, false, true));
    assertNotNull(
        "Lanes are not flow nodes",
        BpmnUtils.sequenceFlowViolation(NodeRole.TASK, NodeRole.LANE, false, true));
    String crossPool = BpmnUtils.sequenceFlowViolation(NodeRole.TASK, NodeRole.TASK, false, false);
    assertNotNull("No sequence flow across pools", crossPool);
    assertTrue("Points to message flows", crossPool.contains("addBpmnMessageFlow"));
  }

  @Test
  public void testMessageFlowRules() {
    assertNull(
        "Task to task between pools",
        BpmnUtils.messageFlowViolation(NodeRole.TASK, NodeRole.TASK, false, false, false));
    assertNull(
        "Task to a message start event",
        BpmnUtils.messageFlowViolation(NodeRole.TASK, NodeRole.START_EVENT, false, false, false));
    assertNull(
        "Message end event to a pool",
        BpmnUtils.messageFlowViolation(NodeRole.END_EVENT, NodeRole.POOL, false, false, false));
    assertNotNull(
        "Same pool",
        BpmnUtils.messageFlowViolation(NodeRole.TASK, NodeRole.TASK, false, true, false));
    assertNotNull(
        "No pools at all",
        BpmnUtils.messageFlowViolation(NodeRole.TASK, NodeRole.TASK, false, false, true));
    assertNotNull(
        "Start events only receive",
        BpmnUtils.messageFlowViolation(NodeRole.START_EVENT, NodeRole.TASK, false, false, false));
    assertNotNull(
        "End events only send",
        BpmnUtils.messageFlowViolation(NodeRole.TASK, NodeRole.END_EVENT, false, false, false));
    assertNotNull(
        "Gateways take no message flows",
        BpmnUtils.messageFlowViolation(NodeRole.GATEWAY, NodeRole.TASK, false, false, false));
    assertNotNull(
        "Lanes take no message flows",
        BpmnUtils.messageFlowViolation(NodeRole.TASK, NodeRole.LANE, false, false, false));
    assertNotNull(
        "Data objects take no message flows",
        BpmnUtils.messageFlowViolation(NodeRole.DATA_OBJECT, NodeRole.TASK, false, false, false));
    assertNotNull(
        "Same element twice",
        BpmnUtils.messageFlowViolation(NodeRole.POOL, NodeRole.POOL, true, true, false));
  }

  @Test
  public void testDataAssociationRules() {
    assertNull(BpmnUtils.dataAssociationViolation(NodeRole.DATA_OBJECT, NodeRole.TASK, false));
    assertNull(BpmnUtils.dataAssociationViolation(NodeRole.TASK, NodeRole.DATA_OBJECT, false));
    assertNull(
        "Events may read/write data",
        BpmnUtils.dataAssociationViolation(NodeRole.DATA_OBJECT, NodeRole.END_EVENT, false));
    assertNotNull(
        "Gateways do not touch data",
        BpmnUtils.dataAssociationViolation(NodeRole.DATA_OBJECT, NodeRole.GATEWAY, false));
    assertNotNull(
        "Two data objects",
        BpmnUtils.dataAssociationViolation(NodeRole.DATA_OBJECT, NodeRole.DATA_OBJECT, false));
    assertNotNull(
        "Not with a pool",
        BpmnUtils.dataAssociationViolation(NodeRole.POOL, NodeRole.DATA_OBJECT, false));
  }

  // --- Sizes ---

  @Test
  public void testTaskSize() {
    assertArrayEquals(
        "A fitted long label keeps its width, height raised to the minimum",
        new int[] {140, BpmnUtils.MIN_TASK_HEIGHT},
        BpmnUtils.taskSize(140, 30, "Kiem tra don hang"));
    assertArrayEquals(
        "A short label is raised to the minimum width",
        new int[] {BpmnUtils.MIN_TASK_WIDTH, 50},
        BpmnUtils.taskSize(60, 50, "Pay"));
    assertArrayEquals(
        "Unmeasured text falls back to an estimate",
        new int[] {13 * 7 + 24, BpmnUtils.MIN_TASK_HEIGHT},
        BpmnUtils.taskSize(0, 0, "Approve order"));
    assertArrayEquals(
        new int[] {BpmnUtils.MIN_TASK_WIDTH, BpmnUtils.MIN_TASK_HEIGHT},
        BpmnUtils.taskSize(5, 5, null));
  }

  // --- Pools and lanes ---

  @Test
  public void testNewPoolBounds() {
    assertEquals(
        "First pool at the origin with the default size",
        new Rectangle(40, 40, 900, 160),
        BpmnUtils.newPoolBounds(0, 0, 0, 0, Collections.<Rectangle>emptyList()));
    assertEquals(
        "Next pool below the lowest one",
        new Rectangle(40, 340 + BpmnUtils.POOL_GAP, 900, 160),
        BpmnUtils.newPoolBounds(0, 0, 0, 0, Arrays.asList(new Rectangle(40, 40, 900, 300))));
    assertEquals(
        "Explicit bounds are kept",
        new Rectangle(100, 50, 1200, 400),
        BpmnUtils.newPoolBounds(100, 50, 1200, 400, Collections.<Rectangle>emptyList()));
    assertEquals(
        "Tiny explicit sizes are raised",
        new Rectangle(40, 40, BpmnUtils.MIN_POOL_WIDTH, BpmnUtils.MIN_POOL_HEIGHT),
        BpmnUtils.newPoolBounds(0, 0, 50, 10, Collections.<Rectangle>emptyList()));
  }

  @Test
  public void testNewLaneBounds() {
    Rectangle pool = new Rectangle(40, 40, 900, 160);
    Rectangle first = BpmnUtils.newLaneBounds(pool, Collections.<Rectangle>emptyList());
    assertEquals("First lane fills the pool body", new Rectangle(70, 40, 870, 160), first);
    Rectangle second = BpmnUtils.newLaneBounds(pool, Arrays.asList(first));
    assertEquals(
        "Next lane is stacked below", new Rectangle(70, 200, 870, BpmnUtils.LANE_HEIGHT), second);
  }

  @Test
  public void testEncloseChildren() {
    Rectangle lane = new Rectangle(70, 40, 870, 160);
    assertEquals(
        "Children inside: unchanged",
        lane,
        BpmnUtils.encloseChildren(lane, Arrays.asList(new Rectangle(100, 60, 100, 50))));
    assertEquals(
        "Grows right and down with padding, never moves",
        new Rectangle(70, 40, 1030 + BpmnUtils.PAD - 70, 230 + BpmnUtils.PAD - 40),
        BpmnUtils.encloseChildren(
            lane, Arrays.asList(new Rectangle(930, 60, 100, 50), new Rectangle(100, 190, 30, 40))));
  }

  @Test
  public void testFreeAreaAndBody() {
    assertEquals(
        "Without pools the free area starts at the origin",
        BpmnUtils.ORIGIN_Y,
        BpmnUtils.freeArea(Collections.<Rectangle>emptyList()).y);
    assertEquals(
        "With pools it starts below them",
        200 + BpmnUtils.POOL_GAP,
        BpmnUtils.freeArea(Arrays.asList(new Rectangle(40, 40, 900, 160))).y);
    assertEquals(
        "Body is right of the name band",
        new Rectangle(100, 40, 840, 160),
        BpmnUtils.body(new Rectangle(70, 40, 870, 160), BpmnUtils.LANE_HEADER));
  }

  // --- Placement ---

  @Test
  public void testNextNodePosition() {
    Rectangle body = new Rectangle(100, 40, 840, 160);
    Point first = BpmnUtils.nextNodePosition(Collections.<Rectangle>emptyList(), body, 30, 30);
    assertEquals("First node after the inset", 100 + BpmnUtils.FIRST_NODE_INSET, first.x);
    assertEquals("Centered on the first row", 40 + 80 - 15, first.y);

    List<Rectangle> nodes = Arrays.asList(new Rectangle(130, 105, 30, 30));
    Point second = BpmnUtils.nextNodePosition(nodes, body, 110, 50);
    assertEquals("Right of the last node", 160 + BpmnUtils.NODE_GAP, second.x);
    assertEquals("Same center line as the last node", 120 - 25, second.y);
  }

  @Test
  public void testDataObjectPosition() {
    Rectangle body = new Rectangle(100, 40, 840, 160);
    List<Rectangle> nodes = Arrays.asList(new Rectangle(210, 95, 110, 50));
    Point under = BpmnUtils.dataObjectPosition(nodes, Collections.<Rectangle>emptyList(), body, 40);
    assertEquals("Aligned with the last node", 210, under.x);
    assertEquals("Below it", 145 + BpmnUtils.DATA_GAP, under.y);
    Point next =
        BpmnUtils.dataObjectPosition(
            nodes, Arrays.asList(new Rectangle(210, 170, 30, 40)), body, 40);
    assertEquals("Right of the previous data object", 240 + BpmnUtils.DATA_GAP, next.x);
  }

  @Test
  public void testClampInto() {
    Rectangle body = new Rectangle(100, 40, 840, 160);
    Point left = BpmnUtils.clampInto(20, 60, 50, body);
    assertEquals("Not left of the body", 100 + BpmnUtils.MIN_INSET, left.x);
    assertEquals("Y inside the band kept", 60, left.y);
    assertEquals("Not above the lane", 50, BpmnUtils.clampInto(500, 0, 50, body).y);
    assertEquals(
        "Not below the lane", 40 + 160 - 50 - 10, BpmnUtils.clampInto(500, 900, 50, body).y);
    assertEquals("Taller than the lane: top", 50, BpmnUtils.clampInto(500, 900, 400, body).y);
    assertEquals(
        "X to the right is kept (lane grows)", 2000, BpmnUtils.clampInto(2000, 60, 50, body).x);
  }

  // --- Routing ---

  @Test
  public void testRouteStraightHorizontal() {
    Rectangle a = new Rectangle(100, 100, 110, 50);
    Rectangle b = new Rectangle(260, 100, 110, 50);
    assertEquals(
        "Right border to left border on the shared line",
        Arrays.asList(new Point(210, 125), new Point(260, 125)),
        BpmnUtils.route(a, false, b, false, false));
    assertEquals(
        "Backwards (a loop back): over the top of both shapes",
        Arrays.asList(
            new Point(315, 100), new Point(315, 75), new Point(155, 75), new Point(155, 100)),
        BpmnUtils.route(b, false, a, false, false));
  }

  @Test
  public void testRouteHitsRoundShapesThroughTheirCenter() {
    Rectangle event = new Rectangle(40, 110, 30, 30); // center y 125
    Rectangle task = new Rectangle(120, 100, 110, 50); // center y 125
    assertEquals(
        Arrays.asList(new Point(70, 125), new Point(120, 125)),
        BpmnUtils.route(event, true, task, false, false));
    Rectangle lowTask = new Rectangle(120, 115, 110, 50); // shares the band, center y 140
    assertEquals(
        "The event's center line is used, not the overlap's middle",
        Arrays.asList(new Point(70, 125), new Point(120, 125)),
        BpmnUtils.route(event, true, lowTask, false, false));
  }

  @Test
  public void testRouteElbowWhenRoundCentersDiffer() {
    Rectangle gateway = new Rectangle(100, 100, 40, 40); // center y 120
    Rectangle end = new Rectangle(200, 110, 30, 30); // center y 125
    assertEquals(
        "Z elbow through the middle of the gap",
        Arrays.asList(
            new Point(140, 120), new Point(170, 120), new Point(170, 125), new Point(200, 125)),
        BpmnUtils.route(gateway, true, end, true, false));
  }

  @Test
  public void testRouteDiagonal() {
    Rectangle gateway = new Rectangle(100, 100, 40, 40); // center (120,120)
    Rectangle task = new Rectangle(200, 260, 110, 50); // center (255,285), lower lane
    assertEquals(
        "A gateway branch to a lower row leaves through the bottom vertex",
        Arrays.asList(new Point(120, 140), new Point(120, 285), new Point(200, 285)),
        BpmnUtils.route(gateway, true, task, false, false));
    assertEquals(
        "A join gateway on a lower row is entered through its top vertex",
        Arrays.asList(new Point(310, 285), new Point(370, 285), new Point(370, 320)),
        BpmnUtils.route(
            new Rectangle(200, 260, 110, 50), false, new Rectangle(350, 320, 40, 40), true, false));
    assertEquals(
        "Message flows leave through top/bottom",
        Arrays.asList(
            new Point(120, 140), new Point(120, 200), new Point(255, 200), new Point(255, 260)),
        BpmnUtils.route(gateway, true, task, false, true));
  }

  @Test
  public void testRouteVerticalBetweenPools() {
    Rectangle task = new Rectangle(200, 100, 110, 50); // center x 255
    Rectangle pool = new Rectangle(40, 300, 900, 160);
    assertEquals(
        "Straight down from the task into the pool below",
        Arrays.asList(new Point(255, 150), new Point(255, 300)),
        BpmnUtils.route(task, false, pool, false, true));
    assertEquals(
        "Straight up into the task",
        Arrays.asList(new Point(255, 300), new Point(255, 150)),
        BpmnUtils.route(pool, false, task, false, true));
  }

  @Test
  public void testRouteOverlappingShapes() {
    assertNull(
        "Overlapping shapes have no border-to-border route",
        BpmnUtils.route(
            new Rectangle(0, 0, 100, 100), false, new Rectangle(50, 50, 100, 100), false, false));
  }

  @Test
  public void testSharedLine() {
    assertEquals(Integer.valueOf(125), BpmnUtils.sharedLine(100, 50, false, 110, 30, true));
    assertNull("Too little overlap", BpmnUtils.sharedLine(100, 50, false, 145, 50, false));
    assertEquals(
        "Both round with equal centers",
        Integer.valueOf(120),
        BpmnUtils.sharedLine(100, 40, true, 105, 30, true));
    assertNull("Both round, centers apart", BpmnUtils.sharedLine(100, 40, true, 110, 30, true));
    assertEquals(
        "Two boxes: middle of the overlap",
        Integer.valueOf(130),
        BpmnUtils.sharedLine(100, 50, false, 110, 60, false));
  }

  @Test
  public void testMessageRouteUsesThePoolGapAndAFreeColumn() {
    Rectangle source = new Rectangle(100, 100, 110, 50); // upper pool, bottom 150
    Rectangle target = new Rectangle(400, 420, 110, 50); // lower lane of the lower pool
    java.util.List<Rectangle> obstacles = new java.util.ArrayList<>();
    obstacles.add(new Rectangle(420, 300, 80, 40)); // a node right above the target
    java.util.List<Point> route = BpmnUtils.messageRoute(source, target, 200, obstacles);
    assertEquals("leaves the source bottom", new Point(155, 150), route.get(0));
    assertEquals("runs along the gap", 200, route.get(1).y);
    for (int i = 0; i + 1 < route.size(); i++) {
      Point p = route.get(i);
      Point q = route.get(i + 1);
      org.junit.Assert.assertFalse(
          "leg " + i + " clear of the node",
          DiagramUtils.segmentHitsRect(p.x, p.y, q.x, q.y, new int[] {420, 300, 80, 40}, 2));
    }
    Point last = route.get(route.size() - 1);
    org.junit.Assert.assertTrue(
        "ends on the target's border", last.x == 400 || last.x == 510 || last.y == 420);
  }

  @Test
  public void branchesOfOneGatewayLeaveThroughDifferentVertices() {
    Rectangle gateway = new Rectangle(300, 300, 40, 40);
    Rectangle up = new Rectangle(420, 100, 90, 45);
    Rectangle loopBack = new Rectangle(100, 98, 90, 45);
    java.util.Set<Integer> used = new java.util.HashSet<>();
    BpmnUtils.FlowRoute first =
        BpmnUtils.routeFlow(gateway, true, up, false, used, new java.util.ArrayList<>());
    assertEquals(BpmnUtils.EXIT_TOP, first.exit());
    used.add(first.exit());
    BpmnUtils.FlowRoute second =
        BpmnUtils.routeFlow(gateway, true, loopBack, false, used, new java.util.ArrayList<>());
    assertEquals("the top vertex is taken", BpmnUtils.EXIT_LEFT, second.exit());
    assertNotEquals(first.points().get(0), second.points().get(0));
  }

  @Test
  public void aFlowAvoidsAnElementInTheWayOfItsVertex() {
    Rectangle event = new Rectangle(500, 300, 30, 30);
    Rectangle target = new Rectangle(620, 100, 90, 45);
    // another event sits straight above the source: the top vertex route runs through it
    java.util.List<Rectangle> obstacles = java.util.Arrays.asList(new Rectangle(500, 200, 30, 30));
    BpmnUtils.FlowRoute route =
        BpmnUtils.routeFlow(event, true, target, false, new java.util.HashSet<>(), obstacles);
    assertEquals(BpmnUtils.EXIT_RIGHT, route.exit());
  }

  @Test
  public void aResponseMessageFlowRunsBesideItsRequest() {
    Rectangle a = new Rectangle(100, 100, 90, 45);
    Rectangle b = new Rectangle(100, 300, 90, 45);
    java.util.List<Point> request =
        BpmnUtils.messageRoute(a, b, 220, new java.util.ArrayList<>(), 0);
    java.util.List<Point> response =
        BpmnUtils.messageRoute(b, a, 220, new java.util.ArrayList<>(), 1);
    assertNotEquals("legs apart", request.get(0).x, response.get(response.size() - 1).x);
    assertNotEquals("gap lines apart", request.get(1).y, response.get(1).y);
  }

  @Test
  public void unknownKindsAreNotMistakenForKnownOnes() {
    assertNull(BpmnUtils.parseGatewayKind("xor2"));
    assertNull(BpmnUtils.parseGatewayKind("or3"));
    assertEquals(BpmnUtils.GatewayKind.EXCLUSIVE, BpmnUtils.parseGatewayKind("XOR"));
    assertEquals(BpmnUtils.GatewayKind.EVENT_BASED, BpmnUtils.parseGatewayKind("event-based"));
    assertEquals(
        BpmnUtils.GatewayKind.EVENT_BASED, BpmnUtils.parseGatewayKind("Event Based Gateway"));
  }

  @Test
  public void aLongTaskNameWrapsIntoABoxOfReadableWidth() {
    int[] size = BpmnUtils.taskSize(410, 45, "Kiem tra tinh trang phong hat va gia gio cao diem");
    assertEquals(BpmnUtils.MAX_TASK_WIDTH, size[0]);
    assertTrue("taller to hold the extra lines", size[1] > 45);
  }

  @Test
  public void aFullRowContinuesOnASecondRow() {
    Rectangle body = new Rectangle(70, 240, 870, 160);
    List<Rectangle> row = new java.util.ArrayList<>();
    row.add(new Rectangle(100, 298, 90, 45));
    row.add(new Rectangle(820, 298, 90, 45));
    Point next = BpmnUtils.nextNodePosition(row, body, 90, 45);
    assertEquals("back at the start of the row", body.x + BpmnUtils.FIRST_NODE_INSET, next.x);
    assertTrue("below the lowest node", next.y >= 298 + 45 + BpmnUtils.NODE_GAP);
  }

  @Test
  public void aFlowBackToTheNextRowEntersTheTopOfItsTarget() {
    Rectangle end = new Rectangle(880, 98, 90, 45);
    Rectangle start = new Rectangle(100, 202, 90, 45);
    assertEquals(
        Arrays.asList(
            new Point(925, 143), new Point(925, 172), new Point(145, 172), new Point(145, 202)),
        BpmnUtils.route(end, false, start, false, false));
  }

  @Test
  public void gapLinesAlternateAroundTheMiddleOfTheGap() {
    assertEquals(0, BpmnUtils.gapLineOffset(0));
    assertEquals(BpmnUtils.MESSAGE_LINE_STEP, BpmnUtils.gapLineOffset(1));
    assertEquals(-BpmnUtils.MESSAGE_LINE_STEP, BpmnUtils.gapLineOffset(2));
    assertEquals(2 * BpmnUtils.MESSAGE_LINE_STEP, BpmnUtils.gapLineOffset(3));
  }

  @Test
  public void messageFlowsToOnePoolMeetItAtDifferentPoints() {
    Rectangle a = new Rectangle(100, 100, 90, 45);
    Rectangle c = new Rectangle(500, 100, 90, 45);
    Rectangle pool = new Rectangle(40, 300, 900, 40);
    List<Point> first =
        BpmnUtils.messageRoute(a, pool, 220, new java.util.ArrayList<>(), 0, 0, 0, 0);
    List<Point> second =
        BpmnUtils.messageRoute(c, pool, 220, new java.util.ArrayList<>(), 0, 1, 0, 1);
    Point endOfFirst = first.get(first.size() - 1);
    Point endOfSecond = second.get(second.size() - 1);
    assertEquals("the pool is entered from above", 300, endOfFirst.y);
    assertNotEquals("the second flow meets the pool at its own point", endOfFirst.x, endOfSecond.x);
  }

  @Test
  public void messageFlowsOnTheSameGapLineDoNotShareWhenTheyOverlap() {
    Rectangle a = new Rectangle(100, 100, 90, 45);
    Rectangle b = new Rectangle(400, 300, 90, 45);
    List<Point> upper = BpmnUtils.messageRoute(a, b, 220, new java.util.ArrayList<>(), 0, 0);
    List<Point> lower = BpmnUtils.messageRoute(a, b, 220, new java.util.ArrayList<>(), 1, 1);
    assertNotEquals(upper.get(1).y, lower.get(1).y);
  }
}
