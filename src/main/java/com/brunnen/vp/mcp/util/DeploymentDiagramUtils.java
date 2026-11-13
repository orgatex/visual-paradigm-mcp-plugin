package com.brunnen.vp.mcp.util;

import java.util.Locale;

/**
 * Pure helpers for UML deployment diagrams: node-kind parsing, label formatting, name validation
 * and size resolution. The layout is {@link DeploymentLayoutProfile}.
 */
public final class DeploymentDiagramUtils {

  private DeploymentDiagramUtils() {}

  /** The UML node flavours a deployment node can have. */
  public enum NodeKind {
    /** A plain node, drawn without a keyword. */
    NODE(null),
    /** A physical device, drawn with the {@code <<device>>} keyword. */
    DEVICE("device"),
    /** A software execution environment, drawn with {@code <<executionEnvironment>>}. */
    EXECUTION_ENVIRONMENT("executionEnvironment");

    private final String stereotype;

    NodeKind(String stereotype) {
      this.stereotype = stereotype;
    }

    /**
     * The stereotype that marks this kind on the node.
     *
     * @return the stereotype name, or null for a plain node
     */
    public String getStereotype() {
      return stereotype;
    }
  }

  /** A communication path label split into its name and optional {@code <<stereotype>>}. */
  public static final class PathLabel {
    private final String name;
    private final String stereotype;

    PathLabel(String name, String stereotype) {
      this.name = name;
      this.stereotype = stereotype;
    }

    /**
     * The plain text shown as the connector name.
     *
     * @return the name, or null when there is none
     */
    public String getName() {
      return name;
    }

    /**
     * The keyword written as {@code <<x>>} or {@code «x»}, without the guillemets.
     *
     * @return the stereotype, or null when there is none
     */
    public String getStereotype() {
      return stereotype;
    }
  }

  /**
   * Parse the node kind parameter. Case, spaces, hyphens and underscores are ignored, so {@code
   * "Execution Environment"} and {@code "execution_environment"} both work.
   *
   * @param kind node, device or executionEnvironment (blank means node)
   * @return the kind, or null when the text is not a known kind
   */
  public static NodeKind parseNodeKind(String kind) {
    if (kind == null) {
      return NodeKind.NODE;
    }
    String key = kind.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-<>«»]", "");
    switch (key) {
      case "":
      case "node":
        return NodeKind.NODE;
      case "device":
        return NodeKind.DEVICE;
      case "executionenvironment":
      case "executionenv":
      case "execenvironment":
      case "execenv":
      case "environment":
      case "ee":
        return NodeKind.EXECUTION_ENVIRONMENT;
      default:
        return null;
    }
  }

  /**
   * Format a stereotype as the UML keyword text used in tool results.
   *
   * @param stereotype the stereotype name, may be null or blank
   * @return {@code <<stereotype>>}, or an empty string when there is no stereotype
   */
  public static String keyword(String stereotype) {
    if (stereotype == null || stereotype.trim().isEmpty()) {
      return "";
    }
    return "<<" + stereotype.trim() + ">>";
  }

  /**
   * Check whether a name can be used for a deployment element. Visual Paradigm rejects empty names
   * on several classifiers and the names are the handles used by later tool calls.
   *
   * @param name the element name
   * @return true when the name is usable
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Trim an optional reference such as a parent node name.
   *
   * @param name the raw text
   * @return the trimmed name, or null when nothing was supplied
   */
  public static String normalizeOptional(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Split a communication path label. A leading {@code <<x>>} or {@code «x»} becomes the
   * stereotype, the remaining text the connector name: {@code "HTTPS"} is a name, {@code
   * "<<JDBC>>"} a stereotype and {@code "<<TCP/IP>> port 443"} both.
   *
   * @param label the raw label, may be null or blank
   * @return the parsed label; both parts are null for a blank label
   */
  public static PathLabel parsePathLabel(String label) {
    String text = normalizeOptional(label);
    if (text == null) {
      return new PathLabel(null, null);
    }
    String open;
    String close;
    if (text.startsWith("<<")) {
      open = "<<";
      close = ">>";
    } else if (text.startsWith("«")) {
      open = "«";
      close = "»";
    } else {
      return new PathLabel(text, null);
    }
    int end = text.indexOf(close, open.length());
    if (end < 0) {
      return new PathLabel(text, null);
    }
    String stereotype = normalizeOptional(text.substring(open.length(), end));
    String name = normalizeOptional(text.substring(end + close.length()));
    return new PathLabel(name, stereotype);
  }

  /**
   * Pick one dimension of a shape: an explicit value wins, otherwise the fitted size, otherwise the
   * default.
   *
   * @param explicit the size requested by the caller ({@code <= 0} means not given)
   * @param fitted the size Visual Paradigm's fit-size produced ({@code <= 0} means unknown)
   * @param fallback the default size
   * @return the size to apply
   */
  public static int resolveSize(int explicit, int fitted, int fallback) {
    if (explicit > 0) {
      return explicit;
    }
    return fitted > 0 ? fitted : fallback;
  }
}
