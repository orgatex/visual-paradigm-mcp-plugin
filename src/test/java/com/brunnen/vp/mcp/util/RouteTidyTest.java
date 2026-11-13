package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RouteTidyTest {

  private static List<int[]> pts(int... xy) {
    List<int[]> list = new ArrayList<>();
    for (int i = 0; i < xy.length; i += 2) {
      list.add(new int[] {xy[i], xy[i + 1]});
    }
    return list;
  }

  private static Map<String, int[]> rects() {
    Map<String, int[]> rects = new HashMap<>();
    rects.put("a", new int[] {0, 220, 100, 70});
    rects.put("b", new int[] {215, 250, 120, 50});
    return rects;
  }

  @Test
  public void smallJogAtTheStartIsSlidOut() {
    List<int[]> route = pts(100, 267, 122, 267, 122, 275, 215, 275);
    List<RouteTidy.Item> items = new ArrayList<>();
    items.add(new RouteTidy.Item("a", "b", route));
    RouteTidy.straighten(items, rects(), 12, 28);
    assertEquals(2, route.size());
    assertEquals(275, route.get(0)[1]);
    assertEquals(275, route.get(1)[1]);
  }

  @Test
  public void jogThatWouldLeaveTheSideStays() {
    // neither end can slide to the other's line without leaving its side
    Map<String, int[]> rects = rects();
    rects.put("a", new int[] {0, 262, 100, 20});
    rects.put("b", new int[] {215, 276, 120, 20});
    List<int[]> route = pts(100, 267, 122, 267, 122, 280, 215, 280);
    List<RouteTidy.Item> items = new ArrayList<>();
    items.add(new RouteTidy.Item("a", "b", route));
    RouteTidy.straighten(items, rects, 12, 28);
    assertEquals(4, route.size());
  }

  @Test
  public void endsStayApartWhenSliding() {
    List<int[]> first = pts(100, 267, 122, 267, 122, 275, 215, 275);
    List<int[]> other = pts(100, 270, 150, 270, 150, 100, 400, 100);
    List<RouteTidy.Item> items = new ArrayList<>();
    items.add(new RouteTidy.Item("a", "b", first));
    items.add(new RouteTidy.Item("a", "b", other));
    RouteTidy.straighten(items, rects(), 12, 28);
    // sliding to 275 would put the end 5 px from the other one: the jog stays at the start
    assertEquals(267, first.get(0)[1]);
  }

  @Test
  public void selfLoopKeepsItsRectangle() {
    List<int[]> loop = pts(100, 235, 124, 235, 124, 251, 100, 251);
    List<RouteTidy.Item> items = new ArrayList<>();
    items.add(new RouteTidy.Item("a", "a", loop));
    RouteTidy.straighten(items, rects(), 12, 28);
    assertEquals(4, loop.size());
  }

  @Test
  public void collinearPointsAreDropped() {
    List<int[]> route = pts(0, 0, 50, 0, 100, 0, 100, 40, 100, 80);
    RouteTidy.removeCollinear(route);
    assertEquals(
        Arrays.asList(Arrays.asList(0, 0), Arrays.asList(100, 0), Arrays.asList(100, 80)),
        Arrays.asList(
            Arrays.asList(route.get(0)[0], route.get(0)[1]),
            Arrays.asList(route.get(1)[0], route.get(1)[1]),
            Arrays.asList(route.get(2)[0], route.get(2)[1])));
  }
}
