package com.brunnen.vp.mcp.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helpers for flowcharts and data flow diagrams: kind/notation parsing, DFD modelling rules,
 * ID numbering and label normalization. No Visual Paradigm API calls, so every method is
 * unit-testable without a running VP.
 */
public final class FlowchartDfdUtils {

  /**
   * Splits an ID into a prefix and its trailing number: {@code D3 -> D, 3}, {@code 1.2 -> 1., 2}.
   */
  private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

  private static final Pattern LEVEL_ZERO = Pattern.compile("^(\\d+)\\.0$");

  private FlowchartDfdUtils() {}

  /** Flowchart symbol kinds supported by {@code addFlowchartShape}. */
  public enum FlowchartKind {
    TERMINATOR("terminator"),
    PROCESS("process"),
    DECISION("decision"),
    INPUT_OUTPUT("inputOutput"),
    DOCUMENT("document"),
    PREDEFINED_PROCESS("predefinedProcess"),
    DATABASE("database"),
    MANUAL_INPUT("manualInput"),
    CONNECTOR("connector");

    private final String label;

    FlowchartKind(String label) {
      this.label = label;
    }

    /**
     * The canonical name accepted by the tool.
     *
     * @return the camelCase kind name
     */
    public String label() {
      return label;
    }
  }

  /** The DFD notations Visual Paradigm offers, each backed by its own diagram type. */
  public enum DfdNotation {
    /** Visual Paradigm's own data flow diagram. */
    STANDARD("DataFlowDiagram", "standard"),
    GANE_SARSON("GaneSarsonDFD", "Gane-Sarson"),
    YOURDON_DEMARCO("YourdonDeMarcoDFD", "Yourdon-DeMarco"),
    YOURDON_COAD("YourdonAndCoadDFD", "Yourdon-Coad");

    private final String diagramType;
    private final String label;

    DfdNotation(String diagramType, String label) {
      this.diagramType = diagramType;
      this.label = label;
    }

    /**
     * The Visual Paradigm diagram type string (the matching {@code IDiagramTypeConstants} value).
     *
     * @return the diagram type
     */
    public String diagramType() {
      return diagramType;
    }

    /**
     * Human-readable notation name.
     *
     * @return the label
     */
    public String label() {
      return label;
    }
  }

  /** The role a shape plays in a data flow diagram. */
  public enum DfdRole {
    PROCESS("process"),
    EXTERNAL_ENTITY("external entity"),
    DATA_STORE("data store");

    private final String label;

    DfdRole(String label) {
      this.label = label;
    }

    /**
     * Human-readable role name.
     *
     * @return the label
     */
    public String label() {
      return label;
    }
  }

  /**
   * Parse a flowchart kind, ignoring case and separators ({@code input/output}, {@code
   * predefined_process}) and accepting a few common aliases.
   *
   * @param kind the raw kind
   * @return the kind, or null when it is not recognised
   */
  public static FlowchartKind parseFlowchartKind(String kind) {
    String key = squash(kind);
    switch (key) {
      case "terminator":
      case "terminal":
      case "start":
      case "end":
      case "startend":
      case "begin":
      case "stop":
        return FlowchartKind.TERMINATOR;
      case "process":
      case "action":
      case "step":
        return FlowchartKind.PROCESS;
      case "decision":
      case "condition":
      case "if":
        return FlowchartKind.DECISION;
      case "inputoutput":
      case "io":
      case "data":
      case "input":
      case "output":
        return FlowchartKind.INPUT_OUTPUT;
      case "document":
      case "doc":
        return FlowchartKind.DOCUMENT;
      case "predefinedprocess":
      case "predefined":
      case "subprocess":
      case "subroutine":
        return FlowchartKind.PREDEFINED_PROCESS;
      case "database":
      case "db":
        return FlowchartKind.DATABASE;
      case "manualinput":
        return FlowchartKind.MANUAL_INPUT;
      case "connector":
      case "onpageconnector":
      case "onpagereference":
        return FlowchartKind.CONNECTOR;
      default:
        return null;
    }
  }

