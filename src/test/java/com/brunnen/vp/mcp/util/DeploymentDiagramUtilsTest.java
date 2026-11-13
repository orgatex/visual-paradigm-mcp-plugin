package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.NodeKind;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.PathLabel;
import java.util.Arrays;
import org.junit.Test;

/** Test class for DeploymentDiagramUtils. */
public class DeploymentDiagramUtilsTest {

  @Test
  public void testParseNodeKindDefaultsToNode() {
    assertEquals("Null kind is a plain node", NodeKind.NODE, parse(null));
    assertEquals("Blank kind is a plain node", NodeKind.NODE, parse("  "));
    assertEquals("node is a plain node", NodeKind.NODE, parse("Node"));
  }

  @Test
  public void testParseNodeKindDevice() {
    assertEquals("device", NodeKind.DEVICE, parse("device"));
    assertEquals("Case is ignored", NodeKind.DEVICE, parse(" DEVICE "));
    assertEquals("Guillemets are ignored", NodeKind.DEVICE, parse("<<device>>"));
  }

  @Test
  public void testParseNodeKindExecutionEnvironment() {
    for (String kind :
        Arrays.asList(
            "executionEnvironment",
            "Execution Environment",
            "execution_environment",
            "execution-environment",
            "execEnv",
            "EE")) {
      assertEquals(kind, NodeKind.EXECUTION_ENVIRONMENT, parse(kind));
    }
  }

  @Test
  public void testParseNodeKindRejectsUnknown() {
    assertNull("Unknown kinds are rejected", parse("server"));
    assertNull("Artifacts are not nodes", parse("artifact"));
  }

  @Test
  public void testNodeKindStereotypes() {
    assertNull("Plain nodes carry no keyword", NodeKind.NODE.getStereotype());
    assertEquals("device", NodeKind.DEVICE.getStereotype());
    assertEquals("executionEnvironment", NodeKind.EXECUTION_ENVIRONMENT.getStereotype());
  }

  @Test
  public void testKeyword() {
    assertEquals("<<device>>", DeploymentDiagramUtils.keyword("device"));
    assertEquals("Keyword is trimmed", "<<deploy>>", DeploymentDiagramUtils.keyword(" deploy "));
    assertEquals("No stereotype, no keyword", "", DeploymentDiagramUtils.keyword(null));
    assertEquals("Blank stereotype, no keyword", "", DeploymentDiagramUtils.keyword(" "));
  }

  @Test
  public void testIsValidName() {
    assertTrue(DeploymentDiagramUtils.isValidName("AppServer"));
    assertFalse(DeploymentDiagramUtils.isValidName(null));
    assertFalse(DeploymentDiagramUtils.isValidName(""));
    assertFalse(DeploymentDiagramUtils.isValidName("   "));
  }

  @Test
  public void testNormalizeOptional() {
    assertEquals("AppServer", DeploymentDiagramUtils.normalizeOptional("  AppServer "));
    assertNull(DeploymentDiagramUtils.normalizeOptional(null));
    assertNull(DeploymentDiagramUtils.normalizeOptional("  "));
  }

  @Test
  public void testParsePathLabelPlainName() {
    PathLabel label = DeploymentDiagramUtils.parsePathLabel(" HTTPS ");
    assertEquals("Plain text is the connector name", "HTTPS", label.getName());
    assertNull(label.getStereotype());
  }

  @Test
  public void testParsePathLabelStereotype() {
    PathLabel label = DeploymentDiagramUtils.parsePathLabel("<<JDBC>>");
    assertNull("A bare keyword has no name", label.getName());
    assertEquals("JDBC", label.getStereotype());

    PathLabel guillemets = DeploymentDiagramUtils.parsePathLabel("« TCP/IP »");
    assertEquals("Guillemets work too", "TCP/IP", guillemets.getStereotype());
  }

  @Test
  public void testParsePathLabelStereotypeAndName() {
    PathLabel label = DeploymentDiagramUtils.parsePathLabel("<<TCP/IP>> port 443");
    assertEquals("TCP/IP", label.getStereotype());
    assertEquals("port 443", label.getName());
  }

  @Test
  public void testParsePathLabelBlankAndUnclosed() {
    PathLabel blank = DeploymentDiagramUtils.parsePathLabel("  ");
    assertNull(blank.getName());
    assertNull(blank.getStereotype());

    PathLabel unclosed = DeploymentDiagramUtils.parsePathLabel("<<HTTPS");
    assertEquals("An unclosed keyword stays plain text", "<<HTTPS", unclosed.getName());
    assertNull(unclosed.getStereotype());
  }

  @Test
  public void testResolveSize() {
    assertEquals("Explicit size wins", 300, DeploymentDiagramUtils.resolveSize(300, 120, 140));
    assertEquals("Fitted size otherwise", 120, DeploymentDiagramUtils.resolveSize(0, 120, 140));
    assertEquals("Default as last resort", 140, DeploymentDiagramUtils.resolveSize(-1, 0, 140));
  }

  private static NodeKind parse(String kind) {
    return DeploymentDiagramUtils.parseNodeKind(kind);
  }
}
