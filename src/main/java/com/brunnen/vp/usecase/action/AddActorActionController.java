package com.brunnen.vp.usecase.action;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.factory.IModelElementFactory;

import javax.swing.JOptionPane;

/**
 * Action controller for adding actors to use case diagrams
 */
public class AddActorActionController implements VPActionController {

    @Override
    public void performAction(VPAction action) {
        try {
            DiagramManager diagramManager = ApplicationManager.instance().getDiagramManager();

            // Check if current diagram is a use case diagram
            if (diagramManager.getActiveDiagram() == null) {
                JOptionPane.showMessageDialog(null, "Please open a Use Case Diagram first.");
                return;
            }

            if (!(diagramManager.getActiveDiagram() instanceof IUseCaseDiagramUIModel)) {
                JOptionPane.showMessageDialog(null, "This action can only be performed on Use Case Diagrams.");
                return;
            }

            IUseCaseDiagramUIModel diagram = (IUseCaseDiagramUIModel) diagramManager.getActiveDiagram();

            // Get actor name from user
            String actorName = JOptionPane.showInputDialog(
                null,
                "Enter actor name:",
                "Add Actor",
                JOptionPane.PLAIN_MESSAGE
            );

            if (actorName != null && !actorName.trim().isEmpty()) {
                // Create actor model element
                IActor actor = IModelElementFactory.instance().createActor();
                actor.setName(actorName.trim());

                // Add to diagram
                IDiagramElement diagramElement = diagramManager.createDiagramElement(diagram, actor);
                diagramElement.setBounds(50, 50, 80, 100);

                JOptionPane.showMessageDialog(
                    null,
                    "Actor '" + actorName + "' added successfully!",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                null,
                "Error adding actor: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            e.printStackTrace();
        }
    }

    @Override
    public void update(VPAction action) {
        DiagramManager diagramManager = ApplicationManager.instance().getDiagramManager();
        action.setEnabled(diagramManager.getActiveDiagram() instanceof IUseCaseDiagramUIModel);
}
