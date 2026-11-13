package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IArtifact;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IComponent;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IDFDataFlow;
import com.vp.plugin.model.IDFDataStore;
import com.vp.plugin.model.IDFExternalEntity;
import com.vp.plugin.model.IDFProcess;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IGsDfdProcess;
import com.vp.plugin.model.IInstanceSpecification;
import com.vp.plugin.model.ILink;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.INOTE;
import com.vp.plugin.model.INode;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IPort;
import com.vp.plugin.model.IYcDfdProcess;
import com.vp.plugin.model.IYdDfdProcess;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** The structural layout profiles against mocked Visual Paradigm shapes and connectors. */
public class StructuralLayoutProfilesTest {

  private static int ids;

  private static IShapeUIModel shape(IModelElement model, int x, int y, int w, int h) {
    IShapeUIModel s = mock(IShapeUIModel.class);
    when(s.getId()).thenReturn("s" + ids++);
    when(s.getModelElement()).thenReturn(model);
    when(s.getX()).thenReturn(x);
    when(s.getY()).thenReturn(y);
    when(s.getWidth()).thenReturn(w);
    when(s.getHeight()).thenReturn(h);
    when(s.getCaptionUIModel()).thenReturn(mock(ICaptionUIModel.class));
    return s;
  }

  private static <T extends IModelElement> T model(Class<T> type, String name) {
    T m = mock(type);
    when(m.getName()).thenReturn(name);
    return m;
  }

  private static IConnectorUIModel connector(
      IModelElement model, IShapeUIModel from, IShapeUIModel to, int captionW) {
    IConnectorUIModel c = mock(IConnectorUIModel.class);
    when(c.getId()).thenReturn("c" + ids++);
    when(c.getModelElement()).thenReturn(model);
    when(c.getFromShape()).thenReturn(from);
    when(c.getToShape()).thenReturn(to);
    ICaptionUIModel caption = mock(ICaptionUIModel.class);
    when(caption.getWidth()).thenReturn(captionW);
    when(caption.getHeight()).thenReturn(captionW > 0 ? 14 : 0);
    when(c.getCaptionUIModel()).thenReturn(caption);
    return c;
  }

  // --- data flow diagrams

  @Test
  public void dfdEntitiesAreTheFirstLayerAndStoresTheLast() {
    DfdLayoutProfile profile = new DfdLayoutProfile();
    assertEquals(
        ElkLayout.FIRST_LAYER,
        profile.node(shape(model(IDFExternalEntity.class, "Khách"), 0, 0, 100, 40)).layer);
    assertEquals(
        ElkLayout.LAST_LAYER,
        profile.node(shape(model(IDFDataStore.class, "Phòng"), 0, 0, 100, 40)).layer);
    assertEquals(
        ElkLayout.ANY_LAYER,
        profile.node(shape(model(IDFProcess.class, "Đặt phòng"), 0, 0, 100, 40)).layer);
    assertFalse("the shared layout draws the lines of the layered layout", profile.routesAll());
  }

  @Test
  public void yourdonProcessesAreCirclesTheOthersBoxes() {
    DfdLayoutProfile profile = new DfdLayoutProfile();
    assertEquals(
        ShapeOutline.Kind.ELLIPSE,
        profile.node(shape(model(IYdDfdProcess.class, "P"), 0, 0, 90, 60)).outline);
    assertEquals(
        ShapeOutline.Kind.ELLIPSE,
        profile.node(shape(model(IYcDfdProcess.class, "P"), 0, 0, 90, 60)).outline);
    assertEquals(
        ShapeOutline.Kind.RECT,
        profile.node(shape(model(IGsDfdProcess.class, "P"), 0, 0, 90, 60)).outline);
  }

  @Test
  public void aDataFlowCarriesItsNameBesideTheMiddleOfItsLine() {
    DfdLayoutProfile profile = new DfdLayoutProfile();
    IShapeUIModel a = shape(model(IDFExternalEntity.class, "A"), 0, 0, 100, 40);
    IShapeUIModel b = shape(model(IDFProcess.class, "B"), 300, 0, 100, 40);
    LayoutProfile.EdgeSpec named =
        profile.edge(connector(model(IDFDataFlow.class, "Yêu cầu đặt phòng"), a, b, 110));
    assertEquals(1, named.labels.size());
    assertEquals(ElkLayout.Slot.CENTER, named.labels.get(0).slot);
    assertTrue(named.labels.get(0).w >= 110);
    assertTrue(profile.edge(connector(model(IDFDataFlow.class, ""), a, b, 0)).labels.isEmpty());
  }

