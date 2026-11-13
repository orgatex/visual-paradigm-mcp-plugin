package com.brunnen.vp.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Streamable HTTP transport at /mcp, the one Codex and Claude Code type "http" use. */
public class McpServerHttpTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static McpServer server;
  private static int port;

  @BeforeClass
  public static void start() throws Exception {
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    server = new McpServer();
    server.setPort(port);
    server.start();
  }

  @AfterClass
  public static void stop() {
    server.stop();
  }

  private static HttpURLConnection open(String method) throws Exception {
    HttpURLConnection c =
        (HttpURLConnection) new URL("http://localhost:" + port + "/mcp").openConnection();
    c.setRequestMethod(method);
    c.setConnectTimeout(5000);
    c.setReadTimeout(5000);
    return c;
  }

  private static HttpURLConnection post(String body) throws Exception {
    HttpURLConnection c = open("POST");
    c.setDoOutput(true);
    c.setRequestProperty("Content-Type", "application/json");
    c.setRequestProperty("Accept", "application/json, text/event-stream");
    try (OutputStream out = c.getOutputStream()) {
      out.write(body.getBytes(StandardCharsets.UTF_8));
    }
    return c;
  }

  private static JsonNode json(HttpURLConnection c) throws Exception {
    try (InputStream in = c.getInputStream()) {
      return MAPPER.readTree(in);
    }
  }

  @Test
  public void initializeEchoesSupportedProtocolVersion() throws Exception {
    HttpURLConnection c =
        post(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}");
    assertEquals(200, c.getResponseCode());
    assertTrue(c.getContentType().startsWith("application/json"));
    JsonNode r = json(c);
    assertEquals(1, r.get("id").asInt());
    assertEquals("2025-06-18", r.at("/result/protocolVersion").asText());
    assertEquals("visual-paradigm-mcp-server", r.at("/result/serverInfo/name").asText());
  }

  @Test
  public void unknownProtocolVersionFallsBackToLatest() throws Exception {
    HttpURLConnection c =
        post(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"1999-01-01\"}}");
    assertEquals("2025-06-18", json(c).at("/result/protocolVersion").asText());
  }

  @Test
  public void notificationIsAcceptedWithoutBody() throws Exception {
    HttpURLConnection c = post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
    assertEquals(202, c.getResponseCode());
  }

  @Test
  public void toolsListAndPingAnswerInline() throws Exception {
    JsonNode list = json(post("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}"));
    assertTrue(list.at("/result/tools").isArray());
    JsonNode ping = json(post("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}"));
    assertTrue(ping.get("result").isObject());
  }

  @Test
  public void getIsRefused() throws Exception {
    assertEquals(405, open("GET").getResponseCode());
  }

  @Test
  public void malformedBodyIsParseError() throws Exception {
    HttpURLConnection c = post("{not json");
    assertEquals(400, c.getResponseCode());
    try (InputStream in = c.getErrorStream()) {
      assertEquals(-32700, MAPPER.readTree(in).at("/error/code").asInt());
    }
  }
}
