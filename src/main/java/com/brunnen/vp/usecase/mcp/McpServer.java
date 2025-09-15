package com.brunnen.vp.usecase.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/** MCP Server for Visual Paradigm Use Case Plugin. */
@SpringBootConfiguration
@ComponentScan
public class McpServer {

  private ConfigurableApplicationContext context;

  /** Start the MCP server. */
  public void start() {
    if (context == null || !context.isActive()) {
      SpringApplication app = new SpringApplication(McpServer.class);
      app.setAdditionalProfiles("mcp");
      context = app.run(new String[] {});
    }
  }

  /** Stop the MCP server. */
  public void stop() {
    if (context != null && context.isActive()) {
      SpringApplication.exit(context);
      context = null;
    }
  }

  /** Check if the server is running. */
  public boolean isRunning() {
    return context != null && context.isActive();
  }
}
