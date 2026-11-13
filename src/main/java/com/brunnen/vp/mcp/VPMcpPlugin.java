package com.brunnen.vp.mcp;

import com.brunnen.vp.mcp.tools.ActivityDiagramMcpTools;
import com.brunnen.vp.mcp.tools.BpmnDiagramMcpTools;
import com.brunnen.vp.mcp.tools.ClassDiagramMcpTools;
import com.brunnen.vp.mcp.tools.CodeGenerationMcpTools;
import com.brunnen.vp.mcp.tools.CommunicationDiagramMcpTools;
import com.brunnen.vp.mcp.tools.ComponentDiagramMcpTools;
import com.brunnen.vp.mcp.tools.DeploymentDiagramMcpTools;
import com.brunnen.vp.mcp.tools.ErdMcpTools;
import com.brunnen.vp.mcp.tools.FlowchartDfdMcpTools;
import com.brunnen.vp.mcp.tools.PackageObjectDiagramMcpTools;
import com.brunnen.vp.mcp.tools.SequenceDiagramMcpTools;
import com.brunnen.vp.mcp.tools.StateMachineDiagramMcpTools;
import com.brunnen.vp.mcp.tools.UseCaseMcpTools;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;

/**
 * Visual Paradigm MCP Plugin. Provides MCP server integration for Use Case, Class, Sequence, ERD,
 * state machine, activity, component, deployment, package, object, communication, flowchart, data
 * flow and BPMN diagrams.
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
          new SequenceDiagramMcpTools(),
          new StateMachineDiagramMcpTools(),
          new ActivityDiagramMcpTools(),
          new ComponentDiagramMcpTools(),
          new DeploymentDiagramMcpTools(),
          new PackageObjectDiagramMcpTools(),
          new CommunicationDiagramMcpTools(),
          new FlowchartDfdMcpTools(),
          new BpmnDiagramMcpTools(),
          new CodeGenerationMcpTools());
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
