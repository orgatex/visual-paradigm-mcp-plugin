package com.brunnen.vp.mcp.util;

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
}
