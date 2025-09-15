package com.brunnen.vp.usecase.action;

import com.brunnen.vp.usecase.VPUseCasePlugin;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;

/** Action controller for showing MCP server status. */
public class McpServerStatusActionController implements VPActionController {

  @Override
  public void performAction(VPAction action) {
    try {
      // Get the plugin instance
      VPPlugin[] plugins = ApplicationManager.instance().getPluginManager().getPlugins();
      VPUseCasePlugin mcpPlugin = null;

      for (VPPlugin plugin : plugins) {
        if (plugin instanceof VPUseCasePlugin) {
          mcpPlugin = (VPUseCasePlugin) plugin;
          break;
        }
      }

      if (mcpPlugin != null) {
        String status = mcpPlugin.isServerRunning() ? "Running" : "Stopped";
        String message = "MCP Server Status: " + status;

        if (mcpPlugin.isServerRunning()) {
          message += "\nServer URL: http://localhost:8080";
          message += "\nSSE Endpoint: http://localhost:8080/mcp/messages";
          message += "\n\nAvailable Tools:";
          message += "\n- create_use_case_diagram";
          message += "\n- add_actor";
          message += "\n- add_use_case";
          message += "\n- add_relationship";
          message += "\n- generate_use_case_report";
          message += "\n- list_use_case_diagrams";
          message += "\n\nAvailable Resources:";
          message += "\n- vp://project/info";
          message += "\n- vp://diagram/{diagramName}";
          message += "\n- vp://capabilities";
        }

        ApplicationManager.instance().getViewManager().showMessage(message);
      } else {
        ApplicationManager.instance().getViewManager().showMessage("MCP Plugin not found");
      }

    } catch (Exception e) {
      ApplicationManager.instance()
          .getViewManager()
          .showMessage("Error getting MCP server status: " + e.getMessage());
    }
  }

  @Override
  public void update(VPAction action) {
    // Always enable status check
    action.setEnabled(true);
  }
}
