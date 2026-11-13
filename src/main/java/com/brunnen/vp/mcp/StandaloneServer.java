package com.brunnen.vp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Standalone MCP server for Docker deployment. Acts as a proxy: serves MCP protocol (streamable
 * HTTP JSON-RPC) to clients, delegates tool execution to a running VP instance via HTTP.
 */
public class StandaloneServer {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getenv().getOrDefault("MCP_PORT", "2026"));
    String vpBaseUrl =
        System.getenv().getOrDefault("VP_API_URL", "http://host.docker.internal:2026");

    McpServer server = new McpServer();
    server.setPort(port);

    // Fetch tool metadata from VP and register proxy tools
    System.out.println("Connecting to VP at " + vpBaseUrl + " ...");
    JsonNode toolsJson = fetchToolsMetadata(vpBaseUrl);

    int count = 0;
    for (JsonNode toolNode : toolsJson) {
      String name = toolNode.get("name").asText();
      String description = toolNode.get("description").asText();
      JsonNode inputSchema = toolNode.get("inputSchema");
      ProxyToolDefinition proxy =
          new ProxyToolDefinition(name, description, inputSchema, vpBaseUrl);
      server.registerProxyTool(proxy);
      count++;
    }

    server.start();
    System.out.println("MCP Proxy Server running on port " + port);
    System.out.println("Proxied " + count + " tools from VP at " + vpBaseUrl);
    System.out.println("Streamable HTTP endpoint: http://localhost:" + port + "/mcp");

    Thread.currentThread().join();
  }

  private static JsonNode fetchToolsMetadata(String vpBaseUrl) throws Exception {
    String url = vpBaseUrl + "/api/tools";
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(10000);
    conn.setReadTimeout(30000);

    int code = conn.getResponseCode();
    if (code != 200) {
      throw new RuntimeException(
          "Failed to fetch tools from VP at " + url + " (HTTP " + code + ")");
    }

    try (InputStream is = conn.getInputStream()) {
      return MAPPER.readTree(is);
    }
  }
}
