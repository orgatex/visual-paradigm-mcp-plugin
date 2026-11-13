package com.brunnen.vp.mcp.util;

/** Utility class for UML state machine diagram operations. */
public final class StateMachineUtils {

  private StateMachineUtils() {}

  /**
   * Check whether a state name can be used as a handle for transitions.
   *
   * @param name the state name
   * @return true when the name is usable
   */
  public static boolean isValidStateName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Normalize a transition label. Blank labels become null so the connector stays unlabelled
   * instead of showing an empty caption box.
   *
   * @param label the raw label, UML form {@code event [guard] / effect}
   * @return the trimmed label, or null when nothing was supplied
   */
  public static String normalizeTransitionLabel(String label) {
    if (label == null) {
      return null;
    }
    String trimmed = label.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Whether two transition labels are the same (null and blank are both "no label").
   *
   * @param a one label, may be null
   * @param b the other label, may be null
   * @return true when both are empty or both read the same
   */
  public static boolean sameLabel(String a, String b) {
    String x = normalizeTransitionLabel(a);
    String y = normalizeTransitionLabel(b);
    return x == null ? y == null : x.equals(y);
  }
}
