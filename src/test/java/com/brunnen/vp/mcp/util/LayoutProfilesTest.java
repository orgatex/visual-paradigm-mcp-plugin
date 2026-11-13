package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivityPartitionHeaderUIModel;
import com.vp.plugin.diagram.shape.IBaseCommunicationMessageUIModel;
import com.vp.plugin.model.IActivityAction;
import com.vp.plugin.model.IActivityFinalNode;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IDecisionNode;
import com.vp.plugin.model.IFinalState2;
import com.vp.plugin.model.IFlowchartDecision;
import com.vp.plugin.model.IFlowchartFlowline;
import com.vp.plugin.model.IFlowchartProcess;
import com.vp.plugin.model.IForkNode;
import com.vp.plugin.model.IInitialNode;
import com.vp.plugin.model.IInitialPseudoState;
import com.vp.plugin.model.IInteractionActor;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionLifeLineLink;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IState2;
import com.vp.plugin.model.ITransition2;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** The behavioral layout profiles against mocked Visual Paradigm shapes and connectors. */
public class LayoutProfilesTest {

  private static int ids;

  private static IShapeUIModel shape(IModelElement model, int x, int y, int w, int h) {
    IShapeUIModel s = mock(IShapeUIModel.class);
    when(s.getId()).thenReturn("s" + ids++);
    when(s.getModelElement()).thenReturn(model);
    when(s.getX()).thenReturn(x);
    when(s.getY()).thenReturn(y);
    when(s.getWidth()).thenReturn(w);
    when(s.getHeight()).thenReturn(h);
    ICaptionUIModel caption = mock(ICaptionUIModel.class);
    when(s.getCaptionUIModel()).thenReturn(caption);
    return s;
  }

  private static <T extends IModelElement> T model(Class<T> type, String name) {
    T m = mock(type);
    when(m.getName()).thenReturn(name);
    return m;
  }

  private static IConnectorUIModel connector(
      IModelElement model, IShapeUIModel from, IShapeUIModel to) {
    IConnectorUIModel c = mock(IConnectorUIModel.class);
    when(c.getId()).thenReturn("c" + ids++);
    when(c.getModelElement()).thenReturn(model);
    when(c.getFromShape()).thenReturn(from);
    when(c.getToShape()).thenReturn(to);
    when(c.getCaptionUIModel()).thenReturn(mock(ICaptionUIModel.class));
    return c;
  }

  // --- state machine

  @Test
  public void stateProfileStartsAtTheTopEndsAtTheBottomAndHangsTheNamesRight() {
    StateLayoutProfile profile = new StateLayoutProfile();
    IShapeUIModel start = shape(model(IInitialPseudoState.class, "Start"), 100, 40, 30, 30);
    IShapeUIModel end = shape(model(IFinalState2.class, "End"), 100, 400, 30, 30);
    LayoutProfile.NodeSpec first = profile.node(start);
    LayoutProfile.NodeSpec last = profile.node(end);
    assertEquals(ElkLayout.FIRST_LAYER, first.layer);
    assertEquals(ElkLayout.LAST_LAYER, last.layer);
    assertEquals(ShapeOutline.Kind.ELLIPSE, first.outline);
    assertEquals(ElkLayout.Side.RIGHT, first.labelSide);
    assertTrue(profile.routesAll());
    int[] name = profile.hanging(start);
    assertEquals(100 + 30 + 6, name[0]);
    assertEquals(40 + 15 - name[3] / 2, name[1]);
    assertNull(profile.hanging(shape(model(IState2.class, "Trống"), 0, 0, 100, 30)));
  }

  @Test
  public void stateWithASelfTransitionGetsRoomForTheLoopAndItsLabel() {
    StateLayoutProfile profile = new StateLayoutProfile();
    IShapeUIModel state = shape(model(IState2.class, "Đang sử dụng"), 0, 0, 100, 30);
    ITransition2 loop = model(ITransition2.class, "gia hạn giờ hát");
    IConnectorUIModel c = connector(loop, state, state);
    when(state.toFromConnectorArray()).thenReturn(new IConnectorUIModel[] {c});
    LayoutProfile.NodeSpec spec = profile.node(state);
    assertNotNull(spec.outsideLabel);
    assertEquals(ElkLayout.Side.RIGHT, spec.labelSide);
    assertTrue(spec.outsideLabel[0] > TextMetrics.width("gia hạn giờ hát", false));
    IShapeUIModel plain = shape(model(IState2.class, "Trống"), 0, 0, 100, 30);
    assertNull(profile.node(plain).outsideLabel);
  }

