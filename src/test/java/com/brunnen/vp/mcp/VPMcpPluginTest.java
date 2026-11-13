package com.brunnen.vp.mcp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

/** Test class for VPMcpPlugin. */
public class VPMcpPluginTest {

  private VPMcpPlugin plugin;

  @Before
  public void setUp() {
    plugin = new VPMcpPlugin();
  }

  @Test
  public void testPluginInstantiation() {
    assertNotNull("Plugin should be instantiated", plugin);
  }

  @Test
  public void testPluginUnloaded() {
    try {
      plugin.unloaded();
    } catch (Exception e) {
      fail("Plugin unloaded should not throw exceptions: " + e.getMessage());
    }
  }
}
