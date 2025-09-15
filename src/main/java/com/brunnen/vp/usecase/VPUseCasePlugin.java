package com.brunnen.vp.usecase;

import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * MCP Server Plugin for Visual Paradigm. Provides MCP server functionality for creating and
 * managing use cases through AI interactions.
 */
@Slf4j
public final class VPUseCasePlugin implements VPPlugin {

  private ConfigurableApplicationContext springContext;
  private JButton toggleButton;
  private McpServerController serverController;

  @Override
  public void loaded(final VPPluginInfo info) {
    String pluginId = (info != null) ? info.getPluginId() : "unknown";
    log.info("MCP Server Plugin loaded: {}", pluginId);

    SwingUtilities.invokeLater(this::initializeUI);
  }

  @Override
  public void unloaded() {
    log.info("MCP Server Plugin unloading...");
    if (springContext != null && springContext.isActive()) {
      springContext.close();
    }
    log.info("MCP Server Plugin unloaded");
  }

  private void initializeUI() {
    try {
      // Create UI components
      toggleButton = new JButton("Start MCP Server");
      toggleButton.addActionListener(new ToggleServerAction());

      // Add to Visual Paradigm UI - simplified approach
      // In a real implementation, you would use VP's UI extension points
      log.info("MCP Server UI initialized");

    } catch (Exception e) {
      log.error("Failed to initialize UI", e);
    }
  }

  private void startMcpServer() {
    try {
      if (springContext == null || !springContext.isActive()) {
        log.info("Starting MCP Server...");

        // Configure Spring Boot for embedded execution
        System.setProperty("spring.main.web-application-type", "servlet");
        System.setProperty("server.port", "8080");
        System.setProperty("logging.level.org.springframework.ai.mcp", "INFO");

        springContext =
            SpringApplication.run(
                VPMcpServerApplication.class, new String[] {"--server.port=8080"});

        serverController = springContext.getBean(McpServerController.class);
        toggleButton.setText("Stop MCP Server");
        log.info("MCP Server started successfully on port 8080");
      }
    } catch (Exception e) {
      log.error("Failed to start MCP Server", e);
    }
  }

  private void stopMcpServer() {
    try {
      if (springContext != null && springContext.isActive()) {
        log.info("Stopping MCP Server...");
        springContext.close();
        springContext = null;
        toggleButton.setText("Start MCP Server");
        log.info("MCP Server stopped");
      }
    } catch (Exception e) {
      log.error("Failed to stop MCP Server", e);
    }
  }

  private class ToggleServerAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (springContext == null || !springContext.isActive()) {
        startMcpServer();
      } else {
        stopMcpServer();
      }
    }
  }

  /**
   * Get the server status for external monitoring.
   *
   * @return true if server is running, false otherwise
   */
  public boolean isServerRunning() {
    return springContext != null && springContext.isActive();
  }

  /**
   * Get the toggle button for integration with VP UI.
   *
   * @return the toggle button
   */
  public JButton getToggleButton() {
    return toggleButton;
  }
}
