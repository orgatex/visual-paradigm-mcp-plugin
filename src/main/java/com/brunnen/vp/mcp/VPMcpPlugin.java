package com.brunnen.vp.mcp;

import com.brunnen.vp.mcp.tools.ClassDiagramMcpTools;
import com.brunnen.vp.mcp.tools.ErdMcpTools;
import com.brunnen.vp.mcp.tools.SequenceDiagramMcpTools;
import com.brunnen.vp.mcp.tools.UseCaseMcpTools;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;

/**
 * Visual Paradigm MCP Plugin. Provides MCP server integration for Use Case, Class, Sequence, and
 * ERD diagrams.
 */
public final class VPMcpPlugin implements VPPlugin {

  private McpServer mcpServer;

  @Override
  public void loaded(final VPPluginInfo info) {
    String pluginId = (info != null) ? info.getPluginId() : "unknown";
    System.out.println("VP MCP Plugin loaded: " + pluginId);

    try {
      mcpServer = new McpServer();
      mcpServer.setPort(2026);
      mcpServer.registerTools(
          new UseCaseMcpTools(),
          new ClassDiagramMcpTools(),
          new ErdMcpTools(),
          new SequenceDiagramMcpTools());
      mcpServer.start();
    } catch (Exception e) {
      System.err.println("Failed to start MCP server: " + e.getMessage());
      e.printStackTrace();
    }
  }

  @Override
  public void unloaded() {
    System.out.println("VP MCP Plugin unloaded");
    if (mcpServer != null) {
      mcpServer.stop();
    }
  }
}
