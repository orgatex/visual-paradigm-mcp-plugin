package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ToolResultsTest {

  @Test
  public void successSentencesStayAsTheyAre() {
    assertEquals(
        "Unchanged: the primary key of 'T' is id",
        ToolResults.normalize("Unchanged: the primary key of 'T' is id"));
    assertEquals("Wrote /x/a.sql", ToolResults.normalize("Wrote /x/a.sql"));
    assertEquals(
        "Primary key changed: T: a (was b)",
        ToolResults.normalize("Primary key changed: T: a (was b)"));
  }

  @Test
  public void failuresGetErrorPrefix() {
    assertEquals(
        "Error: Table not on diagram: X", ToolResults.normalize("Table not on diagram: X"));
    assertEquals(
        "Error: diagramName is required: choose",
        ToolResults.normalize("diagramName is required: choose"));
  }
}
