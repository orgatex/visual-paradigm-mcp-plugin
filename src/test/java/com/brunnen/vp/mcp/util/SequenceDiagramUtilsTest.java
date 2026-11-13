package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for SequenceDiagramUtils. */
public class SequenceDiagramUtilsTest {

  @Test
  public void testIsValidLifelineName() {
    assertTrue(
        "Valid lifeline name should return true",
        SequenceDiagramUtils.isValidLifelineName("Actor"));
    assertTrue(
        "Valid lifeline name should return true",
        SequenceDiagramUtils.isValidLifelineName("LoginController"));

    assertFalse("Null name should return false", SequenceDiagramUtils.isValidLifelineName(null));
    assertFalse("Empty name should return false", SequenceDiagramUtils.isValidLifelineName(""));
    assertFalse(
        "Whitespace name should return false", SequenceDiagramUtils.isValidLifelineName("   "));
    assertFalse(
        "Too short name should return false", SequenceDiagramUtils.isValidLifelineName("A"));
  }

  @Test
  public void testOperandStartsEvenAndExplicit() {
    org.junit.Assert.assertArrayEquals(
        new int[] {4, 5}, SequenceDiagramUtils.operandStarts(4, 5, 2, null));
    org.junit.Assert.assertArrayEquals(
        new int[] {2, 5}, SequenceDiagramUtils.operandStarts(2, 7, 2, new int[] {5}));
    org.junit.Assert.assertArrayEquals(
        "operands never start on the same message",
        new int[] {3, 4, 5},
        SequenceDiagramUtils.operandStarts(3, 3, 3, null));
  }

  @Test
  public void sequenceNumbersCompareNumberByNumber() {
    String[] ordered = {"1", "1.1", "1.1.1", "1.2", "2", "2a", "10", ""};
    for (int i = 0; i + 1 < ordered.length; i++) {
      assertTrue(
          ordered[i] + " < " + ordered[i + 1],
          SequenceDiagramUtils.compareSequenceNumbers(ordered[i], ordered[i + 1]) < 0);
      assertTrue(SequenceDiagramUtils.compareSequenceNumbers(ordered[i + 1], ordered[i]) > 0);
    }
    org.junit.Assert.assertEquals(0, SequenceDiagramUtils.compareSequenceNumbers("1.2", "1.2"));
  }

  @Test
  public void topLevelAndNextNumbers() {
    org.junit.Assert.assertEquals(4, SequenceDiagramUtils.topLevelNumber("4.2.1"));
    org.junit.Assert.assertEquals(0, SequenceDiagramUtils.topLevelNumber(""));
    org.junit.Assert.assertEquals(
        "3", SequenceDiagramUtils.nextTopLevelNumber(new String[] {"1", "2.1", "", "1.1"}));
  }

  @Test
  public void nestedMessagesGetTheirOwnRows() {
    // 1, 1.1, 1.1.1, 1.2 added in order: one row each
    String[] seqs = {"1", "1.1", "1.1.1"};
    int[] ys = {100, 136, 172};
    org.junit.Assert.assertArrayEquals(
        new int[] {208, 0}, SequenceDiagramUtils.messageSlot(seqs, ys, "1.2", 100, 36));
    org.junit.Assert.assertArrayEquals(
        new int[] {100, 0},
        SequenceDiagramUtils.messageSlot(new String[0], new int[0], "1", 100, 36));
  }

  @Test
  public void anEarlierNumberAddedLaterPushesTheRestDown() {
    String[] seqs = {"1", "2"};
    int[] ys = {100, 136};
    // 1.1 goes below 1, and 2 has to move down a full row
    org.junit.Assert.assertArrayEquals(
        new int[] {136, 36}, SequenceDiagramUtils.messageSlot(seqs, ys, "1.1", 100, 36));
  }
}
