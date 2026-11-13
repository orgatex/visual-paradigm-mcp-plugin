package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PortPlacerTest {

  private static final int[] OWNER = {100, 200, 130, 40};

  private static PortPlacer.Placed one(PortPlacer.Port port) {
    List<PortPlacer.Port> ports = new ArrayList<>();
    ports.add(port);
    return PortPlacer.place(OWNER, ports).get(0);
  }

  @Test
  public void aPortStandsOnTheSideItsConnectorsLeadAwayTo() {
    // the other end is far above: north, straight above it
    PortPlacer.Placed north = one(new PortPlacer.Port(12, 40, 1, 165, 20));
    assertEquals(0, north.side());
    assertEquals("straddles the border", OWNER[1] - 6, north.rect()[1]);
    assertEquals("above the other end", 165, north.rect()[0] + 6);
    // far to the right: east, at the height of the other end when the side is long enough
    PortPlacer.Placed east = one(new PortPlacer.Port(12, 40, 1, 900, 220));
    assertEquals(1, east.side());
    assertEquals(OWNER[0] + OWNER[2] - 6, east.rect()[0]);
    PortPlacer.Placed south = one(new PortPlacer.Port(12, 40, 1, 150, 700));
    assertEquals(2, south.side());
    PortPlacer.Placed west = one(new PortPlacer.Port(12, 40, 1, -500, 220));
    assertEquals(3, west.side());
    assertEquals(OWNER[0] - 6, west.rect()[0]);
  }

  @Test
  public void aPortWithoutConnectorsGoesToTheEastThenTheWest() {
    List<PortPlacer.Port> ports = new ArrayList<>();
    ports.add(new PortPlacer.Port(12, 40));
    ports.add(new PortPlacer.Port(12, 40));
    List<PortPlacer.Placed> placed = PortPlacer.place(OWNER, ports);
    assertEquals(1, placed.get(0).side());
    assertEquals(3, placed.get(1).side());
  }

  @Test
  public void portsOnOneSideKeepTheirNamesApart() {
    List<PortPlacer.Port> ports = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      ports.add(new PortPlacer.Port(12, 50, 1, 165 + 4 * i, 20));
    }
    List<PortPlacer.Placed> placed = PortPlacer.place(OWNER, ports);
    assertEquals(0, placed.get(0).side());
    assertEquals(0, placed.get(1).side());
    int distance = Math.abs(placed.get(0).rect()[0] - placed.get(1).rect()[0]);
    assertTrue(
        "the name of one port must not sit on the line of the next: " + distance,
        distance >= 12 + 50);
  }

  @Test
  public void aFullSideSendsTheNextPortToAnotherSide() {
    // a 40 px high component holds one port per vertical side: three ports to the east spread out
    List<PortPlacer.Port> ports = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      ports.add(new PortPlacer.Port(12, 40, 1, 900, 220 + i));
    }
    List<PortPlacer.Placed> placed = PortPlacer.place(OWNER, ports);
    int east = 0;
    for (PortPlacer.Placed p : placed) {
      east += p.side() == 1 ? 1 : 0;
    }
    assertEquals(1, east);
  }

  @Test
  public void everyPortStaysOnTheBorderOfItsOwner() {
    List<PortPlacer.Port> ports = new ArrayList<>();
    ports.add(new PortPlacer.Port(12, 60, 1, 30, 20));
    ports.add(new PortPlacer.Port(12, 60, 1, 400, 300));
    ports.add(new PortPlacer.Port(12, 60, 1, 150, 500));
    ports.add(new PortPlacer.Port(12, 60));
    for (PortPlacer.Placed p : PortPlacer.place(OWNER, ports)) {
      int[] r = p.rect();
      int cx = r[0] + r[2] / 2;
      int cy = r[1] + r[3] / 2;
      boolean onVertical = cx == OWNER[0] || cx == OWNER[0] + OWNER[2];
      boolean onHorizontal = cy == OWNER[1] || cy == OWNER[1] + OWNER[3];
      assertTrue("on the border: " + java.util.Arrays.toString(r), onVertical || onHorizontal);
      assertTrue(cx >= OWNER[0] && cx <= OWNER[0] + OWNER[2]);
      assertTrue(cy >= OWNER[1] && cy <= OWNER[1] + OWNER[3]);
    }
  }

  @Test
  public void aPortThatSeveralLinesEndOnIsLongEnoughForAnEndOfEachLine() {
    PortPlacer.Placed north = one(new PortPlacer.Port(12, 40, 3, 165, 20));
    assertEquals(0, north.side());
    assertEquals("thin across the border", 12, north.rect()[3]);
    assertTrue(
        "room for three ends 12 apart plus margins: " + north.rect()[2], north.rect()[2] >= 3 * 12);
    PortPlacer.Placed single = one(new PortPlacer.Port(12, 40, 1, 165, 20));
    assertEquals("a port with one line stays a square", 12, single.rect()[2]);
    PortPlacer.Placed east = one(new PortPlacer.Port(12, 40, 2, 900, 220));
    assertEquals(1, east.side());
    assertEquals("thin across the border", 12, east.rect()[2]);
    assertTrue("longer along the border", east.rect()[3] > 12);
  }

  @Test
  public void twoPortsWhoseNamesDoNotFitAlongOneSideDoNotShareIt() {
    // both lead north; a 130 px side holds one name of 68 px with its pitch, not two
    List<PortPlacer.Port> ports = new ArrayList<>();
    ports.add(new PortPlacer.Port(12, 68, 2, 150, 20));
    ports.add(new PortPlacer.Port(12, 68, 1, 170, 20));
    List<PortPlacer.Placed> placed = PortPlacer.place(OWNER, ports);
    assertTrue(placed.get(0).side() != placed.get(1).side());
  }
}
