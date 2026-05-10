package com.brunnen.vp.mcp.action;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import javax.swing.JOptionPane;

/** Action controller for adding use cases to diagrams. */
public class AddUseCaseActionController implements VPActionController {

  @Override
  public void performAction(VPAction action) {
    try {
      DiagramManager diagramManager = ApplicationManager.instance().getDiagramManager();

      if (diagramManager.getActiveDiagram() == null) {
        JOptionPane.showMessageDialog(null, "Please open a Use Case Diagram first.");
        return;
      }

      if (!(diagramManager.getActiveDiagram() instanceof IUseCaseDiagramUIModel)) {
        JOptionPane.showMessageDialog(
            null, "This action can only be performed on Use Case Diagrams.");
        return;
      }

      IUseCaseDiagramUIModel diagram = (IUseCaseDiagramUIModel) diagramManager.getActiveDiagram();

      String useCaseName =
          JOptionPane.showInputDialog(
              null, "Enter use case name:", "Add Use Case", JOptionPane.PLAIN_MESSAGE);

      if (useCaseName != null && !useCaseName.trim().isEmpty()) {
        IUseCase useCase = IModelElementFactory.instance().createUseCase();
        useCase.setName(useCaseName.trim());

        IDiagramElement diagramElement = diagramManager.createDiagramElement(diagram, useCase);
        diagramElement.setBounds(100, 100, 120, 60);

        JOptionPane.showMessageDialog(
            null,
            "Use Case '" + useCaseName + "' added successfully!",
            "Success",
            JOptionPane.INFORMATION_MESSAGE);
      }

    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          null, "Error adding use case: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
    }
  }

  @Override
  public void update(VPAction action) {
    DiagramManager diagramManager = ApplicationManager.instance().getDiagramManager();
    action.setEnabled(diagramManager.getActiveDiagram() instanceof IUseCaseDiagramUIModel);
  }
}