  @Test
  public void aNamedTransitionCarriesItsLabelBesideTheMiddle() {
    StateLayoutProfile profile = new StateLayoutProfile();
    IShapeUIModel a = shape(model(IState2.class, "A"), 0, 0, 100, 30);
    IShapeUIModel b = shape(model(IState2.class, "B"), 0, 100, 100, 30);
    LayoutProfile.EdgeSpec named =
        profile.edge(connector(model(ITransition2.class, "khách đặt phòng"), a, b));
    assertEquals(1, named.labels.size());
    assertEquals(ElkLayout.Slot.CENTER, named.labels.get(0).slot);
    LayoutProfile.EdgeSpec plain = profile.edge(connector(model(ITransition2.class, ""), a, b));
    assertTrue(plain.labels.isEmpty());
  }

  // --- flowchart

  @Test
  public void flowchartDecisionsAreDiamondsWithASideForEveryBranch() {
    FlowchartLayoutProfile profile = new FlowchartLayoutProfile();
    LayoutProfile.NodeSpec decision =
        profile.node(shape(model(IFlowchartDecision.class, "Có khuyến mãi?"), 0, 0, 120, 60));
    assertEquals(ShapeOutline.Kind.DIAMOND, decision.outline);
    assertTrue(decision.ownSides);
    LayoutProfile.NodeSpec process =
        profile.node(shape(model(IFlowchartProcess.class, "Tính tiền"), 0, 0, 120, 40));
    assertEquals(ShapeOutline.Kind.RECT, process.outline);
    assertFalse(process.ownSides);
    assertTrue(profile.routesAll());
  }

  @Test
  public void flowchartFlowLabelsYesAndNo() {
    FlowchartLayoutProfile profile = new FlowchartLayoutProfile();
    IShapeUIModel a = shape(model(IFlowchartDecision.class, "?"), 0, 0, 100, 60);
    IShapeUIModel b = shape(model(IFlowchartProcess.class, "P"), 0, 100, 100, 40);
    assertEquals(
        1, profile.edge(connector(model(IFlowchartFlowline.class, "Yes"), a, b)).labels.size());
    assertTrue(profile.edge(connector(model(IFlowchartFlowline.class, ""), a, b)).labels.isEmpty());
  }

  // --- communication

  private static IShapeUIModel lifeline(String name, String role, int x, int y) {
    IInteractionLifeLine m = model(IInteractionLifeLine.class, name);
    when(m.hasStereotype(role)).thenReturn(true);
    return shape(m, x, y, 46, 40);
  }

  @Test
  public void communicationPutsEveryRoleInARowOfItsOwn() {
    CommunicationLayoutProfile profile = new CommunicationLayoutProfile();
    IShapeUIModel actor = shape(model(IInteractionActor.class, "Sinh viên"), 0, 0, 30, 60);
    IShapeUIModel boundary = lifeline("GDChinhSV", "boundary", 0, 0);
    IShapeUIModel boundary2 = lifeline("GDDangki", "boundary", 0, 0);
    IShapeUIModel entity = lifeline("Khoa", "entity", 0, 0);
    IDiagramUIModel diagram = mock(IDiagramUIModel.class);
    when(diagram.toShapeUIModelArray())
        .thenReturn(new IShapeUIModel[] {actor, boundary, boundary2, entity});
    when(diagram.toConnectorUIModelArray()).thenReturn(new IConnectorUIModel[0]);
    profile.beforeLayout(diagram);
    assertEquals(0, profile.node(actor).row);
    assertEquals(1, profile.node(boundary).row);
    assertEquals(1, profile.node(boundary2).row);
    assertEquals("controls are not used: the entity row follows", 2, profile.node(entity).row);
    // a link inside a row is only routed; a link back to the actor points down for the layout
    IInteractionLifeLineLink link = mock(IInteractionLifeLineLink.class);
    assertTrue(profile.edge(connector(link, boundary, boundary2)).deferred);
    LayoutProfile.EdgeSpec back = profile.edge(connector(link, boundary, actor));
    assertTrue(back.reverse);
    assertFalse(back.deferred);
  }

  private static IBaseCommunicationMessageUIModel message(String name, String seq) {
    IMessage m = model(IMessage.class, name);
    when(m.getSequenceNumber()).thenReturn(seq);
    IBaseCommunicationMessageUIModel s = mock(IBaseCommunicationMessageUIModel.class);
    when(s.getModelElement()).thenReturn(m);
    when(s.getCaptionUIModel()).thenReturn(mock(ICaptionUIModel.class));
    return s;
  }

