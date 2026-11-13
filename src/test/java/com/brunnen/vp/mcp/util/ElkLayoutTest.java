package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ElkLayoutTest {

  private static List<String> problems(
      List<ElkLayout.Node> nodes, List<ElkLayout.Edge> edges, ElkLayout.Result r) {
    Map<String, String> parents = new HashMap<>();
    for (ElkLayout.Node n : nodes) {
      parents.put(n.id, n.parent);
    }
    List<LayoutInvariants.Route> routes = new ArrayList<>();
    List<int[]> labels = new ArrayList<>();
    for (ElkLayout.Edge e : edges) {
      routes.add(new LayoutInvariants.Route(e.id, e.src, e.tgt, r.routes().get(e.id)));
      labels.addAll(r.labels().get(e.id));
    }
    return LayoutInvariants.check(
        LayoutInvariants.shapesOf(r.nodes(), parents), routes, labels, 12);
  }

  @Test
  public void classLikeGraphKeepsEverySegmentHorizontalOrVertical() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    for (String n : new String[] {"Person", "Customer", "Employee", "Order", "Room", "Booking"}) {
      nodes.add(new ElkLayout.Node(n, 140, 90));
    }
    String[][] pairs = {
      {"Person", "Customer"},
      {"Person", "Employee"},
      {"Customer", "Order"},
      {"Order", "Room"},
      {"Order", "Booking"},
      {"Booking", "Room"},
      {"Employee", "Booking"}
    };
    List<ElkLayout.Edge> edges = new ArrayList<>();
    int i = 0;
    for (String[] p : pairs) {
      ElkLayout.Edge e = new ElkLayout.Edge("e" + i++, p[0], p[1]);
      e.label(new ElkLayout.Label(24, 14, ElkLayout.Slot.TAIL));
      e.label(new ElkLayout.Label(24, 14, ElkLayout.Slot.HEAD));
      edges.add(e);
    }
    ElkLayout.Result r =
        ElkLayout.layout(nodes, edges, new ElkLayout.Options().direction(ElkLayout.Dir.DOWN));
    assertEquals(nodes.size(), r.nodes().size());
    assertTrue(problems(nodes, edges, r).toString(), problems(nodes, edges, r).isEmpty());
    for (ElkLayout.Edge e : edges) {
      assertTrue(r.routes().get(e.id).size() >= 2);
    }
  }

  @Test
  public void parentAboveChildInFlowDirection() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    nodes.add(new ElkLayout.Node("parent", 100, 60));
    nodes.add(new ElkLayout.Node("child", 100, 60));
    List<ElkLayout.Edge> edges = new ArrayList<>();
    edges.add(new ElkLayout.Edge("g", "parent", "child"));
    ElkLayout.Result r =
        ElkLayout.layout(nodes, edges, new ElkLayout.Options().direction(ElkLayout.Dir.DOWN));
    assertTrue(r.nodes().get("parent")[1] < r.nodes().get("child")[1]);
  }

  @Test
  public void firstLayerNodesStandLeftWhenFlowingRight() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    nodes.add(new ElkLayout.Node("actor", 30, 60).layer(ElkLayout.FIRST_LAYER));
    nodes.add(new ElkLayout.Node("a", 100, 40));
    nodes.add(new ElkLayout.Node("b", 100, 40));
    List<ElkLayout.Edge> edges = new ArrayList<>();
    edges.add(new ElkLayout.Edge("e1", "a", "b"));
    edges.add(new ElkLayout.Edge("e2", "actor", "b"));
    ElkLayout.Result r =
        ElkLayout.layout(nodes, edges, new ElkLayout.Options().direction(ElkLayout.Dir.RIGHT));
    int[] actor = r.nodes().get("actor");
    int[] a = r.nodes().get("a");
    // the same (first) layer as the other source: the layer's nodes end at the same x
    assertEquals(a[0] + a[2], actor[0] + actor[2]);
    assertTrue(actor[0] + actor[2] < r.nodes().get("b")[0]);
    assertTrue(problems(nodes, edges, r).isEmpty());
  }

  @Test
  public void containerEnclosesItsChildren() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    nodes.add(new ElkLayout.Node("actor", 30, 60).layer(ElkLayout.FIRST_LAYER));
    nodes.add(new ElkLayout.Node("box", 100, 100));
    nodes.add(new ElkLayout.Node("u1", 110, 40).in("box"));
    nodes.add(new ElkLayout.Node("u2", 110, 40).in("box"));
    nodes.add(new ElkLayout.Node("u3", 110, 40).in("box"));
    List<ElkLayout.Edge> edges = new ArrayList<>();
    edges.add(new ElkLayout.Edge("a1", "actor", "u1"));
    edges.add(new ElkLayout.Edge("a2", "actor", "u2"));
    edges.add(new ElkLayout.Edge("i", "u1", "u3"));
    ElkLayout.Result r =
        ElkLayout.layout(
            nodes, edges, new ElkLayout.Options().direction(ElkLayout.Dir.RIGHT).padding(44, 30));
    int[] box = r.nodes().get("box");
    for (String id : new String[] {"u1", "u2", "u3"}) {
      int[] c = r.nodes().get(id);
      assertTrue(id, c[0] >= box[0] && c[1] >= box[1]);
      assertTrue(id, c[0] + c[2] <= box[0] + box[2] && c[1] + c[3] <= box[1] + box[3]);
      assertTrue("title room for " + id, c[1] >= box[1] + 40);
    }
    assertTrue(problems(nodes, edges, r).toString(), problems(nodes, edges, r).isEmpty());
  }

  @Test
  public void oppositeEdgesAndSelfLoopsAreRectangularAndApart() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    nodes.add(new ElkLayout.Node("A", 100, 50));
    nodes.add(new ElkLayout.Node("B", 100, 50));
    List<ElkLayout.Edge> edges = new ArrayList<>();
    edges.add(new ElkLayout.Edge("ab", "A", "B"));
    edges.add(new ElkLayout.Edge("ba", "B", "A"));
    edges.add(new ElkLayout.Edge("aa", "A", "A"));
    ElkLayout.Result r = ElkLayout.layout(nodes, edges, new ElkLayout.Options());
    assertEquals(4, r.routes().get("aa").size());
    assertTrue(problems(nodes, edges, r).toString(), problems(nodes, edges, r).isEmpty());
  }

  @Test
  public void rowsPutRolesInOneLayerEach() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    nodes.add(new ElkLayout.Node("actor", 40, 60).row(0));
    nodes.add(new ElkLayout.Node("b1", 60, 40).row(1));
    nodes.add(new ElkLayout.Node("b2", 60, 40).row(1));
    nodes.add(new ElkLayout.Node("e1", 60, 40).row(2));
    nodes.add(new ElkLayout.Node("e2", 60, 40).row(2));
    List<ElkLayout.Edge> edges = new ArrayList<>();
    edges.add(new ElkLayout.Edge("a1", "actor", "b1"));
    edges.add(new ElkLayout.Edge("a2", "actor", "b2"));
    edges.add(new ElkLayout.Edge("x1", "b1", "e1"));
    edges.add(new ElkLayout.Edge("x2", "b2", "e2"));
    ElkLayout.Result r =
        ElkLayout.layout(nodes, edges, new ElkLayout.Options().direction(ElkLayout.Dir.DOWN));
    assertEquals(r.nodes().get("b1")[1], r.nodes().get("b2")[1]);
    assertEquals(r.nodes().get("e1")[1], r.nodes().get("e2")[1]);
    assertTrue(r.nodes().get("actor")[1] < r.nodes().get("b1")[1]);
    assertTrue(r.nodes().get("b1")[1] < r.nodes().get("e1")[1]);
  }

  @Test
  public void outsideLabelOnTheRightKeepsNeighboursAway() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    nodes.add(new ElkLayout.Node("x", 30, 30).outsideLabel(120, 16, ElkLayout.Side.RIGHT));
    nodes.add(new ElkLayout.Node("y", 100, 40));
    nodes.add(new ElkLayout.Node("z", 100, 40));
    List<ElkLayout.Edge> edges = new ArrayList<>();
    edges.add(new ElkLayout.Edge("xz", "x", "z"));
    edges.add(new ElkLayout.Edge("yz", "y", "z"));
    ElkLayout.Result r =
        ElkLayout.layout(
            nodes, edges, new ElkLayout.Options().direction(ElkLayout.Dir.DOWN).flowOrder());
    int[] x = r.nodes().get("x");
    int[] y = r.nodes().get("y");
    assertTrue("one layer", Math.abs(x[1] + x[3] / 2 - (y[1] + y[3] / 2)) <= 12);
    int gap = x[0] < y[0] ? y[0] - (x[0] + x[2]) : x[0] - (y[0] + y[2]);
    if (x[0] < y[0]) {
      assertTrue("room for the name right of x: " + gap, gap >= 120);
    }
  }

  @Test
  public void flowOrderBreaksTheCycleAtTheSource() {
    List<ElkLayout.Node> nodes = new ArrayList<>();
    nodes.add(new ElkLayout.Node("start", 30, 30).layer(ElkLayout.FIRST_LAYER));
    nodes.add(new ElkLayout.Node("a", 100, 40));
    nodes.add(new ElkLayout.Node("b", 100, 40));
    nodes.add(new ElkLayout.Node("c", 100, 40));
    List<ElkLayout.Edge> edges = new ArrayList<>();
    edges.add(new ElkLayout.Edge("s", "start", "a"));
    edges.add(new ElkLayout.Edge("ab", "a", "b"));
    edges.add(new ElkLayout.Edge("bc", "b", "c"));
    edges.add(new ElkLayout.Edge("ca", "c", "a")); // the way back
    ElkLayout.Result r =
        ElkLayout.layout(
            nodes, edges, new ElkLayout.Options().direction(ElkLayout.Dir.DOWN).flowOrder());
    assertTrue(r.nodes().get("start")[1] < r.nodes().get("a")[1]);
    assertTrue(r.nodes().get("a")[1] < r.nodes().get("b")[1]);
    assertTrue(r.nodes().get("b")[1] < r.nodes().get("c")[1]);
  }
}
