package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Utility class for ERD operations. */
public final class ErdUtils {

  private ErdUtils() {}

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
   * Split a SQL column type such as {@code nvarchar(100)} or {@code decimal(10,2)} into its name,
   * length and scale.
   *
   * @param type the type text
   * @param length the length used when the text has none
   * @param scale the scale used when the text has none
   * @return {name, length, scale} as strings
   */
  public static String[] parseColumnType(String type, int length, int scale) {
    String t = type.trim();
    int open = t.indexOf('(');
    if (open > 0 && t.endsWith(")")) {
      String[] args = t.substring(open + 1, t.length() - 1).split(",");
      String l = args[0].trim();
      String sc = args.length > 1 ? args[1].trim() : String.valueOf(scale);
      return new String[] {t.substring(0, open).trim(), l, sc};
    }
    // a varchar without a length would be an unbounded "varchar" in the DDL: the book's 255
    int len = "varchar".equalsIgnoreCase(t) && length <= 0 ? DEFAULT_VARCHAR_LENGTH : length;
    return new String[] {t, String.valueOf(len), String.valueOf(scale)};
  }

  /** Length given to a varchar column that names none. */
  public static final int DEFAULT_VARCHAR_LENGTH = 255;

  /**
   * Whether a table or column name is a plain SQL identifier: a letter or underscore, then letters,
   * digits and underscores, ASCII only (the DDL writes names unquoted).
   *
   * @param name the name
   * @return true for a plain identifier
   */
  public static boolean isSqlIdentifier(String name) {
    return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*");
  }

  /**
   * Type names to try, in order, for a column type Visual Paradigm's database may not know (SQL
   * Server spellings first mapped to their standard equivalents).
   *
   * @param typeName the requested type name
   * @return the requested name followed by its fallbacks
   */
  public static List<String> typeCandidates(String typeName) {
    List<String> names = new ArrayList<>();
    names.add(typeName);
    switch (typeName.toLowerCase(java.util.Locale.ROOT)) {
      case "nvarchar":
      case "varchar2":
      case "nvarchar2":
        names.add("varchar");
        break;
      case "nchar":
        names.add("char");
        break;
      case "ntext":
      case "longtext":
      case "mediumtext":
        names.add("text");
        names.add("longvarchar");
        break;
      case "datetime":
      case "datetime2":
      case "smalldatetime":
      case "datetimeoffset":
        names.add("timestamp");
        break;
      case "bit":
        names.add("boolean");
        break;
      case "money":
      case "smallmoney":
      case "number":
        names.add("decimal");
        names.add("numeric");
        break;
      case "tinyint":
        names.add("smallint");
        break;
      case "int":
        names.add("integer");
        break;
      case "uniqueidentifier":
        names.add("char");
        break;
      default:
        break;
    }
    return names;
  }

  /** Valid parent-end multiplicities of a foreign key ({@code IDBForeignKey.FROM_*}). */
  public static final List<String> FROM_MULTIPLICITIES = java.util.Arrays.asList("1", "0..1");

  /** Valid child-end multiplicities of a foreign key ({@code IDBForeignKey.TO_*}). */
  public static final List<String> TO_MULTIPLICITIES =
      java.util.Arrays.asList("0..1", "0..*", "1", "1..*");

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
