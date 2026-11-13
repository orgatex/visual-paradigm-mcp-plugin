package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.FlowchartDfdUtils.DfdNotation;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.DfdRole;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.FlowchartKind;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Test class for FlowchartDfdUtils. */
public class FlowchartDfdUtilsTest {

  @Test
  public void testParseFlowchartKindCanonicalNames() {
    for (FlowchartKind kind : FlowchartKind.values()) {
      assertEquals(
          "Canonical name parses: " + kind.label(),
          kind,
          FlowchartDfdUtils.parseFlowchartKind(kind.label()));
    }
  }

  @Test
  public void testParseFlowchartKindIgnoresCaseAndSeparators() {
    assertEquals(FlowchartKind.INPUT_OUTPUT, FlowchartDfdUtils.parseFlowchartKind("Input/Output"));
    assertEquals(
        FlowchartKind.PREDEFINED_PROCESS,
        FlowchartDfdUtils.parseFlowchartKind("predefined_process"));
    assertEquals(
        FlowchartKind.MANUAL_INPUT, FlowchartDfdUtils.parseFlowchartKind(" Manual Input "));
    assertEquals(FlowchartKind.DECISION, FlowchartDfdUtils.parseFlowchartKind("DECISION"));
  }

  @Test
  public void testParseFlowchartKindAliases() {
    assertEquals(FlowchartKind.TERMINATOR, FlowchartDfdUtils.parseFlowchartKind("start"));
    assertEquals(FlowchartKind.TERMINATOR, FlowchartDfdUtils.parseFlowchartKind("End"));
    assertEquals(FlowchartKind.INPUT_OUTPUT, FlowchartDfdUtils.parseFlowchartKind("io"));
    assertEquals(
        FlowchartKind.PREDEFINED_PROCESS, FlowchartDfdUtils.parseFlowchartKind("subprocess"));
    assertEquals(FlowchartKind.DATABASE, FlowchartDfdUtils.parseFlowchartKind("db"));
    assertEquals(
        FlowchartKind.CONNECTOR, FlowchartDfdUtils.parseFlowchartKind("on-page connector"));
  }

  @Test
  public void testParseFlowchartKindRejectsUnknown() {
    assertNull("Unknown kind", FlowchartDfdUtils.parseFlowchartKind("swimlane"));
    assertNull("Empty kind", FlowchartDfdUtils.parseFlowchartKind(""));
    assertNull("Null kind", FlowchartDfdUtils.parseFlowchartKind(null));
  }

  @Test
  public void testFlowchartKindNames() {
    assertEquals(
        "terminator|process|decision|inputOutput|document|predefinedProcess|database|manualInput"
            + "|connector",
        FlowchartDfdUtils.flowchartKindNames());
  }

  @Test
  public void testParseDfdNotation() {
    assertEquals(DfdNotation.STANDARD, FlowchartDfdUtils.parseDfdNotation(null));
    assertEquals(DfdNotation.STANDARD, FlowchartDfdUtils.parseDfdNotation("  "));
    assertEquals(DfdNotation.STANDARD, FlowchartDfdUtils.parseDfdNotation("Standard"));
    assertEquals(DfdNotation.GANE_SARSON, FlowchartDfdUtils.parseDfdNotation("Gane-Sarson"));
    assertEquals(DfdNotation.GANE_SARSON, FlowchartDfdUtils.parseDfdNotation("ganeSarson"));
    assertEquals(
        DfdNotation.YOURDON_DEMARCO, FlowchartDfdUtils.parseDfdNotation("Yourdon DeMarco"));
    assertEquals(DfdNotation.YOURDON_COAD, FlowchartDfdUtils.parseDfdNotation("Yourdon & Coad"));
    assertEquals(DfdNotation.YOURDON_COAD, FlowchartDfdUtils.parseDfdNotation("yourdonCoad"));
    assertNull("Unknown notation", FlowchartDfdUtils.parseDfdNotation("SSADM"));
  }

  @Test
  public void testDfdNotationDiagramTypesMatchVisualParadigm() {
    assertEquals(
        IDiagramTypeConstants.DIAGRAM_TYPE_DATA_FLOW_DIAGRAM, DfdNotation.STANDARD.diagramType());
    assertEquals(
        IDiagramTypeConstants.DIAGRAM_TYPE_GANE_SARSON_DFDUI_MODEL,
        DfdNotation.GANE_SARSON.diagramType());
    assertEquals(
        IDiagramTypeConstants.DIAGRAM_TYPE_YOURDON_DE_MARCO_DFDUI_MODEL,
        DfdNotation.YOURDON_DEMARCO.diagramType());
    assertEquals(
        IDiagramTypeConstants.DIAGRAM_TYPE_YOURDON_AND_COAD_DFDUI_MODEL,
        DfdNotation.YOURDON_COAD.diagramType());
  }

  @Test
  public void testNotationOfDiagramType() {
    for (DfdNotation notation : DfdNotation.values()) {
      assertEquals(
          "Round trip " + notation,
          notation,
          FlowchartDfdUtils.notationOfDiagramType(notation.diagramType()));
    }
    assertNull(
        "Flowchart is not a DFD",
        FlowchartDfdUtils.notationOfDiagramType(IDiagramTypeConstants.DIAGRAM_TYPE_FLOWCHART));
    assertNull("Null type", FlowchartDfdUtils.notationOfDiagramType(null));
  }

