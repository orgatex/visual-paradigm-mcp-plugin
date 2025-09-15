package com.brunnen.vp.usecase.mcp;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ProjectManager;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IUseCaseDiagram;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test class for UseCaseDiagramTools MCP tools. */
public class UseCaseDiagramToolsTest {

  @Mock private ApplicationManager mockAppManager;
  @Mock private ProjectManager mockProjectManager;
  @Mock private IProject mockProject;
  @Mock private IUseCaseDiagram mockDiagram;

  private UseCaseDiagramTools tools;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    tools = new UseCaseDiagramTools();

    // Note: ApplicationManager is a singleton, so mocking it in unit tests
    // is complex. These tests demonstrate the structure but may need
    // integration testing with actual VP environment for full coverage.
  }

  @Test
  public void testCreateUseCaseDiagramWithNullProject() {
    // When
    String result = tools.createUseCaseDiagram("Test Diagram");

    // Then
    assertTrue(
        "Should return error message when no project is open",
        result.contains("No Visual Paradigm project is currently open"));
  }

  @Test
  public void testAddActorWithNullProject() {
    // When
    String result = tools.addActor("Test Diagram", "Test Actor", 100, 100);

    // Then
    assertTrue(
        "Should return error message when no project is open",
        result.contains("No Visual Paradigm project is currently open"));
  }

  @Test
  public void testAddUseCaseWithNullProject() {
    // When
    String result = tools.addUseCase("Test Diagram", "Test Use Case", "Description", 100, 100);

    // Then
    assertTrue(
        "Should return error message when no project is open",
        result.contains("No Visual Paradigm project is currently open"));
  }

  @Test
  public void testAddRelationshipValidatesType() {
    // When
    String result = tools.addRelationship("Test Diagram", "Actor1", "UseCase1", "invalid_type");

    // Then
    assertTrue("Should validate relationship type", result.contains("Invalid relationship type"));
  }

  @Test
  public void testAddRelationshipWithValidTypes() {
    // Test valid relationship types don't trigger validation error in the method
    String[] validTypes = {"association", "include", "extend"};

    for (String type : validTypes) {
      String result = tools.addRelationship("Test Diagram", "Actor1", "UseCase1", type);
      // With no project, it should fail at project check, not type validation
      assertTrue(
          "Should pass type validation for: " + type,
          result.contains("No Visual Paradigm project is currently open"));
    }
  }

  @Test
  public void testListUseCaseDiagramsWithNullProject() {
    // When
    String result = tools.listUseCaseDiagrams();

    // Then
    assertTrue(
        "Should return error message when no project is open",
        result.contains("No Visual Paradigm project is currently open"));
  }

  @Test
  public void testGenerateUseCaseReportWithNullProject() {
    // When
    String result = tools.generateUseCaseReport("Test Diagram");

    // Then
    assertTrue(
        "Should return error message when no project is open",
        result.contains("No Visual Paradigm project is currently open"));
  }
}
