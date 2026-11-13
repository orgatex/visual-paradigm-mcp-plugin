package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.model.IActivityObjectFlow;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IStereotype;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a caption box needs so that Visual Paradigm draws its whole text.
 *
 * <p>VP cuts a caption whose box is narrower than its text plus a little padding with "..."
 * ("&lt;&lt;use..."), and clips one that is too low. A box VP sizes itself (after {@code
 * resetCaption()}) is the text plus {@link TextMetrics#PAD}; that is the size every label of the
 * layout keeps, however crowded the place: a label that finds no room moves, the layout makes room,
 * it never shrinks. The layout asks VP for that size ({@link #natural}) instead of measuring the
 * text: the measure of {@link TextMetrics} is a few pixels short for runs of narrow letters. The
 * least a box may have before the text is cut, calibrated on exported PNGs, is {@link
 * TextMetrics#MIN_PAD} beyond the text, i.e. {@link TextMetrics#PAD} - {@link TextMetrics#MIN_PAD}
 * less than VP's own size.
 *
 * <p>{@link #check} says whether a caption is cut without touching it (a reset would also move the
 * multiplicities of an association): it compares the box with the size the layout got from VP, or,
 * for a caption no layout measured, with the least the text needs.
 */
public final class CaptionFit {

  /** Most sizes remembered (the oldest are forgotten first). */
  private static final int MEMORY = 20000;

  private static final Map<String, int[]> SEEN =
      new LinkedHashMap<String, int[]>() {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
          return size() > MEMORY;
        }
      };

  private CaptionFit() {}

  /** What is known about a caption, and whether the box it has now shows the text. */
  public static final class Result {
    /** The text the caption shows (as far as it is known), "" when unknown. */
    public final String text;

    /** Least width and height of a box that draws the text on one line, {0, 0} when unknown. */
    public final int[] needed;

    /** Whether the caption's bounds are too small to draw the text in full. */
    public final boolean truncated;

    Result(String text, int[] needed, boolean truncated) {
      this.text = text;
      this.needed = needed;
      this.truncated = truncated;
    }
  }

  /**
   * Whether a box of {@code w x h} draws a text in full.
   *
   * @param w width of the box
   * @param h height of the box
   * @param neededW least width of a box that holds the text on one line, 0 when it is not known
   * @param neededH least height of such a box
   * @param text the caption's text, "" or null when unknown (only the one-line need counts then)
   * @return true when nothing of the text is cut
   */
  public static boolean fits(int w, int h, int neededW, int neededH, String text) {
    if (neededW <= 0 || neededH <= 0) {
      return true; // nothing is known about the text
    }
    if (w >= neededW && h >= neededH) {
      return true;
    }
    // narrower than one line of the text: it must wrap into the lines the box has
    if (h < 2 * TextMetrics.LINE_HEIGHT - 2) {
      return false;
    }
    return text == null || text.isEmpty() || TextMetrics.fits(text, w, h);
  }

  /**
   * The size VP gives the caption of an element when it resets it, and remember it for {@link
   * #check}. The caption takes VP's place for it then: a layout that is about to place the caption
   * reads the size first.
   *
   * @param element a connector or shape of an open diagram
   * @return {width, height}, {0, 0} when it has no caption or VP draws no text
   */
  public static int[] natural(IBaseDiagramElement element) {
    ICaptionUIModel caption = element.getCaptionUIModel();
    if (caption == null) {
      return new int[] {0, 0};
    }
    element.resetCaption();
    int[] size = {caption.getWidth(), caption.getHeight()};
    remember(element.getId(), size);
    return size;
  }

  /**
   * Remember the size VP gave the caption of an element (it is what the element's caption must
   * keep).
   *
   * @param id the id of the diagram element
   * @param size {width, height} of its caption right after VP sized it
   */
  public static void remember(String id, int[] size) {
    if (id == null || size == null) {
      return;
    }
    synchronized (SEEN) {
      SEEN.put(id, size.clone());
    }
  }

  private static int[] remembered(String id) {
    synchronized (SEEN) {
      return SEEN.get(id);
    }
  }

  /**
   * Check the caption of a connector, leaving it as it is.
   *
   * @param connector a connector
   * @return the check, null when the connector shows no caption
   */
  public static Result check(IConnectorUIModel connector) {
    ICaptionUIModel caption = connector.getCaptionUIModel();
    if (caption == null || !caption.isVisible() || caption.getWidth() <= 0) {
      return null;
    }
    String text = textOf(connector);
    int[] size = remembered(connector.getId());
    int[] needed;
    if (size != null && size[0] > 0 && size[1] > 0) {
      needed = new int[] {size[0] - (TextMetrics.PAD - TextMetrics.MIN_PAD), size[1] - 2};
    } else if (!text.isEmpty()) {
      needed = new int[] {TextMetrics.width(text, false) + TextMetrics.MIN_PAD, 12};
    } else {
      needed = new int[] {0, 0};
    }
    boolean fits = fits(caption.getWidth(), caption.getHeight(), needed[0], needed[1], text);
    return new Result(text, needed, !fits);
  }

  /**
   * The text VP draws on a connector, as far as the model tells: the stereotypes as
   * &lt;&lt;name&gt;&gt;, the keyword of an include or extend, the guard of a flow in brackets, the
   * name.
   *
   * @param connector a connector
   * @return the text, "" when it has none
   */
  public static String textOf(IConnectorUIModel connector) {
    IModelElement model = connector.getModelElement();
    if (model == null) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    if (model instanceof IInclude) {
      text.append("<<include>>");
    } else if (model instanceof IExtend) {
      text.append("<<extend>>");
    }
    IStereotype[] stereotypes = model.toStereotypeModelArray();
    if (stereotypes != null && stereotypes.length > 0) {
      text.append(text.length() > 0 ? " " : "").append("<<");
      for (int i = 0; i < stereotypes.length; i++) {
        text.append(i > 0 ? ", " : "").append(stereotypes[i].getName());
      }
      text.append(">>");
    }
    String guard = null;
    if (model instanceof IControlFlow) {
      guard = ((IControlFlow) model).getGuard();
    } else if (model instanceof IActivityObjectFlow) {
      guard = ((IActivityObjectFlow) model).getGuard();
    }
    if (guard != null && !guard.trim().isEmpty()) {
      text.append(text.length() > 0 ? " " : "").append('[').append(guard.trim()).append(']');
    }
    String name = model.getName();
    if (name != null && !name.trim().isEmpty()) {
      text.append(text.length() > 0 ? " " : "").append(name.trim());
    }
    return text.toString();
  }
}
