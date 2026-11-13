package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IFlowchartFlowline;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IStereotype;
import com.vp.plugin.model.ITransition2;
import java.util.List;
import org.junit.Test;

/**
 * The rule every connector label of every layout profile follows: a caption is never narrower or
 * lower than what Visual Paradigm needs to draw its text. A label that finds no clear place moves
 * (further from its line); it does not shrink ("&lt;&lt;use..." came from a label that was allowed
 * to shrink by the padding of its caption). The only other box a label may take is the box of two
 * lines its text wraps into, as wide as the longer line needs.
 */
public class LabelSizeRuleTest {

  private static int ids;

  private static IShapeUIModel shape() {
    IShapeUIModel s = mock(IShapeUIModel.class);
    when(s.getId()).thenReturn("s" + ids++);
    return s;
  }

  private static IConnectorUIModel connector(IModelElement model, int captionW, int captionH) {
    IConnectorUIModel c = mock(IConnectorUIModel.class);
    when(c.getId()).thenReturn("c" + ids++);
    when(c.getModelElement()).thenReturn(model);
    IShapeUIModel from = shape();
    IShapeUIModel to = shape();
    when(c.getFromShape()).thenReturn(from);
    when(c.getToShape()).thenReturn(to);
    ICaptionUIModel caption = mock(ICaptionUIModel.class);
    when(caption.getWidth()).thenReturn(captionW);
    when(caption.getHeight()).thenReturn(captionH);
    when(c.getCaptionUIModel()).thenReturn(caption);
    return c;
  }

  private static IDependency dependency(String stereotype) {
    IDependency d = mock(IDependency.class);
    when(d.getName()).thenReturn("");
    IStereotype st = mock(IStereotype.class);
    when(st.getName()).thenReturn(stereotype);
    when(d.toStereotypeModelArray()).thenReturn(new IStereotype[] {st});
    return d;
  }

  @Test
  public void aKeywordLabelKeepsTheWholeSizeOfItsCaptionAndHasNoSmallerBox() {
    // <<use>> is 60 px of text, VP's caption for it 74x14
    LayoutProfile.EdgeSpec spec =
        new ComponentLayoutProfile().edge(connector(dependency("use"), 74, 14));
    assertEquals(1, spec.labels.size());
    LayoutProfile.LabelSpec label = spec.labels.get(0);
    assertEquals(74, label.w);
    assertEquals(14, label.h);
    assertEquals("no narrower box to fall back on", 0, label.altW);
    assertEquals(0, label.altH);
  }

  @Test
  public void aNameThatIsWiderThanItsCaptionGetsTheWidthOfTheName() {
    IDependency named = mock(IDependency.class);
    when(named.getName()).thenReturn("Thanh toan hoa don bang the");
    LayoutProfile.EdgeSpec spec = new ObjectLayoutProfile().edge(connector(named, 30, 14));
    assertTrue(spec.labels.get(0).w >= TextMetrics.caption("Thanh toan hoa don bang the")[0]);
  }

  @Test
  public void aLabelThatMayWrapGetsTwoLinesThatHoldTheTextAndNoOtherBox() {
    String[] texts = {"chon 1 lop hoc phan", "khach hang dong y", "het phong"};
    for (String text : texts) {
      int full = TextMetrics.caption(text)[0];
      LayoutProfile.LabelSpec label =
          new LayoutProfile.LabelSpec("name", full, 14, ElkLayout.Slot.CENTER).wrappable(text);
      assertTrue("a box of two lines for " + text, label.altW > 0);
      assertEquals(2 * TextMetrics.LINE_HEIGHT, label.altH);
      assertTrue("narrower than one line", label.altW < full);
      assertTrue("every line fits the box", TextMetrics.fits(text, label.altW, label.altH));
      List<String> lines = TextMetrics.wrap(text, label.altW - TextMetrics.MIN_PAD);
      assertEquals(2, lines.size());
      for (String line : lines) {
        assertTrue(
            "the widest line plus VP's padding fits",
            TextMetrics.width(line, false) + TextMetrics.PAD <= label.altW);
      }
    }
  }

  @Test
  public void aLabelThatCannotWrapHasNoSecondBox() {
    LayoutProfile.LabelSpec label =
        new LayoutProfile.LabelSpec("name", 74, 14, ElkLayout.Slot.CENTER).wrappable("<<use>>");
    assertEquals(0, label.altW);
    LayoutProfile.LabelSpec two =
        new LayoutProfile.LabelSpec("name", 74, 28, ElkLayout.Slot.CENTER).wrappable("a b c d e");
    assertEquals("a label of two lines already is not wrapped again", 0, two.altW);
  }

  @Test
  public void everyFamilyThatWrapsGivesTheTextTheWidthItsLongerLineNeeds() {
    // state transition, activity guard and flowchart flow: the labels that may wrap
    ITransition2 transition = mock(ITransition2.class);
    when(transition.getName()).thenReturn("khach hang xac nhan dat phong");
    LayoutProfile.LabelSpec state =
        new StateLayoutProfile().edge(connector(transition, 190, 14)).labels.get(0);
    assertWrapsWithoutCutting("khach hang xac nhan dat phong", state);

    IControlFlow flow = mock(IControlFlow.class);
    when(flow.getGuard()).thenReturn("con phong trong de dat");
    LayoutProfile.LabelSpec guard =
        new ActivityLayoutProfile(null, new java.util.HashMap<>())
            .edge(connector(flow, 150, 14))
            .labels
            .get(0);
    assertWrapsWithoutCutting("[con phong trong de dat]", guard);

    IFlowchartFlowline line = mock(IFlowchartFlowline.class);
    when(line.getName()).thenReturn("du tien thanh toan");
    LayoutProfile.LabelSpec flowchart =
        new FlowchartLayoutProfile().edge(connector(line, 130, 14)).labels.get(0);
    assertWrapsWithoutCutting("du tien thanh toan", flowchart);
  }

  private static void assertWrapsWithoutCutting(String text, LayoutProfile.LabelSpec label) {
    assertTrue(label.w >= TextMetrics.caption(text)[0] || label.w >= 100);
    assertTrue("wrappable: " + text, label.altW > 0);
    assertTrue(
        "its lines are no wider than the box: " + text,
        TextMetrics.fits(text, label.altW, label.altH));
  }
}
