package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ClassModelReader;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.ErdLayoutProfile;
import com.brunnen.vp.mcp.util.ErdUtils;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.brunnen.vp.mcp.util.OrmErdPlanner;
import com.brunnen.vp.mcp.util.SqlGenerator;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DatabaseManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IERDiagramUIModel;
import com.vp.plugin.model.DatabaseType;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBForeignKeyConstraint;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IORMAttributeDetail;
import com.vp.plugin.model.IORMClassDetail;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * MCP tools for Visual Paradigm ERD operations. The diagram is laid out by the shared layered
 * layout ({@link ErdLayoutProfile}): referenced tables left, tables with their foreign keys right,
 * every foreign key a horizontal or vertical line.
 */
public class ErdMcpTools extends AbstractDiagramMcpTools {

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return diagram instanceof IERDiagramUIModel ? new ErdLayoutProfile() : null;
  }

  @Tool(
      name = "createErd",
      description = "Create a new Entity-Relationship diagram in Visual Paradigm")
  public String createErd(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ER_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created ER diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating ER diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addTable",
      description =
          "Add a table/entity to an ER diagram. A table of that name elsewhere in the project is"
              + " the same table and is shown again (with its columns). The layout puts the tables"
              + " in columns by their foreign keys (referenced tables left) and every foreign key"
              + " is a horizontal or vertical line; names are SQL identifiers (letters, digits,"
              + " underscore: no spaces or diacritics)")
  public String addTable(String diagramName, String tableName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            String name = tableName == null ? "" : tableName.trim();
            if (name.isEmpty()) {
              return "Table name must not be empty";
            }
            if (!ErdUtils.isSqlIdentifier(name)) {
              return notAnIdentifier("Table", name);
            }
            if (findTableOnDiagram(diagram, name) != null) {
              return "Table already on diagram: " + name;
            }
            IDBTable table = findRootElement(name, IDBTable.class);
            boolean reused = table != null;
            if (!reused) {
              table = getModelElementFactory().createDBTable();
              nameInOwner(table, null, name);
            }
            IDiagramElement de = addToDiagram(diagram, table, name);
            de.resetCaption();
            fitShape(de);
            int shown = showExistingForeignKeys(diagram);
            relayout(diagram);

            return "Added table '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + (reused ? " (existing table of the project)" : "")
                + (shown > 0
                    ? "; shown " + shown + " existing foreign key(s) to the tables here"
                    : "");
          });
    } catch (Exception e) {
      return "Error adding table: " + e.getMessage();
    }
  }

  @Tool(
      name = "addColumn",
      description =
          "Add a column to a table shown on an ER diagram. columnType is a SQL type such as"
              + " varchar, nvarchar(100), int, decimal(10,2), datetime; a length/scale written in"
              + " the type wins over length/scale. Types the project's database does not know are"
              + " mapped to their standard equivalent (nvarchar -> varchar, datetime -> timestamp,"
              + " bit -> boolean, ...); a type that still cannot be set is reported as an error."
              + " varchar without a length gets 255. isUnique marks the column unique (the book's"
              + " \"U\" next to the key). Names are SQL identifiers (no spaces or diacritics)")
  public String addColumn(
      String diagramName,
      String tableName,
      String columnName,
      String columnType,
      int length,
      int scale,
      boolean isPrimaryKey,
      boolean isNullable,
      boolean isUnique) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            if (columnName == null || columnName.trim().isEmpty()) {
              return "Column name must not be empty";
            }
            if (!ErdUtils.isSqlIdentifier(columnName.trim())) {
              return notAnIdentifier("Column", columnName.trim());
            }
            if (findColumnByName(table, columnName) != null) {
              return "Column '" + columnName + "' already exists in table '" + tableName + "'";
            }

            IDBColumn col = getModelElementFactory().createDBColumn();
            try {
              col.setName(columnName.trim());
            } catch (RuntimeException e) {
              col.delete(); // no orphan column when VP refuses the name
              throw e;
            }
            String typeNote = "";
            if (columnType != null && !columnType.trim().isEmpty()) {
              String[] parsed = ErdUtils.parseColumnType(columnType, length, scale);
              String used = null;
              for (String candidate : ErdUtils.typeCandidates(parsed[0])) {
                if (col.setType(
                    candidate, Integer.parseInt(parsed[1]), Integer.parseInt(parsed[2]))) {
                  used = candidate;
                  break;
                }
              }
              if (used == null) {
                col.delete();
                return "Error adding column: Visual Paradigm does not know the type '"
                    + parsed[0]
                    + "' for the project's database (tried "
                    + String.join(", ", ErdUtils.typeCandidates(parsed[0]))
                    + ")";
              }
              if (!used.equalsIgnoreCase(parsed[0])) {
                typeNote = " (type " + parsed[0] + " stored as " + col.getTypeInText() + ")";
              }
            }
            col.setPrimaryKey(isPrimaryKey);
            col.setNullable(isNullable);
            col.setUnique(isUnique);
            table.addDBColumn(col);
            refitTable(table);

            return "Added column '"
                + columnName
                + " "
                + col.getTypeInText()
                + "' to table '"
                + tableName
                + "'"
                + typeNote;
          });
    } catch (Exception e) {
      return "Error adding column: " + e.getMessage();
    }
  }

  @Tool(
      name = "addForeignKey",
      description =
          "Add a foreign key: fromTable (the child) holds fromColumn, which references toColumn"
              + " of toTable (the parent; empty toColumn = its primary key). The relationship is"
              + " drawn with the crow's foot at the child. An existing fromColumn becomes the FK"
              + " column; a fromColumn that does not exist yet is created with that name; when it"
              + " is empty, Visual Paradigm adds a column named after the parent")
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
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable child = findTableOnDiagram(diagram, fromTable);
            IDBTable parent = findTableOnDiagram(diagram, toTable);
            if (child == null || parent == null) {
              return "Table not on diagram: " + (child == null ? fromTable : toTable);
            }
            IDiagramElement childShape = findDiagramElementByModel(diagram, child);
            IDiagramElement parentShape = findDiagramElementByModel(diagram, parent);
            IDBColumn childCol = null;
            String newColumnName = null; // a name for the column Visual Paradigm adds
            if (isSet(fromColumn)) {
              childCol = findColumnByName(child, fromColumn.trim());
              if (childCol == null) {
                if (!ErdUtils.isSqlIdentifier(fromColumn.trim())) {
                  return notAnIdentifier("Column", fromColumn.trim());
                }
                newColumnName = fromColumn.trim();
              }
            }
            IDBColumn refCol = null;
            if (isSet(toColumn)) {
              refCol = findColumnByName(parent, toColumn.trim());
              if (refCol == null) {
                return "Column '" + toColumn + "' not found in table '" + toTable + "'";
              }
            }

            if (childCol != null
                && childCol.toForeignKeyConstraintArray() != null
                && childCol.toForeignKeyConstraintArray().length > 0) {
              // the foreign key of a table shown from another diagram: its line is drawn here
              int shown = showExistingForeignKeys(diagram);
              if (shown > 0) {
                relayout(diagram);
                return "Shown the existing foreign key "
                    + fromTable
                    + "."
                    + childCol.getName()
                    + " -> "
                    + toTable
                    + " on diagram '"
                    + diagramName
                    + "'";
              }
              return "Column '"
                  + childCol.getName()
                  + "' of '"
                  + fromTable
                  + "' already is a foreign key";
            }
            if (childCol == null
                && newColumnName == null
                && findForeignKey(diagram, parent, child) != null) {
              return "A foreign key from '"
                  + fromTable
                  + "' to '"
                  + toTable
                  + "' already exists; name the column (fromColumn) for a second one";
            }
            // VP's foreign key runs from the referenced (parent) table to the child and adds an
            // FK column to the child that references the parent's primary key.
            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            if (isSet(relationshipName)) {
              fk.setName(relationshipName.trim());
            }
            fk.setFrom(parent);
            fk.setTo(child);
            fk.setFromMultiplicity(IDBForeignKey.FROM_MULTIPLICITY_ONE);
            fk.setToMultiplicity(IDBForeignKey.TO_MULTIPLICITY_ZERO_TO_MANY);
            String columnNote = adoptForeignKeyColumn(fk, child, childCol, refCol, newColumnName);
            IDiagramElement connector = connectShapes(diagram, fk, parentShape, childShape);
            if (connector == null) {
              fk.delete();
              return "Error adding foreign key: Visual Paradigm refused the connector";
            }
            refitTable(child);

            return "Added foreign key "
                + fromTable
                + "."
                + columnNote
                + " -> "
                + toTable
                + (refCol != null ? "." + refCol.getName() : " (primary key)");
          });
    } catch (Exception e) {
      return "Error adding foreign key: " + e.getMessage();
    }
  }

  @Tool(
      name = "addTableRelationship",
      description =
          "Add a relationship between tables (identifying or non-identifying): fromTable is the"
              + " parent (referenced) table, toTable the child. fromMultiplicity is 1 or 0..1,"
              + " toMultiplicity one of 0..1, 0..*, 1, 1..* (\"\" = 1 and 0..*)")
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
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable source = findTableOnDiagram(diagram, fromTable);
            IDBTable target = findTableOnDiagram(diagram, toTable);
            if (source == null || target == null) {
              return "Table not on diagram: " + (source == null ? fromTable : toTable);
            }
            String fromMult =
                isSet(fromMultiplicity)
                    ? fromMultiplicity.trim()
                    : IDBForeignKey.FROM_MULTIPLICITY_ONE;
            String toMult =
                isSet(toMultiplicity)
                    ? toMultiplicity.trim()
                    : IDBForeignKey.TO_MULTIPLICITY_ZERO_TO_MANY;
            if (!ErdUtils.FROM_MULTIPLICITIES.contains(fromMult)) {
              return "fromMultiplicity must be one of " + ErdUtils.FROM_MULTIPLICITIES;
            }
            if (!ErdUtils.TO_MULTIPLICITIES.contains(toMult)) {
              return "toMultiplicity must be one of " + ErdUtils.TO_MULTIPLICITIES;
            }
            String kind = type == null ? "" : type.trim();
            if (!kind.isEmpty()
                && !"identifying".equalsIgnoreCase(kind)
                && !"nonIdentifying".equalsIgnoreCase(kind)
                && !"non-identifying".equalsIgnoreCase(kind)) {
              return "Unknown relationship type '"
                  + type
                  + "': use identifying, nonIdentifying or \"\" (non-identifying)";
            }

            if (findForeignKey(diagram, source, target) != null) {
              return "A relationship from '"
                  + fromTable
                  + "' to '"
                  + toTable
                  + "' already exists (each one adds a foreign key column to '"
                  + toTable
                  + "'); use addForeignKey with fromColumn for a second one";
            }
            if (!foreignKeysBetween(source, target).isEmpty()) {
              // the pair is joined by a foreign key of another diagram: its line is drawn here
              // instead of a second foreign key with a duplicate column
              showExistingForeignKeys(diagram);
              relayout(diagram);
              return "Shown the existing relationship from '"
                  + fromTable
                  + "' to '"
                  + toTable
                  + "' on diagram '"
                  + diagramName
                  + "' (it is a foreign key of the project, no new column was added)";
            }
            java.util.Map<String, List<String>> columnsBefore = columnNames(diagram);
            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            fk.setFromMultiplicity(fromMult);
            fk.setToMultiplicity(toMult);
            fk.setIdentifying("identifying".equalsIgnoreCase(type));
            IDiagramElement connector =
                connectShapes(
                    diagram,
                    fk,
                    findDiagramElementByModel(diagram, source),
                    findDiagramElementByModel(diagram, target));
            if (connector == null) {
              fk.delete();
              return "Error adding relationship: Visual Paradigm refused the connector";
            }
            refitTable(target);
            String added = addedColumns(diagram, columnsBefore);

            return "Added "
                + (fk.isIdentifying() ? "identifying" : "non-identifying")
                + " relationship from '"
                + fromTable
                + "' to '"
                + toTable
                + "'"
                + added;
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  /**
   * Make the child's own column the FK column: VP adds a column named after the parent's key when
   * the foreign key is set up; it is renamed to the requested column (which it replaces, keeping
   * that column's primary-key and null flags) so the user's column carries the reference. A
   * requested referenced column replaces the parent's primary key as the reference.
   *
   * @return the name of the child's FK column
   */
  private static String adoptForeignKeyColumn(
      IDBForeignKey fk,
      IDBTable child,
      IDBColumn childCol,
      IDBColumn refCol,
      String newColumnName) {
    IDBColumn generated = null;
    Iterator<?> cols = fk.foreignKeyColumnsIterator();
    if (cols.hasNext()) {
      Object first = cols.next();
      if (first instanceof IDBColumn) {
        generated = (IDBColumn) first;
      }
    }
    if (generated == null) {
      return childCol != null ? childCol.getName() : "?";
    }
    if (refCol != null) {
      for (IDBForeignKeyConstraint constraint : generated.toForeignKeyConstraintArray()) {
        constraint.setRefColumn(refCol);
      }
    }
    if (childCol == null && newColumnName != null) {
      generated.setName(newColumnName); // the FK column of the requested name
    }
    if (childCol != null && childCol != generated) {
      String name = childCol.getName();
      boolean pk = childCol.isPrimaryKey();
      boolean nullable = childCol.isNullable();
      boolean unique = childCol.isUnique();
      int index = 0;
      IDBColumn[] before = child.toDBColumnArray();
      for (int i = 0; i < before.length; i++) {
        if (before[i].getId().equals(childCol.getId())) {
          index = i;
        }
      }
      childCol.delete();
      generated.setName(name);
      generated.setPrimaryKey(pk);
      generated.setNullable(nullable);
      generated.setUnique(unique);
      // the column keeps its place among the table's columns (VP appends the one it generated)
      List<IDBColumn> order = new ArrayList<>();
      for (IDBColumn c : child.toDBColumnArray()) {
        if (!c.getId().equals(generated.getId())) {
          order.add(c);
        }
      }
      order.add(Math.min(index, order.size()), generated);
      child.reorderDBColumn(order.toArray(new IDBColumn[0]));
    }
    return generated.getName();
  }

  private static String notAnIdentifier(String what, String name) {
    return what
        + " name '"
        + name
        + "' is not a SQL identifier: use letters, digits and underscores, starting with a letter"
        + " (no spaces or diacritics: they would be written unquoted into the DDL)";
  }

  /** Names of the columns of every table of the diagram, by table id. */
  private java.util.Map<String, List<String>> columnNames(IDiagramUIModel diagram) {
    java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
    for (IDBTable table : ErdUtils.getTablesInDiagram(diagram)) {
      List<String> names = new ArrayList<>();
      for (IDBColumn c : table.toDBColumnArray()) {
        names.add(c.getName());
      }
      result.put(table.getId(), names);
    }
    return result;
  }

  /**
   * The columns Visual Paradigm generated since {@code before}, as a note for a result text: a
   * relationship adds foreign key columns to the child, and an identifying one passes them on to
   * the tables below it.
   */
  private String addedColumns(IDiagramUIModel diagram, java.util.Map<String, List<String>> before) {
    List<String> added = new ArrayList<>();
    for (IDBTable table : ErdUtils.getTablesInDiagram(diagram)) {
      List<String> old = before.getOrDefault(table.getId(), new ArrayList<>());
      for (IDBColumn c : table.toDBColumnArray()) {
        if (!old.contains(c.getName())) {
          added.add(table.getName() + "." + c.getName());
        }
      }
    }
    return added.isEmpty() ? "" : "; added column(s): " + String.join(", ", added);
  }

  @Tool(
      name = "removeColumn",
      description =
          "Delete a column of a table shown on an ER diagram (from the project, not just the"
              + " view). A foreign key column cannot be removed: remove the foreign key first"
              + " (removeForeignKey)")
  public String removeColumn(String diagramName, String tableName, String columnName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            IDBColumn col = columnName == null ? null : findColumnByName(table, columnName.trim());
            if (col == null) {
              return "Column '" + columnName + "' not found in table '" + tableName + "'";
            }
            IDBForeignKeyConstraint[] fks = col.toForeignKeyConstraintArray();
            if (fks != null && fks.length > 0) {
              return "Column '"
                  + col.getName()
                  + "' is a foreign key column: remove the foreign key first (removeForeignKey)";
            }
            String name = col.getName();
            col.delete();
            refitTable(table);
            return "Removed column '" + name + "' from table '" + tableName + "'";
          });
    } catch (Exception e) {
      return "Error removing column: " + e.getMessage();
    }
  }

  @Tool(
      name = "renameColumn",
      description =
          "Rename a column of a table shown on an ER diagram (a SQL identifier: letters, digits,"
              + " underscores)")
  public String renameColumn(
      String diagramName, String tableName, String columnName, String newName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            IDBColumn col = columnName == null ? null : findColumnByName(table, columnName.trim());
            if (col == null) {
              return "Column '" + columnName + "' not found in table '" + tableName + "'";
            }
            if (!isSet(newName) || !ErdUtils.isSqlIdentifier(newName.trim())) {
              return notAnIdentifier("Column", newName == null ? "" : newName.trim());
            }
            if (findColumnByName(table, newName.trim()) != null) {
              return "Column '" + newName.trim() + "' already exists in table '" + tableName + "'";
            }
            col.setName(newName.trim());
            refitTable(table);
            return "Renamed column '"
                + columnName.trim()
                + "' of '"
                + tableName
                + "' to '"
                + newName.trim()
                + "'";
          });
    } catch (Exception e) {
      return "Error renaming column: " + e.getMessage();
    }
  }

  @Tool(
      name = "setPrimaryKey",
      description =
          "Choose the primary key of a table shown on an ER diagram: columnNames is one column or"
              + " a comma-separated list (a composite key). The listed columns become the key (NOT"
              + " NULL), every other column stops being part of it; foreign keys that reference the"
              + " table follow Visual Paradigm's model")
  public String setPrimaryKey(String diagramName, String tableName, String columnNames) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            List<String> wanted = new ArrayList<>();
            for (String n : (columnNames == null ? "" : columnNames).split(",")) {
              if (!n.trim().isEmpty()) {
                wanted.add(n.trim());
              }
            }
            if (wanted.isEmpty()) {
              return "columnNames must name at least one column of '" + tableName + "'";
            }
            for (String n : wanted) {
              IDBColumn col = findColumnByName(table, n);
              if (col == null) {
                List<String> have = new ArrayList<>();
                for (IDBColumn c : table.toDBColumnArray()) {
                  have.add(c.getName());
                }
                return "Column '"
                    + n
                    + "' not found in table '"
                    + tableName
                    + "' (columns: "
                    + String.join(", ", have)
                    + ")";
              }
            }
            String change = rekey(table, wanted);
            return change == null
                ? "Unchanged: the primary key of '"
                    + tableName
                    + "' is "
                    + String.join(", ", wanted)
                : "Primary key changed: " + change;
          });
    } catch (Exception e) {
      return "Error setting primary key: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeTable",
      description =
          "Delete a table from the project (every diagram that shows it loses it). A table that is"
              + " part of a foreign key is refused: remove the foreign keys first"
              + " (removeForeignKey). removeDiagramElement removes only the view")
  public String removeTable(String diagramName, String tableName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              IModelElement m = de.getModelElement();
              if (m instanceof IDBForeignKey) {
                IDBForeignKey fk = (IDBForeignKey) m;
                if ((fk.getFrom() != null && fk.getFrom().getId().equals(table.getId()))
                    || (fk.getTo() != null && fk.getTo().getId().equals(table.getId()))) {
                  return "Table '"
                      + tableName
                      + "' is part of a foreign key ("
                      + fk.getFrom().getName()
                      + " -> "
                      + fk.getTo().getName()
                      + "): remove it first (removeForeignKey)";
                }
              }
            }
            String name = table.getName();
            List<String> prompts = new ArrayList<>();
            answeringDeletePrompts(
                () -> {
                  table.delete();
                  return null;
                },
                prompts);
            return "Removed table '" + name + "' from the project";
          });
    } catch (Exception e) {
      return "Error removing table: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeForeignKey",
      description =
          "Delete the foreign key between two tables shown on an ER diagram: parentTable is the"
              + " referenced (one) side, childTable the table that holds the foreign key column."
              + " The foreign key column itself stays in the child (remove it with removeColumn)")
  public String removeForeignKey(String diagramName, String parentTable, String childTable) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable parent = findTableOnDiagram(diagram, parentTable);
            IDBTable child = findTableOnDiagram(diagram, childTable);
            if (parent == null || child == null) {
              return "Table not on diagram: " + (parent == null ? parentTable : childTable);
            }
            IDBForeignKey fk = findForeignKey(diagram, parent, child);
            if (fk == null) {
              return "No foreign key from '"
                  + parentTable
                  + "' to '"
                  + childTable
                  + "' on this"
                  + " diagram";
            }
            List<String> columns = new ArrayList<>();
            Iterator<?> it = fk.foreignKeyColumnsIterator();
            while (it.hasNext()) {
              Object c = it.next();
              if (c instanceof IDBColumn) {
                columns.add(((IDBColumn) c).getName());
              }
            }
            List<String> prompts = new ArrayList<>();
            answeringDeletePrompts(
                () -> {
                  fk.delete();
                  return null;
                },
                prompts);
            List<String> left = new ArrayList<>();
            for (String name : columns) {
              if (findColumnByName(child, name) != null) {
                left.add(name);
              }
            }
            refitTable(child);
            return "Removed the foreign key '"
                + parentTable
                + "' -> '"
                + childTable
                + "'"
                + (left.isEmpty()
                    ? ""
                    : "; column(s) " + String.join(", ", left) + " stay in '" + childTable + "'");
          });
    } catch (Exception e) {
      return "Error removing foreign key: " + e.getMessage();
    }
  }

  /** Refit every view of a table after its columns changed and lay the diagram out again. */
  private void refitTable(IDBTable table) {
    IDiagramElement[] views = table.getDiagramElements();
    if (views == null) {
      return;
    }
    java.util.Set<String> laidOut = new java.util.HashSet<>();
    for (IDiagramElement view : views) {
      fitShape(view);
      IDiagramUIModel diagram = view.getDiagramUIModel();
      if (diagram != null && laidOut.add(diagram.getId())) {
        relayout(diagram); // the box grew: the places and the lines change
      }
    }
  }

  private IDiagramUIModel findErd(String diagramName) {
    return findDiagram(diagramName, IERDiagramUIModel.class, "entity relationship");
  }

  /** The foreign keys of the project from {@code parent} to {@code child}, drawn or not. */
  private static List<IDBForeignKey> foreignKeysBetween(IDBTable parent, IDBTable child) {
    List<IDBForeignKey> keys = new ArrayList<>();
    com.vp.plugin.model.ISimpleRelationship[] out = parent.toFromRelationshipArray();
    for (com.vp.plugin.model.ISimpleRelationship r :
        out == null ? new com.vp.plugin.model.ISimpleRelationship[0] : out) {
      if (r instanceof IDBForeignKey
          && ((IDBForeignKey) r).getTo() != null
          && ((IDBForeignKey) r).getTo().getId().equals(child.getId())) {
        keys.add((IDBForeignKey) r);
      }
    }
    return keys;
  }

  /**
   * Draw the foreign keys of the project that join two tables shown on the diagram and are not
   * drawn on it yet (a table shown from another diagram brings its relationships along).
   *
   * @return the number of lines drawn
   */
  private int showExistingForeignKeys(IDiagramUIModel diagram) {
    int shown = 0;
    for (IDBTable parent : ErdUtils.getTablesInDiagram(diagram)) {
      IDiagramElement parentShape = findDiagramElementByModel(diagram, parent);
      for (IDiagramElement childShape : getDiagramElementsList(diagram)) {
        IModelElement child = childShape.getModelElement();
        if (!(child instanceof IDBTable) || parentShape == null) {
          continue;
        }
        for (IDBForeignKey fk : foreignKeysBetween(parent, (IDBTable) child)) {
          if (!drawnOn(diagram, fk)
              && connectShapes(diagram, fk, parentShape, childShape) != null) {
            shown++;
          }
        }
      }
    }
    return shown;
  }

  private static boolean drawnOn(IDiagramUIModel diagram, IModelElement model) {
    IDiagramElement[] views = model.getDiagramElements();
    for (IDiagramElement view : views == null ? new IDiagramElement[0] : views) {
      if (view.getDiagramUIModel() != null
          && view.getDiagramUIModel().getId().equals(diagram.getId())) {
        return true;
      }
    }
    return false;
  }

  /** A foreign key drawn on the diagram from {@code parent} to {@code child}, or null. */
  private IDBForeignKey findForeignKey(IDiagramUIModel diagram, IDBTable parent, IDBTable child) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement m = de.getModelElement();
      if (m instanceof IDBForeignKey) {
        IDBForeignKey fk = (IDBForeignKey) m;
        if (fk.getFrom() != null
            && fk.getTo() != null
            && fk.getFrom().getId().equals(parent.getId())
            && fk.getTo().getId().equals(child.getId())) {
          return fk;
        }
      }
    }
    return null;
  }

  /** The table shown on the diagram with that name, or null. */
  private IDBTable findTableOnDiagram(IDiagramUIModel diagram, String name) {
    if (name == null) {
      return null;
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement m = de.getModelElement();
      if (m instanceof IDBTable && name.trim().equals(m.getName())) {
        return (IDBTable) m;
      }
    }
    return null;
  }

  private static boolean isSet(String value) {
    return value != null && !value.trim().isEmpty();
  }

  @Tool(
      name = "syncClassesToErd",
      description =
          "Class diagram to ERD (like Visual Paradigm's Synchronize to Entity Relationship"
              + " Diagram): every class with the <<ORM Persistable>> stereotype on classDiagramName"
              + " becomes a table on erdName (created when missing; tables already in the project"
              + " are reused and only get the missing columns). Attributes become columns (String"
              + " -> varchar(255), int -> integer, Date -> date, double, decimal(19,2), boolean,"
              + " ...); an attribute named id, <class>Id, ma<Class> or with a <<PK>> stereotype is the primary"
              + " key, otherwise an ID column is added. primaryKeys chooses the keys instead:"
              + " \"KhachHang=maKhachHang; ChiTietHoaDon=maHoaDon,maMon\" (several attributes make a"
              + " composite key; a class not named keeps the rule above; re-running with another"
              + " choice moves the key). Associations: one-to-many puts a foreign key"
              + " into the many side, one-to-one into the optional side, many-to-many becomes a join"
              + " table; a generalization makes the subclass share its superclass key (identifying"
              + " foreign key, table per subclass). Classes and attributes are linked to their"
              + " tables and columns (ORM mapping). Warns about classes without the stereotype,"
              + " associations to non-persistable classes, missing multiplicities, untyped or"
              + " unknown types and multi-valued attributes")
  public String syncClassesToErd(String classDiagramName, String erdName, String primaryKeys) {
    try {
      java.util.Map<String, List<String>> keys = OrmErdPlanner.parseKeys(primaryKeys);
      return runOnEdt(
          () -> {
            com.vp.plugin.diagram.IClassDiagramUIModel classDiagram =
                (com.vp.plugin.diagram.IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(
                        classDiagramName, com.vp.plugin.diagram.IClassDiagramUIModel.class);
            if (classDiagram == null) {
              return diagramNotFound(classDiagramName);
            }
            if (!isSet(erdName)) {
              return "erdName must not be empty";
            }
            ClassModelReader.Shown shown = ClassModelReader.shown(classDiagram);
            List<OrmErdPlanner.ClassSpec> specs = new ArrayList<>();
            List<OrmErdPlanner.AssocSpec> assocs = new ArrayList<>();
            ClassModelReader.ormSpecs(shown, specs, assocs);
            OrmErdPlanner.Plan plan = OrmErdPlanner.plan(specs, assocs, keys);
            if (plan.tables.isEmpty()) {
              return "No class on '"
                  + classDiagramName
                  + "' has the <<"
                  + OrmErdPlanner.PERSISTABLE
                  + ">> stereotype: nothing to synchronize. Mark the entity classes with"
                  + " addStereotypeToClasses(diagramName, classNames, \""
                  + OrmErdPlanner.PERSISTABLE
                  + "\", \"\")"
                  + warningsText(plan.warnings);
            }

            IDiagramUIModel erd =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(erdName.trim(), IERDiagramUIModel.class);
            boolean created = erd == null;
            if (created) {
              erd = newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ER_DIAGRAM, erdName.trim());
              erd.setAutoFitShapesSize(true);
              useCenterConnectionPoints(erd);
            }
            java.util.Map<String, IClass> classByName = new java.util.HashMap<>();
            for (IClass cls : shown.classes) {
              classByName.putIfAbsent(cls.getName(), cls);
            }

            List<String> newTables = new ArrayList<>();
            List<String> reusedTables = new ArrayList<>();
            List<String> newColumns = new ArrayList<>();
            List<String> changedKeys = new ArrayList<>();
            java.util.Map<String, IDBTable> tables = new java.util.LinkedHashMap<>();
            for (OrmErdPlanner.Table t : plan.tables) {
              IDBTable table = findTableOnDiagram(erd, t.name);
              if (table == null) {
                table = findRootElement(t.name, IDBTable.class);
              }
              boolean reused = table != null;
              if (!reused) {
                table = getModelElementFactory().createDBTable();
                nameInOwner(table, null, t.name);
                newTables.add(t.name);
              } else {
                reusedTables.add(t.name);
              }
              IClass cls = t.className == null ? null : classByName.get(t.className);
              for (OrmErdPlanner.Column c : t.columns) {
                IDBColumn col = findColumnByName(table, c.name);
                if (col == null) {
                  col = getModelElementFactory().createDBColumn();
                  col.setName(c.name);
                  setColumnType(col, c.type, c.length, c.scale, plan.warnings, t.name);
                  // a key column added to a referenced table goes through rekey (children)
                  col.setPrimaryKey(c.primaryKey && !reused);
                  col.setNullable(c.nullable);
                  table.addDBColumn(col);
                  if (reused) {
                    newColumns.add(t.name + "." + c.name);
                  }
                }
                if (cls != null && c.attribute != null) {
                  linkAttribute(cls, c.attribute, col);
                }
              }
              if (reused && t.hasPrimaryKey()) {
                // the key chosen now wins over the last sync; a key column that is also a
                // foreign key (shared with a superclass, a join table's) stays in the key
                List<String> wanted = new ArrayList<>();
                for (OrmErdPlanner.Column c : t.columns) {
                  if (c.primaryKey) {
                    wanted.add(c.name);
                  }
                }
                for (IDBColumn col : table.toDBColumnArray()) {
                  if (col.isPrimaryKey() && isForeignKeyColumn(col)) {
                    wanted.add(col.getName());
                  }
                }
                String change = rekey(table, wanted);
                if (change != null) {
                  changedKeys.add(change);
                  // a column that left the key takes the attribute's nullability again
                  for (OrmErdPlanner.Column c : t.columns) {
                    IDBColumn col = findColumnByName(table, c.name);
                    if (col != null && !c.primaryKey && !isForeignKeyColumn(col)) {
                      col.setNullable(c.nullable);
                    }
                  }
                }
              }
              if (cls != null) {
                linkClass(cls, table);
              }
              tables.put(t.name, table);
              if (findDiagramElementByModel(erd, table) == null) {
                IDiagramElement de = addToDiagram(erd, table, t.name);
                de.resetCaption();
              }
            }

            List<String> fkAdded = new ArrayList<>();
            for (OrmErdPlanner.ForeignKey f : plan.foreignKeys) {
              IDBTable parent = tables.get(f.parent);
              IDBTable child = tables.get(f.child);
              if (parent == null || child == null) {
                continue;
              }
              if (!foreignKeysBetween(parent, child).isEmpty()) {
                continue; // synchronized before: the line is drawn by showExistingForeignKeys
              }
              IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
              fk.setFrom(parent);
              fk.setTo(child);
              fk.setFromMultiplicity(f.fromMultiplicity);
              fk.setToMultiplicity(f.toMultiplicity);
              fk.setIdentifying(f.identifying);
              IDiagramElement connector =
                  connectShapes(
                      erd,
                      fk,
                      findDiagramElementByModel(erd, parent),
                      findDiagramElementByModel(erd, child));
              if (connector == null) {
                fk.delete();
                plan.warnings.add(
                    "Visual Paradigm refused the foreign key " + f.parent + " -> " + f.child);
                continue;
              }
              nameForeignKeyColumns(fk, child, parent, "generalization".equals(f.source));
              fkAdded.add(f.toString());
            }
            showExistingForeignKeys(erd);
            for (IDBTable table : tables.values()) {
              IDiagramElement de = findDiagramElementByModel(erd, table);
              if (de != null) {
                fitShape(de);
              }
            }
            relayout(erd);
            getDiagramManager().openDiagram(erd);

            StringBuilder out = new StringBuilder();
            out.append(created ? "Created" : "Updated")
                .append(" ER diagram '")
                .append(erd.getName())
                .append("' from '")
                .append(classDiagramName)
                .append("': ")
                .append(tables.size())
                .append(" table(s)");
            if (!newTables.isEmpty()) {
              out.append("; new: ").append(String.join(", ", newTables));
            }
            if (!reusedTables.isEmpty()) {
              out.append("; existing: ").append(String.join(", ", reusedTables));
            }
            if (!newColumns.isEmpty()) {
              out.append("; added columns: ").append(String.join(", ", newColumns));
            }
            if (!changedKeys.isEmpty()) {
              out.append("; primary key changed: ").append(String.join("; ", changedKeys));
            }
            out.append("; ").append(fkAdded.size()).append(" new foreign key(s)");
            if (!fkAdded.isEmpty()) {
              out.append(": ").append(String.join(", ", fkAdded));
            }
            out.append(warningsText(plan.warnings));
            return out.toString();
          });
    } catch (Exception e) {
      return "Error synchronizing classes to ERD: " + e.getMessage();
    }
  }

  /**
   * Readable names for the columns Visual Paradigm added for a foreign key (it joins the parent's
   * name and key: KhachHangmaKhachHang, PhongHatid): the referenced key's own name (maKhachHang),
   * or parent + Id for a key named id (phongHatId); a subclass keeps its superclass key's name. A
   * name already taken in the child keeps Visual Paradigm's.
   */
  private static void nameForeignKeyColumns(
      IDBForeignKey fk, IDBTable child, IDBTable parent, boolean inherited) {
    Iterator<?> it = fk.foreignKeyColumnsIterator();
    while (it.hasNext()) {
      Object o = it.next();
      if (!(o instanceof IDBColumn)) {
        continue;
      }
      IDBColumn col = (IDBColumn) o;
      IDBForeignKeyConstraint[] constraints = col.toForeignKeyConstraintArray();
      IDBColumn ref =
          constraints == null || constraints.length == 0 ? null : constraints[0].getRefColumn();
      if (ref == null) {
        continue;
      }
      String name = ref.getName();
      if (!inherited && "id".equalsIgnoreCase(name)) {
        String p = parent.getName();
        name = Character.toLowerCase(p.charAt(0)) + p.substring(1) + "Id";
      }
      boolean taken = false;
      for (IDBColumn other : child.toDBColumnArray()) {
        if (!other.getId().equals(col.getId()) && name.equalsIgnoreCase(other.getName())) {
          taken = true;
        }
      }
      if (!taken && ErdUtils.isSqlIdentifier(name)) {
        col.setName(name);
      }
    }
  }

  /**
   * Make exactly {@code wanted} the primary key of a table. Visual Paradigm keeps the foreign keys
   * that reference the table in step: it adds a column to every child for a new key column (named
   * parent + column) but leaves the column of a dropped key behind. Those stale columns are
   * deleted, the new ones get readable names, and a child whose key changed with it (identifying
   * foreign key) passes the change on to its own children.
   *
   * @param table the table
   * @param wanted names of the key columns
   * @return "Table: a, b (was c)" when the key changed, null when it already was that key
   */
  private String rekey(IDBTable table, List<String> wanted) throws Exception {
    List<String> before = new ArrayList<>();
    for (IDBColumn col : table.toDBColumnArray()) {
      if (col.isPrimaryKey()) {
        before.add(col.getName());
      }
    }
    if (new java.util.HashSet<>(before).equals(new java.util.HashSet<>(wanted))) {
      return null;
    }
    // every table below this one, with the ids of its columns that referenced its parent
    java.util.Map<String, java.util.Set<String>> referencing = new java.util.HashMap<>();
    List<IDBForeignKey> cascade = new ArrayList<>();
    java.util.ArrayDeque<IDBTable> todo = new java.util.ArrayDeque<>();
    java.util.Set<String> visited = new java.util.HashSet<>();
    todo.add(table);
    while (!todo.isEmpty()) {
      IDBTable parent = todo.poll();
      if (!visited.add(parent.getId())) {
        continue;
      }
      com.vp.plugin.model.ISimpleRelationship[] out = parent.toFromRelationshipArray();
      for (com.vp.plugin.model.ISimpleRelationship r :
          out == null ? new com.vp.plugin.model.ISimpleRelationship[0] : out) {
        if (r instanceof IDBForeignKey && r.getTo() instanceof IDBTable) {
          IDBForeignKey fk = (IDBForeignKey) r;
          IDBTable child = (IDBTable) r.getTo();
          cascade.add(fk);
          java.util.Set<String> ids =
              referencing.computeIfAbsent(fk.getId(), k -> new java.util.HashSet<>());
          for (IDBColumn col : child.toDBColumnArray()) {
            if (references(col, parent)) {
              ids.add(col.getId());
            }
          }
          if (fk.isIdentifying()) {
            todo.add(child);
          }
        }
      }
    }
    java.util.Map<String, java.util.Set<String>> columnsBefore = new java.util.HashMap<>();
    for (IDBForeignKey fk : cascade) {
      java.util.Set<String> ids = new java.util.HashSet<>();
      for (IDBColumn col : ((IDBTable) fk.getTo()).toDBColumnArray()) {
        ids.add(col.getId());
      }
      columnsBefore.put(fk.getId(), ids);
    }

    for (IDBColumn col : table.toDBColumnArray()) {
      boolean key = wanted.contains(col.getName());
      col.setPrimaryKey(key);
      if (key) {
        col.setNullable(false);
      }
    }

    List<String> removed = new ArrayList<>();
    for (IDBForeignKey fk : cascade) {
      IDBTable parent = (IDBTable) fk.getFrom();
      IDBTable child = (IDBTable) fk.getTo();
      for (IDBColumn col : child.toDBColumnArray()) {
        boolean stale =
            referencing.get(fk.getId()).contains(col.getId()) && !referencesKey(col, parent);
        if (stale) {
          String name = child.getName() + "." + col.getName();
          answeringDeletePrompts(
              () -> {
                col.delete();
                return null;
              },
              new ArrayList<>());
          removed.add(name);
        }
      }
      boolean renameNew = false;
      for (IDBColumn col : child.toDBColumnArray()) {
        renameNew |= !columnsBefore.get(fk.getId()).contains(col.getId());
      }
      if (renameNew) {
        nameForeignKeyColumns(
            fk,
            child,
            parent,
            fk.isIdentifying()
                && IDBForeignKey.TO_MULTIPLICITY_ZERO_TO_ONE.equals(fk.getToMultiplicity()));
      }
    }
    java.util.Set<String> refit = new java.util.HashSet<>();
    refitTable(table);
    for (IDBForeignKey fk : cascade) {
      if (refit.add(fk.getTo().getId())) {
        refitTable((IDBTable) fk.getTo());
      }
    }
    return table.getName()
        + ": "
        + String.join(", ", wanted)
        + (before.isEmpty() ? "" : " (was " + String.join(", ", before) + ")")
        + (removed.isEmpty()
            ? ""
            : ", removed stale foreign key column(s) " + String.join(", ", removed));
  }

  /** Whether a column is a foreign key column referencing a column of {@code parent}. */
  private static boolean references(IDBColumn col, IDBTable parent) {
    IDBForeignKeyConstraint[] fks = col.toForeignKeyConstraintArray();
    for (IDBForeignKeyConstraint fk : fks == null ? new IDBForeignKeyConstraint[0] : fks) {
      IDBColumn ref = fk.getRefColumn();
      if (ref != null
          && ref.getParent() != null
          && ref.getParent().getId().equals(parent.getId())) {
        return true;
      }
    }
    return false;
  }

  /** Whether a column references a primary key column of {@code parent}. */
  private static boolean referencesKey(IDBColumn col, IDBTable parent) {
    IDBForeignKeyConstraint[] fks = col.toForeignKeyConstraintArray();
    for (IDBForeignKeyConstraint fk : fks == null ? new IDBForeignKeyConstraint[0] : fks) {
      IDBColumn ref = fk.getRefColumn();
      if (ref != null
          && ref.isPrimaryKey()
          && ref.getParent() != null
          && ref.getParent().getId().equals(parent.getId())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isForeignKeyColumn(IDBColumn col) {
    IDBForeignKeyConstraint[] fks = col.toForeignKeyConstraintArray();
    return fks != null && fks.length > 0;
  }

  private static boolean planned(OrmErdPlanner.Table t, String column) {
    for (OrmErdPlanner.Column c : t.columns) {
      if (c.name.equals(column)) {
        return true;
      }
    }
    return false;
  }

  private static String warningsText(List<String> warnings) {
    if (warnings.isEmpty()) {
      return "";
    }
    return "\nWarnings (" + warnings.size() + "):\n  - " + String.join("\n  - ", warnings);
  }

  /** Set a column type, trying the standard spellings the project's database may know. */
  private static void setColumnType(
      IDBColumn col, String type, int length, int scale, List<String> warnings, String table) {
    for (String candidate : ErdUtils.typeCandidates(type)) {
      if (col.setType(candidate, length, scale)) {
        return;
      }
    }
    warnings.add(
        "Visual Paradigm does not know the type '"
            + type
            + "' for column "
            + table
            + "."
            + col.getName()
            + ": left untyped");
  }

  /** ORM mapping class -> table, the link Visual Paradigm's synchronization keeps. */
  private void linkClass(IClass cls, IDBTable table) {
    try {
      IORMClassDetail detail = cls.getOrmDetail();
      if (detail == null) {
        detail = getModelElementFactory().createORMClassDetail();
        cls.setOrmDetail(detail);
      }
      detail.setTableModel(table);
    } catch (RuntimeException e) {
      // the mapping is a convenience: the tables and columns stand without it
    }
  }

  /** ORM mapping attribute -> column. */
  private void linkAttribute(IClass cls, String attribute, IDBColumn col) {
    try {
      IAttribute attr = cls.getAttributeByName(attribute);
      if (attr == null) {
        return;
      }
      IORMAttributeDetail detail = attr.getOrmDetail();
      if (detail == null) {
        detail = getModelElementFactory().createORMAttributeDetail();
        attr.setOrmDetail(detail);
      }
      detail.setColumnModel(col);
    } catch (RuntimeException e) {
      // the mapping is a convenience: the column stands without it
    }
  }

  @Tool(
      name = "generateSql",
      description =
          "ERD to SQL: generate SQL for the tables shown on an ER diagram with Visual Paradigm's"
              + " database generator. database: MySQL (default), MariaDB, MSSQLServer (sqlserver),"
              + " PostgreSQL (postgres), Oracle, SQLite, H2, HSQL, Derby, DB2, ... statements: a"
              + " comma-separated list of create (default; CREATE TABLE with primary and foreign"
              + " keys, referenced tables first), drop, select, insert, update, delete. When Visual"
              + " Paradigm's generator is not set up for the database (Tools > DB > Database"
              + " Configuration) the plugin writes the SQL in that dialect itself (MySQL/MariaDB,"
              + " PostgreSQL, SQL Server with NVARCHAR, Oracle, SQLite; generic for the others)."
              + " outputFile: an absolute .sql path to write as well (empty: only returned)")
  public String generateSql(
      String diagramName, String database, String statements, String outputFile) {
    try {
      DatabaseType type = databaseType(database);
      if (type == null) {
        return "Unknown database '"
            + database
            + "': use one of "
            + java.util.Arrays.toString(DatabaseType.values());
      }
      List<String> kinds = new ArrayList<>();
      for (String k : (isSet(statements) ? statements : "create").split(",")) {
        String kind = k.trim().toLowerCase(java.util.Locale.ROOT);
        if (kind.isEmpty()) {
          continue;
        }
        if (!java.util.Arrays.asList("create", "drop", "select", "insert", "update", "delete")
            .contains(kind)) {
          return "Unknown statement '"
              + k.trim()
              + "': use create, drop, select, insert,"
              + " update or delete";
        }
        kinds.add(kind);
      }
      String sql =
          runOnEdt(
              () -> {
                IDiagramUIModel diagram =
                    (IDiagramUIModel)
                        DiagramUtils.findDiagramByName(diagramName, IERDiagramUIModel.class);
                if (diagram == null) {
                  return null;
                }
                List<IDBTable> tables = ErdUtils.getTablesInDiagram(diagram);
                if (tables.isEmpty()) {
                  return "";
                }
                IModelElement[] elements = tables.toArray(new IModelElement[0]);
                DatabaseManager db = ApplicationManager.instance().getDatabaseManager();
                List<SqlGenerator.TableSpec> specs = tableSpecs(tables);
                SqlGenerator.Dialect dialect = SqlGenerator.dialect(type.name());
                StringBuilder b = new StringBuilder();
                String vpProblem = null;
                for (String kind : kinds) {
                  b.append("-- ")
                      .append(kind.toUpperCase(java.util.Locale.ROOT))
                      .append(" (")
                      .append(type)
                      .append(")\n");
                  String text = null;
                  try {
                    text = vpSql(db, type, kind, elements);
                  } catch (RuntimeException e) {
                    vpProblem = String.valueOf(e.getMessage()).trim();
                  }
                  if (text == null || text.trim().isEmpty()) {
                    text = SqlGenerator.generate(dialect, kind, specs);
                  }
                  b.append(text.trim()).append("\n\n");
                }
                if (vpProblem != null) {
                  b.insert(
                      0,
                      "-- Written by the plugin's "
                          + dialect
                          + " generator: Visual Paradigm's own generator needs the database set"
                          + " up in Tools > DB > Database Configuration ("
                          + vpProblem
                          + ")\n\n");
                }
                return b.toString();
              });
      if (sql == null) {
        return diagramNotFound(diagramName);
      }
      if (sql.isEmpty()) {
        return "No tables on diagram: " + diagramName;
      }
      if (isSet(outputFile)) {
        java.io.File file = new java.io.File(outputFile.trim());
        if (!file.isAbsolute()) {
          return "outputFile must be an absolute path: " + outputFile;
        }
        if (file.getParentFile() != null) {
          java.nio.file.Files.createDirectories(file.getParentFile().toPath());
        }
        java.nio.file.Files.write(
            file.toPath(), sql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Wrote " + file.getAbsolutePath() + "\n\n" + sql;
      }
      return sql;
    } catch (Exception e) {
      return "Error generating SQL: " + e.getMessage();
    }
  }

  /** The SQL of one statement kind from Visual Paradigm's generator (null or empty: none). */
  private static String vpSql(
      DatabaseManager db, DatabaseType type, String kind, IModelElement[] elements) {
    switch (kind) {
      case "drop":
        return db.generateDropSQL(type, elements);
      case "select":
        return db.generateSelectSQL(type, elements);
      case "insert":
        return db.generateInsertSQL(type, elements);
      case "update":
        return db.generateUpdateSQL(type, elements);
      case "delete":
        return db.generateDeleteSQL(type, elements);
      default:
        return db.generateCreateSQL(type, elements);
    }
  }

  /** The SQL generator's view of the tables: columns, keys and the columns they reference. */
  private static List<SqlGenerator.TableSpec> tableSpecs(List<IDBTable> tables) {
    List<SqlGenerator.TableSpec> specs = new ArrayList<>();
    for (IDBTable table : tables) {
      SqlGenerator.TableSpec t = new SqlGenerator.TableSpec(table.getName());
      for (IDBColumn col : table.toDBColumnArray()) {
        SqlGenerator.ColumnSpec c =
            new SqlGenerator.ColumnSpec(
                col.getName(), col.getTypeInText(), col.getLength(), col.getScale());
        c.primaryKey = col.isPrimaryKey();
        c.nullable = col.isNullable();
        c.unique = col.isUnique();
        IDBForeignKeyConstraint[] fks = col.toForeignKeyConstraintArray();
        if (fks != null) {
          for (IDBForeignKeyConstraint fk : fks) {
            IDBColumn ref = fk.getRefColumn();
            if (ref != null && ref.getParent() != null) {
              c.refTable = ref.getParent().getName();
              c.refColumn = ref.getName();
              break;
            }
          }
        }
        t.add(c);
      }
      specs.add(t);
    }
    return specs;
  }

  /**
   * The database type for a user spelling (case-insensitive name, or sqlserver/mssql, postgres,
   * maria), MySQL when empty, null when unknown.
   *
   * @param name spelling
   * @return the type or null
   */
  static DatabaseType databaseType(String name) {
    String n = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    switch (n) {
      case "":
      case "mysql":
        return DatabaseType.MySQL;
      case "sqlserver":
      case "mssql":
      case "mssqlserver":
        return DatabaseType.MSSQLServer;
      case "postgres":
      case "postgresql":
        return DatabaseType.PostgreSQL;
      case "maria":
      case "mariadb":
        return DatabaseType.MariaDB;
      default:
        for (DatabaseType t : DatabaseType.values()) {
          if (t.name().equalsIgnoreCase(n)) {
            return t;
          }
        }
        return null;
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
              return diagramNotFound(diagramName);
            }

            List<IDBTable> tables = ErdUtils.getTablesInDiagram(diagram);

            // Build table -> user name map from diagram captions
            java.util.Map<IDBTable, String> tableNames = new java.util.LinkedHashMap<>();
            Iterator<?> deIter = diagram.diagramElementIterator();
            while (deIter.hasNext()) {
              Object obj = deIter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model instanceof IDBTable) {
                  String displayName = model.getName();
                  if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
                    String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
                    if (caption != null && !caption.isEmpty()) {
                      displayName = caption;
                    }
                  }
                  tableNames.put((IDBTable) model, displayName);
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("ERD REPORT: ").append(diagramName).append("\n");
            report.append("================================\n");

            // Tables with columns
            report.append("Tables (").append(tables.size()).append("):\n");
            for (IDBTable table : tables) {
              String tableName = tableNames.getOrDefault(table, table.getName());
              List<String> cols = new ArrayList<>();
              Iterator<?> colIter = table.dBColumnIterator();
              while (colIter.hasNext()) {
                Object colObj = colIter.next();
                if (colObj instanceof IDBColumn) {
                  IDBColumn col = (IDBColumn) colObj;
                  StringBuilder colStr = new StringBuilder();
                  colStr.append(col.getName());
                  if (col.getTypeInText() != null) {
                    colStr.append(" ").append(col.getTypeInText());
                  }
                  if (col.isPrimaryKey()) {
                    colStr.append(" PK");
                  } else if (!col.isNullable()) {
                    colStr.append(" NOT NULL");
                  }
                  if (col.isUnique() && !col.isPrimaryKey()) {
                    colStr.append(" UNIQUE");
                  }
                  cols.add(colStr.toString());
                }
              }
              report.append("  - ").append(tableName);
              report.append(" (").append(cols.size()).append(" columns)\n");
              for (String col : cols) {
                report.append("    ").append(col).append("\n");
              }
            }

            // Foreign Keys
            List<String> fks = new ArrayList<>();
            Iterator<?> elemIter = diagram.diagramElementIterator();
            while (elemIter.hasNext()) {
              Object obj = elemIter.next();
              if (obj instanceof IDiagramElement) {
                IModelElement model = ((IDiagramElement) obj).getModelElement();
                if (model instanceof IDBForeignKey) {
                  IDBForeignKey fk = (IDBForeignKey) model;
                  String from = fk.getFrom() != null ? fk.getFrom().getName() : "?";
                  String to = fk.getTo() != null ? fk.getTo().getName() : "?";
                  String fkName =
                      fk.getName() != null && !fk.getName().trim().isEmpty()
                          ? fk.getName()
                          : "fk_" + from + "_" + to;
                  fks.add(fkName + ": " + from + " -> " + to);
                }
              }
            }
            if (!fks.isEmpty()) {
              report.append("Foreign Keys (").append(fks.size()).append("):\n");
              for (String fk : fks) {
                report.append("  - ").append(fk).append("\n");
              }
            }

            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  private IDBColumn findColumnByName(IDBTable table, String columnName) {
    Iterator<?> iter = table.dBColumnIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDBColumn) {
        IDBColumn col = (IDBColumn) obj;
        if (columnName.equals(col.getName())) {
          return col;
        }
      }
    }
    return null;
  }
}
