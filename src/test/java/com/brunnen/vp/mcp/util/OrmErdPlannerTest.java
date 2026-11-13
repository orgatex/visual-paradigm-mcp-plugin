package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.OrmErdPlanner.AssocSpec;
import com.brunnen.vp.mcp.util.OrmErdPlanner.ClassSpec;
import com.brunnen.vp.mcp.util.OrmErdPlanner.Plan;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class OrmErdPlannerTest {

  private static boolean warns(Plan plan, String part) {
    return plan.warnings.stream().anyMatch(w -> w.contains(part));
  }

  @Test
  public void nonPersistableClassIsWarnedAndSkipped() {
    Plan plan =
        OrmErdPlanner.plan(
            Arrays.asList(
                new ClassSpec("Customer", true).attr("id", "int", ""),
                new ClassSpec("Helper", false)),
            Collections.emptyList());
    assertEquals(1, plan.tables.size());
    assertNull(plan.table("Helper"));
    assertTrue(warns(plan, "'Helper' has no <<ORM Persistable>>"));
  }

  @Test
  public void noPersistableClassGivesEmptyPlan() {
    Plan plan =
        OrmErdPlanner.plan(
            Collections.singletonList(new ClassSpec("A", false)), Collections.emptyList());
    assertTrue(plan.tables.isEmpty());
    assertEquals(1, plan.warnings.size());
  }

  @Test
  public void attributesBecomeTypedColumnsAndIdIsKey() {
    Plan plan =
        OrmErdPlanner.plan(
            Collections.singletonList(
                new ClassSpec("Room", true)
                    .attr("roomId", "int", "")
                    .attr("name", "String", "")
                    .attr("price", "double", "")
                    .attr("opened", "Date", "")
                    .attr("rate", "decimal(10,2)", "")),
            Collections.emptyList());
    OrmErdPlanner.Table room = plan.table("Room");
    assertEquals(5, room.columns.size());
    assertTrue(room.columns.get(0).primaryKey);
    assertFalse(room.columns.get(0).nullable);
    assertEquals("integer", room.columns.get(0).type);
    assertEquals("varchar", room.columns.get(1).type);
    assertEquals(255, room.columns.get(1).length);
    assertEquals("date", room.columns.get(3).type);
    assertEquals(10, room.columns.get(4).length);
    assertEquals(2, room.columns.get(4).scale);
    assertTrue(plan.warnings.isEmpty());
  }

  @Test
  public void missingKeyAddsIdColumnWithWarning() {
    Plan plan =
        OrmErdPlanner.plan(
            Collections.singletonList(new ClassSpec("Song", true).attr("title", "String", "")),
            Collections.emptyList());
    OrmErdPlanner.Table song = plan.table("Song");
    assertEquals("ID", song.columns.get(0).name);
    assertTrue(song.columns.get(0).primaryKey);
    assertTrue(warns(plan, "no key attribute"));
  }

  @Test
  public void pkStereotypeMarksKey() {
    Plan plan =
        OrmErdPlanner.plan(
            Collections.singletonList(new ClassSpec("Song", true).attr("code", "String", "", "PK")),
            Collections.emptyList());
    assertEquals(1, plan.table("Song").columns.size());
    assertTrue(plan.table("Song").columns.get(0).primaryKey);
  }

  @Test
  public void oneToManyPutsKeyInManySide() {
    List<ClassSpec> classes =
        Arrays.asList(
            new ClassSpec("Customer", true).attr("id", "int", ""),
            new ClassSpec("Booking", true).attr("id", "int", ""));
    Plan plan =
        OrmErdPlanner.plan(
            classes,
            Collections.singletonList(new AssocSpec("Booking", "0..*", "Customer", "1", "")));
    assertEquals(1, plan.foreignKeys.size());
    OrmErdPlanner.ForeignKey fk = plan.foreignKeys.get(0);
    assertEquals("Customer", fk.parent);
    assertEquals("Booking", fk.child);
    assertEquals("1", fk.fromMultiplicity);
    assertEquals("0..*", fk.toMultiplicity);
    assertFalse(fk.identifying);
  }

  @Test
  public void manyToManyBecomesJoinTable() {
    List<ClassSpec> classes =
        Arrays.asList(
            new ClassSpec("Room", true).attr("id", "int", ""),
            new ClassSpec("Device", true).attr("id", "int", ""));
    Plan plan =
        OrmErdPlanner.plan(
            classes, Collections.singletonList(new AssocSpec("Room", "*", "Device", "1..*", "")));
    OrmErdPlanner.Table join = plan.table("Room_Device");
    assertNotNull(join);
    assertNull(join.className);
    assertEquals(2, plan.foreignKeys.size());
    assertTrue(plan.foreignKeys.get(0).identifying);
    assertEquals("Room_Device", plan.foreignKeys.get(1).child);
  }

  @Test
  public void oneToOneKeyGoesToOptionalSide() {
    List<ClassSpec> classes =
        Arrays.asList(
            new ClassSpec("Person", true).attr("id", "int", ""),
            new ClassSpec("Card", true).attr("id", "int", ""));
    Plan plan =
        OrmErdPlanner.plan(
            classes, Collections.singletonList(new AssocSpec("Card", "0..1", "Person", "1", "")));
    OrmErdPlanner.ForeignKey fk = plan.foreignKeys.get(0);
    assertEquals("Person", fk.parent);
    assertEquals("Card", fk.child);
    assertEquals("0..1", fk.toMultiplicity);
  }

  @Test
  public void generalizationSharesParentKeyParentFirst() {
    List<ClassSpec> classes =
        Arrays.asList(
            new ClassSpec("Manager", true).attr("level", "int", "").parent("Employee"),
            new ClassSpec("Employee", true).attr("id", "int", "").attr("name", "String", ""));
    Plan plan = OrmErdPlanner.plan(classes, Collections.emptyList());
    assertEquals("Employee", plan.tables.get(0).name);
    OrmErdPlanner.Table manager = plan.table("Manager");
    assertFalse(manager.hasPrimaryKey()); // the identifying key adds it
    assertEquals(1, plan.foreignKeys.size());
    assertTrue(plan.foreignKeys.get(0).identifying);
    assertEquals("generalization", plan.foreignKeys.get(0).source);
    assertFalse(warns(plan, "no key attribute"));
  }

  @Test
  public void associationToNonPersistableAndMissingMultiplicityWarn() {
    List<ClassSpec> classes =
        Arrays.asList(
            new ClassSpec("A", true).attr("id", "int", ""),
            new ClassSpec("B", true).attr("id", "int", ""),
            new ClassSpec("Ui", false));
    Plan plan =
        OrmErdPlanner.plan(
            classes,
            Arrays.asList(
                new AssocSpec("A", "", "B", "*", ""), new AssocSpec("A", "1", "Ui", "*", "")));
    assertTrue(warns(plan, "non-persistable class 'Ui'"));
    assertTrue(warns(plan, "no multiplicity at A"));
    assertEquals(1, plan.foreignKeys.size());
  }

  @Test
  public void unknownAndCollectionAndClassTypedAttributesWarn() {
    List<ClassSpec> classes =
        Arrays.asList(
            new ClassSpec("A", true)
                .attr("id", "int", "")
                .attr("tags", "String", "0..*")
                .attr("b", "B", "")
                .attr("blob", "Weird", "")
                .attr("note", "", ""),
            new ClassSpec("B", true).attr("id", "int", ""));
    Plan plan = OrmErdPlanner.plan(classes, Collections.emptyList());
    assertEquals(3, plan.table("A").columns.size());
    assertTrue(warns(plan, "multi-valued"));
    assertTrue(warns(plan, "persistable class type 'B'"));
    assertTrue(warns(plan, "unknown type 'Weird'"));
    assertTrue(warns(plan, "untyped"));
  }

  @Test
  public void identifiersDropDiacritics() {
    assertEquals("PhongHat", OrmErdPlanner.identifier("PhòngHát"));
    assertEquals("Don_dat", OrmErdPlanner.identifier("Đơn đặt").replace("D", "D"));
    assertEquals("_1abc", OrmErdPlanner.identifier("1abc"));
  }

  @Test
  public void chosenKeysWinIncludingComposite() {
    List<ClassSpec> classes =
        Arrays.asList(
            new ClassSpec("Room", true).attr("id", "int", "").attr("code", "String", ""),
            new ClassSpec("Line", true).attr("orderNo", "int", "").attr("itemNo", "int", ""),
            new ClassSpec("Song", true).attr("title", "String", ""));
    Plan plan =
        OrmErdPlanner.plan(
            classes,
            Collections.emptyList(),
            OrmErdPlanner.parseKeys("Room=code; Line=orderNo,itemNo; Song=nope; Ghost=x"));
    OrmErdPlanner.Table room = plan.table("Room");
    assertFalse(room.columns.get(0).primaryKey); // id is no longer the key
    assertTrue(room.columns.get(1).primaryKey);
    OrmErdPlanner.Table line = plan.table("Line");
    assertTrue(line.columns.get(0).primaryKey && line.columns.get(1).primaryKey);
    assertEquals("ID", plan.table("Song").columns.get(0).name); // unknown choice: fallback
    assertTrue(warns(plan, "class 'Song' has no attribute 'nope'"));
    assertTrue(warns(plan, "class 'Ghost' that is not on the diagram"));
  }

  @Test
  public void parseKeysForms() {
    java.util.Map<String, List<String>> k = OrmErdPlanner.parseKeys("A.x, B.y, B.z");
    assertEquals(Arrays.asList("x"), k.get("A"));
    assertEquals(Arrays.asList("y", "z"), k.get("B"));
    assertTrue(OrmErdPlanner.parseKeys("").isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseKeysRejectsGarbage() {
    OrmErdPlanner.parseKeys("justaword");
  }

  @Test
  public void manyDetection() {
    assertTrue(OrmErdPlanner.isMany("*"));
    assertTrue(OrmErdPlanner.isMany("0..*"));
    assertTrue(OrmErdPlanner.isMany("1..n"));
    assertTrue(OrmErdPlanner.isMany("2..5"));
    assertFalse(OrmErdPlanner.isMany("0..1"));
    assertFalse(OrmErdPlanner.isMany("1"));
    assertFalse(OrmErdPlanner.isMany(""));
  }
}
