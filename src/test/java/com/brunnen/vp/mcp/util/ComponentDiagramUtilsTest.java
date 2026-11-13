package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.ComponentDiagramUtils.Kind;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Test class for ComponentDiagramUtils. */
public class ComponentDiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Named element is valid", ComponentDiagramUtils.isValidName("OrderService"));
    assertFalse("Null name is invalid", ComponentDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", ComponentDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", ComponentDiagramUtils.isValidName("   "));
  }

  @Test
  public void testKindOfShapeType() {
    assertEquals(Kind.COMPONENT, ComponentDiagramUtils.kindOfShapeType("Component"));
    assertEquals(Kind.INTERFACE, ComponentDiagramUtils.kindOfShapeType("ComponentInterface"));
    assertEquals(Kind.PORT, ComponentDiagramUtils.kindOfShapeType("Port"));
    assertEquals(Kind.OTHER, ComponentDiagramUtils.kindOfShapeType("NOTE"));
    assertEquals(Kind.OTHER, ComponentDiagramUtils.kindOfShapeType(null));
  }

  @Test
  public void testKindLabels() {
    assertEquals("component", Kind.COMPONENT.label());
    assertEquals("interface", Kind.INTERFACE.label());
    assertEquals("port", Kind.PORT.label());
    assertEquals("element", Kind.OTHER.label());
  }

  @Test
  public void testNormalizeStereotype() {
    assertEquals("subsystem", ComponentDiagramUtils.normalizeStereotype("  subsystem "));
    assertEquals("subsystem", ComponentDiagramUtils.normalizeStereotype("<<subsystem>>"));
    assertEquals("service", ComponentDiagramUtils.normalizeStereotype("«service»"));
    assertEquals("use", ComponentDiagramUtils.normalizeStereotype(" << use >> "));
    assertNull("Null stays null", ComponentDiagramUtils.normalizeStereotype(null));
    assertNull("Blank becomes null", ComponentDiagramUtils.normalizeStereotype("   "));
    assertNull("Bare chevrons become null", ComponentDiagramUtils.normalizeStereotype("<<>>"));
  }

  @Test
  public void testParseStereotypes() {
    assertEquals(
        Arrays.asList("subsystem", "service"),
        ComponentDiagramUtils.parseStereotypes("subsystem, <<service>>"));
    assertEquals(
        "Duplicates are dropped case-insensitively, first spelling wins",
        Collections.singletonList("Subsystem"),
        ComponentDiagramUtils.parseStereotypes("Subsystem,subsystem, <<SUBSYSTEM>>"));
    // VP draws <<component>> itself
    assertTrue(ComponentDiagramUtils.parseStereotypes("component").isEmpty());
    assertTrue(ComponentDiagramUtils.parseStereotypes("<<Component>>").isEmpty());
    assertEquals(
        java.util.Arrays.asList("service"),
        ComponentDiagramUtils.parseStereotypes("component, service"));
    assertTrue(ComponentDiagramUtils.parseStereotypes(null).isEmpty());
    assertTrue(ComponentDiagramUtils.parseStereotypes("").isEmpty());
    assertTrue(ComponentDiagramUtils.parseStereotypes(" , ,").isEmpty());
  }

  @Test
  public void testFormatStereotypes() {
    assertEquals(
        "<<subsystem>> <<service>>",
        ComponentDiagramUtils.formatStereotypes(Arrays.asList("subsystem", "service")));
    assertEquals("", ComponentDiagramUtils.formatStereotypes(Collections.<String>emptyList()));
    assertEquals("", ComponentDiagramUtils.formatStereotypes(null));
  }

  @Test
  public void testCheckInterfaceLinkAcceptsComponentAndPortSources() {
    assertNull(
        ComponentDiagramUtils.checkInterfaceLink(Kind.COMPONENT, "Orders", Kind.INTERFACE, "IPay"));
    assertNull(ComponentDiagramUtils.checkInterfaceLink(Kind.PORT, "api", Kind.INTERFACE, "IPay"));
  }

  @Test
  public void testCheckInterfaceLinkRejectsWrongEnds() {
    assertEquals(
        "'IPay' is an interface, not a component or port: only components and ports provide or"
            + " require interfaces",
        ComponentDiagramUtils.checkInterfaceLink(Kind.INTERFACE, "IPay", Kind.INTERFACE, "IShip"));
    assertEquals(
        "'Billing' is a component, not an interface (add it with addComponentInterface)",
        ComponentDiagramUtils.checkInterfaceLink(
            Kind.COMPONENT, "Orders", Kind.COMPONENT, "Billing"));
    assertEquals(
        "'Note1' is an element, not an interface (add it with addComponentInterface)",
        ComponentDiagramUtils.checkInterfaceLink(Kind.COMPONENT, "Orders", Kind.OTHER, "Note1"));
  }

  @Test
  public void testCheckDependencyEnd() {
    assertNull(ComponentDiagramUtils.checkDependencyEnd(Kind.COMPONENT, "Orders"));
    assertNull(ComponentDiagramUtils.checkDependencyEnd(Kind.INTERFACE, "IPay"));
    assertNull(ComponentDiagramUtils.checkDependencyEnd(Kind.PORT, "api"));
    assertEquals(
        "'Note1' is not a component, interface or port",
        ComponentDiagramUtils.checkDependencyEnd(Kind.OTHER, "Note1"));
  }

  @Test
  public void testCheckKind() {
    assertNull(ComponentDiagramUtils.checkKind(Kind.COMPONENT, Kind.COMPONENT, "Orders"));
    assertEquals(
        "'api' is a port, not a component",
        ComponentDiagramUtils.checkKind(Kind.PORT, Kind.COMPONENT, "api"));
    assertEquals(
        "'IPay' is an interface, not a component",
        ComponentDiagramUtils.checkKind(Kind.INTERFACE, Kind.COMPONENT, "IPay"));
  }

  @Test
  public void testSymbolSize() {
    assertArrayEquals(
        "Believable fitted size is kept",
        new int[] {26, 26},
        ComponentDiagramUtils.symbolSize(26, 26, 24, 60));
    assertArrayEquals(
        "Ball stretched around its label falls back to the default",
        new int[] {24, 24},
        ComponentDiagramUtils.symbolSize(140, 30, 24, 60));
    assertArrayEquals(
        "Empty size falls back to the default",
        new int[] {16, 16},
        ComponentDiagramUtils.symbolSize(0, 0, 16, 30));
  }
}
