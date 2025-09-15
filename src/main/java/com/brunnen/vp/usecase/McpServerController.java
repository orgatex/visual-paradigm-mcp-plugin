package com.brunnen.vp.usecase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Controller for managing the MCP server lifecycle and status within the Visual Paradigm plugin.
 */
@Component
@Slf4j
public class McpServerController {

  private boolean running = false;

  /**
   * Check if the MCP server is running.
   *
   * @return true if running, false otherwise
   */
  public boolean isRunning() {
    return running;
  }

  /** Mark the server as started. */
  public void markStarted() {
    this.running = true;
    log.info("MCP Server controller marked as started");
  }

  /** Mark the server as stopped. */
  public void markStopped() {
    this.running = false;
    log.info("MCP Server controller marked as stopped");
  }

  /**
   * Get server status information.
   *
   * @return status string
   */
  public String getStatus() {
    return running ? "Running" : "Stopped";
  }
}
