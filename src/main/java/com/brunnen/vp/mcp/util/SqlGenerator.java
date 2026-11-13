package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQL for the tables of an ER diagram, in the dialect of a database: CREATE TABLE (primary keys,
 * unique columns, named foreign key constraints, referenced tables first), DROP TABLE (dependent
 * tables first) and the parameterized SELECT, INSERT, UPDATE and DELETE of each table. Pure Java:
 * the tool reads the Visual Paradigm tables into {@link TableSpec}s.
 */
public final class SqlGenerator {

  private SqlGenerator() {}

  /** A table. */
  public static final class TableSpec {
    public final String name;
    public final List<ColumnSpec> columns = new ArrayList<>();

    /**
     * A table.
     *
     * @param name table name
     */
    public TableSpec(String name) {
      this.name = name;
    }

    /**
     * Add a column.
     *
     * @param column the column
     * @return this
     */
    public TableSpec add(ColumnSpec column) {
      columns.add(column);
      return this;
    }
  }

  /** A column. */
  public static final class ColumnSpec {
    public final String name;
    public final String type;
    public final int length;
    public final int scale;
    public boolean primaryKey;
    public boolean nullable = true;
    public boolean unique;

    /** Referenced table and column of a foreign key column, or null. */
    public String refTable;

    public String refColumn;

    /**
     * A column.
     *
     * @param name column name
     * @param type SQL type name (varchar, integer, ...)
     * @param length length/precision (0 = none)
     * @param scale scale (0 = none)
     */
    public ColumnSpec(String name, String type, int length, int scale) {
      this.name = name;
      this.type = type == null ? "" : type.trim();
      this.length = length;
      this.scale = scale;
    }
  }

  /** Dialects {@link #dialect} knows. */
  public enum Dialect {
    MYSQL,
    POSTGRESQL,
    SQLSERVER,
    ORACLE,
    SQLITE,
    GENERIC
  }

  /**
   * The dialect of a database name (Visual Paradigm's DatabaseType names included).
   *
   * @param database database name
   * @return the dialect, GENERIC for the others
   */
  public static Dialect dialect(String database) {
    String d = database == null ? "" : database.toLowerCase(Locale.ROOT);
    if (d.startsWith("mysql") || d.startsWith("maria")) {
      return Dialect.MYSQL;
    }
    if (d.startsWith("postgre")) {
      return Dialect.POSTGRESQL;
    }
    if (d.startsWith("mssql") || d.startsWith("sqlserver")) {
      return Dialect.SQLSERVER;
    }
    if (d.startsWith("oracle")) {
      return Dialect.ORACLE;
    }
    if (d.startsWith("sqlite")) {
      return Dialect.SQLITE;
    }
    return Dialect.GENERIC;
  }

  /**
   * SQL of one statement kind for all tables.
   *
   * @param dialect target dialect
   * @param kind create, drop, select, insert, update or delete
   * @param tables the tables
   * @return the statements
   */
  public static String generate(Dialect dialect, String kind, List<TableSpec> tables) {
    List<TableSpec> order = dependencyOrder(tables);
    StringBuilder b = new StringBuilder();
    switch (kind) {
      case "drop":
        for (int i = order.size() - 1; i >= 0; i--) {
          b.append("DROP TABLE ")
              .append(dialect == Dialect.ORACLE || dialect == Dialect.SQLSERVER ? "" : "IF EXISTS ")
              .append(q(dialect, order.get(i).name))
              .append(dialect == Dialect.ORACLE ? " CASCADE CONSTRAINTS" : "")
              .append(";\n");
        }
        break;
      case "select":
        for (TableSpec t : order) {
          b.append("SELECT ")
              .append(columnList(dialect, t.columns))
              .append(" FROM ")
              .append(q(dialect, t.name))
              .append(";\n");
        }
        break;
      case "insert":
        for (TableSpec t : order) {
          List<String> marks = new ArrayList<>();
          for (int i = 0; i < t.columns.size(); i++) {
            marks.add("?");
          }
          b.append("INSERT INTO ")
              .append(q(dialect, t.name))
              .append(" (")
              .append(columnList(dialect, t.columns))
              .append(") VALUES (")
              .append(String.join(", ", marks))
              .append(");\n");
        }
        break;
      case "update":
        for (TableSpec t : order) {
          List<String> sets = new ArrayList<>();
          for (ColumnSpec c : t.columns) {
            if (!c.primaryKey) {
              sets.add(q(dialect, c.name) + " = ?");
            }
          }
          if (sets.isEmpty()) {
            b.append("-- ").append(t.name).append(": only key columns, nothing to update\n");
            continue;
          }
          b.append("UPDATE ")
              .append(q(dialect, t.name))
              .append(" SET ")
              .append(String.join(", ", sets))
              .append(keyWhere(dialect, t))
              .append(";\n");
        }
        break;
      case "delete":
        for (int i = order.size() - 1; i >= 0; i--) {
          TableSpec t = order.get(i);
          b.append("DELETE FROM ")
              .append(q(dialect, t.name))
              .append(keyWhere(dialect, t))
              .append(";\n");
        }
        break;
      default:
        for (TableSpec t : order) {
          b.append(createTable(dialect, t)).append("\n\n");
        }
        break;
    }
    return b.toString().trim() + "\n";
  }

