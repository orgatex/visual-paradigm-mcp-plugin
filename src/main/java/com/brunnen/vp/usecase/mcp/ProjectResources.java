package com.brunnen.vp.usecase.mcp;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IUseCaseDiagram;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.McpResource;
import org.springframework.stereotype.Component;

/**
 * MCP Resources for providing Visual Paradigm project information. These resources allow AI systems
 * to access project metadata and diagram information.
 */
@Component
@Slf4j
public class ProjectResources {

  /**
   * Provides project information resource.
   *
   * @return project information in JSON format
   */
  @McpResource(
      uri = "vp://project/info",
      name = "Visual Paradigm Project Information",
      description = "Provides information about the currently open Visual Paradigm project")
  public String getProjectInfo() {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "{\"error\": \"No Visual Paradigm project is currently open\", \"status\": \"no_project\"}";
      }

      StringBuilder jsonBuilder = new StringBuilder();
      jsonBuilder.append("{\n");
      jsonBuilder.append("  \"name\": \"").append(escape(project.getName())).append("\",\n");
      jsonBuilder.append("  \"id\": \"").append(escape(project.getId())).append("\",\n");
      jsonBuilder.append("  \"status\": \"active\",\n");

      // Count diagrams
      IModelElement[] diagrams = project.toModelElementArray(IUseCaseDiagram.class);
      jsonBuilder.append("  \"use_case_diagram_count\": ").append(diagrams.length).append(",\n");

      // Add diagram list
      jsonBuilder.append("  \"use_case_diagrams\": [\n");
      for (int i = 0; i < diagrams.length; i++) {
        if (diagrams[i] instanceof IUseCaseDiagram) {
          IUseCaseDiagram diagram = (IUseCaseDiagram) diagrams[i];
          jsonBuilder.append("    {\n");
          jsonBuilder
              .append("      \"name\": \"")
              .append(escape(diagram.getName()))
              .append("\",\n");
          jsonBuilder.append("      \"id\": \"").append(escape(diagram.getId())).append("\"\n");
          jsonBuilder.append("    }");
          if (i < diagrams.length - 1) {
            jsonBuilder.append(",");
          }
          jsonBuilder.append("\n");
        }
      }
      jsonBuilder.append("  ]\n");
      jsonBuilder.append("}");

