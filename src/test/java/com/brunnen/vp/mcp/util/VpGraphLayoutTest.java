package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IComponent;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IPort;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** The shared layout against a mocked diagram: where ports, shapes and connectors end up. */
public class VpGraphLayoutTest {

  private static int ids;

  /** A shape that remembers the bounds it was given, as Visual Paradigm does. */
  private static IShapeUIModel shape(IModelElement model, int x, int y, int w, int h) {
    IShapeUIModel s = mock(IShapeUIModel.class);
    int[] box = {x, y, w, h};
    when(s.getId()).thenReturn("s" + ids++);
    when(s.getModelElement()).thenReturn(model);
    when(s.getX()).thenAnswer(i -> box[0]);
    when(s.getY()).thenAnswer(i -> box[1]);
    when(s.getWidth()).thenAnswer(i -> box[2]);
    when(s.getHeight()).thenAnswer(i -> box[3]);
    org.mockito.Mockito.doAnswer(
            i -> {
              for (int k = 0; k < 4; k++) {
                box[k] = i.getArgument(k);
              }
              return null;
            })
        .when(s)
        .setBounds(anyInt(), anyInt(), anyInt(), anyInt());
    ICaptionUIModel caption = mock(ICaptionUIModel.class);
    when(s.getCaptionUIModel()).thenReturn(caption);
    return s;
  }

  private static <T extends IModelElement> T model(Class<T> type, String name) {
    T m = mock(type);
    when(m.getName()).thenReturn(name);
    return m;
  }

  private static IConnectorUIModel connector(IShapeUIModel from, IShapeUIModel to, String keyword) {
    IConnectorUIModel c = mock(IConnectorUIModel.class);
    IDependency dependency = model(IDependency.class, "");
    when(c.getId()).thenReturn("c" + ids++);
    when(c.getModelElement()).thenReturn(dependency);
    when(c.getFromShape()).thenReturn(from);
    when(c.getToShape()).thenReturn(to);
    ICaptionUIModel caption = mock(ICaptionUIModel.class);
    when(caption.getWidth()).thenReturn(keyword.isEmpty() ? 0 : 60);
    when(caption.getHeight()).thenReturn(keyword.isEmpty() ? 0 : 14);
    when(c.getCaptionUIModel()).thenReturn(caption);
    return c;
  }

  private static IDiagramUIModel diagram(IShapeUIModel[] shapes, IConnectorUIModel... connectors) {
    IDiagramUIModel d = mock(IDiagramUIModel.class);
    when(d.toShapeUIModelArray()).thenReturn(shapes);
    when(d.toConnectorUIModelArray()).thenReturn(connectors);
    when(d.isAutoFitShapesSize()).thenReturn(true);
    return d;
  }

  private static List<Point> points(IConnectorUIModel c) {
    ArgumentCaptor<Point> captor = ArgumentCaptor.forClass(Point.class);
    verify(c, atLeastOnce()).addPoint(captor.capture());
    return new ArrayList<>(captor.getAllValues());
  }

  private static void assertOrthogonal(List<Point> route) {
    for (int i = 0; i + 1 < route.size(); i++) {
      assertTrue(
          "segment " + route.get(i) + " -> " + route.get(i + 1),
          route.get(i).x == route.get(i + 1).x || route.get(i).y == route.get(i + 1).y);
    }
  }

  @Test
  public void aPortStandsOnTheSideOfItsComponentTheLineLeadsAwayToAndTheLineLeavesOutwards() {
    IShapeUIModel gateway = shape(model(IComponent.class, "API Gateway"), 100, 200, 130, 40);
    IShapeUIModel service = shape(model(IComponent.class, "Booking"), 500, 200, 130, 40);
    IShapeUIModel port = shape(model(IPort.class, "p80"), 224, 214, 12, 12);
    when(port.getParent()).thenReturn(gateway);
    when(gateway.toChildArray()).thenReturn(new IShapeUIModel[] {port});
    IConnectorUIModel link = connector(port, service, "<<use>>");
    IDiagramUIModel diagram = diagram(new IShapeUIModel[] {gateway, service, port}, link);

    VpGraphLayout.route(diagram, new ComponentLayoutProfile());

    // the other end is to the right: the port is on the east border, centred on it
    assertEquals(230 - 6, port.getX());
    assertTrue(port.getY() >= 200 - 6 && port.getY() <= 240 - 6);
    List<Point> route = points(link);
    assertOrthogonal(route);
    assertEquals("leaves the east face of the port", port.getX() + 12, route.get(0).x);
    assertEquals(port.getY() + 6, route.get(0).y, 6);
    verify(diagram).setAutoFitShapesSize(false); // VP would put the port back otherwise
  }

