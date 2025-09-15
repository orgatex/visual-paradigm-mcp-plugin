package com.brunnen.vp.usecase.mcp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = McpServer.class)
@ActiveProfiles("mcp")
class UseCaseMcpToolsServiceTest {

  @Autowired private UseCaseMcpToolsService toolsService;

  @Test
  void testCreateUseCaseDiagram() {
    String result = toolsService.createUseCaseDiagram("Test Diagram");
    assertEquals("Created use case diagram: Test Diagram", result);
  }

  @Test
  void testAddActor() {
    String result = toolsService.addActor("User", "Test Diagram");
    assertEquals("Added actor 'User' to diagram 'Test Diagram'", result);
  }

  @Test
  void testAddUseCase() {
    String result = toolsService.addUseCase("Login", "Test Diagram");
    assertEquals("Added use case 'Login' to diagram 'Test Diagram'", result);
  }

  @Test
  void testAddRelationship() {
    String result = toolsService.addRelationship("User", "Login", "Association");
    assertEquals("Added Association relationship between 'User' and 'Login'", result);
  }

  @Test
  void testGenerateReport() {
    String result = toolsService.generateReport("Test Diagram");
    assertEquals("Generated report for diagram: Test Diagram", result);
  }
}
