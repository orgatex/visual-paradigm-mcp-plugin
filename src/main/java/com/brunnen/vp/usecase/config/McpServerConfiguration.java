package com.brunnen.vp.usecase.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Configuration class for the Visual Paradigm MCP Server. Sets up MCP server capabilities and
 * transport configuration.
 */
@Configuration
@Slf4j
public class McpServerConfiguration {

  /**
   * Handle application ready event to log server startup.
   *
   * @param event the application ready event
   */
  @EventListener
  public void handleApplicationReady(ApplicationReadyEvent event) {
    log.info("=== Visual Paradigm MCP Server Started ===");
    log.info("Server URL: http://localhost:8080");
    log.info("SSE Endpoint: http://localhost:8080/mcp/messages");
    log.info(
        "Available Tools: create_use_case_diagram, add_actor, add_use_case, add_relationship, generate_use_case_report, list_use_case_diagrams");
    log.info("Available Resources: vp://project/info, vp://diagram/{name}, vp://capabilities");
    log.info("==========================================");
  }

  /**
   * Handle context refresh event to ensure proper MCP server initialization.
   *
   * @param event the context refresh event
   */
  @EventListener
  public void handleContextRefresh(ContextRefreshedEvent event) {
    log.info("Spring context refreshed - MCP server components initialized");
  }
}