  @Test
  public void everyMessageOfALinkIsALabelThatNeedsRoomForItsArrow() {
    CommunicationLayoutProfile profile = new CommunicationLayoutProfile();
    IShapeUIModel a = lifeline("A", "boundary", 0, 0);
    IShapeUIModel b = lifeline("B", "entity", 0, 0);
    IInteractionLifeLineLink linkModel = mock(IInteractionLifeLineLink.class);
    IConnectorUIModel link = connector(linkModel, a, b);
    IShapeUIModel m1 = message("goi", "1");
    IShapeUIModel m2 = message("tra ve", "2");
    when(link.toChildArray()).thenReturn(new IShapeUIModel[] {m1, m2});
    LayoutProfile.EdgeSpec spec = profile.edge(link);
    assertEquals(2, spec.labels.size());
    LayoutProfile.LabelSpec first = spec.labels.get(0);
    assertTrue(first.h >= CommunicationLayoutProfile.ARROW);
    assertTrue(first.verticalW > CommunicationLayoutProfile.ARROW);
  }

  @Test
  public void aMessageArrowStandsBesideAHorizontalLegWithItsTextBeyond() {
    CommunicationLayoutProfile profile = new CommunicationLayoutProfile();
    IShapeUIModel a = lifeline("A", "boundary", 0, 0);
    IShapeUIModel b = lifeline("B", "entity", 0, 0);
    IConnectorUIModel link = connector(mock(IInteractionLifeLineLink.class), a, b);
    IBaseCommunicationMessageUIModel m = message("goi", "1");
    when(link.toChildArray()).thenReturn(new IShapeUIModel[] {m});
    when(link.getPoints()).thenReturn(new Point[] {new Point(50, 100), new Point(350, 100)});
    // a rectangle below the line, 100 wide and 40 high
    profile.applyLabel(link, "m0", new int[] {120, 103, 100, 40});
    ArgumentCaptor<Integer> x = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> y = ArgumentCaptor.forClass(Integer.class);
    verify(m)
        .setBounds(
            x.capture(),
            y.capture(),
            org.mockito.ArgumentMatchers.eq(CommunicationLayoutProfile.MESSAGE_BOX),
            org.mockito.ArgumentMatchers.eq(CommunicationLayoutProfile.MESSAGE_BOX));
    int half = CommunicationLayoutProfile.MESSAGE_BOX / 2;
    assertEquals("arrow centred over the rectangle", 170, x.getValue() + half);
    assertEquals(
        "arrow next to the line", 103 + CommunicationLayoutProfile.ARROW / 2, y.getValue() + half);
    ICaptionUIModel caption = m.getCaptionUIModel();
    ArgumentCaptor<Integer> textY = ArgumentCaptor.forClass(Integer.class);
    verify(caption).setBounds(anyInt(), textY.capture(), anyInt(), anyInt());
    assertTrue("text beyond the arrow", textY.getValue() >= 103 + CommunicationLayoutProfile.ARROW);
  }

  @Test
  public void aMessageBesideAVerticalLegStaysThereEvenWhenAHorizontalLegIsNearerToItsMiddle() {
    CommunicationLayoutProfile profile = new CommunicationLayoutProfile();
    IShapeUIModel a = lifeline("A", "boundary", 0, 0);
    IShapeUIModel b = lifeline("B", "entity", 0, 0);
    IConnectorUIModel link = connector(mock(IInteractionLifeLineLink.class), a, b);
    IBaseCommunicationMessageUIModel m = message("kiemTraPhong()", "1.1");
    when(link.toChildArray()).thenReturn(new IShapeUIModel[] {m});
    // a long vertical leg at x=291 (330..452), then a horizontal one at y=452
    when(link.getPoints())
        .thenReturn(
            new Point[] {
              new Point(291, 330), new Point(291, 452), new Point(205, 452), new Point(205, 468)
            });
    LayoutProfile.EdgeSpec spec = profile.edge(link);
    LayoutProfile.LabelSpec label = spec.labels.get(0);
    // the rectangle the placer gives a label beside the vertical leg: left of the line
    int[] rect = {291 - 3 - label.verticalW, 379, label.verticalW, label.verticalH};
    profile.applyLabel(link, "m0", rect);
    ArgumentCaptor<Integer> x = ArgumentCaptor.forClass(Integer.class);
    verify(m).setBounds(x.capture(), anyInt(), anyInt(), anyInt());
    int arrowCenter = x.getValue() + CommunicationLayoutProfile.MESSAGE_BOX / 2;
    assertEquals(
        "the arrow stands at the line, not in the middle of the label",
        291 - 3 - CommunicationLayoutProfile.ARROW / 2,
        arrowCenter);
  }