  private static String createTable(Dialect d, TableSpec t) {
    List<String> lines = new ArrayList<>();
    List<String> keys = new ArrayList<>();
    for (ColumnSpec c : t.columns) {
      StringBuilder line =
          new StringBuilder("  ").append(q(d, c.name)).append(' ').append(columnType(d, c));
      if (!c.nullable || c.primaryKey) {
        line.append(" NOT NULL");
      }
      if (c.unique && !c.primaryKey) {
        line.append(" UNIQUE");
      }
      lines.add(line.toString());
      if (c.primaryKey) {
        keys.add(q(d, c.name));
      }
    }
    if (!keys.isEmpty()) {
      lines.add(
          "  CONSTRAINT "
              + q(d, "pk_" + t.name)
              + " PRIMARY KEY ("
              + String.join(", ", keys)
              + ")");
    }
    // one constraint per referenced table: the columns of a composite key go together
    Map<String, List<ColumnSpec>> byRef = new LinkedHashMap<>();
    for (ColumnSpec c : t.columns) {
      if (c.refTable != null) {
        byRef.computeIfAbsent(c.refTable, k -> new ArrayList<>()).add(c);
      }
    }
    Set<String> names = new HashSet<>();
    for (Map.Entry<String, List<ColumnSpec>> e : byRef.entrySet()) {
      List<String> cols = new ArrayList<>();
      List<String> refs = new ArrayList<>();
      for (ColumnSpec c : e.getValue()) {
        cols.add(q(d, c.name));
        refs.add(q(d, c.refColumn));
      }
      String name = "fk_" + t.name + "_" + e.getKey();
      int i = 2;
      while (!names.add(name)) {
        name = "fk_" + t.name + "_" + e.getKey() + i++;
      }
      lines.add(
          "  CONSTRAINT "
              + q(d, name)
              + " FOREIGN KEY ("
              + String.join(", ", cols)
              + ") REFERENCES "
              + q(d, e.getKey())
              + " ("
              + String.join(", ", refs)
              + ")");
    }
    return "CREATE TABLE " + q(d, t.name) + " (\n" + String.join(",\n", lines) + "\n);";
  }

