package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IUseCase;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class for use case operations. */
public final class UseCaseUtils {

  private static final int MIN_USECASE_NAME_LENGTH = 3;
  private static final int MIN_ACTOR_NAME_LENGTH = 2;

  private UseCaseUtils() {}

  /**
   * Get all use case diagrams in the current project.
   *
   * @return list of use case diagrams
   */
  public static List<IUseCaseDiagramUIModel> getAllUseCaseDiagrams() {
    List<IDiagramUIModel> diagrams = DiagramUtils.findAllDiagrams(IUseCaseDiagramUIModel.class);
    return diagrams.stream()
        .map(d -> (IUseCaseDiagramUIModel) d)
        .collect(Collectors.toList());
  }

  /**
   * Get all use cases in the current project.
   *
   * @return list of use cases
   */
  public static List<IUseCase> getAllUseCases() {
    return DiagramUtils.findAllModelElements(IUseCase.class);
  }

  /**
   * Get all actors in the current project.
   *
   * @return list of actors
   */
  public static List<IActor> getAllActors() {
    return DiagramUtils.findAllModelElements(IActor.class);
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