  // --- activity

  /** Two lanes of 240 starting at x = 40 that remember what was applied to them. */
  private static final class FakeLanes implements ActivityLayoutProfile.Lanes {
    final Map<String, Integer> laneOf = new HashMap<>();
    int[] xs = {40, 280};
    int[] widths = {240, 240};
    int body;

    @Override
    public int size() {
      return 2;
    }

    @Override
    public int laneOf(IShapeUIModel node) {
      Integer lane = laneOf.get(node.getId());
      return lane == null ? -1 : lane;
    }

    @Override
    public int left() {
      return 40;
    }

    @Override
    public int top() {
      return 40;
    }

    @Override
    public int headerHeight() {
      return 24;
    }

    @Override
    public int minWidth(int lane) {
      return 240;
    }

    @Override
    public int minBodyHeight() {
      return 0;
    }

    @Override
    public void apply(int[] newXs, int[] newWidths, int bodyHeight) {
      xs = newXs;
      widths = newWidths;
      body = bodyHeight;
    }

    @Override
    public int[] xs() {
      return xs;
    }

    @Override
    public int[] widths() {
      return widths;
    }
  }

  @Test
  public void activityControlNodesKeepTheirNotationRoles() {
    ActivityLayoutProfile profile = new ActivityLayoutProfile(null, new HashMap<>());
    LayoutProfile.NodeSpec decision =
        profile.node(shape(model(IDecisionNode.class, "Còn phòng?"), 0, 0, 32, 32));
    assertEquals(ShapeOutline.Kind.DIAMOND, decision.outline);
    assertTrue(decision.ownSides);
    assertEquals(ElkLayout.Side.RIGHT, decision.labelSide);
    LayoutProfile.NodeSpec fork =
        profile.node(shape(model(IForkNode.class, "Fork"), 0, 0, 300, 10));
    assertTrue(fork.bar);
    assertEquals(120, fork.size[0]);
    assertNull("a fork shows no name", fork.outsideLabel);
    assertEquals(
        ElkLayout.FIRST_LAYER,
        profile.node(shape(model(IInitialNode.class, "Bắt đầu"), 0, 0, 24, 24)).layer);
    assertEquals(
        ElkLayout.LAST_LAYER,
        profile.node(shape(model(IActivityFinalNode.class, "Kết thúc"), 0, 0, 26, 26)).layer);
    LayoutProfile.NodeSpec action =
        profile.node(shape(model(IActivityAction.class, "Nhập"), 0, 0, 100, 40));
    assertNull(action.outsideLabel);
    assertTrue(profile.routesAll());
  }

  @Test
  public void activityLaneShapesAreNoNodesAndGuardsAreLabels() {
    ActivityLayoutProfile profile = new ActivityLayoutProfile(null, new HashMap<>());
    IActivityPartitionHeaderUIModel header = mock(IActivityPartitionHeaderUIModel.class);
    IModelElement laneModel = model(IModelElement.class, "Khách hàng");
    when(header.getModelElement()).thenReturn(laneModel);
    assertNull(profile.node(header));
    IShapeUIModel a = shape(model(IDecisionNode.class, "?"), 0, 0, 32, 32);
    IShapeUIModel b = shape(model(IActivityAction.class, "B"), 0, 100, 100, 40);
    IControlFlow flow = model(IControlFlow.class, "");
    when(flow.getGuard()).thenReturn("còn phòng");
    LayoutProfile.EdgeSpec spec = profile.edge(connector(flow, a, b));
    assertEquals(1, spec.labels.size());
    assertEquals(ElkLayout.Slot.TAIL, spec.labels.get(0).slot);
    IControlFlow plain = model(IControlFlow.class, "");
    assertTrue(profile.edge(connector(plain, a, b)).labels.isEmpty());
  }