  /**
   * The column type in the dialect.
   *
   * @param d dialect
   * @param c column
   * @return type text
   */
  static String columnType(Dialect d, ColumnSpec c) {
    String t = c.type.toLowerCase(Locale.ROOT);
    int len = c.length;
    String size = len > 0 ? "(" + len + (c.scale > 0 ? "," + c.scale : "") + ")" : "";
    switch (t) {
      case "varchar":
      case "nvarchar":
      case "varchar2":
        int n = len > 0 ? len : ErdUtils.DEFAULT_VARCHAR_LENGTH;
        switch (d) {
          case SQLSERVER:
            return "NVARCHAR(" + n + ")";
          case ORACLE:
            return "VARCHAR2(" + n + " CHAR)";
          case SQLITE:
            return "TEXT";
          default:
            return "VARCHAR(" + n + ")";
        }
      case "char":
      case "nchar":
        return (d == Dialect.SQLSERVER ? "NCHAR" : d == Dialect.SQLITE ? "TEXT" : "CHAR")
            + (d == Dialect.SQLITE ? "" : "(" + (len > 0 ? len : 1) + ")");
      case "text":
      case "longvarchar":
      case "clob":
        return d == Dialect.SQLSERVER ? "NVARCHAR(MAX)" : d == Dialect.ORACLE ? "CLOB" : "TEXT";
      case "int":
      case "integer":
      case "smallint":
      case "tinyint":
        return d == Dialect.ORACLE
            ? "NUMBER(10)"
            : d == Dialect.MYSQL || d == Dialect.SQLSERVER
                ? (t.equals("integer") ? "INT" : t.toUpperCase(Locale.ROOT))
                : "INTEGER";
      case "bigint":
        return d == Dialect.ORACLE ? "NUMBER(19)" : d == Dialect.SQLITE ? "INTEGER" : "BIGINT";
      case "double":
        switch (d) {
          case POSTGRESQL:
            return "DOUBLE PRECISION";
          case SQLSERVER:
            return "FLOAT";
          case ORACLE:
            return "BINARY_DOUBLE";
          case SQLITE:
            return "REAL";
          default:
            return "DOUBLE";
        }
      case "float":
      case "real":
        return d == Dialect.ORACLE ? "BINARY_FLOAT" : d == Dialect.MYSQL ? "FLOAT" : "REAL";
      case "decimal":
      case "numeric":
        String p = len > 0 ? size : "(19,2)";
        return d == Dialect.ORACLE ? "NUMBER" + p : d == Dialect.SQLITE ? "NUMERIC" : "DECIMAL" + p;
      case "boolean":
      case "bit":
        switch (d) {
          case SQLSERVER:
            return "BIT";
          case ORACLE:
            return "NUMBER(1)";
          case SQLITE:
            return "INTEGER";
          default:
            return "BOOLEAN";
        }
      case "date":
        return d == Dialect.SQLITE ? "TEXT" : "DATE";
      case "time":
        return d == Dialect.ORACLE ? "TIMESTAMP" : d == Dialect.SQLITE ? "TEXT" : "TIME";
      case "timestamp":
      case "datetime":
        return d == Dialect.MYSQL
            ? "DATETIME"
            : d == Dialect.SQLSERVER ? "DATETIME2" : d == Dialect.SQLITE ? "TEXT" : "TIMESTAMP";
      case "blob":
      case "varbinary":
      case "longvarbinary":
        return d == Dialect.POSTGRESQL
            ? "BYTEA"
            : d == Dialect.SQLSERVER ? "VARBINARY(MAX)" : "BLOB";
      default:
        return (t.isEmpty() ? "VARCHAR" : c.type.toUpperCase(Locale.ROOT)) + size;
    }
  }

  private static String keyWhere(Dialect d, TableSpec t) {
    List<String> keys = new ArrayList<>();
    for (ColumnSpec c : t.columns) {
      if (c.primaryKey) {
        keys.add(q(d, c.name) + " = ?");
      }
    }
    return keys.isEmpty() ? "" : " WHERE " + String.join(" AND ", keys);
  }

  private static String columnList(Dialect d, List<ColumnSpec> columns) {
    List<String> names = new ArrayList<>();
    for (ColumnSpec c : columns) {
      names.add(q(d, c.name));
    }
    return names.isEmpty() ? "*" : String.join(", ", names);
  }

  /** A quoted identifier: backticks for MySQL, brackets for SQL Server, plain otherwise. */
  private static String q(Dialect d, String name) {
    switch (d) {
      case MYSQL:
        return "`" + name + "`";
      case SQLSERVER:
        return "[" + name + "]";
      default:
        return name;
    }
  }

  /**
   * Tables ordered so that a referenced table comes before the tables that reference it (a cycle
   * keeps the given order for the tables on it).
   *
   * @param tables the tables
   * @return ordered tables
   */
  public static List<TableSpec> dependencyOrder(List<TableSpec> tables) {
    Map<String, TableSpec> byName = new LinkedHashMap<>();
    for (TableSpec t : tables) {
      byName.put(t.name, t);
    }
    List<TableSpec> order = new ArrayList<>();
    Set<String> done = new HashSet<>();
    for (TableSpec t : tables) {
      visit(t, byName, done, new HashSet<>(), order);
    }
    return order;
  }

  private static void visit(
      TableSpec t,
      Map<String, TableSpec> byName,
      Set<String> done,
      Set<String> path,
      List<TableSpec> order) {
    if (done.contains(t.name) || !path.add(t.name)) {
      return;
    }
    for (ColumnSpec c : t.columns) {
      TableSpec ref = c.refTable == null ? null : byName.get(c.refTable);
      if (ref != null && ref != t) {
        visit(ref, byName, done, path, order);
      }
    }
    done.add(t.name);
    order.add(t);
  }
}
