package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.SqlGenerator.ColumnSpec;
import com.brunnen.vp.mcp.util.SqlGenerator.Dialect;
import com.brunnen.vp.mcp.util.SqlGenerator.TableSpec;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SqlGeneratorTest {

  private static ColumnSpec key(String name) {
    ColumnSpec c = new ColumnSpec(name, "integer", 0, 0);
    c.primaryKey = true;
    c.nullable = false;
    return c;
  }

  private static ColumnSpec ref(String name, String table, String column, boolean pk) {
    ColumnSpec c = new ColumnSpec(name, "integer", 0, 0);
    c.refTable = table;
    c.refColumn = column;
    c.primaryKey = pk;
    c.nullable = false;
    return c;
  }

  /** Booking references Customer; the join table references Booking and Service. */
  private static List<TableSpec> model() {
    TableSpec booking =
        new TableSpec("Booking")
            .add(key("id"))
            .add(new ColumnSpec("total", "decimal", 10, 2))
            .add(new ColumnSpec("start", "timestamp", 0, 0))
            .add(ref("customerId", "Customer", "id", false));
    ColumnSpec name = new ColumnSpec("name", "varchar", 100, 0);
    name.unique = true;
    TableSpec customer = new TableSpec("Customer").add(key("id")).add(name);
    TableSpec join =
        new TableSpec("Booking_Service")
            .add(ref("bookingId", "Booking", "id", true))
            .add(ref("serviceId", "Service", "id", true));
    TableSpec service =
        new TableSpec("Service").add(key("id")).add(new ColumnSpec("active", "boolean", 0, 0));
    return Arrays.asList(join, booking, customer, service);
  }

  @Test
  public void createOrdersReferencedTablesFirst() {
    String sql = SqlGenerator.generate(Dialect.MYSQL, "create", model());
    int customer = sql.indexOf("CREATE TABLE `Customer`");
    int booking = sql.indexOf("CREATE TABLE `Booking`");
    int join = sql.indexOf("CREATE TABLE `Booking_Service`");
    assertTrue(customer >= 0 && customer < booking && booking < join);
    assertTrue(sql.indexOf("CREATE TABLE `Service`") < join);
    assertTrue(sql.contains("`name` VARCHAR(100) UNIQUE"));
    assertTrue(sql.contains("`total` DECIMAL(10,2)"));
    assertTrue(sql.contains("`start` DATETIME"));
    assertTrue(
        sql.contains("CONSTRAINT `pk_Booking_Service` PRIMARY KEY (`bookingId`, `serviceId`)"));
    assertTrue(
        sql.contains(
            "CONSTRAINT `fk_Booking_Customer` FOREIGN KEY (`customerId`) REFERENCES `Customer` (`id`)"));
  }

  @Test
  public void dialectTypes() {
    String ss = SqlGenerator.generate(Dialect.SQLSERVER, "create", model());
    assertTrue(ss.contains("[name] NVARCHAR(100)"));
    assertTrue(ss.contains("[active] BIT"));
    assertTrue(ss.contains("[start] DATETIME2"));
    String pg = SqlGenerator.generate(Dialect.POSTGRESQL, "create", model());
    assertTrue(pg.contains("active BOOLEAN"));
    assertTrue(pg.contains("id INTEGER NOT NULL"));
    String ora = SqlGenerator.generate(Dialect.ORACLE, "create", model());
    assertTrue(ora.contains("name VARCHAR2(100 CHAR)"));
    assertTrue(ora.contains("id NUMBER(10) NOT NULL"));
    assertTrue(ora.contains("total NUMBER(10,2)"));
    String lite = SqlGenerator.generate(Dialect.SQLITE, "create", model());
    assertTrue(lite.contains("name TEXT"));
  }

  @Test
  public void dropAndDeleteGoDependentsFirst() {
    String drop = SqlGenerator.generate(Dialect.POSTGRESQL, "drop", model());
    assertTrue(drop.indexOf("Booking_Service") < drop.indexOf("DROP TABLE IF EXISTS Customer"));
    String ora = SqlGenerator.generate(Dialect.ORACLE, "drop", model());
    assertTrue(ora.contains("DROP TABLE Customer CASCADE CONSTRAINTS;"));
    String delete = SqlGenerator.generate(Dialect.GENERIC, "delete", model());
    assertTrue(
        delete.contains("DELETE FROM Booking_Service WHERE bookingId = ? AND serviceId = ?;"));
  }

  @Test
  public void crudStatements() {
    String insert = SqlGenerator.generate(Dialect.GENERIC, "insert", model());
    assertTrue(insert.contains("INSERT INTO Customer (id, name) VALUES (?, ?);"));
    String update = SqlGenerator.generate(Dialect.GENERIC, "update", model());
    assertTrue(update.contains("UPDATE Customer SET name = ? WHERE id = ?;"));
    assertTrue(update.contains("-- Booking_Service: only key columns"));
    String select = SqlGenerator.generate(Dialect.GENERIC, "select", model());
    assertTrue(select.contains("SELECT id, active FROM Service;"));
  }

  @Test
  public void dialectNames() {
    assertEquals(Dialect.MYSQL, SqlGenerator.dialect("MariaDB"));
    assertEquals(Dialect.SQLSERVER, SqlGenerator.dialect("MSSQLServer_2008"));
    assertEquals(Dialect.POSTGRESQL, SqlGenerator.dialect("PostgreSQL"));
    assertEquals(Dialect.GENERIC, SqlGenerator.dialect("H2"));
  }
}
