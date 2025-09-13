package com.brunnen.vp.usecase.action;

import org.junit.Test;

/** Basic test for AddRelationshipActionController. */
public class AddRelationshipActionControllerTest {

  @Test
  public void testActionControllerCreation() {
    AddRelationshipActionController controller = new AddRelationshipActionController();
    // Basic test that the controller can be created
    assert controller != null : "Controller should not be null";
  }

  @Test
  public void testControllerImplementsInterface() {
    AddRelationshipActionController controller = new AddRelationshipActionController();
    // Verify that the controller implements the VPActionController interface
    assert controller instanceof com.vp.plugin.action.VPActionController
        : "Controller should implement VPActionController";
  }
}
