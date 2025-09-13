package com.brunnen.vp.usecase;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

/** Test class for VPUseCasePlugin. */
public class VPUseCasePluginTest {

  private VPUseCasePlugin plugin;

  @Before
  public void setUp() {
    plugin = new VPUseCasePlugin();
  }

  @Test
  public void testPluginInstantiation() {
    assertNotNull("Plugin should be instantiated", plugin);
  }

  @Test
  public void testPluginUnloaded() {
    // Test that unloaded method doesn't throw exceptions
    try {
      plugin.unloaded();
    } catch (Exception e) {
      fail("Plugin unloaded should not throw exceptions: " + e.getMessage());
    }
  }
}