  @Test
  public void aPortToTheNorthKeepsItsLineOffTheComponent() {
    IShapeUIModel gateway = shape(model(IComponent.class, "API Gateway"), 100, 300, 130, 40);
    IShapeUIModel iface = shape(model(IComponent.class, "IPayment"), 100, 60, 130, 40);
    IShapeUIModel port = shape(model(IPort.class, "p80"), 224, 314, 12, 12);
    when(port.getParent()).thenReturn(gateway);
    when(gateway.toChildArray()).thenReturn(new IShapeUIModel[] {port});
    IConnectorUIModel link = connector(port, iface, "<<use>>");
    IDiagramUIModel diagram = diagram(new IShapeUIModel[] {gateway, iface, port}, link);

    VpGraphLayout.route(diagram, new ComponentLayoutProfile());

    assertEquals("on the north border", 300 - 6, port.getY());
    List<Point> route = points(link);
    assertOrthogonal(route);
    assertTrue("goes up first", route.get(1).y < route.get(0).y);
    for (Point p : route) {
      boolean insideGateway = p.x > 100 && p.x < 230 && p.y > 300 && p.y < 340;
      assertTrue("never through its own component: " + p, !insideGateway);
    }
  }

  @Test
  public void aDiagramWithoutContainersOrPortsKeepsItsAutoFit() {
    IShapeUIModel a = shape(model(IComponent.class, "A"), 100, 100, 130, 40);
    IShapeUIModel b = shape(model(IComponent.class, "B"), 100, 300, 130, 40);
    IConnectorUIModel link = connector(a, b, "");
    IDiagramUIModel diagram = diagram(new IShapeUIModel[] {a, b}, link);
    VpGraphLayout.route(diagram, new ComponentLayoutProfile());
    verify(diagram, never()).setAutoFitShapesSize(false);
    assertOrthogonal(points(link));
  }

  @Test
  public void aLayeredLayoutStacksTheLayersByTheirDependenciesAndEveryLineIsOrthogonal() {
    IShapeUIModel presentation = shape(model(IPackage.class, "Presentation"), 0, 0, 100, 50);
    IShapeUIModel data = shape(model(IPackage.class, "Data"), 0, 0, 100, 50);
    IShapeUIModel web = shape(model(IComponent.class, "Web"), 0, 0, 130, 40);
    IShapeUIModel db = shape(model(IComponent.class, "DB"), 0, 0, 130, 40);
    when(web.getParent()).thenReturn(presentation);
    when(db.getParent()).thenReturn(data);
    when(presentation.toChildArray()).thenReturn(new IShapeUIModel[] {web});
    when(data.toChildArray()).thenReturn(new IShapeUIModel[] {db});
    IConnectorUIModel link = connector(web, db, "<<use>>");
    IDiagramUIModel diagram = diagram(new IShapeUIModel[] {presentation, web, data, db}, link);

    VpGraphLayout.layout(diagram, new ComponentLayoutProfile());

    assertTrue("the client layer stands above its supplier", presentation.getY() < data.getY());
    assertTrue(
        "the component stands inside its layer",
        web.getX() >= presentation.getX()
            && web.getX() + web.getWidth() <= presentation.getX() + presentation.getWidth()
            && web.getY() >= presentation.getY()
            && web.getY() + web.getHeight() <= presentation.getY() + presentation.getHeight());
    List<Point> route = points(link);
    assertOrthogonal(route);
    assertEquals(
        "starts on the bottom border of the client", web.getY() + web.getHeight(), route.get(0).y);
    assertEquals(
        "ends on the top border of the supplier", db.getY(), route.get(route.size() - 1).y);
    verify(diagram).setAutoFitShapesSize(false);
  }

  /**
   * The caption of a connector keeps the size Visual Paradigm gives its text, however small an
   * earlier layout left it ("&lt;&lt;use..." came from a caption squeezed to 60 and then 46 px that
   * a later layout read as its size): the layout resets the caption before it reads it.
   */
  @Test
  public void aCaptionSqueezedByAnEarlierLayoutIsLaidOutAtTheSizeVpGivesIt() {
    IShapeUIModel a = shape(model(IComponent.class, "A"), 100, 100, 130, 40);
    IShapeUIModel b = shape(model(IComponent.class, "B"), 100, 300, 130, 40);
    IConnectorUIModel link = connector(a, b, "<<use>>");
    ICaptionUIModel caption = link.getCaptionUIModel();
    int[] box = {0, 0, 46, 14}; // what a former layout left: far less than the text needs
    when(caption.getWidth()).thenAnswer(i -> box[2]);
    when(caption.getHeight()).thenAnswer(i -> box[3]);
    when(caption.isVisible()).thenReturn(true);
    org.mockito.Mockito.doAnswer(
            i -> {
              box[2] = 74; // VP sizes a reset caption to its text plus padding
              box[3] = 14;
              return null;
            })
        .when(link)
        .resetCaption();
    IDiagramUIModel diagram = diagram(new IShapeUIModel[] {a, b}, link);

    VpGraphLayout.route(diagram, new ComponentLayoutProfile());

    ArgumentCaptor<Integer> widths = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> heights = ArgumentCaptor.forClass(Integer.class);
    verify(caption, atLeastOnce())
        .setBounds(anyInt(), anyInt(), widths.capture(), heights.capture());
    int last = widths.getAllValues().size() - 1;
    assertEquals(
        "the label keeps the width of its text", 74, (int) widths.getAllValues().get(last));
    assertEquals(14, (int) heights.getAllValues().get(last));
  }
}
