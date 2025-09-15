package com.brunnen.vp.usecase.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone MCP server for testing purposes. This runs independently of Visual Paradigm for
 * testing.
 */
@SpringBootApplication
public class StandaloneMcpServer {

  public static void main(String[] args) {
    System.setProperty("spring.profiles.active", "mcp");
    SpringApplication.run(StandaloneMcpServer.class, args);
  }
}
