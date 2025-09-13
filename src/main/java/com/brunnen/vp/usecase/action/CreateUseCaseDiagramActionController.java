package com.brunnen.vp.usecase.action;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IProject;
import javax.swing.JOptionPane;

/** Action controller for creating use case diagrams. */
public class CreateUseCaseDiagramActionController implements VPActionController {

  @Override
  public void performAction(VPAction action) {
    try {
      DiagramManager diagramManager = ApplicationManager.instance().getDiagramManager();
      IProject project = ApplicationManager.instance().getProjectManager().getProject();

      if (project == null) {
        JOptionPane.showMessageDialog(null, "No project is open. Please open a project first.");
        return;
      }

      // Get diagram name from user
      String diagramName =
          JOptionPane.showInputDialog(
              null, "Enter diagram name:", "Create Use Case Diagram", JOptionPane.PLAIN_MESSAGE);

      if (diagramName != null && !diagramName.trim().isEmpty()) {
        // Create a new use case diagram
        IDiagramUIModel diagram =
            diagramManager.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM);
        diagram.setName(diagramName.trim());

        // Open the diagram
        diagramManager.openDiagram(diagram);

        JOptionPane.showMessageDialog(
            null,
            "Use Case Diagram '" + diagramName + "' created successfully!",
            "Success",
            JOptionPane.INFORMATION_MESSAGE);
      }

    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          null,
          "Error creating use case diagram: " + e.getMessage(),
          "Error",
          JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
    }
  }

  @Override
  public void update(VPAction action) {
    // Enable action when a project is open
    action.setEnabled(ApplicationManager.instance().getProjectManager().getProject() != null);
  }
}
