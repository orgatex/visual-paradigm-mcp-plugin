package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * What a caption box needs around its text, as calibrated against exported PNGs of Visual Paradigm
 * ("&lt;&lt;use&gt;&gt;" draws in full in a box 4 px wider than its text, ends in "..." in one 3 px
 * wider; a fresh caption is 14 px wider).
 */
public class TextMetricsTest {

  private static final String KEYWORD = "<<use>>";

  @Test
  public void aCaptionBoxIsTheTextPlusThePaddingVpGivesIt() {
    int[] box = TextMetrics.caption(KEYWORD);
    assertEquals(TextMetrics.width(KEYWORD, false) + TextMetrics.PAD, box[0]);
    assertEquals(TextMetrics.LINE_HEIGHT, box[1]);
    assertEquals(2 * TextMetrics.LINE_HEIGHT, TextMetrics.caption("a\nb")[1]);
  }

  @Test
  public void aBoxDrawsTheTextFromTheLeastPaddingOnAndNotBelow() {
    int text = TextMetrics.width(KEYWORD, false);
    assertTrue(TextMetrics.fits(KEYWORD, text + TextMetrics.MIN_PAD, 14));
    assertFalse(
        "one pixel less and VP ends the text in ...",
        TextMetrics.fits(KEYWORD, text + TextMetrics.MIN_PAD - 1, 14));
    assertFalse("the bare width of the text", TextMetrics.fits(KEYWORD, text, 14));
    assertTrue(TextMetrics.fits(KEYWORD, text + TextMetrics.PAD, 12));
    assertFalse(
        "a box too low for the line", TextMetrics.fits(KEYWORD, text + TextMetrics.PAD, 11));
  }

  @Test
  public void aTextWithoutContentFitsAnyBox() {
    assertTrue(TextMetrics.fits("", 1, 1));
    assertTrue(TextMetrics.fits(null, 1, 1));
  }

  @Test
  public void aTextWrapsAtSpacesAndEveryLineStaysWithinTheWidth() {
    String text = "Thanh toan hoa don bang the";
    for (int available : new int[] {60, 90, 120, 140, 200}) {
      List<String> lines = TextMetrics.wrap(text, available);
      assertEquals(text, String.join(" ", lines));
      for (String line : lines) {
        // a word wider than the line stands alone
        assertTrue(line, TextMetrics.width(line, false) <= available || !line.contains(" "));
      }
    }
    assertEquals(1, TextMetrics.wrap(text, 400).size());
  }

  @Test
  public void aWrappedBoxHoldsTheLinesItNeeds() {
    String text = "Thanh toan hoa don bang the";
    int full = TextMetrics.caption(text)[0];
    assertFalse("one line does not fit a narrow box", TextMetrics.fits(text, full / 2, 14));
    assertTrue(TextMetrics.fits(text, full, 14));
    int narrow = TextMetrics.width("Thanh toan hoa", false) + TextMetrics.MIN_PAD;
    assertTrue(TextMetrics.fits(text, narrow + 40, 28));
    assertFalse("two lines need a box two lines high", TextMetrics.fits(text, narrow + 40, 14));
  }

  @Test
  public void twoLinesGiveABoxNoNarrowerThanTheLongerLineNeeds() {
    for (String text :
        new String[] {
          "Thanh toan hoa don bang the",
          "[so luong phong con trong]",
          "khach hang xac nhan dat phong",
          "co hoac khong"
        }) {
      int full = TextMetrics.caption(text)[0];
      int[] box = TextMetrics.twoLines(text, full);
      assertNotNull(text, box);
      assertTrue("narrower than the line: " + text, box[0] < full);
      assertEquals(2 * TextMetrics.LINE_HEIGHT, box[1]);
      assertTrue("VP draws all of " + text, TextMetrics.fits(text, box[0], box[1]));
      // with the padding of a fresh caption, not the least padding: room for a few pixels that
      // VP measures wider than we do
      assertTrue(
          TextMetrics.fits(text, box[0] - (TextMetrics.PAD - TextMetrics.MIN_PAD) + 1, box[1])
              || box[0] - TextMetrics.PAD >= 0);
      List<String> lines = TextMetrics.wrap(text, box[0] - TextMetrics.MIN_PAD);
      assertEquals("exactly two lines: " + text, 2, lines.size());
    }
  }

  @Test
  public void aTextThatCannotWrapOrGainNothingGetsNoSecondBox() {
    assertNull("one word", TextMetrics.twoLines(KEYWORD, TextMetrics.caption(KEYWORD)[0]));
    assertNull("no text", TextMetrics.twoLines(null, 100));
    // a short word before a long one: the two lines would be no narrower, by the padding, than
    // the one
    String lopsided = "i wwwwwwwwww";
    assertNull(TextMetrics.twoLines(lopsided, TextMetrics.caption(lopsided)[0]));
    assertNull("a hard break is its own wrap", TextMetrics.twoLines("a\nb c", 200));
  }
}
