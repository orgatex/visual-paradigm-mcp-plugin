package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IInteractionDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IActivation;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utility class for sequence diagram operations. */
public final class SequenceDiagramUtils {

  private SequenceDiagramUtils() {}

  /**
   * Get all sequence diagrams in the current project.
   *
   * @return list of sequence diagrams
   */
  public static List<IInteractionDiagramUIModel> getAllSequenceDiagrams() {
    return DiagramUtils.findAllDiagrams(IInteractionDiagramUIModel.class).stream()
        .map(d -> (IInteractionDiagramUIModel) d)
        .collect(Collectors.toList());
  }

  /**
   * Find a lifeline by name in a sequence diagram.
   *
   * @param diagram the sequence diagram
   * @param name the lifeline name
   * @return the lifeline, or null if not found
   */
  public static IInteractionLifeLine findLifelineByName(
      IInteractionDiagramUIModel diagram, String name) {
    if (diagram == null || name == null) {
      return null;
    }
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (!(obj instanceof IDiagramElement)) {
        continue;
      }
      IDiagramElement de = (IDiagramElement) obj;
      IModelElement model = de.getModelElement();
      if (!(model instanceof IInteractionLifeLine)) {
        continue;
      }
      IInteractionLifeLine lifeline = (IInteractionLifeLine) model;
      // 1. Model name. When an alias/nickname is set VP returns the nickname here, so this also
      //    matches references by alias.
      if (name.equals(lifeline.getName())) {
        return lifeline;
      }
      // 2. Shape caption — addToDiagram sets it to the original lifelineName, which stays stable
      //    even after a nickname overrides getName(). This is the reliable identifier.
      if (de instanceof IShapeUIModel && name.equals(((IShapeUIModel) de).getCustomText())) {
        return lifeline;
      }
      // 3. Base classifier name (the className argument).
      IModelElement classifier = lifeline.getBaseClassifierAsModel();
      if (classifier != null && name.equals(classifier.getName())) {
        return lifeline;
      }
    }
    return null;
  }

  /**
   * Get all lifelines in a sequence diagram.
   *
   * @param diagram the sequence diagram
   * @return list of lifelines
   */
  public static List<IInteractionLifeLine> getAllLifelines(IInteractionDiagramUIModel diagram) {
    List<IInteractionLifeLine> lifelines = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IInteractionLifeLine) {
          lifelines.add((IInteractionLifeLine) model);
        }
      }
    }
    return lifelines;
  }

  /**
   * Get all messages in a sequence diagram.
   *
   * @param diagram the sequence diagram
   * @return list of messages
   */
  public static List<IMessage> getAllMessages(IInteractionDiagramUIModel diagram) {
    List<IMessage> messages = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IMessage) {
          messages.add((IMessage) model);
        }
      }
    }
    return messages;
  }

  /**
   * Build a reverse map from activation to lifeline name.
   *
   * @param diagram the sequence diagram
   * @return map from IActivation to lifeline name
   */
  public static Map<IActivation, String> buildActivationToLifelineMap(
      IInteractionDiagramUIModel diagram) {
    Map<IActivation, String> map = new HashMap<>();
    List<IInteractionLifeLine> lifelines = getAllLifelines(diagram);
    for (IInteractionLifeLine ll : lifelines) {
      Iterator<?> iter = ll.activationIterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        if (obj instanceof IActivation) {
          map.put((IActivation) obj, ll.getName());
        }
      }
    }
    return map;
  }

  /**
   * Validate a lifeline name.
   *
   * @param name the name to validate
   * @return true if valid
   */
  public static boolean isValidLifelineName(String name) {
    return name != null && !name.trim().isEmpty() && name.trim().length() >= 2;
  }

  /**
   * First message number of each operand of a combined fragment that covers messages {@code from}
   * to {@code to}. Explicit starts are used when given (one per operand after the first), otherwise
   * the messages are shared out evenly.
   *
   * @param from first covered top-level message number
   * @param to last covered top-level message number
   * @param operands number of operands
   * @param explicitStarts first message of operands 2..n, or null
   * @return first message number of every operand (length {@code operands})
   */
  public static int[] operandStarts(int from, int to, int operands, int[] explicitStarts) {
    int[] starts = new int[operands];
    starts[0] = from;
    int count = Math.max(1, to - from + 1);
    for (int k = 1; k < operands; k++) {
      if (explicitStarts != null && explicitStarts.length >= operands - 1) {
        starts[k] = explicitStarts[k - 1];
      } else {
        starts[k] = from + (int) Math.round((double) k * count / operands);
      }
      starts[k] = Math.max(starts[k], starts[k - 1] + 1);
    }
    return starts;
  }

  /**
   * Compare two UML sequence expressions in reading order: number by number ("1" &lt; "1.1" &lt;
   * "1.1.1" &lt; "1.2" &lt; "2" &lt; "10"), a suffix after the digits ("2a", "1.1*") ordered as
   * text. A blank expression sorts after every numbered one.
   *
   * @param a first expression
   * @param b second expression
   * @return negative, zero or positive like {@link java.util.Comparator#compare}
   */
  public static int compareSequenceNumbers(String a, String b) {
    boolean blankA = a == null || a.trim().isEmpty();
    boolean blankB = b == null || b.trim().isEmpty();
    if (blankA || blankB) {
      return blankA == blankB ? 0 : blankA ? 1 : -1;
    }
    String[] pa = a.trim().split("\\.");
    String[] pb = b.trim().split("\\.");
    for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
      int na = leadingNumber(pa[i]);
      int nb = leadingNumber(pb[i]);
      if (na != nb) {
        return Integer.compare(na, nb);
      }
      int text = suffix(pa[i]).compareTo(suffix(pb[i]));
      if (text != 0) {
        return text;
      }
    }
    return Integer.compare(pa.length, pb.length);
  }

  /**
   * The top-level number of a sequence expression ("4" for "4.2.1"), or 0 when it has none.
   *
   * @param seq the sequence expression
   * @return its first number
   */
  public static int topLevelNumber(String seq) {
    if (seq == null || seq.trim().isEmpty()) {
      return 0;
    }
    return Math.max(0, leadingNumber(seq.trim().split("\\.")[0]));
  }

  /**
   * The number for a message given none: one past the highest top-level number so far.
   *
   * @param seqs the sequence expressions already on the diagram (blank entries ignored)
   * @return the next top-level number as text
   */
  public static String nextTopLevelNumber(String[] seqs) {
    int max = 0;
    for (String seq : seqs) {
      max = Math.max(max, topLevelNumber(seq));
    }
    return String.valueOf(max + 1);
  }

  /**
   * Row of a new message among the messages already drawn, laid out in sequence-number order: one
   * step below the lowest message numbered before (or equal to) it, at {@code top} when there is
   * none. When a message numbered after it is drawn less than a step below that row, the messages
   * from the row on have to move down.
   *
   * @param seqs sequence expressions of the drawn messages
   * @param bottoms lowest y of each drawn message (its arrow, or the bottom of a self loop)
   * @param seq the new message's sequence expression
   * @param top y of the first message row
   * @param step distance between two message rows
   * @return {y of the new message, how far the messages at or below that y must move down (0 for
   *     none)}
   */
  public static int[] messageSlot(String[] seqs, int[] bottoms, String seq, int top, int step) {
    int before = -1;
    int after = Integer.MAX_VALUE;
    for (int i = 0; i < seqs.length; i++) {
      if (compareSequenceNumbers(seqs[i], seq) <= 0) {
        before = Math.max(before, bottoms[i]);
      } else {
        after = Math.min(after, bottoms[i]);
      }
    }
    int y = before < 0 ? top : before + step;
    if (after == Integer.MAX_VALUE) {
      return new int[] {y, 0};
    }
    int shift = y + step - after;
    return new int[] {y, Math.max(0, shift)};
  }

  private static int leadingNumber(String part) {
    int end = 0;
    while (end < part.length() && Character.isDigit(part.charAt(end))) {
      end++;
    }
    if (end == 0) {
      return -1;
    }
    try {
      return Integer.parseInt(part.substring(0, end));
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  private static String suffix(String part) {
    int end = 0;
    while (end < part.length() && Character.isDigit(part.charAt(end))) {
      end++;
    }
    return part.substring(end);
  }
}
