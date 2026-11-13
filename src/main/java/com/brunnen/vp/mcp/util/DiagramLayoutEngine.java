package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IUseCase;

/** Initial placement of a new shape by the zone its kind belongs to (actors left, DAOs middle). */
public final class DiagramLayoutEngine {

  /** Element zones for initial positioning. */
  public enum ElementZone {
    LEFT,
    CENTER,
    RIGHT,
    TOP,
    MIDDLE,
    BOTTOM
  }

  private static final int ZONE_SPACING_X = 250;
  private static final int ZONE_SPACING_Y = 80;
  private static final int ELEMENT_WIDTH = 160;
  private static final int ELEMENT_HEIGHT = 60;

  private DiagramLayoutEngine() {}

  /**
   * Classify an element into a zone based on diagram type and element characteristics.
   *
   * @param diagramType the diagram type string
   * @param element the model element
   * @return the zone this element belongs to
   */
  public static ElementZone classifyElement(String diagramType, IModelElement element) {
    if (element == null) {
      return ElementZone.CENTER;
    }

    if (IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM.equals(diagramType)) {
      if (element instanceof IActor) {
        return ElementZone.LEFT;
      }
      if (element instanceof IUseCase) {
        return ElementZone.CENTER;
      }
    } else if (IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM.equals(diagramType)) {
      String name = element.getName();
      if (name != null) {
        String lower = name.toLowerCase();
        if (lower.endsWith("dao") || lower.endsWith("repository") || lower.endsWith("mapper")) {
          return ElementZone.MIDDLE;
        }
        if (lower.startsWith("frm")
            || lower.startsWith("page")
            || lower.endsWith("form")
            || lower.endsWith("view")
            || lower.endsWith("screen")) {
          return ElementZone.TOP;
        }
      }
      return ElementZone.BOTTOM;
    } else if (IDiagramTypeConstants.DIAGRAM_TYPE_ER_DIAGRAM.equals(diagramType)) {
      return ElementZone.CENTER;
    }

    return ElementZone.CENTER;
  }

  /**
   * Calculate initial bounds for an element based on diagram type, zone, and index within zone.
   *
   * @param diagramType the diagram type
   * @param element the model element
   * @param indexInZone the index of this element within its zone
   * @return int[4] = {x, y, width, height}
   */
  public static int[] calculateInitialBounds(
      String diagramType, IModelElement element, int indexInZone) {
    ElementZone zone = classifyElement(diagramType, element);
    int baseX;
    int baseY;

    switch (zone) {
      case LEFT:
        baseX = 50;
        baseY = 50 + (indexInZone * ZONE_SPACING_Y);
        break;
      case RIGHT:
        baseX = 50 + (2 * ZONE_SPACING_X);
        baseY = 50 + (indexInZone * ZONE_SPACING_Y);
        break;
      case TOP:
        baseX = 50 + (indexInZone * ZONE_SPACING_X);
        baseY = 50;
        break;
      case MIDDLE:
        baseX = 50 + (indexInZone * ZONE_SPACING_X);
        baseY = 200;
        break;
      case BOTTOM:
        baseX = 50 + (indexInZone * ZONE_SPACING_X);
        baseY = 350;
        break;
      case CENTER:
      default:
        if (IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM.equals(diagramType)) {
          baseX = 50 + ZONE_SPACING_X;
          baseY = 50 + (indexInZone * ZONE_SPACING_Y);
        } else {
          baseX = 50 + (indexInZone * ZONE_SPACING_X);
          baseY = 50;
        }
        break;
    }

    return new int[] {baseX, baseY, ELEMENT_WIDTH, ELEMENT_HEIGHT};
  }
}
