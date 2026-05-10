package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for ErdUtils. */
public class ErdUtilsTest {

  @Test
  public void testIsValidTableName() {
    assertTrue("Valid table name should return true", ErdUtils.isValidTableName("Users"));
    assertTrue("Valid table name should return true", ErdUtils.isValidTableName("BookingDetails"));

    assertFalse("Null name should return false", ErdUtils.isValidTableName(null));
    assertFalse("Empty name should return false", ErdUtils.isValidTableName(""));
    assertFalse("Whitespace name should return false", ErdUtils.isValidTableName("   "));
    assertFalse("Too short name should return false", ErdUtils.isValidTableName("A"));
  }
}