  @Test
  public void aDfdLetsOppositeFlowsKeepRoomForTheirLabels() {
    assertTrue(new DfdLayoutProfile().laneGap() >= 24);
  }

  // --- ERD, object, package

  @Test
  public void anErdLaysTablesLeftToRightAndLabelsNamedRelationships() {
    ErdLayoutProfile profile = new ErdLayoutProfile();
    assertEquals(ElkLayout.Dir.RIGHT, profile.options(null).direction);
    IShapeUIModel parent = shape(model(IDBTable.class, "tblPhong"), 0, 0, 150, 90);
    IShapeUIModel child = shape(model(IDBTable.class, "tblPhieu"), 0, 0, 150, 90);
    assertNotNull(profile.node(parent));
    LayoutProfile.EdgeSpec fk =
        profile.edge(connector(model(IDBForeignKey.class, "thuộc"), parent, child, 40));
    assertEquals(1, fk.labels.size());
    assertFalse(fk.reverse);
  }

  @Test
  public void objectLinksKeepTheirDirectionAndNames() {
    ObjectLayoutProfile profile = new ObjectLayoutProfile();
    IShapeUIModel a = shape(model(IInstanceSpecification.class, "kh"), 0, 0, 140, 60);
    IShapeUIModel b = shape(model(IInstanceSpecification.class, "hd"), 0, 0, 140, 60);
    LayoutProfile.EdgeSpec link = profile.edge(connector(model(ILink.class, "lập"), a, b, 30));
    assertEquals(1, link.labels.size());
    assertFalse(link.reverse);
    LayoutProfile.EdgeSpec note = profile.edge(connector(mock(IAnchor.class), a, b, 0));
    assertTrue("a note stands next to what it explains", note.reverse);
  }

  @Test
  public void packagesAreContainersWithATitleBand() {
    PackageLayoutProfile profile = new PackageLayoutProfile();
    IShapeUIModel pkg = shape(model(IPackage.class, "r5service"), 0, 0, 160, 94);
    LayoutProfile.NodeSpec spec = profile.node(pkg);
    assertTrue(
        "room for the name in the tab", spec.minSize[0] > TextMetrics.width("r5service", true));
    assertTrue(profile.headerHeight(pkg) >= 40);
    IShapeUIModel cls = shape(model(IClass.class, "Phong"), 0, 0, 80, 40);
    assertNull(profile.node(cls).minSize);
    LayoutProfile.EdgeSpec use =
        profile.edge(connector(model(IDependency.class, ""), pkg, pkg, 74));
    assertEquals("the <<use>> keyword is a label", 1, use.labels.size());
  }

  @Test
  public void aNoteStandsInTheContainerOfTheShapeItExplains() {
    PackageLayoutProfile profile = new PackageLayoutProfile();
    IShapeUIModel frame = shape(model(IPackage.class, "view"), 0, 0, 300, 200);
    IShapeUIModel target = shape(model(IClass.class, "Form"), 20, 60, 80, 40);
    when(target.getParent()).thenReturn(frame);
    IShapeUIModel note = shape(mock(INOTE.class), 150, 60, 100, 40);
    IConnectorUIModel anchor = connector(mock(IAnchor.class), note, target, 0);
    when(note.toFromConnectorArray()).thenReturn(new IConnectorUIModel[] {anchor});
    assertSame(frame, profile.container(note));
    assertSame(frame, profile.container(target));
    assertNull(profile.container(frame));
  }

  // --- component

  private static IShapeUIModel component(String name, int x, int y) {
    return shape(model(IComponent.class, name), x, y, 130, 40);
  }

  private static IShapeUIModel port(String name, IShapeUIModel owner, int x, int y) {
    IShapeUIModel p = shape(model(IPort.class, name), x, y, 12, 12);
    when(p.getParent()).thenReturn(owner);
    return p;
  }

  @Test
  public void aPortIsAttachedToItsComponentAndLeavesTheComponentsLayout() {
    ComponentLayoutProfile profile = new ComponentLayoutProfile();
    IShapeUIModel owner = component("API Gateway", 100, 200);
    IShapeUIModel p = port("p80", owner, 224, 214);
    when(owner.toChildArray()).thenReturn(new IShapeUIModel[] {p});
    assertSame(owner, profile.attachedTo(p));
    assertNull(profile.attachedTo(owner));
    assertNull("a port is no child of the layered layout", profile.container(p));
    assertTrue(profile.routesAll());
    LayoutProfile.NodeSpec ownerSpec = profile.node(owner);
    assertNotNull("room for the names beside and above the ports", ownerSpec.pad);
    assertTrue(ownerSpec.pad[0] > TextMetrics.width("p80", false));
    assertNotNull(profile.node(p).outsideLabel);
  }

