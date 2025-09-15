package com.brunnen.vp.usecase.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = McpServer.class)
@ActiveProfiles("mcp")
class McpServerTest {

  @Test
  void contextLoads() {
    // Test that Spring context loads correctly
  }
}
