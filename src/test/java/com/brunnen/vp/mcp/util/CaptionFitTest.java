package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IStereotype;
import org.junit.Test;

/**
 * The check that says a caption is cut by Visual Paradigm ("&lt;&lt;use..."), without a picture.
 */
public class CaptionFitTest {

  private static int ids;

  private static IConnectorUIModel connector(IModelElement model, int w, int h, boolean visible) {
    IConnectorUIModel c = mock(IConnectorUIModel.class);
    when(c.getId()).thenReturn("caption-fit-" + ids++);
    when(c.getModelElement()).thenReturn(model);
    ICaptionUIModel caption = mock(ICaptionUIModel.class);
    when(caption.getWidth()).thenReturn(w);
    when(caption.getHeight()).thenReturn(h);
    when(caption.isVisible()).thenReturn(visible);
    when(c.getCaptionUIModel()).thenReturn(caption);
    return c;
  }

  private static IDependency useDependency() {
    IDependency dependency = mock(IDependency.class);
    IStereotype use = mock(IStereotype.class);
    when(use.getName()).thenReturn("use");
    when(dependency.toStereotypeModelArray()).thenReturn(new IStereotype[] {use});
    return dependency;
  }

  @Test
  public void aBoxOfTheSizeVpGaveTheTextFits() {
    // a keyword 60 px wide: VP made the box 74x14, and draws it in 64x12 and up
    assertTrue(CaptionFit.fits(74, 14, 64, 12, "<<use>>"));
    assertTrue(CaptionFit.fits(64, 12, 64, 12, "<<use>>"));
  }

  @Test
  public void aBoxSmallerThanTheTextNeedsIsCut() {
    assertFalse("bare text width (the defect: 60x14)", CaptionFit.fits(60, 14, 64, 12, "<<use>>"));
    assertFalse("squeezed twice (46x14)", CaptionFit.fits(46, 14, 64, 12, "<<use>>"));
    assertFalse("too low", CaptionFit.fits(74, 10, 64, 12, "<<use>>"));
  }

  @Test
  public void aWrappedBoxFitsWhenItsLinesDo() {
    String text = "Thanh toan hoa don bang the";
    int[] box = TextMetrics.twoLines(text, TextMetrics.caption(text)[0]);
    int oneLine = TextMetrics.width(text, false) + TextMetrics.MIN_PAD;
    assertTrue(CaptionFit.fits(box[0], box[1], oneLine, 12, text));
    assertFalse(
        "narrow and one line high",
        CaptionFit.fits(box[0], TextMetrics.LINE_HEIGHT, oneLine, 12, text));
    assertFalse(
        "two lines high but too narrow for them",
        CaptionFit.fits(TextMetrics.width("Thanh toan", false), box[1], oneLine, 12, text));
  }

  @Test
  public void nothingIsKnownAboutTheTextNothingIsCut() {
    assertTrue(CaptionFit.fits(5, 5, 0, 0, ""));
  }

  @Test
  public void aCaptionNarrowerThanVpSizedItIsTruncated() {
    IConnectorUIModel c = connector(useDependency(), 74, 14, true);
    CaptionFit.remember(c.getId(), new int[] {74, 14}); // what the layout read from VP
    CaptionFit.Result ok = CaptionFit.check(c);
    assertNotNull(ok);
    assertEquals("<<use>>", ok.text);
    assertFalse(ok.truncated);
    assertEquals(64, ok.needed[0]);

    for (int w : new int[] {60, 46}) {
      IConnectorUIModel squeezed = connector(useDependency(), w, 14, true);
      CaptionFit.remember(squeezed.getId(), new int[] {74, 14});
      assertTrue("width " + w, CaptionFit.check(squeezed).truncated);
    }
  }

  @Test
  public void aCaptionNoLayoutMeasuredIsComparedWithItsText() {
    int text = TextMetrics.width("<<use>>", false);
    assertFalse(CaptionFit.check(connector(useDependency(), text + 14, 14, true)).truncated);
    assertFalse(
        CaptionFit.check(connector(useDependency(), text + TextMetrics.MIN_PAD, 14, true))
            .truncated);
    assertTrue(
        "the bare text", CaptionFit.check(connector(useDependency(), text, 14, true)).truncated);
  }

  @Test
  public void aCaptionThatShowsNothingIsNotChecked() {
    assertNull(CaptionFit.check(connector(useDependency(), 0, 0, true)));
    assertNull(CaptionFit.check(connector(useDependency(), 74, 14, false)));
    IConnectorUIModel noCaption = mock(IConnectorUIModel.class);
    assertNull(CaptionFit.check(noCaption));
  }

  @Test
  public void theTextIsWhatVpDrawsOnTheConnector() {
    assertEquals("<<use>>", CaptionFit.textOf(connector(useDependency(), 1, 1, true)));
    assertEquals("<<include>>", CaptionFit.textOf(connector(mock(IInclude.class), 1, 1, true)));
    IControlFlow flow = mock(IControlFlow.class);
    when(flow.getGuard()).thenReturn(" còn phòng ");
    assertEquals("[còn phòng]", CaptionFit.textOf(connector(flow, 1, 1, true)));
    IDependency named = mock(IDependency.class);
    when(named.getName()).thenReturn("gọi");
    assertEquals("gọi", CaptionFit.textOf(connector(named, 1, 1, true)));
    assertEquals("", CaptionFit.textOf(connector(null, 1, 1, true)));
  }
}
