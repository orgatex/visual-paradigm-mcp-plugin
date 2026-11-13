package com.brunnen.vp.mcp;

import com.brunnen.vp.mcp.tool.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * A tool definition that delegates execution to a remote VP server's /api/execute endpoint.
 * Replaces reflective local invocation with HTTP POST to the VP JVM.
 */
public class ProxyToolDefinition extends ToolDefinition {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final String executeUrl;

  public ProxyToolDefinition(
      String name, String description, JsonNode inputSchema, String vpBaseUrl) {
    super(name, description, inputSchema);
    this.executeUrl = vpBaseUrl + "/api/execute";
  }

  @Override
  public String execute(JsonNode arguments) throws Exception {
    ObjectNode request = MAPPER.createObjectNode();
    request.put("toolName", getName());
    request.set("arguments", arguments);

    HttpURLConnection conn = (HttpURLConnection) new URL(executeUrl).openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setDoOutput(true);
    conn.setConnectTimeout(30000);
    conn.setReadTimeout(120000);

    try (OutputStream os = conn.getOutputStream()) {
      os.write(MAPPER.writeValueAsBytes(request));
    }

    int code = conn.getResponseCode();
    InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

    JsonNode response = MAPPER.readTree(body);
    if (response.has("error") && !response.get("error").isNull()) {
      throw new RuntimeException(response.get("error").asText());
    }
    return response.has("result") ? response.get("result").asText() : "OK";
  }
}