  @Test
  public void aComponentThatHoldsComponentsIsSizedAroundThemWithoutAPad() {
    ComponentLayoutProfile profile = new ComponentLayoutProfile();
    IShapeUIModel outer = component("Hệ thống", 100, 200);
    IShapeUIModel inner = component("Booking", 120, 240);
    IShapeUIModel p = port("p80", outer, 224, 214);
    when(outer.toChildArray()).thenReturn(new IShapeUIModel[] {p, inner});
    assertNull(profile.node(outer).pad);
    assertTrue(profile.headerHeight(outer) > 0);
  }

  @Test
  public void thePortNameHangsAboveTheLineOutsideTheComponent() {
    ComponentLayoutProfile profile = new ComponentLayoutProfile();
    IShapeUIModel owner = component("API Gateway", 100, 200);
    IShapeUIModel east = port("p80", owner, 224, 214); // centre on the east border (230)
    int[] label = profile.hanging(east);
    assertTrue("right of the square", label[0] >= 224 + 12);
    assertTrue("above the line that leaves the square", label[1] + label[3] <= 214);
    IShapeUIModel west = port("pw", owner, 94, 214);
    int[] left = profile.hanging(west);
    assertTrue("left of the square", left[0] + left[2] <= 94);
    assertNull(profile.hanging(owner));
    profile.placedAttached(east, 1);
    verify(east.getCaptionUIModel()).setBounds(label[0], label[1], label[2], label[3]);
  }

  @Test
  public void aComponentDependencyCarriesItsKeywordAsALabel() {
    ComponentLayoutProfile profile = new ComponentLayoutProfile();
    IShapeUIModel a = component("A", 0, 0);
    IShapeUIModel b = component("B", 0, 200);
    assertEquals(1, profile.edge(connector(model(IDependency.class, ""), a, b, 74)).labels.size());
    assertTrue(profile.edge(connector(model(IDependency.class, ""), a, b, 0)).labels.isEmpty());
  }

  // --- deployment

  @Test
  public void aNodeIsAContainerWhoseTitleAndAskedSizeAreItsLeastSize() {
    Map<String, int[]> asked = new HashMap<>();
    DeploymentLayoutProfile profile = new DeploymentLayoutProfile(asked);
    INode node = model(INode.class, "Máy chủ ứng dụng");
    IShapeUIModel shape = shape(node, 0, 0, 200, 100);
    int[] title = DeploymentLayoutProfile.titleSize(shape);
    assertTrue(title[0] > TextMetrics.width("Máy chủ ứng dụng", true));
    assertEquals(title[0], profile.node(shape).minSize[0]);
    asked.put(shape.getId(), new int[] {500, 300});
    LayoutProfile.NodeSpec spec = profile.node(shape);
    assertEquals(500, spec.minSize[0]);
    assertEquals(300, spec.minSize[1]);
    assertEquals(ElkLayout.Dir.RIGHT, profile.options(null).direction);
    assertEquals(DeploymentLayoutProfile.HEADER, profile.headerHeight(shape));
    assertFalse(profile.routesAll());
  }

  @Test
  public void anArtifactTitleHasRoomForItsIcon() {
    IShapeUIModel artifact = shape(model(IArtifact.class, "karaoke.war"), 0, 0, 130, 50);
    int[] title = DeploymentLayoutProfile.titleSize(artifact);
    assertTrue(title[0] > TextMetrics.width("karaoke.war", true) + 30);
    IShapeUIModel other = shape(model(IClass.class, "X"), 0, 0, 10, 10);
    assertEquals(0, DeploymentLayoutProfile.titleSize(other)[0]);
  }

  @Test
  public void aNodeTitleIsMadeAsWideAsTheNodeAndSitsAtTheTopOfAContainer() {
    IShapeUIModel parent = shape(model(INode.class, "Server"), 0, 0, 300, 200);
    IShapeUIModel child = shape(model(IArtifact.class, "a.war"), 20, 60, 100, 40);
    when(parent.toChildArray()).thenReturn(new IShapeUIModel[] {child});
    ICaptionUIModel caption = parent.getCaptionUIModel();
    when(caption.getHeight()).thenReturn(32);
    DeploymentLayoutProfile.syncCaption(parent);
    verify(caption).setBounds(0, 8, 300, 32);
  }
}
