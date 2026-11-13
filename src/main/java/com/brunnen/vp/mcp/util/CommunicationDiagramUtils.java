package com.brunnen.vp.mcp.util;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for UML communication diagram operations: name and sequence-number validation and
 * message label handling (the layout is {@link CommunicationLayoutProfile}).
 */
public final class CommunicationDiagramUtils {

  /**
   * UML sequence expression: dot-separated integers, each optionally followed by a concurrent
   * thread letter ({@code 1.2a}), with an optional trailing recurrence star ({@code 1.1*}).
   */
  private static final String SEQUENCE_REGEX = "\\d+[a-zA-Z]?(?:\\.\\d+[a-zA-Z]?)*\\*?";

  private static final Pattern SEQUENCE = Pattern.compile(SEQUENCE_REGEX);

  private static final Pattern PREFIXED =
      Pattern.compile("^\\s*(" + SEQUENCE_REGEX + ")\\s*:\\s*(.*)$", Pattern.DOTALL);

  private CommunicationDiagramUtils() {}

  /**
   * Check whether a participant or diagram name can be used as a handle.
   *
   * @param name the name
   * @return true when the name has visible characters
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Trim a free-text value.
   *
   * @param value the raw value, may be null
   * @return the trimmed value, or null when nothing was supplied
   */
  public static String clean(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Normalize a sequence number: trims it and drops a trailing colon ({@code "1.2:"} becomes {@code
   * "1.2"}).
   *
   * @param sequenceNumber the raw sequence number
   * @return the normalized number, or null when nothing was supplied
   */
  public static String normalizeSequenceNumber(String sequenceNumber) {
    String trimmed = clean(sequenceNumber);
    if (trimmed == null) {
      return null;
    }
    while (trimmed.endsWith(":")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Check a sequence number against the UML sequence expression form ({@code 1}, {@code 1.2},
   * {@code 2a}, {@code 1.1*}).
   *
   * @param sequenceNumber the normalized sequence number
   * @return true when it is a valid sequence expression
   */
  public static boolean isValidSequenceNumber(String sequenceNumber) {
    return sequenceNumber != null && SEQUENCE.matcher(sequenceNumber).matches();
  }

  /**
   * Extract the sequence number written in front of a message text ({@code "1.2: validate()"}).
   *
   * @param text the message text
   * @return the prefix number, or null when the text carries none
   */
  public static String sequencePrefixOf(String text) {
    if (text == null) {
      return null;
    }
    Matcher m = PREFIXED.matcher(text);
    return m.matches() ? m.group(1) : null;
  }

  /**
   * Remove a leading {@code "N:"} / {@code "1.2:"} sequence prefix from a message text. Visual
   * Paradigm renders the sequence number itself, so keeping the prefix would print it twice.
   *
   * @param text the message text
   * @return the text without the prefix, trimmed; empty when nothing remains
   */
  public static String stripSequencePrefix(String text) {
    if (text == null) {
      return "";
    }
    Matcher m = PREFIXED.matcher(text);
    return m.matches() ? m.group(2).trim() : text.trim();
  }

  /**
   * Resolve the sequence number and the bare message text of a message. An explicit sequence number
   * wins over a number written in front of the text; the prefix is always stripped from the text.
   *
   * @param sequenceNumber the explicit sequence number, may be null or blank
   * @param messageText the message text, may carry a {@code "1.2:"} prefix
   * @return {sequence number or null, message text}
   */
  public static String[] resolveMessage(String sequenceNumber, String messageText) {
    String seq = normalizeSequenceNumber(sequenceNumber);
    if (seq == null) {
      seq = sequencePrefixOf(messageText);
    }
    return new String[] {seq, stripSequencePrefix(messageText)};
  }

  /**
   * Format a message label the way it reads on the diagram.
   *
   * @param sequenceNumber the sequence number, may be null
   * @param text the message text
   * @return {@code "1.2: validate()"}, or just the text when there is no number
   */
  public static String formatMessageLabel(String sequenceNumber, String text) {
    String body = text == null ? "" : text.trim();
    String seq = normalizeSequenceNumber(sequenceNumber);
    return seq == null ? body : seq + ": " + body;
  }

  /**
   * The leading integer of a sequence number ({@code "1.2"} gives 1, {@code "3a"} gives 3).
   *
   * @param sequenceNumber the sequence number
   * @return the top-level number, or -1 when it does not start with a digit
   */
  public static int topLevelNumber(String sequenceNumber) {
    String seq = normalizeSequenceNumber(sequenceNumber);
    if (seq == null) {
      return -1;
    }
    int end = 0;
    while (end < seq.length() && Character.isDigit(seq.charAt(end))) {
      end++;
    }
    if (end == 0 || end > 9) {
      return -1;
    }
    return Integer.parseInt(seq.substring(0, end));
  }

  /**
   * Next free top-level sequence number: one past the highest top-level number already used.
   *
   * @param existing the sequence numbers already on the diagram, may be null
   * @return the next number as text, {@code "1"} for an empty diagram
   */
  public static String nextSequenceNumber(Collection<String> existing) {
    int max = 0;
    if (existing != null) {
      for (String seq : existing) {
        max = Math.max(max, topLevelNumber(seq));
      }
    }
    return String.valueOf(max + 1);
  }
}
