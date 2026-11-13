package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for DiagramUtils. */
public class DiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Valid name should return true", DiagramUtils.isValidName("Test"));
    assertTrue("Valid name should return true", DiagramUtils.isValidName("My Diagram"));

    assertFalse("Null name should return false", DiagramUtils.isValidName(null));
    assertFalse("Empty name should return false", DiagramUtils.isValidName(""));
    assertFalse("Whitespace name should return false", DiagramUtils.isValidName("   "));
  }

  @Test
  public void pushVectorMovesRightNeighbourRight() {
    // a grew to 390 wide over b, which sits on the same row
    assertArrayEquals(
        new int[] {180, 0},
        DiagramUtils.pushVector(new int[] {50, 350, 390, 68}, new int[] {300, 350, 160, 60}, 40));
  }

  @Test
  public void pushVectorMovesLowerNeighbourDown() {
    assertArrayEquals(
        new int[] {0, 50},
        DiagramUtils.pushVector(new int[] {50, 50, 200, 160}, new int[] {60, 200, 100, 40}, 40));
  }

  @Test
  public void pushVectorKeepsRowNeighbourOnItsRow() {
    // b starts left of a's center but on a's row: it goes right, not down
    assertArrayEquals(
        new int[] {205, 0},
        DiagramUtils.pushVector(new int[] {50, 350, 285, 50}, new int[] {170, 350, 80, 40}, 40));
  }

  @Test
  public void pushVectorLeavesClearShapesAlone() {
    assertArrayEquals(
        new int[] {0, 0},
        DiagramUtils.pushVector(new int[] {50, 50, 100, 40}, new int[] {300, 50, 100, 40}, 40));
    assertArrayEquals(
        new int[] {0, 0},
        DiagramUtils.pushVector(new int[] {300, 300, 100, 40}, new int[] {50, 50, 100, 40}, 40));
  }

  @Test
  public void firstFreeSpotSkipsOccupiedShapesOnTheRow() {
    java.util.List<int[]> occupied = new java.util.ArrayList<>();
    occupied.add(new int[] {50, 350, 390, 68});
    occupied.add(new int[] {480, 350, 259, 54});
    assertArrayEquals(
        new int[] {779, 350}, DiagramUtils.firstFreeSpot(occupied, 50, 350, 160, 40, 1400, 40));
  }

  @Test
  public void firstFreeSpotWrapsToNextRow() {
    java.util.List<int[]> occupied = new java.util.ArrayList<>();
    occupied.add(new int[] {50, 50, 300, 60});
    int[] spot = DiagramUtils.firstFreeSpot(occupied, 50, 50, 200, 40, 400, 40);
    assertArrayEquals(new int[] {50, 150}, spot);
  }

  @Test
  public void segmentHitsRect() {
    int[] r = {100, 100, 50, 50};
    assertTrue(DiagramUtils.segmentHitsRect(0, 125, 300, 125, r, 0));
    assertFalse(DiagramUtils.segmentHitsRect(0, 0, 300, 0, r, 0));
    assertTrue("inside", DiagramUtils.segmentHitsRect(110, 110, 120, 120, r, 0));
  }

  @Test
  public void typeFilterAcceptsFriendlyNamesForTheExactType() {
    assertTrue(DiagramUtils.matchesTypeFilter("InteractionDiagram", "Sequence"));
    assertTrue(DiagramUtils.matchesTypeFilter("InteractionDiagram", "sequence diagram"));
    assertTrue(DiagramUtils.matchesTypeFilter("StateDiagram", "StateMachine"));
    assertTrue(DiagramUtils.matchesTypeFilter("StateDiagram", "State Machine"));
    assertTrue(DiagramUtils.matchesTypeFilter("CommunicationDiagram", "Collaboration"));
    assertTrue(DiagramUtils.matchesTypeFilter("ERDiagram", "ER"));
    assertTrue(DiagramUtils.matchesTypeFilter("GaneSarsonDFD", "DFD"));
    assertTrue(DiagramUtils.matchesTypeFilter("ClassDiagram", ""));
    assertTrue(DiagramUtils.matchesTypeFilter("ClassDiagram", null));
  }

  @Test
  public void typeFilterNeverMatchesPartOfAnotherTypeName() {
    // "ER" used to match "InteractionDiagram" (inteRactionDiagram) as a substring
    assertFalse(DiagramUtils.matchesTypeFilter("InteractionDiagram", "ER"));
    assertFalse(DiagramUtils.matchesTypeFilter("ClassDiagram", "Diagram"));
    assertFalse(DiagramUtils.matchesTypeFilter("StateDiagram", "Sequence"));
    assertTrue(DiagramUtils.matchesTypeFilter("Flowchart", "flowchart"));
    assertTrue(DiagramUtils.matchesTypeFilter("ObjectDiagram", "ObjectDiagram"));
    assertTrue(DiagramUtils.diagramTypesFor("bogus").isEmpty());
  }

  @Test
  public void labelSpotsBesideStartAboveAShallowLineAndRightOfASteepOne() {
    java.util.List<int[]> flat =
        DiagramUtils.labelSpotsBeside(new int[][] {{100, 100}, {300, 100}}, 80, 14);
    assertTrue("above the line, centered", flat.get(0)[1] + 14 <= 100 && flat.get(0)[0] == 160);
    java.util.List<int[]> steep =
        DiagramUtils.labelSpotsBeside(new int[][] {{100, 100}, {100, 300}}, 80, 14);
    assertTrue("right of the line", steep.get(0)[0] > 100);
  }
}
