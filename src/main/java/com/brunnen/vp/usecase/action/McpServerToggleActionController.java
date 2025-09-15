package com.brunnen.vp.usecase.action;

import com.brunnen.vp.usecase.VPUseCasePlugin;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.VPPlugin;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;

/** Action controller for toggling the MCP server on/off. */
public class McpServerToggleActionController implements VPActionController {

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
        // Toggle server via button click
        if (mcpPlugin.getToggleButton() != null) {
          mcpPlugin.getToggleButton().doClick();
        }
      } else {
        ApplicationManager.instance().getViewManager().showMessage("MCP Plugin not found");
      }

    } catch (Exception e) {
      ApplicationManager.instance()
          .getViewManager()
          .showMessage("Error toggling MCP server: " + e.getMessage());
    }
  }

  @Override
  public void update(VPAction action) {
    // Update action state if needed
    action.setEnabled(true);

    try {
      VPPlugin[] plugins = ApplicationManager.instance().getPluginManager().getPlugins();
      for (VPPlugin plugin : plugins) {
        if (plugin instanceof VPUseCasePlugin) {
          VPUseCasePlugin mcpPlugin = (VPUseCasePlugin) plugin;
          if (mcpPlugin.isServerRunning()) {
            action.setLabel("Stop MCP Server");
          } else {
            action.setLabel("Start MCP Server");
          }
          break;
        }
      }
    } catch (Exception e) {
      // Ignore update errors
    }
  }
}
