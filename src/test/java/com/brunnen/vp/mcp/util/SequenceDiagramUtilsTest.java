package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for SequenceDiagramUtils. */
public class SequenceDiagramUtilsTest {

  @Test
  public void testIsValidLifelineName() {
    assertTrue("Valid lifeline name should return true", SequenceDiagramUtils.isValidLifelineName("Actor"));
    assertTrue("Valid lifeline name should return true", SequenceDiagramUtils.isValidLifelineName("LoginController"));

    assertFalse("Null name should return false", SequenceDiagramUtils.isValidLifelineName(null));
    assertFalse("Empty name should return false", SequenceDiagramUtils.isValidLifelineName(""));
    assertFalse("Whitespace name should return false", SequenceDiagramUtils.isValidLifelineName("   "));
    assertFalse("Too short name should return false", SequenceDiagramUtils.isValidLifelineName("A"));
  }
}
