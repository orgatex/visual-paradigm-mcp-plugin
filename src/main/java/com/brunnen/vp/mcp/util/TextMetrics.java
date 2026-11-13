package com.brunnen.vp.mcp.util;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel size of the text Visual Paradigm draws in captions (Dialog 12, as it measures them), and
 * what a caption box needs around its text.
 *
 * <p>Calibrated live against exported PNGs of VP 1.32 ("&lt;&lt;use&gt;&gt;", 60 px of text): a box
 * of {@link #PAD} px more than the text is what VP sizes a fresh caption to, a box of {@link
 * #MIN_PAD} px more still draws the whole text, a box of one pixel less ends the text in "...". A
 * box of two lines wraps at spaces. The measure here is VP's own for most texts but up to a few
 * pixels short for runs of narrow letters ("gui", "iiii"); {@link CaptionFit} asks VP itself.
 */
public final class TextMetrics {

  /** Height of a one-line caption. */
  public static final int LINE_HEIGHT = 14;

  /** Width VP adds to the text of a caption it sizes itself (the size every label must keep). */
  public static final int PAD = 14;

  /**
   * Least width beyond the text that still draws it in full (VP cuts the text with "..." below).
   */
  public static final int MIN_PAD = 4;

  private static FontMetrics plain;
  private static FontMetrics bold;

  private TextMetrics() {}

  private static synchronized FontMetrics metrics(boolean isBold) {
    if (plain == null) {
      Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
      plain = g.getFontMetrics(new Font("Dialog", Font.PLAIN, 12));
      bold = g.getFontMetrics(new Font("Dialog", Font.BOLD, 12));
    }
    return isBold ? bold : plain;
  }

  /**
   * Width of the widest line of a text.
   *
   * @param text the text, lines separated by a newline
   * @param isBold whether it is drawn bold
   * @return width in pixels
   */
  public static int width(String text, boolean isBold) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    int widest = 0;
    for (String line : text.split("\n", -1)) {
      widest = Math.max(widest, metrics(isBold).stringWidth(line));
    }
    return widest;
  }

  /**
   * Size of a caption box around a text: the text plus the padding VP leaves.
   *
   * @param text the text
   * @return {width, height}
   */
  public static int[] caption(String text) {
    int lines = text == null ? 0 : text.split("\n", -1).length;
    return new int[] {width(text, false) + PAD, Math.max(lines, 1) * LINE_HEIGHT};
  }

  /**
   * The lines VP draws a text in when its box leaves {@code available} px for the text: words fill
   * a line while they fit, a word wider than the line stands alone.
   *
   * @param text the text (a newline is a hard break)
   * @param available width of the text area of the box
   * @return the lines
   */
  public static List<String> wrap(String text, int available) {
    List<String> lines = new ArrayList<>();
    if (text == null) {
      return lines;
    }
    for (String paragraph : text.split("\n", -1)) {
      StringBuilder line = new StringBuilder();
      for (String word : paragraph.split(" ", -1)) {
        String joined = line.length() == 0 ? word : line + " " + word;
        if (line.length() > 0 && width(joined, false) > available) {
          lines.add(line.toString());
          line = new StringBuilder(word);
        } else {
          line = new StringBuilder(joined);
        }
      }
      lines.add(line.toString());
    }
    return lines;
  }

  /**
   * Whether a box of {@code w x h} shows a text in full: it fits on the lines the box has, each of
   * them within the width. Only for a text VP draws on its own (the lines are as {@link #wrap}
   * makes them).
   *
   * @param text the text
   * @param w width of the box
   * @param h height of the box
   * @return true when nothing of the text would be cut
   */
  public static boolean fits(String text, int w, int h) {
    if (text == null || text.isEmpty()) {
      return true;
    }
    List<String> lines = wrap(text, w - MIN_PAD);
    if (lines.size() * LINE_HEIGHT > h + 2) {
      return false;
    }
    for (String line : lines) {
      if (width(line, false) > w - MIN_PAD) {
        return false;
      }
    }
    return true;
  }

  /**
   * The box of two lines a one-line label may wrap into: as wide as the longer of the two halves of
   * the text (split at the space that makes them most equal) plus the padding of a caption, twice
   * the height. VP wraps greedily at the box width, so it makes no more than two lines out of it.
   *
   * @param text the label's text
   * @param fullWidth width of the box of the text on one line
   * @return {width, height}, or null when the text has no space or the two lines would be no
   *     narrower than the one by the padding of a caption
   */
  public static int[] twoLines(String text, int fullWidth) {
    if (text == null) {
      return null;
    }
    String t = text.trim();
    if (t.contains("\n") || !t.contains(" ")) {
      return null;
    }
    int best = Integer.MAX_VALUE;
    for (int i = t.indexOf(' '); i >= 0; i = t.indexOf(' ', i + 1)) {
      best =
          Math.min(
              best, Math.max(width(t.substring(0, i), false), width(t.substring(i + 1), false)));
    }
    int w = best + PAD;
    return w + PAD - MIN_PAD > fullWidth ? null : new int[] {w, 2 * LINE_HEIGHT};
  }
}
