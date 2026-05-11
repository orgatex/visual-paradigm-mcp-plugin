package com.brunnen.vp.mcp.util;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Shared utility for finding diagrams and model elements in a Visual Paradigm project. */
public final class DiagramUtils {

  private DiagramUtils() {}

  public static IProject getProject() {
    return ApplicationManager.instance().getProjectManager().getProject();
  }

  public static DiagramManager getDiagramManager() {
    return ApplicationManager.instance().getDiagramManager();
  }

  /**
   * Find a diagram by name and type.
   */
  public static IDiagramUIModel findDiagramByName(String name, Class<?> diagramType) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel) {
        IDiagramUIModel diagram = (IDiagramUIModel) obj;
        if (name.equals(diagram.getName()) && diagramType.isInstance(diagram)) {
          return diagram;
        }
      }
    }
    return null;
  }

  /**
   * Find any diagram by name (any type).
   */
  public static IDiagramUIModel findDiagramByName(String name) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel) {
        IDiagramUIModel diagram = (IDiagramUIModel) obj;
        if (name.equals(diagram.getName())) {
          return diagram;
        }
      }
    }
    return null;
  }

  /**
   * Find a model element by name and type.
   */
  public static <T extends IModelElement> T findModelElementByName(String name, Class<T> type) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (type.isInstance(obj)) {
        T element = type.cast(obj);
        if (name.equals(element.getName())) {
          return element;
        }
      }
    }
    return null;
  }

  /**
   * Find all model elements of a given type.
   */
  public static <T extends IModelElement> List<T> findAllModelElements(Class<T> type) {
    List<T> result = new ArrayList<>();
    IProject project = getProject();
    if (project == null) {
      return result;
    }
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (type.isInstance(obj)) {
        result.add(type.cast(obj));
      }
    }
    return result;
  }

  /**
   * Find all diagrams of a given type.
   */
  public static List<IDiagramUIModel> findAllDiagrams(Class<?> diagramType) {
    List<IDiagramUIModel> result = new ArrayList<>();
    IProject project = getProject();
    if (project == null) {
      return result;
    }
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel && diagramType.isInstance(obj)) {
        result.add((IDiagramUIModel) obj);
      }
    }
    return result;
  }

  public static void openDiagram(IDiagramUIModel diagram) {
    getDiagramManager().openDiagram(diagram);
  }

  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }
}