  /**
   * The accepted flowchart kinds, for error messages and tool descriptions.
   *
   * @return the kinds joined with {@code |}
   */
  public static String flowchartKindNames() {
    StringBuilder sb = new StringBuilder();
    for (FlowchartKind kind : FlowchartKind.values()) {
      if (sb.length() > 0) {
        sb.append('|');
      }
      sb.append(kind.label());
    }
    return sb.toString();
  }

  /**
   * Parse a DFD notation name, ignoring case and separators. Blank selects Visual Paradigm's
   * standard data flow diagram.
   *
   * @param notation the raw notation
   * @return the notation, or null when it is not recognised
   */
  public static DfdNotation parseDfdNotation(String notation) {
    String key = squash(notation);
    switch (key) {
      case "":
      case "standard":
      case "default":
      case "dfd":
        return DfdNotation.STANDARD;
      case "ganesarson":
      case "gane":
      case "gs":
        return DfdNotation.GANE_SARSON;
      case "yourdondemarco":
      case "yourdon":
      case "demarco":
      case "yd":
        return DfdNotation.YOURDON_DEMARCO;
      case "yourdoncoad":
      case "yourdonandcoad":
      case "coad":
      case "yc":
        return DfdNotation.YOURDON_COAD;
      default:
        return null;
    }
  }

  /**
   * Map a Visual Paradigm diagram type string to its DFD notation.
   *
   * @param diagramType the value of {@code IDiagramUIModel.getType()}
   * @return the notation, or null when the diagram is not a data flow diagram
   */
  public static DfdNotation notationOfDiagramType(String diagramType) {
    if (diagramType == null) {
      return null;
    }
    for (DfdNotation notation : DfdNotation.values()) {
      if (notation.diagramType().equals(diagramType)) {
        return notation;
      }
    }
    return null;
  }

  /**
   * Check a data flow against the DFD rule that every flow starts or ends at a process: an external
   * entity or data store cannot exchange data directly with another entity or store.
   *
   * @param from the role of the source shape
   * @param to the role of the target shape
   * @param sameElement whether source and target are the same shape
   * @return a human-readable violation, or null when the flow is allowed
   */
  public static String dfdFlowViolation(DfdRole from, DfdRole to, boolean sameElement) {
    if (from == null || to == null) {
      return "Both ends of a data flow must be a process, external entity or data store";
    }
    if (sameElement) {
      return "A data flow needs two different elements";
    }
    if (from != DfdRole.PROCESS && to != DfdRole.PROCESS) {
      return "Invalid data flow "
          + from.label()
          + " -> "
          + to.label()
          + ": every data flow must start or end at a process; route it through a process";
    }
    return null;
  }

