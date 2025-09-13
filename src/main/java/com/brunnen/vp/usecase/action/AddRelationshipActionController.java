package com.brunnen.vp.usecase.action;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Action controller for adding include/extend relationships between use cases. */
public class AddRelationshipActionController implements VPActionController {

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
        JOptionPane.showMessageDialog(
            null, "This action can only be performed on Use Case Diagrams.");
        return;
      }

      IUseCaseDiagramUIModel diagram = (IUseCaseDiagramUIModel) diagramManager.getActiveDiagram();

      // Get all use cases in the diagram
      List<IUseCase> useCases = getUseCasesInDiagram(diagram);

      if (useCases.size() < 2) {
        JOptionPane.showMessageDialog(
            null, "At least two Use Cases are required to create a relationship.");
        return;
      }

      // Create UI for selecting relationship details
      JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));

      // Relationship type selection
      String[] relationshipTypes = {"Include", "Extend"};
      JComboBox<String> typeCombo = new JComboBox<>(relationshipTypes);
      panel.add(new JLabel("Relationship Type:"));
      panel.add(typeCombo);

      // Source use case selection
      JComboBox<IUseCase> sourceCombo = new JComboBox<>(useCases.toArray(new IUseCase[0]));
      sourceCombo.setRenderer(new UseCaseListCellRenderer());
      panel.add(new JLabel("From Use Case:"));
      panel.add(sourceCombo);

      // Target use case selection
      JComboBox<IUseCase> targetCombo = new JComboBox<>(useCases.toArray(new IUseCase[0]));
      targetCombo.setRenderer(new UseCaseListCellRenderer());
      panel.add(new JLabel("To Use Case:"));
      panel.add(targetCombo);

      int result =
          JOptionPane.showConfirmDialog(
              null,
              panel,
              "Add Relationship",
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE);

      if (result == JOptionPane.OK_OPTION) {
        String relationshipType = (String) typeCombo.getSelectedItem();
        IUseCase sourceUseCase = (IUseCase) sourceCombo.getSelectedItem();
        IUseCase targetUseCase = (IUseCase) targetCombo.getSelectedItem();

        if (sourceUseCase == null || targetUseCase == null) {
          JOptionPane.showMessageDialog(null, "Please select both source and target use cases.");
          return;
        }

        if (sourceUseCase.equals(targetUseCase)) {
          JOptionPane.showMessageDialog(null, "Source and target use cases cannot be the same.");
          return;
        }

        // Create the relationship
        if ("Include".equals(relationshipType)) {
          createIncludeRelationship(diagram, sourceUseCase, targetUseCase, diagramManager);
        } else {
          createExtendRelationship(diagram, sourceUseCase, targetUseCase, diagramManager);
        }

        JOptionPane.showMessageDialog(
            null,
            relationshipType + " relationship created successfully!",
            "Success",
            JOptionPane.INFORMATION_MESSAGE);
      }

    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          null,
          "Error creating relationship: " + e.getMessage(),
          "Error",
          JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
    }
  }

  private void createIncludeRelationship(
      IUseCaseDiagramUIModel diagram,
      IUseCase source,
      IUseCase target,
      DiagramManager diagramManager) {
    // Create include model element
    IInclude include = IModelElementFactory.instance().createInclude();
    include.setFrom(source);
    include.setTo(target);

    // Add to diagram
    diagramManager.createDiagramElement(diagram, include);

    // The connector will automatically connect the use cases with proper routing
  }

  private void createExtendRelationship(
      IUseCaseDiagramUIModel diagram,
      IUseCase source,
      IUseCase target,
      DiagramManager diagramManager) {
    // Create extend model element
    IExtend extend = IModelElementFactory.instance().createExtend();
    extend.setFrom(source);
    extend.setTo(target);

    // Add to diagram
    diagramManager.createDiagramElement(diagram, extend);

    // The connector will automatically connect the use cases with proper routing
  }

  private List<IUseCase> getUseCasesInDiagram(IUseCaseDiagramUIModel diagram) {
    List<IUseCase> useCases = new ArrayList<>();

    // Get all diagram elements
    Iterator<?> diagramElementIterator = diagram.diagramElementIterator();
    while (diagramElementIterator.hasNext()) {
      Object element = diagramElementIterator.next();
      if (element instanceof IDiagramElement) {
        IDiagramElement diagramElement = (IDiagramElement) element;
        IModelElement modelElement = diagramElement.getModelElement();
        if (modelElement instanceof IUseCase) {
          useCases.add((IUseCase) modelElement);
        }
      }
    }

    return useCases;
  }

  @Override
  public void update(VPAction action) {
    DiagramManager diagramManager = ApplicationManager.instance().getDiagramManager();
    action.setEnabled(diagramManager.getActiveDiagram() instanceof IUseCaseDiagramUIModel);
  }

  /** Custom cell renderer for displaying use case names in combo boxes. */
  private static class UseCaseListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value instanceof IUseCase) {
        IUseCase useCase = (IUseCase) value;
        setText(useCase.getName() != null ? useCase.getName() : "<Unnamed Use Case>");
      }

      return this;
    }
  }
}
