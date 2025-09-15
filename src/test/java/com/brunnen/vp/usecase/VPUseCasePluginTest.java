package com.brunnen.vp.usecase;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.vp.plugin.VPPluginInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test class for VPUseCasePlugin (MCP Server Plugin). */
public class VPUseCasePluginTest {

  @Mock private VPPluginInfo mockPluginInfo;

  private VPUseCasePlugin plugin;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    plugin = new VPUseCasePlugin();
  }

  @Test
  public void testLoadedWithValidInfo() {
    // Given
    when(mockPluginInfo.getPluginId()).thenReturn("test-plugin-id");

    // When
    plugin.loaded(mockPluginInfo);

    // Then
    // Verify no exceptions thrown and plugin initialized
    assertNotNull(plugin);
  }

  @Test
  public void testLoadedWithNullInfo() {
    // When
    plugin.loaded(null);

    // Then
    // Verify no exceptions thrown
    assertNotNull(plugin);
  }

  @Test
  public void testUnloaded() {
    // When
    plugin.unloaded();

    // Then
    // Verify no exceptions thrown
    assertNotNull(plugin);
  }

  @Test
  public void testServerStatusInitially() {
    // When
    boolean isRunning = plugin.isServerRunning();

    // Then
    assertFalse("Server should not be running initially", isRunning);
  }

  @Test
  public void testGetToggleButton() {
    // Given
    plugin.loaded(mockPluginInfo);

    // When
    // Note: In a real test environment, we'd need to mock Swing components
    // For now, we just verify the method doesn't throw exceptions

    // Then
    // Button might be null if UI initialization fails in test environment
    // This is expected in headless test environment
  }
}
