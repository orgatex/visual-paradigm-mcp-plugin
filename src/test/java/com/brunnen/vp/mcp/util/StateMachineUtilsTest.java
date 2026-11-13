package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for StateMachineUtils. */
public class StateMachineUtilsTest {

  @Test
  public void testIsValidStateName() {
    assertTrue("Named state is valid", StateMachineUtils.isValidStateName("Approved"));
    assertFalse("Null name is invalid", StateMachineUtils.isValidStateName(null));
    assertFalse("Empty name is invalid", StateMachineUtils.isValidStateName(""));
    assertFalse("Blank name is invalid", StateMachineUtils.isValidStateName("   "));
  }

  @Test
  public void testNormalizeTransitionLabel() {
    assertEquals(
        "Label is trimmed",
        "export() [enough stock]",
        StateMachineUtils.normalizeTransitionLabel("  export() [enough stock]  "));
    assertNull("Null label stays null", StateMachineUtils.normalizeTransitionLabel(null));
    assertNull("Empty label becomes null", StateMachineUtils.normalizeTransitionLabel(""));
    assertNull("Blank label becomes null", StateMachineUtils.normalizeTransitionLabel("  "));
  }

  @Test
  public void testSameLabel() {
    assertTrue(StateMachineUtils.sameLabel(null, " "));
    assertTrue(StateMachineUtils.sameLabel("a [b]", " a [b] "));
    assertFalse(StateMachineUtils.sameLabel("a", "b"));
    assertFalse(StateMachineUtils.sameLabel("a", null));
  }
}