  @Test
  public void activityNodesStandInTheColumnOfTheirLane() {
    FakeLanes lanes = new FakeLanes();
    ActivityLayoutProfile profile = new ActivityLayoutProfile(lanes, new HashMap<>());
    IShapeUIModel n1 = shape(model(IActivityAction.class, "n1"), 300, 100, 100, 40);
    IShapeUIModel n2 = shape(model(IActivityAction.class, "n2"), 100, 200, 100, 40);
    IShapeUIModel n3 = shape(model(IActivityAction.class, "n3"), 20, 200, 80, 40);
    lanes.laneOf.put(n1.getId(), 0);
    lanes.laneOf.put(n2.getId(), 1);
    lanes.laneOf.put(n3.getId(), 1);
    Map<String, IShapeUIModel> shapes = new LinkedHashMap<>();
    Map<String, int[]> rects = new LinkedHashMap<>();
    for (IShapeUIModel s : new IShapeUIModel[] {n1, n2, n3}) {
      shapes.put(s.getId(), s);
      rects.put(s.getId(), new int[] {s.getX(), s.getY(), s.getWidth(), s.getHeight()});
    }
    profile.arrange(shapes, rects);
    int[] r1 = rects.get(n1.getId());
    int[] r2 = rects.get(n2.getId());
    int[] r3 = rects.get(n3.getId());
    assertEquals("centred in lane 0", lanes.xs[0] + lanes.widths[0] / 2, r1[0] + r1[2] / 2);
    assertTrue("lane 1 right of lane 0", lanes.xs[1] >= lanes.xs[0] + lanes.widths[0]);
    assertTrue("n3 left of n2 as in the layout", r3[0] + r3[2] <= r2[0]);
    assertTrue(r2[0] >= lanes.xs[1] && r2[0] + r2[2] <= lanes.xs[1] + lanes.widths[1]);
    assertTrue(r3[0] >= lanes.xs[1]);
    assertEquals("same row stays on one line", r2[1], r3[1]);
    assertTrue("rows start below the headers", r1[1] >= 40 + 24);
    assertTrue("the body holds the lowest node", 40 + 24 + lanes.body >= r2[1] + r2[3]);
    List<Integer> widths = new ArrayList<>();
    for (int w : lanes.widths) {
      widths.add(w);
    }
    assertTrue("a row of two nodes widens its lane: " + widths, lanes.widths[1] >= 80 + 100 + 40);
  }

  @Test
  public void aFinalStateWithManyTransitionsGrowsSoEachHasAPort() {
    StateLayoutProfile profile = new StateLayoutProfile();
    IShapeUIModel end = shape(model(IFinalState2.class, "End"), 100, 400, 30, 30);
    List<IConnectorUIModel> incoming = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      IShapeUIModel from = shape(model(IState2.class, "S" + i), 0, i * 50, 100, 30);
      incoming.add(connector(model(ITransition2.class, ""), from, end));
    }
    when(end.toToConnectorArray()).thenReturn(incoming.toArray(new IConnectorUIModel[0]));
    LayoutProfile.NodeSpec grown = profile.node(end);
    assertNotNull(grown.size);
    assertTrue("room for two ports on a side: " + grown.size[0], grown.size[0] >= 56);
    IShapeUIModel quiet = shape(model(IFinalState2.class, "End2"), 100, 400, 30, 30);
    assertNull("a few transitions fit the notation size", profile.node(quiet).size);
  }

  @Test
  public void aFrameHoldsWhatLiesInsideItWhenTheToolsDidNotMakeIt() {
    ActivityLayoutProfile profile = new ActivityLayoutProfile(null, new HashMap<>());
    IShapeUIModel frame =
        shape(model(com.vp.plugin.model.IActivity.class, "Xử lí tại gdX"), 0, 0, 300, 300);
    IShapeUIModel inside = shape(model(IActivityAction.class, "in"), 50, 50, 100, 40);
    IShapeUIModel outside = shape(model(IActivityAction.class, "out"), 400, 50, 100, 40);
    IDiagramUIModel diagram = mock(IDiagramUIModel.class);
    when(diagram.toShapeUIModelArray()).thenReturn(new IShapeUIModel[] {frame, inside, outside});
    when(frame.getDiagramUIModel()).thenReturn(diagram);
    when(inside.getDiagramUIModel()).thenReturn(diagram);
    when(outside.getDiagramUIModel()).thenReturn(diagram);
    assertEquals(frame.getId(), profile.container(inside).getId());
    assertNull(profile.container(outside));
    assertNull("a frame is not inside itself", profile.container(frame));
    // a frame the tools made keeps the nodes it was made for, whatever lies inside it
    Map<String, java.util.Set<String>> made = new HashMap<>();
    made.put(
        frame.getId(), new java.util.HashSet<>(java.util.Collections.singleton(outside.getId())));
    ActivityLayoutProfile known = new ActivityLayoutProfile(null, made);
    assertEquals(frame.getId(), known.container(outside).getId());
    assertNull(known.container(inside));
  }
}
