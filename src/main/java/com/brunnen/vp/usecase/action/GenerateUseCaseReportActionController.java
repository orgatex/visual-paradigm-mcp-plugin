package com.brunnen.vp.usecase.action;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.IActor;

import javax.swing.JOptionPane;
import java.util.Iterator;

/**
 * Action controller for generating use case reports
 */
public class GenerateUseCaseReportActionController implements VPActionController {

    @Override
    public void performAction(VPAction action) {
        try {
            IProject project = ApplicationManager.instance().getProjectManager().getProject();

            if (project == null) {
                JOptionPane.showMessageDialog(null, "No project is open. Please open a project first.");
                return;
            }

            StringBuilder report = new StringBuilder();
            report.append("USE CASE ANALYSIS REPORT\n");
            report.append("========================\n\n");

            // Count elements
            int useCaseCount = 0;
            int actorCount = 0;
            int diagramCount = 0;

            // Iterate through all model elements
            Iterator<IModelElement> iter = project.allLevelModelElementIterator();
            while (iter.hasNext()) {
                IModelElement element = iter.next();

                if (element instanceof IUseCaseDiagramUIModel) {
                    diagramCount++;
                    report.append("Diagram: ").append(element.getName()).append("\n");
                } else if (element instanceof IUseCase) {
                    useCaseCount++;
                    report.append("  Use Case: ").append(element.getName()).append("\n");
                } else if (element instanceof IActor) {
                    actorCount++;
                    report.append("  Actor: ").append(element.getName()).append("\n");
                }
            }

            // Add summary
            report.append("\nSUMMARY\n");
            report.append("-------\n");
            report.append("Total Diagrams: ").append(diagramCount).append("\n");
            report.append("Total Use Cases: ").append(useCaseCount).append("\n");
            report.append("Total Actors: ").append(actorCount).append("\n");

            // Show report
            JOptionPane.showMessageDialog(
                null,
                report.toString(),
                "Use Case Report",
                JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                null,
                "Error generating report: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            e.printStackTrace();
        }
    }

    @Override
    public void update(VPAction action) {
        // Enable action when a project is open
        action.setEnabled(ApplicationManager.instance().getProjectManager().getProject() != null);
    }
}