      log.info("Provided project info for project: {}", project.getName());
      return jsonBuilder.toString();

    } catch (Exception e) {
      log.error("Failed to get project info", e);
      return "{\"error\": \"" + escape(e.getMessage()) + "\", \"status\": \"error\"}";
    }
  }

  /**
   * Provides diagram details resource.
   *
   * @param diagramName the name of the diagram
   * @return diagram details in JSON format
   */
  @McpResource(
      uri = "vp://diagram/{diagramName}",
      name = "Use Case Diagram Details",
      description = "Provides detailed information about a specific use case diagram")
  public String getDiagramDetails(String diagramName) {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "{\"error\": \"No Visual Paradigm project is currently open\", \"status\": \"no_project\"}";
      }

      // Find the diagram
      IUseCaseDiagram diagram = findDiagramByName(project, diagramName);
      if (diagram == null) {
        return "{\"error\": \"Diagram not found\", \"status\": \"not_found\", \"diagram_name\": \""
            + escape(diagramName)
            + "\"}";
      }

      StringBuilder jsonBuilder = new StringBuilder();
      jsonBuilder.append("{\n");
      jsonBuilder.append("  \"name\": \"").append(escape(diagram.getName())).append("\",\n");
      jsonBuilder.append("  \"id\": \"").append(escape(diagram.getId())).append("\",\n");
      jsonBuilder.append("  \"type\": \"use_case_diagram\",\n");
      jsonBuilder.append("  \"status\": \"active\",\n");

      // In a full implementation, you would count actual shapes
      jsonBuilder.append("  \"elements\": {\n");
      jsonBuilder.append("    \"actors\": [],\n");
      jsonBuilder.append("    \"use_cases\": [],\n");
      jsonBuilder.append("    \"relationships\": []\n");
      jsonBuilder.append("  },\n");

      jsonBuilder.append("  \"metadata\": {\n");
      jsonBuilder.append("    \"created\": \"unknown\",\n");
      jsonBuilder.append("    \"modified\": \"unknown\"\n");
      jsonBuilder.append("  }\n");
      jsonBuilder.append("}");

      log.info("Provided diagram details for: {}", diagramName);
      return jsonBuilder.toString();

    } catch (Exception e) {
      log.error("Failed to get diagram details for: {}", diagramName, e);
      return "{\"error\": \""
          + escape(e.getMessage())
          + "\", \"status\": \"error\", \"diagram_name\": \""
          + escape(diagramName)
          + "\"}";
    }
  }

  /**
   * Provides available capabilities resource.
   *
   * @return available MCP capabilities in JSON format
   */
  @McpResource(
      uri = "vp://capabilities",
      name = "Visual Paradigm MCP Capabilities",
      description = "Lists all available MCP tools and resources for Visual Paradigm")
  public String getCapabilities() {

    StringBuilder jsonBuilder = new StringBuilder();
    jsonBuilder.append("{\n");
    jsonBuilder.append("  \"server\": \"Visual Paradigm MCP Plugin\",\n");
    jsonBuilder.append("  \"version\": \"0.1.0\",\n");
    jsonBuilder.append("  \"capabilities\": {\n");
    jsonBuilder.append("    \"tools\": [\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"name\": \"create_use_case_diagram\",\n");
    jsonBuilder.append(
        "        \"description\": \"Create a new use case diagram in Visual Paradigm\"\n");
    jsonBuilder.append("      },\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"name\": \"add_actor\",\n");
    jsonBuilder.append("        \"description\": \"Add an actor to a use case diagram\"\n");
    jsonBuilder.append("      },\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"name\": \"add_use_case\",\n");
    jsonBuilder.append("        \"description\": \"Add a use case to a use case diagram\"\n");
    jsonBuilder.append("      },\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"name\": \"add_relationship\",\n");
    jsonBuilder.append(
        "        \"description\": \"Add a relationship between elements in a use case diagram\"\n");
    jsonBuilder.append("      },\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"name\": \"generate_use_case_report\",\n");
    jsonBuilder.append(
        "        \"description\": \"Generate a detailed report of a use case diagram\"\n");
    jsonBuilder.append("      },\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"name\": \"list_use_case_diagrams\",\n");
    jsonBuilder.append(
        "        \"description\": \"List all use case diagrams in the current project\"\n");
    jsonBuilder.append("      }\n");
    jsonBuilder.append("    ],\n");
    jsonBuilder.append("    \"resources\": [\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"uri\": \"vp://project/info\",\n");
    jsonBuilder.append("        \"description\": \"Visual Paradigm project information\"\n");
    jsonBuilder.append("      },\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"uri\": \"vp://diagram/{diagramName}\",\n");
    jsonBuilder.append("        \"description\": \"Use case diagram details\"\n");
    jsonBuilder.append("      },\n");
    jsonBuilder.append("      {\n");
    jsonBuilder.append("        \"uri\": \"vp://capabilities\",\n");
    jsonBuilder.append("        \"description\": \"Available MCP capabilities\"\n");
    jsonBuilder.append("      }\n");
    jsonBuilder.append("    ]\n");
    jsonBuilder.append("  }\n");
    jsonBuilder.append("}");

    log.info("Provided capabilities information");
    return jsonBuilder.toString();
  }

  /**
   * Helper method to find a diagram by name.
   *
   * @param project the project to search in
   * @param diagramName the name of the diagram
   * @return the diagram or null if not found
   */
  private IUseCaseDiagram findDiagramByName(IProject project, String diagramName) {
    IModelElement[] diagrams = project.toModelElementArray(IUseCaseDiagram.class);

    for (IModelElement element : diagrams) {
      if (element instanceof IUseCaseDiagram) {
        IUseCaseDiagram diagram = (IUseCaseDiagram) element;
        if (diagramName.equals(diagram.getName())) {
          return diagram;
        }
      }
    }
    return null;
  }

  /**
   * Helper method to escape JSON strings.
   *
   * @param input the input string
   * @return escaped string for JSON
   */
  private String escape(String input) {
    if (input == null) {
      return "";
    }
    return input
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
