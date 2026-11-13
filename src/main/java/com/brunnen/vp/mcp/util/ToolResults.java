package com.brunnen.vp.mcp.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Makes the text a tool answers with tell success from failure by its first word: a failure always
 * starts with "Error". The tools state their failures in plain sentences that start with the thing
 * that is wrong ("Diagram not found: X", "Node not on diagram: Y", "Message name must not be
 * empty"); a client that reads only the beginning of the answer cannot tell them from a success.
 */
public final class ToolResults {

  /** Words a success starts with (a failure never does). */
  private static final Pattern SUCCESS =
      Pattern.compile(
          "^(Added|Created|Bounds|Stereotype|Removed|Updated|Exported|Saved|Moved|Laid out|"
              + "Rerouted|Set|Shown|Renamed|Auto-layout|Opened|Generated|Elements|Geometry|Diagrams|"
              + "Nothing to lay out|Re-?laid|Wrote|Primary key|Unchanged)\\b.*",
          Pattern.DOTALL);

  /** What the first sentence of a failure says. */
  private static final Pattern FAILURE =
      Pattern.compile(
          "(not found|not on (the |this )?diagram|not on the (class|use case|sequence|activity)"
              + " diagram|must not be empty|is required|^unknown |^no [a-z ]+ found|already (on"
              + " (the )?diagram|exists|is)|cannot |is not a |refused|no such|needs two|is not on"
              + " )",
          Pattern.CASE_INSENSITIVE);

  private ToolResults() {}

  /**
   * The answer with "Error: " in front when it states a failure that does not say so yet.
   *
   * @param result the text a tool returned
   * @return the same text, or the text after "Error: "
   */
  public static String normalize(String result) {
    if (result == null
        || result.isEmpty()
        || result.startsWith("Error")
        || SUCCESS.matcher(result).matches()) {
      return result;
    }
    String first = result;
    int cut = first.indexOf('\n');
    if (cut >= 0) {
      first = first.substring(0, cut);
    }
    if (first.length() > 160) {
      first = first.substring(0, 160);
    }
    return FAILURE.matcher(first.toLowerCase(Locale.ROOT)).find() ? "Error: " + result : result;
  }
}
