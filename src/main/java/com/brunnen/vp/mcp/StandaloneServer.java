package com.brunnen.vp.mcp;

/**
 * Standalone MCP server entry point for Docker deployment. Starts the MCP server without Visual
 * Paradigm integration (tools return placeholder responses).
 */
public class StandaloneServer {

  public static void main(String[] args) throws Exception {
    int port = 2026;
    if (args.length > 0) {
      try {
        port = Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
        // ignore
      }
    }

    McpServer server = new McpServer();
    server.setPort(port);

    // Register tool objects (they will return errors since VP is not available)
    server.registerTools(
        new com.brunnen.vp.mcp.tools.UseCaseMcpTools(),
        new com.brunnen.vp.mcp.tools.ClassDiagramMcpTools(),
        new com.brunnen.vp.mcp.tools.ErdMcpTools(),
        new com.brunnen.vp.mcp.tools.SequenceDiagramMcpTools());

    server.start();
    System.out.println("Standalone MCP Server running on port " + port);
    System.out.println("SSE endpoint: http://localhost:" + port + "/sse");

    // Keep alive
    Thread.currentThread().join();
  }
}
