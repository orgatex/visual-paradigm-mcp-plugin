package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Test class for CommunicationDiagramUtils. */
public class CommunicationDiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Named lifeline is valid", CommunicationDiagramUtils.isValidName("OrderDAO"));
    assertFalse("Null name is invalid", CommunicationDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", CommunicationDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", CommunicationDiagramUtils.isValidName("  "));
  }

  @Test
  public void testClean() {
    assertEquals("Value is trimmed", "Staff", CommunicationDiagramUtils.clean("  Staff "));
    assertNull("Null stays null", CommunicationDiagramUtils.clean(null));
    assertNull("Blank becomes null", CommunicationDiagramUtils.clean("   "));
  }

  @Test
  public void testNormalizeSequenceNumber() {
    assertEquals("Trimmed", "1.2", CommunicationDiagramUtils.normalizeSequenceNumber(" 1.2 "));
    assertEquals(
        "Trailing colon dropped", "3", CommunicationDiagramUtils.normalizeSequenceNumber("3 :"));
    assertNull("Blank becomes null", CommunicationDiagramUtils.normalizeSequenceNumber(" "));
    assertNull("Lone colon becomes null", CommunicationDiagramUtils.normalizeSequenceNumber(":"));
    assertNull("Null stays null", CommunicationDiagramUtils.normalizeSequenceNumber(null));
  }

  @Test
  public void testIsValidSequenceNumber() {
    assertTrue("Plain number", CommunicationDiagramUtils.isValidSequenceNumber("1"));
    assertTrue("Nested number", CommunicationDiagramUtils.isValidSequenceNumber("1.2.10"));
    assertTrue("Thread letter", CommunicationDiagramUtils.isValidSequenceNumber("2a"));
    assertTrue("Nested thread letter", CommunicationDiagramUtils.isValidSequenceNumber("1.2b"));
    assertTrue("Recurrence star", CommunicationDiagramUtils.isValidSequenceNumber("1.1*"));
    assertFalse("Must start with a digit", CommunicationDiagramUtils.isValidSequenceNumber("a1"));
    assertFalse("No empty level", CommunicationDiagramUtils.isValidSequenceNumber("1..2"));
    assertFalse("No trailing dot", CommunicationDiagramUtils.isValidSequenceNumber("1."));
    assertFalse("No spaces", CommunicationDiagramUtils.isValidSequenceNumber("1 2"));
    assertFalse("Null is invalid", CommunicationDiagramUtils.isValidSequenceNumber(null));
  }

  @Test
  public void testSequencePrefixOf() {
    assertEquals(
        "Dotted prefix", "1.2", CommunicationDiagramUtils.sequencePrefixOf("1.2: validate()"));
    assertEquals("Spaced prefix", "3", CommunicationDiagramUtils.sequencePrefixOf(" 3 : save()"));
    assertNull("No prefix", CommunicationDiagramUtils.sequencePrefixOf("validate()"));
    assertNull(
        "A colon later in the text is not a prefix",
        CommunicationDiagramUtils.sequencePrefixOf("getOrder(id: int)"));
    assertNull("Null text", CommunicationDiagramUtils.sequencePrefixOf(null));
  }

  @Test
  public void testStripSequencePrefix() {
    assertEquals(
        "Prefix removed",
        "validate()",
        CommunicationDiagramUtils.stripSequencePrefix("1.2: validate()"));
    assertEquals(
        "Text without prefix is only trimmed",
        "getOrder(id: int)",
        CommunicationDiagramUtils.stripSequencePrefix("  getOrder(id: int) "));
    assertEquals(
        "Only the prefix remains -> empty",
        "",
        CommunicationDiagramUtils.stripSequencePrefix("1:"));
    assertEquals("Null text -> empty", "", CommunicationDiagramUtils.stripSequencePrefix(null));
  }

  @Test
  public void testResolveMessage() {
    assertArrayEquals(
        "Explicit number wins and the prefix is still stripped",
        new String[] {"2", "save()"},
        CommunicationDiagramUtils.resolveMessage("2", "1.1: save()"));
    assertArrayEquals(
        "Prefix used when no explicit number",
        new String[] {"1.1", "save()"},
        CommunicationDiagramUtils.resolveMessage("", "1.1: save()"));
    assertArrayEquals(
        "No number anywhere",
        new String[] {null, "save()"},
        CommunicationDiagramUtils.resolveMessage(null, " save() "));
  }

  @Test
  public void testFormatMessageLabel() {
    assertEquals(
        "Number then text",
        "1.2: validate()",
        CommunicationDiagramUtils.formatMessageLabel("1.2", "validate()"));
    assertEquals(
        "Text only without number",
        "validate()",
        CommunicationDiagramUtils.formatMessageLabel(" ", "validate()"));
  }

  @Test
  public void testTopLevelNumber() {
    assertEquals("Plain", 3, CommunicationDiagramUtils.topLevelNumber("3"));
    assertEquals("Nested", 1, CommunicationDiagramUtils.topLevelNumber("1.2.3"));
    assertEquals("Thread letter", 12, CommunicationDiagramUtils.topLevelNumber("12a"));
    assertEquals("Not numeric", -1, CommunicationDiagramUtils.topLevelNumber("*"));
    assertEquals("Blank", -1, CommunicationDiagramUtils.topLevelNumber(" "));
    assertEquals("Too large to parse", -1, CommunicationDiagramUtils.topLevelNumber("12345678901"));
  }

  @Test
  public void testNextSequenceNumber() {
    assertEquals(
        "Empty diagram starts at 1",
        "1",
        CommunicationDiagramUtils.nextSequenceNumber(Collections.<String>emptyList()));
    assertEquals("Null list starts at 1", "1", CommunicationDiagramUtils.nextSequenceNumber(null));
    assertEquals(
        "One past the highest top-level number",
        "4",
        CommunicationDiagramUtils.nextSequenceNumber(Arrays.asList("1", "3.2", null, "2a", "x")));
  }
}
