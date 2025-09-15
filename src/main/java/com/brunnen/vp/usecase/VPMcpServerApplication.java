package com.brunnen.vp.usecase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application for the Visual Paradigm MCP Server. Provides MCP server capabilities for
 * use case diagram management and AI interactions.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.brunnen.vp.usecase")
public class VPMcpServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(VPMcpServerApplication.class, args);
  }
}
