package com.brunnen.vp.usecase.util;

import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.ApplicationManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class for use case operations
 */
public class UseCaseUtils {

    /**
     * Get all use case diagrams in the current project
     */
    public static List<IUseCaseDiagramUIModel> getAllUseCaseDiagrams() {
        List<IUseCaseDiagramUIModel> diagrams = new ArrayList<>();
        IProject project = ApplicationManager.instance().getProjectManager().getProject();

        if (project != null) {
            Iterator<IModelElement> iter = project.allLevelModelElementIterator();
            while (iter.hasNext()) {
                IModelElement element = iter.next();
                if (element instanceof IUseCaseDiagramUIModel) {
                    diagrams.add((IUseCaseDiagramUIModel) element);
                }
            }
        }
        return diagrams;
    }

    /**
     * Get all use cases in the current project
     */
    public static List<IUseCase> getAllUseCases() {
        List<IUseCase> useCases = new ArrayList<>();
        IProject project = ApplicationManager.instance().getProjectManager().getProject();

        if (project != null) {
            Iterator<IModelElement> iter = project.allLevelModelElementIterator();
            while (iter.hasNext()) {
                IModelElement element = iter.next();
                if (element instanceof IUseCase) {
                    useCases.add((IUseCase) element);
                }
            }
        }
        return useCases;
    }

    /**
     * Get all actors in the current project
     */
    public static List<IActor> getAllActors() {
        List<IActor> actors = new ArrayList<>();
        IProject project = ApplicationManager.instance().getProjectManager().getProject();

        if (project != null) {
            Iterator<IModelElement> iter = project.allLevelModelElementIterator();
            while (iter.hasNext()) {
                IModelElement element = iter.next();
                if (element instanceof IActor) {
                    actors.add((IActor) element);
                }
            }
        }
        return actors;
    }

    /**
     * Validate use case name
     */
    public static boolean isValidUseCaseName(String name) {
        return name != null && !name.trim().isEmpty() && name.trim().length() > 2;
    }

    /**
     * Validate actor name
     */
    public static boolean isValidActorName(String name) {
        return name != null && !name.trim().isEmpty() && name.trim().length() > 1;
    }
}
