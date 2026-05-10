package com.brunnen.vp.mcp.tools;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

/**
 * Base class for all MCP tool services. Provides shared auto-layout, VP API access, and EDT
 * dispatch.
 */
public abstract class AbstractDiagramMcpTools {

  private static final int ELEMENT_WIDTH = 120;
  private static final int ELEMENT_HEIGHT = 60;
  private static final int X_STEP = 180;
  private static final int Y_STEP = 100;
  private static final int MAX_COLUMNS = 4;

  private final Map<String, int[]> layoutCounters = new ConcurrentHashMap<>();

  /**
   * Run a callable on the Swing EDT and return the result.
   *
   * @param callable the callable to execute
   * @param <T> the return type
   * @return the result
   * @throws Exception if the callable throws
   */
  protected <T> T runOnEdt(Callable<T> callable) throws Exception {
    final Object[] result = new Object[1];
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            result[0] = callable.call();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  /**
   * Run a runnable on the Swing EDT.
   *
   * @param runnable the runnable to execute
   * @throws Exception if the runnable throws
   */
  protected void runOnEdt(Runnable runnable) throws Exception {
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
  }

  /**
   * Get the next auto-layout position for a diagram.
   *
   * @param diagramName the diagram name
   * @return int[] with [x, y] coordinates
   */
  protected int[] getNextPosition(String diagramName) {
    int[] pos =
        layoutCounters.computeIfAbsent(diagramName, k -> new int[] {50, 50});
    int x = pos[0];
    int y = pos[1];
    pos[0] += X_STEP;
    if (pos[0] > 50 + MAX_COLUMNS * X_STEP) {
      pos[0] = 50;
      pos[1] += Y_STEP;
    }
    return new int[] {x, y};
  }

  /**
   * Get the element width for auto-layout.
   *
   * @return width
   */
  protected int getElementWidth() {
    return ELEMENT_WIDTH;
  }

  /**
   * Get the element height for auto-layout.
   *
   * @return height
   */
  protected int getElementHeight() {
    return ELEMENT_HEIGHT;
  }

  /**
   * Add a model element to a diagram with auto-layout positioning.
   *
   * @param diagram the diagram
   * @param element the model element
   * @param diagramName the diagram name (for layout tracking)
   * @return the diagram element
   */
  protected IDiagramElement addToDiagram(
      IDiagramUIModel diagram, IModelElement element, String diagramName) {
    DiagramManager dm = ApplicationManager.instance().getDiagramManager();
    IDiagramElement diagramElement = dm.createDiagramElement(diagram, element);
    int[] pos = getNextPosition(diagramName);
    diagramElement.setBounds(pos[0], pos[1], getElementWidth(), getElementHeight());
    return diagramElement;
  }

  /**
   * Get the current project with null check.
   *
   * @return the project
   * @throws IllegalStateException if no project is open
   */
  protected IProject requireProject() {
    IProject project = ApplicationManager.instance().getProjectManager().getProject();
    if (project == null) {
      throw new IllegalStateException("No project is open");
    }
    return project;
  }

  /**
   * Get the DiagramManager.
   *
   * @return the DiagramManager
   */
  protected DiagramManager getDiagramManager() {
    return ApplicationManager.instance().getDiagramManager();
  }

  /**
   * Get the IModelElementFactory instance.
   *
   * @return the factory
   */
  protected IModelElementFactory getModelElementFactory() {
    return IModelElementFactory.instance();
  }
}
