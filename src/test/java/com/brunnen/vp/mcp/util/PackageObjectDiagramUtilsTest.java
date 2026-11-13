package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

/** Test class for PackageObjectDiagramUtils. */
public class PackageObjectDiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Named element is valid", PackageObjectDiagramUtils.isValidName("Sales"));
    assertFalse("Null name is invalid", PackageObjectDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", PackageObjectDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", PackageObjectDiagramUtils.isValidName("   "));
  }

  @Test
  public void testParseDependencyKindCanonical() {
    assertEquals("use", PackageObjectDiagramUtils.parseDependencyKind("use"));
    assertEquals("import", PackageObjectDiagramUtils.parseDependencyKind("import"));
    assertEquals("access", PackageObjectDiagramUtils.parseDependencyKind("access"));
    assertEquals("merge", PackageObjectDiagramUtils.parseDependencyKind("merge"));
  }

  @Test
  public void testParseDependencyKindLenient() {
    assertEquals(
        "Case and spaces are ignored",
        "import",
        PackageObjectDiagramUtils.parseDependencyKind("  IMPORT "));
    assertEquals(
        "Angle brackets are stripped",
        "merge",
        PackageObjectDiagramUtils.parseDependencyKind("<<merge>>"));
    assertEquals(
        "Guillemets are stripped",
        "access",
        PackageObjectDiagramUtils.parseDependencyKind("«access»"));
    assertEquals("Usage means use", "use", PackageObjectDiagramUtils.parseDependencyKind("usage"));
    assertEquals("Uses means use", "use", PackageObjectDiagramUtils.parseDependencyKind("uses"));
    assertEquals(
        "Imports means import", "import", PackageObjectDiagramUtils.parseDependencyKind("imports"));
  }

  @Test
  public void testParseDependencyKindDefaultsAndUnknown() {
    assertEquals(
        "Null defaults to use", "use", PackageObjectDiagramUtils.parseDependencyKind(null));
    assertEquals(
        "Blank defaults to use", "use", PackageObjectDiagramUtils.parseDependencyKind(" "));
    assertNull("Unknown kind", PackageObjectDiagramUtils.parseDependencyKind("extend"));
    assertNull("Unknown kind", PackageObjectDiagramUtils.parseDependencyKind("<<friend>>"));
  }

  @Test
  public void testStereotypeLabel() {
    assertEquals("<<import>>", PackageObjectDiagramUtils.stereotypeLabel("import"));
  }

  @Test
  public void testFormatObjectLabel() {
    assertEquals("o1 : Order", PackageObjectDiagramUtils.formatObjectLabel("o1", "Order"));
    assertEquals(
        "Names are trimmed",
        "o1 : Order",
        PackageObjectDiagramUtils.formatObjectLabel(" o1 ", " Order "));
    assertEquals("Untyped object", "o1", PackageObjectDiagramUtils.formatObjectLabel("o1", ""));
    assertEquals("Untyped object", "o1", PackageObjectDiagramUtils.formatObjectLabel("o1", null));
    assertEquals(
        "Anonymous object", ": Order", PackageObjectDiagramUtils.formatObjectLabel(null, "Order"));
  }

  @Test
  public void testFormatSlot() {
    assertEquals("status = Pending", PackageObjectDiagramUtils.formatSlot("status", "Pending"));
    assertEquals(
        "Null value is empty", "note = ", PackageObjectDiagramUtils.formatSlot("note", null));
  }

  @Test
  public void testParseSlotsEmpty() {
    assertTrue(PackageObjectDiagramUtils.parseSlots(null).isEmpty());
    assertTrue(PackageObjectDiagramUtils.parseSlots("").isEmpty());
    assertTrue(PackageObjectDiagramUtils.parseSlots("  ;  ; ").isEmpty());
  }

  @Test
  public void testParseSlotsKeepsOrderAndTrims() {
    Map<String, String> slots =
        PackageObjectDiagramUtils.parseSlots(" id = 1 ; status=\"Pending\";total=250000 ");
    assertEquals(Arrays.asList("id", "status", "total"), new ArrayList<>(slots.keySet()));
    assertEquals("1", slots.get("id"));
    assertEquals("Quotes are kept", "\"Pending\"", slots.get("status"));
    assertEquals("250000", slots.get("total"));
  }

  @Test
  public void testParseSlotsValueMayContainEqualsAndBeEmpty() {
    Map<String, String> slots = PackageObjectDiagramUtils.parseSlots("expr=a=b\nnote=");
    assertEquals("Only the first '=' splits", "a=b", slots.get("expr"));
    assertEquals("Empty value is allowed", "", slots.get("note"));
  }

  @Test
  public void testParseSlotsRejectsMalformed() {
    assertInvalidSlots("status");
    assertInvalidSlots("=Pending");
    assertInvalidSlots("id=1;id=2");
  }

  private static void assertInvalidSlots(String slots) {
    try {
      PackageObjectDiagramUtils.parseSlots(slots);
      fail("Expected IllegalArgumentException for: " + slots);
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
    }
  }
}
