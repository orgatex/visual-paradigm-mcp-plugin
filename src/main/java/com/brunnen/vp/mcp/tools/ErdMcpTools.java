package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.ErdUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.List;
import com.brunnen.vp.mcp.tool.Tool;

/** MCP tools for Visual Paradigm ERD operations. */
public class ErdMcpTools extends AbstractDiagramMcpTools {

  @Tool(name = "createErd", description = "Create a new Entity-Relationship diagram in Visual Paradigm")
  public String createErd(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ER_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created ER diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating ER diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addTable", description = "Add a table/entity to an ER diagram")
  public String addTable(String diagramName, String tableName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IDBTable table = getModelElementFactory().createDBTable();
            table.setName(tableName);
            addToDiagram(diagram, table);

            return "Added table '" + tableName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding table: " + e.getMessage();
    }
  }

  @Tool(name = "addColumn", description = "Add a column to a database table")
  public String addColumn(
      String tableName,
      String columnName,
      String columnType,
      int length,
      int scale,
      boolean isPrimaryKey,
      boolean isNullable) {
    try {
      return runOnEdt(
          () -> {
            IDBTable table = ErdUtils.findTableByName(tableName);
            if (table == null) {
              return "Table not found: " + tableName;
            }

            IDBColumn col = getModelElementFactory().createDBColumn();
            col.setName(columnName);
            if (columnType != null && !columnType.trim().isEmpty()) {
              col.setType(columnType.trim(), length, scale);
            }
            col.setPrimaryKey(isPrimaryKey);
            col.setNullable(isNullable);
            table.addDBColumn(col);

            return "Added column '" + columnName + "' to table '" + tableName + "'";
          });
    } catch (Exception e) {
      return "Error adding column: " + e.getMessage();
    }
  }

  @Tool(name = "addForeignKey", description = "Add a foreign key relationship between two tables")
  public String addForeignKey(
      String diagramName,
      String fromTable,
      String toTable,
      String fromColumn,
      String toColumn,
      String relationshipName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDBTable source = ErdUtils.findTableByName(fromTable);
            IDBTable target = ErdUtils.findTableByName(toTable);
            if (source == null || target == null) {
              return "Table not found: " + (source == null ? fromTable : toTable);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Table not on diagram: "
                  + (fromElement == null ? fromTable : toTable);
            }

            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            if (relationshipName != null && !relationshipName.trim().isEmpty()) {
              fk.setName(relationshipName.trim());
            }
            fk.setFromMultiplicity("1");
            fk.setToMultiplicity("*");
            getDiagramManager().createConnector(diagram, fk, fromElement, toElement, null);

            return "Added foreign key from '" + fromTable + "' to '" + toTable + "'";
          });
    } catch (Exception e) {
      return "Error adding foreign key: " + e.getMessage();
    }
  }

  @Tool(name = "addTableRelationship", description = "Add a relationship between tables (identifying or non-identifying)")
  public String addTableRelationship(
      String diagramName,
      String fromTable,
      String toTable,
      String type,
      String fromMultiplicity,
      String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDBTable source = ErdUtils.findTableByName(fromTable);
            IDBTable target = ErdUtils.findTableByName(toTable);
            if (source == null || target == null) {
              return "Table not found: " + (source == null ? fromTable : toTable);
            }
            IDiagramElement fromElement = findDiagramElementByModel(diagram, source);
            IDiagramElement toElement = findDiagramElementByModel(diagram, target);
            if (fromElement == null || toElement == null) {
              return "Table not on diagram: "
                  + (fromElement == null ? fromTable : toTable);
            }

            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fk.setFromMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              fk.setToMultiplicity(toMultiplicity.trim());
            }
            if ("identifying".equalsIgnoreCase(type)) {
              fk.setIdentifying(true);
            } else {
              fk.setIdentifying(false);
            }
            getDiagramManager().createConnector(diagram, fk, fromElement, toElement, null);

            return "Added " + type + " relationship from '" + fromTable + "' to '" + toTable + "'";
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  @Tool(name = "generateDdl", description = "Generate CREATE TABLE DDL statements for all tables in an ER diagram")
  public String generateDdl(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IDBTable> tables = ErdUtils.getTablesInDiagram(diagram);
            if (tables.isEmpty()) {
              return "No tables found in diagram: " + diagramName;
            }

            StringBuilder ddl = new StringBuilder();
            for (IDBTable table : tables) {
              ddl.append(ErdUtils.generateCreateTableSql(table)).append("\n\n");
            }
            return ddl.toString();
          });
    } catch (Exception e) {
      return "Error generating DDL: " + e.getMessage();
    }
  }

  @Tool(name = "generateErdReport", description = "Generate an ERD analysis report")
  public String generateErdReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IDBTable> tables = ErdUtils.getTablesInDiagram(diagram);
            int totalColumns = 0;
            for (IDBTable table : tables) {
              java.util.Iterator<?> colIter = table.dBColumnIterator();
              while (colIter.hasNext()) {
                colIter.next();
                totalColumns++;
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("ERD REPORT: ").append(diagramName).append("\n");
            report.append("================================\n");
            report.append("Tables: ").append(tables.size()).append("\n");
            report.append("Columns: ").append(totalColumns).append("\n");
            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

}
