package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for UML component diagram operations: shape kind parsing, stereotype parsing and
 * label formatting, connector end validation, and the placement geometry (top-level grid, nested
 * components inside their parent, ports on a component's border).
 *
 * <p>Pure helpers with no Visual Paradigm types, so they can be unit tested without a running VP.
 * Rectangles are {@code int[] {x, y, width, height}} in diagram coordinates.
 */
public final class ComponentDiagramUtils {

  /** What a shape on a component diagram stands for. */
  public enum Kind {
    COMPONENT("component"),
    INTERFACE("interface"),
    PORT("port"),
    PACKAGE("package"),
    OTHER("element");

    private final String label;

    Kind(String label) {
      this.label = label;
    }

    /**
     * Lower-case noun used in tool messages.
     *
     * @return the label, e.g. "component"
     */
    public String label() {
      return label;
    }
  }

  /** VP shape type of a component box (IComponentDiagramUIModel.SHAPETYPE_COMPONENT). */
  public static final String SHAPE_TYPE_COMPONENT = "Component";

  /** VP shape type of an interface (ball or class box). */
  public static final String SHAPE_TYPE_COMPONENT_INTERFACE = "ComponentInterface";

  /** VP shape type of a port. */
  public static final String SHAPE_TYPE_PORT = "Port";

  private ComponentDiagramUtils() {}

  /**
   * Check whether a name can be used for a component, interface or port. VP rejects empty names and
   * the name is also the handle other tools look the element up by.
   *
   * @param name the name
   * @return true when the name is usable
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Map a VP shape type id to the component diagram kind.
   *
   * @param shapeType the shape type, e.g. "ComponentInterface"
   * @return the kind, {@link Kind#OTHER} for anything else (notes, packages, null)
   */
  public static Kind kindOfShapeType(String shapeType) {
    if (SHAPE_TYPE_COMPONENT.equals(shapeType)) {
      return Kind.COMPONENT;
    }
    if (SHAPE_TYPE_COMPONENT_INTERFACE.equals(shapeType)) {
      return Kind.INTERFACE;
    }
    if (SHAPE_TYPE_PORT.equals(shapeType)) {
      return Kind.PORT;
    }
    return Kind.OTHER;
  }

  /**
   * Strip guillemets (&laquo;x&raquo;) or chevrons ({@code <<x>>}) and whitespace from one
   * stereotype.
   *
   * @param raw the stereotype as typed
   * @return the bare stereotype name, or null when nothing is left
   */
  public static String normalizeStereotype(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.startsWith("<<")) {
      s = s.substring(2);
    } else if (s.startsWith("«")) {
      s = s.substring(1);
    }
    if (s.endsWith(">>")) {
      s = s.substring(0, s.length() - 2);
    } else if (s.endsWith("»")) {
      s = s.substring(0, s.length() - 1);
    }
    s = s.trim();
    return s.isEmpty() ? null : s;
  }

  /**
   * Parse a comma-separated stereotype list such as {@code "subsystem, <<service>>"}. Blank entries
   * are dropped and duplicates (case-insensitive) are kept once, first spelling wins.
   *
   * @param raw the list as typed, may be null or blank
   * @return the bare stereotype names in input order, never null
   */
  public static List<String> parseStereotypes(String raw) {
    List<String> result = new ArrayList<>();
    if (raw == null) {
      return result;
    }
    List<String> seen = new ArrayList<>();
    for (String token : raw.split(",")) {
      String s = normalizeStereotype(token);
      // Visual Paradigm prints <<component>> on every component itself: a second one would read
      // "<<component>> <<component>>"
      if (s != null && "component".equalsIgnoreCase(s)) {
        continue;
      }
      if (s != null && !seen.contains(s.toLowerCase(Locale.ROOT))) {
        seen.add(s.toLowerCase(Locale.ROOT));
        result.add(s);
      }
    }
    return result;
  }

  /**
   * Format stereotypes the way UML shows them, for tool messages.
   *
   * @param stereotypes bare stereotype names
   * @return e.g. {@code "<<subsystem>> <<service>>"}, or "" when the list is empty
   */
  public static String formatStereotypes(List<String> stereotypes) {
    StringBuilder sb = new StringBuilder();
    if (stereotypes != null) {
      for (String s : stereotypes) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        sb.append("<<").append(s).append(">>");
      }
    }
    return sb.toString();
  }

  /**
   * Validate the ends of a provided/required interface link: the source must be a component or a
   * port, the target an interface.
   *
   * @param source kind of the providing/requiring element
   * @param sourceName its name, for the message
   * @param target kind of the interface end
   * @param targetName its name, for the message
   * @return an error message, or null when the link is valid
   */
  public static String checkInterfaceLink(
      Kind source, String sourceName, Kind target, String targetName) {
    if (source != Kind.COMPONENT && source != Kind.PORT) {
      return "'"
          + sourceName
          + "' is "
          + article(source)
          + ", not a component or port: only components and ports provide or require interfaces";
    }
    if (target != Kind.INTERFACE) {
      return "'"
          + targetName
          + "' is "
          + article(target)
          + ", not an interface (add it with addComponentInterface)";
    }
    return null;
  }

  /**
   * Validate one end of a component dependency.
   *
   * @param kind the end's kind
   * @param name its name, for the message
   * @return an error message, or null when components, interfaces and ports are linked
   */
  public static String checkDependencyEnd(Kind kind, String name) {
    if (kind == Kind.OTHER) {
      return "'" + name + "' is not a component, interface or port";
    }
    return null;
  }

  /**
   * Validate that an element found by name has the kind a tool needs, e.g. the owner of a port.
   *
   * @param actual the element's kind
   * @param expected the kind the tool needs
   * @param name the element's name, for the message
   * @return an error message, or null when the kinds match
   */
  public static String checkKind(Kind actual, Kind expected, String name) {
    if (actual == expected) {
      return null;
    }
    return "'" + name + "' is " + article(actual) + ", not " + article(expected);
  }

  private static String article(Kind kind) {
    String label = kind.label();
    return ("aeiou".indexOf(label.charAt(0)) >= 0 ? "an " : "a ") + label;
  }

  /**
   * Size of a symbol shape (interface ball, port square) after VP's fit-size. The symbol's label
   * sits outside the symbol, so a fitted size larger than {@code maxSize} means VP stretched the
   * symbol around its label; fall back to the default symbol size then.
   *
   * @param width fitted width
   * @param height fitted height
   * @param defaultSize the symbol's default side length
   * @param maxSize the largest believable side length
   * @return {width, height}
   */
  public static int[] symbolSize(int width, int height, int defaultSize, int maxSize) {
    if (width <= 0 || height <= 0 || width > maxSize || height > maxSize) {
      return new int[] {defaultSize, defaultSize};
    }
    return new int[] {width, height};
  }
}