  /**
   * Next free DFD identifier. Continues the numbering of the most recently listed ID that ends in a
   * number, keeping its prefix: {@code [1, 2] -> 3}, {@code [1.1, 1.2] -> 1.3}, {@code [D1, D2] ->
   * D3}; level-0 numbers keep their form, {@code [1.0, 2.0] -> 3.0}. Without such an ID the result
   * is {@code defaultPrefix + "1"}.
   *
   * @param existingIds the IDs already used on the diagram, in creation order (nulls ignored)
   * @param defaultPrefix the prefix of the first ID, e.g. {@code ""} for processes or {@code "D"}
   *     for data stores
   * @return the next ID
   */
  public static String nextDfdId(List<String> existingIds, String defaultPrefix) {
    // Level-0 numbering 1.0, 2.0, 3.0 continues with the next whole number, also when a plain
    // "1" is among them ([1, 5.0] -> 6.0)
    if (existingIds != null && !existingIds.isEmpty()) {
      long maxWhole = -1;
      boolean allWhole = true;
      boolean anyLevelZero = false;
      for (String id : existingIds) {
        String trimmed = id == null ? "" : id.trim();
        Matcher m = LEVEL_ZERO.matcher(trimmed);
        if (m.matches()) {
          anyLevelZero = true;
          maxWhole = Math.max(maxWhole, parseOrZero(m.group(1)));
        } else if (trimmed.matches("\\d+")) {
          maxWhole = Math.max(maxWhole, parseOrZero(trimmed));
        } else {
          allWhole = false;
          break;
        }
      }
      if (allWhole && anyLevelZero) {
        return (maxWhole + 1) + ".0";
      }
    }
    String prefix = null;
    if (existingIds != null) {
      for (String id : existingIds) {
        Matcher m = id == null ? null : TRAILING_NUMBER.matcher(id.trim());
        if (m != null && m.matches()) {
          prefix = m.group(1);
        }
      }
    }
    if (prefix == null) {
      return (defaultPrefix == null ? "" : defaultPrefix) + "1";
    }
    long max = 0;
    for (String id : existingIds) {
      Matcher m = id == null ? null : TRAILING_NUMBER.matcher(id.trim());
      if (m != null && m.matches() && m.group(1).equals(prefix)) {
        max = Math.max(max, parseOrZero(m.group(2)));
      }
    }
    return prefix + (max + 1);
  }

  /**
   * Next marker letter for an external entity: {@code [] -> a}, {@code [a, b] -> c}; after z the
   * markers continue as aa, ab... Ids that are not letters are ignored.
   *
   * @param existingIds the markers already used on the diagram
   * @return the next free marker
   */
  public static String nextEntityId(List<String> existingIds) {
    java.util.Set<String> used = new java.util.HashSet<>();
    if (existingIds != null) {
      for (String id : existingIds) {
        if (id != null) {
          used.add(id.trim().toLowerCase(java.util.Locale.ROOT));
        }
      }
    }
    for (int n = 0; ; n++) {
      StringBuilder sb = new StringBuilder();
      int v = n;
      do {
        sb.insert(0, (char) ('a' + v % 26));
        v = v / 26 - 1;
      } while (v >= 0);
      if (!used.contains(sb.toString())) {
        return sb.toString();
      }
    }
  }

  /**
   * Check whether an element name can be used as a shape caption and a flow handle.
   *
   * @param name the name
   * @return true when the name is not blank
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Normalize a flow label. Blank labels become null so the connector stays unlabelled instead of
   * showing an empty caption box.
   *
   * @param label the raw label
   * @return the trimmed label, or null when nothing was supplied
   */
  public static String normalizeLabel(String label) {
    if (label == null) {
      return null;
    }
    String trimmed = label.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Format an optional label for a tool result, e.g. {@code " [Yes]"}.
   *
   * @param label the normalized label, may be null
   * @return the suffix, or an empty string when there is no label
   */
  public static String labelSuffix(String label) {
    if (label == null) {
      return "";
    }
    // a label written as a guard keeps its own brackets
    return label.startsWith("[") && label.endsWith("]") ? " " + label : " [" + label + "]";
  }

  private static long parseOrZero(String digits) {
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Lower-case and drop everything but letters and digits: {@code "Input/Output" -> inputoutput}.
   */
  private static String squash(String value) {
    if (value == null) {
      return "";
    }
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  /**
   * The number part of a data store ID: Visual Paradigm prints a store's ID with its own "D" in
   * front, so a stored "D1" would read "DD1". A leading D (any case) followed by a digit is
   * dropped.
   *
   * @param id the ID as given, e.g. "D1" or "1"
   * @return the ID without the D prefix, or null for null
   */
  public static String storeIdNumber(String id) {
    if (id == null) {
      return null;
    }
    String t = id.trim();
    if (t.length() > 1
        && (t.charAt(0) == 'D' || t.charAt(0) == 'd')
        && Character.isDigit(t.charAt(1))) {
      return t.substring(1);
    }
    return t;
  }
}
