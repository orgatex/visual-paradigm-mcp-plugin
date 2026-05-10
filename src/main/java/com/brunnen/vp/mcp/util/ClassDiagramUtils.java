package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IClassDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class for class diagram operations. */
public final class ClassDiagramUtils {

  private ClassDiagramUtils() {}

  /**
   * Get all class diagrams in the current project.
   *
   * @return list of class diagrams
   */
  public static List<IClassDiagramUIModel> getAllClassDiagrams() {
    return DiagramUtils.findAllDiagrams(IClassDiagramUIModel.class).stream()
        .map(d -> (IClassDiagramUIModel) d)
        .collect(Collectors.toList());
  }

  /**
   * Find a class by name.
   *
   * @param name the class name
   * @return the class, or null if not found
   */
  public static IClass findClassByName(String name) {
    return DiagramUtils.findModelElementByName(name, IClass.class);
  }

  /**
   * Get all classes in the current project.
   *
   * @return list of classes
   */
  public static List<IClass> getAllClasses() {
    return DiagramUtils.findAllModelElements(IClass.class);
  }

  /**
   * Get all classes in a specific class diagram.
   *
   * @param diagram the class diagram
   * @return list of classes in the diagram
   */
  public static List<IClass> getClassesInDiagram(IClassDiagramUIModel diagram) {
    List<IClass> classes = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IClass) {
          classes.add((IClass) model);
        }
      }
    }
    return classes;
  }

  /**
   * Get all associations in a class diagram.
   *
   * @param diagram the class diagram
   * @return list of associations
   */
  public static List<IAssociation> getAssociationsInDiagram(IClassDiagramUIModel diagram) {
    List<IAssociation> associations = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IAssociation) {
          associations.add((IAssociation) model);
        }
      }
    }
    return associations;
  }

  /**
   * Validate a class name.
   *
   * @param name the name to validate
   * @return true if valid
   */
  public static boolean isValidClassName(String name) {
    return name != null && !name.trim().isEmpty() && name.trim().length() >= 2;
  }
}
