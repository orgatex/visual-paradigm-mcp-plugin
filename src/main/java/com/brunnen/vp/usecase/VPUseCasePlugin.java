package com.brunnen.vp.usecase;

import com.brunnen.vp.usecase.mcp.McpServer;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;

/**
 * Use Case Plugin for Visual Paradigm. Provides functionality for creating and managing use cases.
 */
public final class VPUseCasePlugin implements VPPlugin {

  private McpServer mcpServer;

  @Override
  public void loaded(final VPPluginInfo info) {
    // Plugin loaded - initialization code here
    String pluginId = (info != null) ? info.getPluginId() : "unknown";
    System.out.println("Use Case Plugin loaded: " + pluginId);

    // Start MCP server
    try {
      mcpServer = new McpServer();
      mcpServer.start();
      System.out.println("MCP Server started for Use Case Plugin");
    } catch (Exception e) {
      System.err.println("Failed to start MCP server: " + e.getMessage());
      e.printStackTrace();
    }
  }

  @Override
  public void unloaded() {
    // Plugin unloaded - cleanup code here
    System.out.println("Use Case Plugin unloaded");

    // Stop MCP server
    if (mcpServer != null) {
      try {
        mcpServer.stop();
        System.out.println("MCP Server stopped");
      } catch (Exception e) {
        System.err.println("Error stopping MCP server: " + e.getMessage());
      }
    }
  }
}