  @Test
  public void testDfdFlowViolationAllowsFlowsThroughProcess() {
    assertNull(FlowchartDfdUtils.dfdFlowViolation(DfdRole.EXTERNAL_ENTITY, DfdRole.PROCESS, false));
    assertNull(FlowchartDfdUtils.dfdFlowViolation(DfdRole.PROCESS, DfdRole.DATA_STORE, false));
    assertNull(FlowchartDfdUtils.dfdFlowViolation(DfdRole.DATA_STORE, DfdRole.PROCESS, false));
    assertNull(FlowchartDfdUtils.dfdFlowViolation(DfdRole.PROCESS, DfdRole.PROCESS, false));
  }

  @Test
  public void testDfdFlowViolationRejectsFlowsBypassingProcesses() {
    String entityToStore =
        FlowchartDfdUtils.dfdFlowViolation(DfdRole.EXTERNAL_ENTITY, DfdRole.DATA_STORE, false);
    assertTrue(
        "Names both roles: " + entityToStore,
        entityToStore.contains("external entity -> data store"));
    assertTrue(
        FlowchartDfdUtils.dfdFlowViolation(DfdRole.DATA_STORE, DfdRole.DATA_STORE, false) != null);
    assertTrue(
        FlowchartDfdUtils.dfdFlowViolation(DfdRole.EXTERNAL_ENTITY, DfdRole.EXTERNAL_ENTITY, false)
            != null);
  }

  @Test
  public void testDfdFlowViolationRejectsSelfFlowsAndUnknownEnds() {
    assertEquals(
        "A data flow needs two different elements",
        FlowchartDfdUtils.dfdFlowViolation(DfdRole.PROCESS, DfdRole.PROCESS, true));
    assertTrue(FlowchartDfdUtils.dfdFlowViolation(null, DfdRole.PROCESS, false) != null);
    assertTrue(FlowchartDfdUtils.dfdFlowViolation(DfdRole.PROCESS, null, false) != null);
  }

  @Test
  public void testNextDfdIdStartsNumbering() {
    assertEquals("1", FlowchartDfdUtils.nextDfdId(Collections.<String>emptyList(), ""));
    assertEquals("1", FlowchartDfdUtils.nextDfdId(null, null));
    assertEquals("D1", FlowchartDfdUtils.nextDfdId(Collections.<String>emptyList(), "D"));
    assertEquals(
        "IDs without a trailing number are ignored",
        "D1",
        FlowchartDfdUtils.nextDfdId(Arrays.asList("Archive", null), "D"));
  }

  @Test
  public void testNextDfdIdContinuesNumbering() {
    assertEquals("3", FlowchartDfdUtils.nextDfdId(Arrays.asList("1", "2"), ""));
    assertEquals("4", FlowchartDfdUtils.nextDfdId(Arrays.asList("1", "3", "2"), ""));
    assertEquals("1.3", FlowchartDfdUtils.nextDfdId(Arrays.asList("1.1", "1.2"), ""));
    assertEquals("D3", FlowchartDfdUtils.nextDfdId(Arrays.asList("D1", "D2"), "D"));
    assertEquals("D11", FlowchartDfdUtils.nextDfdId(Arrays.asList("D2", " D10 "), "D"));
  }

  @Test
  public void testIsValidName() {
    assertTrue(FlowchartDfdUtils.isValidName("Check stock"));
    assertFalse(FlowchartDfdUtils.isValidName(null));
    assertFalse(FlowchartDfdUtils.isValidName(""));
    assertFalse(FlowchartDfdUtils.isValidName("   "));
  }

  @Test
  public void testNormalizeLabelAndSuffix() {
    assertEquals("Yes", FlowchartDfdUtils.normalizeLabel("  Yes "));
    assertNull(FlowchartDfdUtils.normalizeLabel(null));
    assertNull(FlowchartDfdUtils.normalizeLabel("   "));
    assertEquals(" [No]", FlowchartDfdUtils.labelSuffix("No"));
    assertEquals("", FlowchartDfdUtils.labelSuffix(null));
  }

  @Test
  public void testNextDfdIdLevelZero() {
    assertEquals("2.0", FlowchartDfdUtils.nextDfdId(Arrays.asList("1.0"), ""));
    // a stray plain "1" next to level-0 numbers does not turn 5.0 into 5.1
    assertEquals("6.0", FlowchartDfdUtils.nextDfdId(Arrays.asList("1", "5.0"), ""));
    assertEquals("3", FlowchartDfdUtils.nextDfdId(Arrays.asList("1", "2"), ""));
    assertEquals("4.0", FlowchartDfdUtils.nextDfdId(Arrays.asList("1.0", "3.0", "2.0"), ""));
    assertEquals(
        "child numbers still count up",
        "1.3",
        FlowchartDfdUtils.nextDfdId(Arrays.asList("1.1", "1.2"), ""));
  }

  @Test
  public void testStoreIdNumber() {
    assertEquals("1", FlowchartDfdUtils.storeIdNumber("D1"));
    assertEquals("12", FlowchartDfdUtils.storeIdNumber(" d12 "));
    assertEquals("1", FlowchartDfdUtils.storeIdNumber("1"));
    assertEquals("DB", FlowchartDfdUtils.storeIdNumber("DB"));
  }
}
