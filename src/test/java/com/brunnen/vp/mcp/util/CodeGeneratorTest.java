package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.CodeGenerator.CgClass;
import com.brunnen.vp.mcp.util.CodeGenerator.CgField;
import com.brunnen.vp.mcp.util.CodeGenerator.CgOp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CodeGeneratorTest {

  private static List<CgClass> model() {
    CgClass customer = new CgClass("Customer");
    customer.persistable = true;
    CgField id = new CgField("id", "int");
    id.key = true;
    customer.fields.add(id);
    customer.fields.add(new CgField("name", "String"));
    CgField bookings = new CgField("bookings", "Booking");
    bookings.many = true;
    bookings.relation = "OneToMany";
    bookings.mappedBy = "customer";
    customer.fields.add(bookings);
    CgOp total = new CgOp("totalSpent", "double");
    total.params.add(new String[] {"year", "int"});
    customer.operations.add(total);

    CgClass booking = new CgClass("Booking");
    booking.persistable = true;
    booking.fields.add(new CgField("start", "DateTime"));
    CgField cust = new CgField("customer", "Customer");
    cust.relation = "ManyToOne";
    booking.fields.add(cust);
    booking.interfaces.add("Billable");

    CgClass billable = new CgClass("Billable");
    billable.kind = "interface";
    billable.operations.add(new CgOp("bill", ""));

    CgClass status = new CgClass("Status");
    status.kind = "enum";
    status.literals.addAll(Arrays.asList("OPEN", "CLOSED"));
    return Arrays.asList(customer, booking, billable, status);
  }

  @Test
  public void javaWithJpa() {
    Map<String, String> files = CodeGenerator.generate("Java", "com.k", model(), true, true);
    assertEquals(4, files.size());
    String c = files.get("com/k/Customer.java");
    assertTrue(c.startsWith("package com.k;"));
    assertTrue(c.contains("@Entity"));
    assertTrue(c.contains("@Id\n  @GeneratedValue"));
    assertTrue(c.contains("@OneToMany(mappedBy = \"customer\")"));
    assertTrue(c.contains("private List<Booking> bookings = new ArrayList<>();"));
    assertTrue(c.contains("public double totalSpent(int year) {"));
    assertTrue(c.contains("return 0.0;"));
    assertTrue(c.contains("public String getName()"));
    String b = files.get("com/k/Booking.java");
    assertTrue(b.contains("implements Billable"));
    assertTrue(b.contains("@ManyToOne\n  @JoinColumn(name = \"customerId\")"));
    assertTrue(b.contains("private Long id;")); // no key attribute: JPA id added
    assertTrue(b.contains("import java.time.LocalDateTime;"));
    assertTrue(files.get("com/k/Billable.java").contains("public interface Billable {"));
    assertTrue(files.get("com/k/Billable.java").contains("  void bill();"));
    assertTrue(files.get("com/k/Status.java").contains("OPEN,\n  CLOSED;"));
  }

  @Test
  public void javaWithoutJpaHasNoAnnotations() {
    String c = CodeGenerator.generate("java", "", model(), false, false).get("Customer.java");
    assertTrue(!c.contains("@"));
    assertTrue(!c.contains("getName"));
  }

  @Test
  public void csharpProperties() {
    Map<String, String> files = CodeGenerator.generate("C#", "Karaoke", model(), false, true);
    String c = files.get("Customer.cs");
    assertTrue(c.contains("namespace Karaoke"));
    assertTrue(c.contains("public string Name { get; set; }"));
    assertTrue(c.contains("public List<Booking> Bookings { get; set; } = new List<Booking>();"));
    assertTrue(c.contains("public double TotalSpent(int year)"));
    assertTrue(files.get("Booking.cs").contains("public class Booking : Billable"));
  }

  @Test
  public void pythonAndTypescriptAndPhp() {
    Map<String, String> py = CodeGenerator.generate("python", "", model(), false, false);
    String c = py.get("customer.py");
    assertTrue(c.contains("class Customer:"));
    assertTrue(c.contains("def total_spent(self, year: int) -> float:"));
    assertTrue(c.contains("self._bookings: list[Booking] = []") || c.contains("self.bookings"));
    assertTrue(py.get("billable.py").contains("@abstractmethod"));
    assertTrue(py.get("status.py").contains("class Status(Enum):"));

    Map<String, String> ts = CodeGenerator.generate("ts", "", model(), false, false);
    assertTrue(ts.get("Customer.ts").contains("bookings: Booking[] = [];"));
    assertTrue(ts.get("Customer.ts").contains("import { Booking } from \"./Booking\";"));
    assertTrue(ts.get("Booking.ts").contains("import type { Billable }"));

    String php =
        CodeGenerator.generate("php", "App.Model", model(), false, true).get("Customer.php");
    assertTrue(php.contains("namespace App\\Model;"));
    assertTrue(php.contains("private array $bookings = [];"));
    assertTrue(php.contains("public function getName(): ?string"));
  }

  @Test
  public void joinColumnFollowsErdNamingAndJoinTableIsNamed() {
    List<CgClass> classes = model();
    assertEquals("customerId", CodeGenerator.joinColumn("Customer", classes));
    CgField code = new CgField("maKhachHang", "String");
    code.key = true;
    CgClass kh = new CgClass("KhachHang");
    kh.fields.add(code);
    assertEquals("maKhachHang", CodeGenerator.joinColumn("KhachHang", Arrays.asList(kh)));

    CgClass room = new CgClass("Room");
    room.persistable = true;
    CgField devices = new CgField("devices", "Device");
    devices.many = true;
    devices.relation = "ManyToMany";
    devices.joinTable = "Room_Device";
    room.fields.add(devices);
    CgField note = new CgField("note", "");
    room.fields.add(note);
    String java =
        CodeGenerator.generate("java", "", Arrays.asList(room), true, false).get("Room.java");
    assertTrue(java.contains("@ManyToMany\n  @JoinTable(\n      name = \"Room_Device\","));
    assertTrue(java.contains("joinColumns = @JoinColumn(name = \"roomId\")"));
    assertTrue(java.contains("inverseJoinColumns = @JoinColumn(name = \"deviceId\")"));
    assertTrue(java.contains("private String note;"));
  }

  @Test
  public void compositeKeyGetsIdClass() {
    CgClass line = new CgClass("OrderLine");
    line.persistable = true;
    CgField order = new CgField("orderNo", "int");
    order.key = true;
    CgField item = new CgField("itemNo", "int");
    item.key = true;
    line.fields.add(order);
    line.fields.add(item);
    line.fields.add(new CgField("qty", "int"));
    String java =
        CodeGenerator.generate("java", "", Arrays.asList(line), true, false).get("OrderLine.java");
    assertTrue(java.contains("@IdClass(OrderLine.Key.class)"));
    assertTrue(java.contains("public static class Key implements Serializable {"));
    assertTrue(java.contains("return Objects.hash(orderNo, itemNo);"));
    assertTrue(!java.contains("@GeneratedValue"));
  }

  @Test
  public void languageNames() {
    assertEquals("csharp", CodeGenerator.normalizeLanguage("cs"));
    assertEquals("java", CodeGenerator.normalizeLanguage(""));
    assertNull(CodeGenerator.normalizeLanguage("cobol"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void unknownLanguageThrows() {
    CodeGenerator.generate("cobol", "", model(), false, false);
  }

  @Test
  public void baseTypeAndSnake() {
    assertEquals("Order", CodeGenerator.baseType("List<Order>"));
    assertEquals("Order", CodeGenerator.baseType("Order[]"));
    assertEquals("order_line", CodeGenerator.snake("OrderLine"));
  }
}
