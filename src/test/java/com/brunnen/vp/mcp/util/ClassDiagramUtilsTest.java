package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for ClassDiagramUtils. */
public class ClassDiagramUtilsTest {

  @Test
  public void testIsValidClassName() {
    assertTrue("Valid class name should return true", ClassDiagramUtils.isValidClassName("User"));
    assertTrue("Valid class name should return true", ClassDiagramUtils.isValidClassName("LoginController"));

    assertFalse("Null name should return false", ClassDiagramUtils.isValidClassName(null));
    assertFalse("Empty name should return false", ClassDiagramUtils.isValidClassName(""));
    assertFalse("Whitespace name should return false", ClassDiagramUtils.isValidClassName("   "));
    assertFalse("Too short name should return false", ClassDiagramUtils.isValidClassName("A"));
  }
}
