package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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

  @Test
  public void parseColumnTypeSplitsLengthAndScale() {
    assertArrayEquals(
        new String[] {"nvarchar", "100", "0"}, ErdUtils.parseColumnType("nvarchar(100)", 0, 0));
    assertArrayEquals(
        new String[] {"decimal", "10", "2"}, ErdUtils.parseColumnType("decimal(10, 2)", 0, 0));
    assertArrayEquals(new String[] {"int", "0", "0"}, ErdUtils.parseColumnType(" int ", 0, 0));
    assertArrayEquals(
        new String[] {"varchar", "20", "0"}, ErdUtils.parseColumnType("varchar", 20, 0));
  }

  @Test
  public void typeCandidatesMapSqlServerTypes() {
    assertEquals(
        java.util.Arrays.asList("nvarchar", "varchar"), ErdUtils.typeCandidates("nvarchar"));
    assertEquals(
        java.util.Arrays.asList("datetime", "timestamp"), ErdUtils.typeCandidates("datetime"));
    assertEquals(java.util.Arrays.asList("varchar"), ErdUtils.typeCandidates("varchar"));
  }

  @Test
  public void sqlIdentifiersAreAsciiWordsWithoutSpaces() {
    org.junit.Assert.assertTrue(ErdUtils.isSqlIdentifier("tblHoaDon"));
    org.junit.Assert.assertTrue(ErdUtils.isSqlIdentifier("_x1"));
    org.junit.Assert.assertFalse(ErdUtils.isSqlIdentifier("ti\u1ebfng vi\u1ec7t"));
    org.junit.Assert.assertFalse(ErdUtils.isSqlIdentifier("1abc"));
    org.junit.Assert.assertFalse(ErdUtils.isSqlIdentifier(""));
    org.junit.Assert.assertFalse(ErdUtils.isSqlIdentifier(null));
  }

  @Test
  public void varcharWithoutLengthGets255() {
    org.junit.Assert.assertArrayEquals(
        new String[] {"varchar", "255", "0"}, ErdUtils.parseColumnType("varchar", 0, 0));
    org.junit.Assert.assertArrayEquals(
        new String[] {"varchar", "40", "0"}, ErdUtils.parseColumnType("varchar", 40, 0));
    org.junit.Assert.assertArrayEquals(
        new String[] {"int", "0", "0"}, ErdUtils.parseColumnType("int", 0, 0));
  }
}
