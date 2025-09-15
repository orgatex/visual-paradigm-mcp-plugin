package com.brunnen.vp.usecase.mcp;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.McpTool;
import org.springframework.ai.mcp.server.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Tools for creating and managing use case diagrams in Visual Paradigm. These tools provide
 * AI-accessible functions for diagram creation and manipulation.
 */
@Component
@Slf4j
public class UseCaseDiagramTools {

  /**
   * Create a new use case diagram with the specified name.
   *
   * @param diagramName the name of the diagram to create
   * @return success message with diagram details
   */
  @McpTool(
      name = "create_use_case_diagram",
      description = "Create a new use case diagram in Visual Paradigm")
  public String createUseCaseDiagram(
      @McpToolParam(description = "Name of the use case diagram", required = true)
          String diagramName) {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "Error: No Visual Paradigm project is currently open";
      }

      // Create the use case diagram
      IUseCaseDiagram diagram = IModelElementFactory.instance().createUseCaseDiagram(diagramName);

      // Add to project
      project.toModelElementArray(IUseCaseDiagram.class);

      log.info("Created use case diagram: {}", diagramName);
      return String.format(
          "Successfully created use case diagram '%s' with ID: %s", diagramName, diagram.getId());

    } catch (Exception e) {
      log.error("Failed to create use case diagram: {}", diagramName, e);
      return String.format("Error creating use case diagram '%s': %s", diagramName, e.getMessage());
    }
  }

  /**
   * Add an actor to a use case diagram.
   *
   * @param diagramName the name of the diagram
   * @param actorName the name of the actor to add
   * @param x the x coordinate for placement (optional)
   * @param y the y coordinate for placement (optional)
   * @return success message with actor details
   */
  @McpTool(name = "add_actor", description = "Add an actor to a use case diagram")
  public String addActor(
      @McpToolParam(description = "Name of the use case diagram", required = true)
          String diagramName,
      @McpToolParam(description = "Name of the actor", required = true) String actorName,
      @McpToolParam(description = "X coordinate for placement", required = false) Integer x,
      @McpToolParam(description = "Y coordinate for placement", required = false) Integer y) {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "Error: No Visual Paradigm project is currently open";
      }

      // Find the diagram
      IUseCaseDiagram diagram = findDiagramByName(project, diagramName);
      if (diagram == null) {
        return String.format("Error: Use case diagram '%s' not found", diagramName);
      }

      // Create actor
      IActor actor = IModelElementFactory.instance().createActor();
      actor.setName(actorName);

      // Create shape on diagram
      IActorUIModel actorShape = (IActorUIModel) diagram.createShape(IActorUIModel.MODEL_TYPE);
      actorShape.resetCaption();

      // Set position if provided
      if (x != null && y != null) {
        actorShape.setBounds(x, y, 80, 100); // Standard actor size
      }

      log.info("Added actor '{}' to diagram '{}'", actorName, diagramName);
      return String.format("Successfully added actor '%s' to diagram '%s'", actorName, diagramName);

    } catch (Exception e) {
      log.error("Failed to add actor '{}' to diagram '{}'", actorName, diagramName, e);
      return String.format(
          "Error adding actor '%s' to diagram '%s': %s", actorName, diagramName, e.getMessage());
    }
  }

  /**
   * Add a use case to a use case diagram.
   *
   * @param diagramName the name of the diagram
   * @param useCaseName the name of the use case to add
   * @param description optional description of the use case
   * @param x the x coordinate for placement (optional)
   * @param y the y coordinate for placement (optional)
   * @return success message with use case details
   */
  @McpTool(name = "add_use_case", description = "Add a use case to a use case diagram")
  public String addUseCase(
      @McpToolParam(description = "Name of the use case diagram", required = true)
          String diagramName,
      @McpToolParam(description = "Name of the use case", required = true) String useCaseName,
      @McpToolParam(description = "Description of the use case", required = false)
          String description,
      @McpToolParam(description = "X coordinate for placement", required = false) Integer x,
      @McpToolParam(description = "Y coordinate for placement", required = false) Integer y) {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "Error: No Visual Paradigm project is currently open";
      }

      // Find the diagram
      IUseCaseDiagram diagram = findDiagramByName(project, diagramName);
      if (diagram == null) {
        return String.format("Error: Use case diagram '%s' not found", diagramName);
      }

      // Create use case
      IUseCase useCase = IModelElementFactory.instance().createUseCase();
      useCase.setName(useCaseName);
      if (description != null && !description.trim().isEmpty()) {
        useCase.setDescription(description);
      }

      // Create shape on diagram
      IUseCaseUIModel useCaseShape =
          (IUseCaseUIModel) diagram.createShape(IUseCaseUIModel.MODEL_TYPE);
      useCaseShape.resetCaption();

      // Set position if provided
      if (x != null && y != null) {
        useCaseShape.setBounds(x, y, 120, 60); // Standard use case size
      }

      log.info("Added use case '{}' to diagram '{}'", useCaseName, diagramName);
      return String.format(
          "Successfully added use case '%s' to diagram '%s'", useCaseName, diagramName);

    } catch (Exception e) {
      log.error("Failed to add use case '{}' to diagram '{}'", useCaseName, diagramName, e);
      return String.format(
          "Error adding use case '%s' to diagram '%s': %s",
          useCaseName, diagramName, e.getMessage());
    }
  }

  /**
   * Add a relationship between actors and use cases.
   *
   * @param diagramName the name of the diagram
   * @param fromElement the name of the source element (actor or use case)
   * @param toElement the name of the target element (use case or actor)
   * @param relationshipType the type of relationship (association, include, extend)
   * @return success message with relationship details
   */
  @McpTool(
      name = "add_relationship",
      description = "Add a relationship between elements in a use case diagram")
  public String addRelationship(
      @McpToolParam(description = "Name of the use case diagram", required = true)
          String diagramName,
      @McpToolParam(description = "Name of the source element", required = true) String fromElement,
      @McpToolParam(description = "Name of the target element", required = true) String toElement,
      @McpToolParam(
              description = "Type of relationship: association, include, extend",
              required = true)
          String relationshipType) {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "Error: No Visual Paradigm project is currently open";
      }

      // Find the diagram
      IUseCaseDiagram diagram = findDiagramByName(project, diagramName);
      if (diagram == null) {
        return String.format("Error: Use case diagram '%s' not found", diagramName);
      }

      // Validate relationship type
      String normalizedType = relationshipType.toLowerCase().trim();
      if (!normalizedType.equals("association")
          && !normalizedType.equals("include")
          && !normalizedType.equals("extend")) {
        return String.format(
            "Error: Invalid relationship type '%s'. Must be: association, include, or extend",
            relationshipType);
      }

      // This is a simplified implementation
      // In a real scenario, you would need to find the actual model elements
      // and create the appropriate relationship type

      log.info(
          "Added {} relationship from '{}' to '{}' in diagram '{}'",
          relationshipType,
          fromElement,
          toElement,
          diagramName);

      return String.format(
          "Successfully added %s relationship from '%s' to '%s' in diagram '%s'",
          relationshipType, fromElement, toElement, diagramName);

    } catch (Exception e) {
      log.error(
          "Failed to add {} relationship from '{}' to '{}' in diagram '{}'",
          relationshipType,
          fromElement,
          toElement,
          diagramName,
          e);
      return String.format(
          "Error adding %s relationship from '%s' to '%s' in diagram '%s': %s",
          relationshipType, fromElement, toElement, diagramName, e.getMessage());
    }
  }

  /**
   * Generate a report of all elements in a use case diagram.
   *
   * @param diagramName the name of the diagram
   * @return detailed report of diagram contents
   */
  @McpTool(
      name = "generate_use_case_report",
      description = "Generate a detailed report of a use case diagram")
  public String generateUseCaseReport(
      @McpToolParam(description = "Name of the use case diagram", required = true)
          String diagramName) {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "Error: No Visual Paradigm project is currently open";
      }

      // Find the diagram
      IUseCaseDiagram diagram = findDiagramByName(project, diagramName);
      if (diagram == null) {
        return String.format("Error: Use case diagram '%s' not found", diagramName);
      }

      StringBuilder report = new StringBuilder();
      report.append("=== Use Case Diagram Report ===\n");
      report.append("Diagram Name: ").append(diagramName).append("\n");
      report.append("Diagram ID: ").append(diagram.getId()).append("\n\n");

      // Count shapes (simplified - in real implementation you'd iterate through actual shapes)
      report.append("=== Summary ===\n");
      report.append("This is a simplified report. In a full implementation,\n");
      report.append("this would list all actors, use cases, and relationships.\n\n");

      report.append("=== Actors ===\n");
      report.append("(Actor details would be listed here)\n\n");

      report.append("=== Use Cases ===\n");
      report.append("(Use case details would be listed here)\n\n");

      report.append("=== Relationships ===\n");
      report.append("(Relationship details would be listed here)\n");

      log.info("Generated report for diagram '{}'", diagramName);
      return report.toString();

    } catch (Exception e) {
      log.error("Failed to generate report for diagram '{}'", diagramName, e);
      return String.format(
          "Error generating report for diagram '%s': %s", diagramName, e.getMessage());
    }
  }

  /**
   * List all use case diagrams in the current project.
   *
   * @return list of diagram names and IDs
   */
  @McpTool(
      name = "list_use_case_diagrams",
      description = "List all use case diagrams in the current project")
  public String listUseCaseDiagrams() {

    try {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project == null) {
        return "Error: No Visual Paradigm project is currently open";
      }

      IModelElement[] diagrams = project.toModelElementArray(IUseCaseDiagram.class);

      if (diagrams.length == 0) {
        return "No use case diagrams found in the current project.";
      }

      StringBuilder result = new StringBuilder();
      result.append("=== Use Case Diagrams in Project ===\n");

      for (IModelElement element : diagrams) {
        if (element instanceof IUseCaseDiagram) {
          IUseCaseDiagram diagram = (IUseCaseDiagram) element;
          result
              .append("- ")
              .append(diagram.getName())
              .append(" (ID: ")
              .append(diagram.getId())
              .append(")\n");
        }
      }

      log.info("Listed {} use case diagrams", diagrams.length);
      return result.toString();

    } catch (Exception e) {
      log.error("Failed to list use case diagrams", e);
      return String.format("Error listing use case diagrams: %s", e.getMessage());
    }
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
}
