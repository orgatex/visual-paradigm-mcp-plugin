package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Utility class for ERD operations. */
public final class ErdUtils {

  private ErdUtils() {}

  /**
   * Get all ER diagrams in the current project.
   *
   * @return list of ER diagrams
   */
  public static List<IDiagramUIModel> getAllERDiagrams() {
    return DiagramUtils.findAllDiagrams(IDiagramUIModel.class);
  }

  /**
   * Find a table by name.
   *
   * @param name the table name
   * @return the table, or null if not found
   */
  public static IDBTable findTableByName(String name) {
    return DiagramUtils.findModelElementByName(name, IDBTable.class);
  }

  /**
   * Get all tables in the current project.
   *
   * @return list of tables
   */
  public static List<IDBTable> getAllTables() {
    return DiagramUtils.findAllModelElements(IDBTable.class);
  }

  /**
   * Get all tables in a specific ER diagram.
   *
   * @param diagram the ER diagram
   * @return list of tables in the diagram
   */
  public static List<IDBTable> getTablesInDiagram(IDiagramUIModel diagram) {
    List<IDBTable> tables = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IDBTable) {
          tables.add((IDBTable) model);
        }
      }
    }
    return tables;
  }

  /**
   * Generate CREATE TABLE DDL for a table.
   *
   * @param table the table
   * @return the DDL string
   */
  public static String generateCreateTableSql(IDBTable table) {
    StringBuilder sql = new StringBuilder();
    sql.append("CREATE TABLE ").append(table.getName()).append(" (\n");

    List<String> primaryKeys = new ArrayList<>();
    Iterator<?> colIter = table.dBColumnIterator();
    boolean first = true;
    while (colIter.hasNext()) {
      Object obj = colIter.next();
      if (obj instanceof IDBColumn) {
        IDBColumn col = (IDBColumn) obj;
        if (!first) {
          sql.append(",\n");
        }
        sql.append("  ")
            .append(col.getName())
            .append(" ")
            .append(col.getTypeInText());
        if (col.getLength() > 0) {
          sql.append("(").append(col.getLength());
          if (col.getScale() > 0) {
            sql.append(",").append(col.getScale());
          }
          sql.append(")");
        }
        if (!col.isNullable()) {
          sql.append(" NOT NULL");
        }
        if (col.isPrimaryKey()) {
          primaryKeys.add(col.getName());
        }
        first = false;
      }
    }

    if (!primaryKeys.isEmpty()) {
      sql.append(",\n  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
    }

    sql.append("\n);");
    return sql.toString();
  }

  /**
   * Validate a table name.
   *
   * @param name the name to validate
   * @return true if valid
   */
  public static boolean isValidTableName(String name) {
    return name != null && !name.trim().isEmpty() && name.trim().length() >= 2;
  }
}
