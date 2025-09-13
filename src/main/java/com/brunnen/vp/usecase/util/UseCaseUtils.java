package com.brunnen.vp.usecase.util;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IUseCase;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Utility class for use case operations. */
public final class UseCaseUtils {

  private static final int MIN_USECASE_NAME_LENGTH = 3;
  private static final int MIN_ACTOR_NAME_LENGTH = 2;

  /** Private constructor to prevent instantiation. */
  private UseCaseUtils() {
    // Utility class
  }

  /**
   * Get all use case diagrams in the current project.
   *
   * @return list of use case diagrams
   */
  public static List<IUseCaseDiagramUIModel> getAllUseCaseDiagrams() {
    List<IUseCaseDiagramUIModel> diagrams = new ArrayList<>();
    IProject project = ApplicationManager.instance().getProjectManager().getProject();

    if (project != null) {
      Iterator<?> rawIter = project.allLevelModelElementIterator();
      while (rawIter.hasNext()) {
        Object obj = rawIter.next();
        if (obj instanceof IUseCaseDiagramUIModel) {
          diagrams.add((IUseCaseDiagramUIModel) obj);
        }
      }
    }
    return diagrams;
  }

  /**
   * Get all use cases in the current project.
   *
   * @return list of use cases
   */
  public static List<IUseCase> getAllUseCases() {
    List<IUseCase> useCases = new ArrayList<>();
    IProject project = ApplicationManager.instance().getProjectManager().getProject();

    if (project != null) {
      Iterator<?> rawIter = project.allLevelModelElementIterator();
      while (rawIter.hasNext()) {
        Object obj = rawIter.next();
        if (obj instanceof IUseCase) {
          useCases.add((IUseCase) obj);
        }
      }
    }
    return useCases;
  }

  /**
   * Get all actors in the current project.
   *
   * @return list of actors
   */
  public static List<IActor> getAllActors() {
    List<IActor> actors = new ArrayList<>();
    IProject project = ApplicationManager.instance().getProjectManager().getProject();

    if (project != null) {
      Iterator<?> rawIter = project.allLevelModelElementIterator();
      while (rawIter.hasNext()) {
        Object obj = rawIter.next();
        if (obj instanceof IActor) {
          actors.add((IActor) obj);
        }
      }
    }
    return actors;
  }

  /**
   * Validate use case name.
   *
   * @param name the name to validate
   * @return true if name is valid
   */
  public static boolean isValidUseCaseName(final String name) {
    return name != null && !name.trim().isEmpty() && name.trim().length() > MIN_USECASE_NAME_LENGTH;
  }

  /**
   * Validate actor name.
   *
   * @param name the name to validate
   * @return true if name is valid
   */
  public static boolean isValidActorName(final String name) {
    return name != null && !name.trim().isEmpty() && name.trim().length() > MIN_ACTOR_NAME_LENGTH;
  }
}
