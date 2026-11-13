package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.NodeKind;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.PathLabel;
import com.brunnen.vp.mcp.util.DeploymentLayoutProfile;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDeploymentDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IArtifact;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IComponent;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IDeployment;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.INode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm UML deployment diagrams.
 *
 * <p>Models the UML 2 deployment elements: {@link INode} for nodes, devices ({@code <<device>>})
 * and execution environments ({@code <<executionEnvironment>>}), {@link IArtifact} for deployable
 * artifacts, {@link IComponent} for components hosted by a node, {@link IAssociation} for the
 * communication paths between nodes and {@link IDeployment} (stereotyped {@code <<deploy>>}) for
 * the artifact-to-node deployment arrow.
 *
 * <p>Nesting is real on both levels: a nested element is owned by its node in the model ({@code
 * INode.addNode/addArtifact/addComponent}) and drawn as a child shape inside the node's shape. Leaf
 * shapes are fitted to their caption; container nodes are sized around their children. The diagram
 * is laid out by the shared layered layout ({@link DeploymentLayoutProfile}): nodes from the left
 * to the right by their communication paths, what a node holds inside it, every line horizontal or
 * vertical; shapes placed with x/y stay where they are.
 */
public class DeploymentDiagramMcpTools extends AbstractDiagramMcpTools {

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return diagram instanceof IDeploymentDiagramUIModel
        ? new DeploymentLayoutProfile(minimumSize)
        : null;
  }

  @Override
  protected int[] minimumSize(IShapeUIModel container) {
    return minimumSize.get(container.getId());
  }

  /** Width and height asked for with addDeploymentNode, by shape id: nodes never get smaller. */
  private final java.util.Map<String, int[]> minimumSize = new java.util.HashMap<>();

  /** Default size of a node box before fit-size. */
  private static final int NODE_WIDTH = 140;

  private static final int NODE_HEIGHT = 80;

  /** Default size of an artifact or component box before fit-size. */
  private static final int LEAF_WIDTH = 130;

  private static final int LEAF_HEIGHT = 50;

  @Tool(
      name = "createDeploymentDiagram",
      description = "Create a new UML deployment diagram in Visual Paradigm")
  public String createDeploymentDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!DeploymentDiagramUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_DEPLOYMENT_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            // Communication path protocols (HTTPS, JDBC) are connector names: always show them.
            diagram.setShowConnectorName(IDiagramUIModel.SHOW_CONNECTOR_NAME_YES);
            dm.openDiagram(diagram);
            return "Created deployment diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating deployment diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeploymentNode",
      description =
          "Add a node to a deployment diagram. kind: node | device (<<device>>) |"
              + " executionEnvironment (<<executionEnvironment>>); \"\" means node. parentNode"
              + " (\"\" = top level) nests it inside a node already on the diagram, e.g. Tomcat"
              + " (executionEnvironment) inside AppServer (device). x/y (>0, diagram coordinates)"
              + " place it explicitly and keep it there, otherwise the layout decides (nodes from"
              + " left to right by their communication paths, nested ones inside the parent)."
              + " width/height (>0) are minimum sizes, otherwise the box is fitted to its caption; a"
              + " node is sized around what is added into it and never smaller than the size"
              + " asked for. A node of that name already in the project is drawn"
              + " again with the kind it has. Node names must be unique on the diagram")
  public String addDeploymentNode(
      String diagramName,
      String nodeName,
      String kind,
      String parentNode,
      int x,
      int y,
      int width,
      int height) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!DeploymentDiagramUtils.isValidName(nodeName)) {
              return "Node name must not be empty";
            }
            String name = nodeName.trim();
            NodeKind nodeKind = DeploymentDiagramUtils.parseNodeKind(kind);
            if (nodeKind == null) {
              return "Unknown node kind '" + kind + "' (use node, device or executionEnvironment)";
            }
            if (findShape(diagram, name, INode.class) != null) {
              return "Node already on diagram: " + name;
            }
            String parentName = DeploymentDiagramUtils.normalizeOptional(parentNode);
            IShapeUIModel parentShape = null;
            if (parentName != null) {
              parentShape = findShape(diagram, parentName, INode.class);
              if (parentShape == null) {
                return "Parent node not on diagram: " + parentName;
              }
            }

            // The same machine drawn on two diagrams (overview and detail) is one node of the
            // project: a node of that name in the same owner is reused (a new view of it).
            INode node = ownedNode(parentShape, name);
            boolean reused = node != null;
            if (node == null) {
              node = getModelElementFactory().createNode();
              node.setName(name);
              if (nodeKind.getStereotype() != null) {
                node.addStereotype(nodeKind.getStereotype());
              }
              if (parentShape != null) {
                ((INode) parentShape.getModelElement()).addNode(node);
              }
            }
            Placement placed =
                placeShape(
                    diagram, node, parentShape, x, y, width, height, NODE_WIDTH, NODE_HEIGHT);
            if (width > 0 && height > 0) {
              minimumSize.put(placed.shape.getId(), new int[] {width, height});
            }

            return "Added "
                + (reused
                    ? "existing node of the project (drawn again; its kind and stereotypes are"
                        + " those of the model, not '"
                        + (kind == null || kind.trim().isEmpty() ? "node" : kind.trim())
                        + "')"
                    : describeNode(nodeKind))
                + " '"
                + name
                + "'"
                + (parentShape != null ? " in '" + parentName + "'" : "")
                + placed.describe();
          });
    } catch (Exception e) {
      return "Error adding deployment node: " + e.getMessage();
    }
  }

  /** Whether a <<deploy>> arrow (deployment or dependency) already joins the artifact and node. */
  private static boolean deployArrowExists(IShapeUIModel artifact, IShapeUIModel node) {
    IConnectorUIModel[] outgoing = artifact.toFromConnectorArray();
    for (IConnectorUIModel c : outgoing == null ? new IConnectorUIModel[0] : outgoing) {
      IModelElement m = c.getModelElement();
      if (c.getToShape() != null
          && sameShape(c.getToShape(), node)
          && (m instanceof IDeployment || m instanceof IDependency)
          && m.hasStereotype("deploy")) {
        return true;
      }
    }
    return false;
  }

  /** The node called {@code name} owned by the node on {@code parent} (project root if null). */
  private static INode ownedNode(IShapeUIModel parent, String name) {
    if (parent == null) {
      return findRootElement(name, INode.class);
    }
    INode[] nested = ((INode) parent.getModelElement()).toNodeArray();
    for (INode n : nested == null ? new INode[0] : nested) {
      if (name.equals(n.getName())) {
        return n;
      }
    }
    return null;
  }

  /** The artifact called {@code name} owned by {@code node}, or null. */
  private static IArtifact ownedArtifact(INode node, String name) {
    IArtifact[] owned = node.toArtifactArray();
    for (IArtifact a : owned == null ? new IArtifact[0] : owned) {
      if (name.equals(a.getName())) {
        return a;
      }
    }
    return null;
  }

  /** The component called {@code name} owned by {@code node}, or null. */
  private static IComponent ownedComponent(INode node, String name) {
    IComponent[] owned = node.toComponentArray();
    for (IComponent c : owned == null ? new IComponent[0] : owned) {
      if (name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  @Tool(
      name = "addDeploymentArtifact",
      description =
          "Add an artifact (e.g. app.war, schema.sql) to a deployment diagram. nodeName \"\""
              + " draws it free-standing (connect it with addDeployDependency); otherwise it is"
              + " deployed into that node (owned by the node and drawn inside it), or drawn inside"
              + " that artifact when nodeName names an artifact. x/y (>0, diagram coordinates)"
              + " place it explicitly, otherwise it is auto-placed. The box is fitted to its"
              + " caption. Artifact names must be unique on the diagram")
  public String addDeploymentArtifact(
      String diagramName, String artifactName, String nodeName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!DeploymentDiagramUtils.isValidName(artifactName)) {
              return "Artifact name must not be empty";
            }
            String name = artifactName.trim();
            if (findShape(diagram, name, IArtifact.class) != null) {
              return "Artifact already on diagram: " + name;
            }
            String hostName = DeploymentDiagramUtils.normalizeOptional(nodeName);
            IShapeUIModel hostShape = null;
            if (hostName != null) {
              hostShape = findHost(diagram, hostName);
              if (hostShape == null) {
                return "Node or artifact not on diagram: " + hostName;
              }
            }

            // Deployed into its node before it is named (named at the project root first, VP
            // would rename it when the root already has an artifact of that name); a free
            // artifact of a taken name is the same artifact, shown again.
            boolean inNode = hostShape != null && hostShape.getModelElement() instanceof INode;
            // the same artifact drawn on a second diagram (overview and detail) is one element of
            // the project: the one the node already owns, or the free one of that name, is reused
            IArtifact artifact =
                inNode
                    ? ownedArtifact((INode) hostShape.getModelElement(), name)
                    : findRootElement(name, IArtifact.class);
            boolean reused = artifact != null;
            if (artifact == null) {
              artifact = getModelElementFactory().createArtifact();
              if (inNode) {
                ((INode) hostShape.getModelElement()).addArtifact(artifact);
              }
              nameInOwner(artifact, null, name);
            }
            Placement placed =
                placeShape(diagram, artifact, hostShape, x, y, 0, 0, LEAF_WIDTH, LEAF_HEIGHT);

            return "Added "
                + (reused ? "existing artifact of the project (drawn again) '" : "artifact '")
                + name
                + "'"
                + (hostShape != null ? " in '" + hostName + "'" : "")
                + placed.describe();
          });
    } catch (Exception e) {
      return "Error adding deployment artifact: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeploymentComponent",
      description =
          "Add a component hosted by a node of a deployment diagram (e.g. OrderService inside"
              + " Tomcat): owned by the node and drawn inside it, auto-placed and fitted to its"
              + " caption. nodeName may also name an artifact (View/Controller/Model inside"
              + " app.war), which then encloses the component. nodeName \"\" draws it"
              + " free-standing. Components of one host go in a row (left to right in the order"
              + " added, 90 px apart, room for the label of the arrow between them) and the host"
              + " grows to hold them; x/y (>0, diagram coordinates) place one explicitly."
              + " Component names must be unique on the diagram")
  public String addDeploymentComponent(
      String diagramName, String componentName, String nodeName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!DeploymentDiagramUtils.isValidName(componentName)) {
              return "Component name must not be empty";
            }
            String name = componentName.trim();
            if (findShape(diagram, name, IComponent.class) != null) {
              return "Component already on diagram: " + name;
            }
            String hostName = DeploymentDiagramUtils.normalizeOptional(nodeName);
            IShapeUIModel hostShape = null;
            if (hostName != null) {
              hostShape = findHost(diagram, hostName);
              if (hostShape == null) {
                return "Node or artifact not on diagram: " + hostName;
              }
            }

            boolean inNode = hostShape != null && hostShape.getModelElement() instanceof INode;
            IComponent component =
                inNode
                    ? ownedComponent((INode) hostShape.getModelElement(), name)
                    : findRootElement(name, IComponent.class);
            boolean reused = component != null;
            if (component == null) {
              component = getModelElementFactory().createComponent();
              if (inNode) {
                ((INode) hostShape.getModelElement()).addComponent(component);
              }
              nameInOwner(component, null, name);
            }
            Placement placed =
                placeShape(diagram, component, hostShape, x, y, 0, 0, LEAF_WIDTH, LEAF_HEIGHT);

            return "Added "
                + (reused ? "existing component of the project (drawn again) '" : "component '")
                + name
                + "'"
                + (hostShape != null ? " in '" + hostName + "'" : "")
                + placed.describe();
          });
    } catch (Exception e) {
      return "Error adding deployment component: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCommunicationPath",
      description =
          "Add a communication path (solid line) between two nodes of a deployment diagram."
              + " label is the protocol shown on the line, e.g. \"HTTPS\" or \"JDBC\"; write"
              + " \"<<TCP/IP>>\" to show it as a stereotype instead; \"\" leaves it unlabelled")
  public String addCommunicationPath(
      String diagramName, String fromNode, String toNode, String label) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel from = findShape(diagram, trim(fromNode), INode.class);
            if (from == null) {
              return "Node not on diagram: " + fromNode;
            }
            IShapeUIModel to = findShape(diagram, trim(toNode), INode.class);
            if (to == null) {
              return "Node not on diagram: " + toNode;
            }
            if (sameShape(from, to)) {
              return "A communication path needs two different nodes";
            }

            PathLabel parsed = DeploymentDiagramUtils.parsePathLabel(label);
            IAssociation path = getModelElementFactory().createAssociation();
            path.setFrom(from.getModelElement());
            path.setTo(to.getModelElement());
            if (parsed.getName() != null) {
              path.setName(parsed.getName());
            }
            if (parsed.getStereotype() != null) {
              path.addStereotype(parsed.getStereotype());
            }
            IDiagramElement connector = connectAndLayout(diagram, path, from, to);
            if (connector == null) {
              path.delete();
              return "Visual Paradigm refused the communication path '"
                  + fromNode
                  + "' -> '"
                  + toNode
                  + "'";
            }

            String text = labelText(parsed);
            return "Added communication path '"
                + from.getModelElement().getName()
                + "' -- '"
                + to.getModelElement().getName()
                + "'"
                + (text.isEmpty() ? "" : " [" + text + "]");
          });
    } catch (Exception e) {
      return "Error adding communication path: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeployDependency",
      description =
          "Add a <<deploy>> dependency (dashed arrow) from an artifact to the node it is deployed"
              + " on. Use it for free-standing artifacts; an artifact drawn inside its node already"
              + " shows the deployment")
  public String addDeployDependency(String diagramName, String artifactName, String nodeName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel artifactShape = findShape(diagram, trim(artifactName), IArtifact.class);
            if (artifactShape == null) {
              return "Artifact not on diagram: " + artifactName;
            }
            IShapeUIModel nodeShape = findShape(diagram, trim(nodeName), INode.class);
            if (nodeShape == null) {
              return "Node not on diagram: " + nodeName;
            }
            if (isAncestor(nodeShape, artifactShape)) {
              return "Artifact '"
                  + artifactName.trim()
                  + "' is drawn inside node '"
                  + nodeName.trim()
                  + "', which already shows the deployment; add the artifact with nodeName \"\""
                  + " to draw a <<deploy>> arrow instead";
            }

            if (deployArrowExists(artifactShape, nodeShape)) {
              return "A <<deploy>> arrow from '"
                  + artifactName.trim()
                  + "' to '"
                  + nodeName.trim()
                  + "' already exists on diagram '"
                  + diagramName
                  + "'";
            }
            IModelElement artifact = artifactShape.getModelElement();
            IModelElement node = nodeShape.getModelElement();
            // UML Deployment, drawn as the dashed arrow artifact -> node. VP draws relationship
            // keywords only from stereotypes (a usage without one is a bare dashed arrow), so the
            // <<deploy>> stereotype is added explicitly.
            IDeployment deployment = getModelElementFactory().createDeployment();
            IDiagramElement connector;
            try {
              deployment.setFrom(artifact);
              deployment.setTo(node);
              deployment.addStereotype("deploy");
              connector = connectAndLayout(diagram, deployment, artifactShape, nodeShape);
            } catch (Exception e) {
              // VP rejects some artifact -> node pairs for a Deployment ("Invalid connection").
              connector = null;
            }
            String modelType = "deployment";
            if (connector == null) {
              // Fall back to a plain <<deploy>> dependency if VP will not draw a Deployment here.
              deployment.delete();
              IDependency dependency = getModelElementFactory().createDependency();
              dependency.setFrom(artifact);
              dependency.setTo(node);
              dependency.addStereotype("deploy");
              connector = connectAndLayout(diagram, dependency, artifactShape, nodeShape);
              if (connector == null) {
                dependency.delete();
                return "Visual Paradigm refused the <<deploy>> arrow '"
                    + artifactName
                    + "' -> '"
                    + nodeName
                    + "'";
              }
              modelType = "dependency";
            }

            return "Added <<deploy>> "
                + modelType
                + " '"
                + artifact.getName()
                + "' -> '"
                + node.getName()
                + "'";
          });
    } catch (Exception e) {
      return "Error adding deploy dependency: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeploymentDependency",
      description =
          "Add a dependency (dashed open arrow) between two elements of a deployment diagram:"
              + " components, artifacts or nodes, e.g. View -> Controller labelled \"submit\"."
              + " label is shown on the arrow; \"<<manifest>>\" style text becomes a stereotype;"
              + " \"\" leaves it unlabelled. Use addDeployDependency for artifact -> node"
              + " deployments")
  public String addDeploymentDependency(
      String diagramName, String fromElement, String toElement, String label) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel from = findAnyShape(diagram, trim(fromElement));
            if (from == null) {
              return "Element not on diagram: " + fromElement;
            }
            IShapeUIModel to = findAnyShape(diagram, trim(toElement));
            if (to == null) {
              return "Element not on diagram: " + toElement;
            }
            if (sameShape(from, to)) {
              return "A dependency needs two different elements";
            }
            PathLabel parsed = DeploymentDiagramUtils.parsePathLabel(label);
            IDependency dependency = getModelElementFactory().createDependency();
            dependency.setFrom(from.getModelElement());
            dependency.setTo(to.getModelElement());
            if (parsed.getName() != null) {
              dependency.setName(parsed.getName());
            }
            if (parsed.getStereotype() != null) {
              dependency.addStereotype(parsed.getStereotype());
            }
            IDiagramElement drawn = connectAndLayout(diagram, dependency, from, to);
            if (drawn == null) {
              dependency.delete();
              return "Visual Paradigm refused the dependency '"
                  + fromElement
                  + "' -> '"
                  + toElement
                  + "'";
            }
            String text = labelText(parsed);
            return "Added dependency '"
                + from.getModelElement().getName()
                + "' -> '"
                + to.getModelElement().getName()
                + "'"
                + (text.isEmpty() ? "" : " [" + text + "]");
          });
    } catch (Exception e) {
      return "Error adding deployment dependency: " + e.getMessage();
    }
  }

  // --- Placement ---

  /** Where a new shape ended up, for the tool result. */
  private static final class Placement {
    private final IShapeUIModel shape;
    private final boolean nested;
    private final boolean nestingRefused;

    Placement(IShapeUIModel shape, boolean nested, boolean nestingRefused) {
      this.shape = shape;
      this.nested = nested;
      this.nestingRefused = nestingRefused;
    }

    String describe() {
      StringBuilder sb = new StringBuilder();
      sb.append(" at ")
          .append(shape.getX())
          .append(',')
          .append(shape.getY())
          .append(' ')
          .append(shape.getWidth())
          .append('x')
          .append(shape.getHeight());
      if (nested && nestingRefused) {
        sb.append(" (Visual Paradigm refused the child shape; it is drawn over the node)");
      }
      return sb.toString();
    }
  }

  /**
   * Create the shape of a deployment element, fit it to its caption, put it in its container (or at
   * x/y) and lay the diagram out.
   */
  private Placement placeShape(
      IDiagramUIModel diagram,
      IModelElement model,
      IShapeUIModel parent,
      int x,
      int y,
      int width,
      int height,
      int defaultWidth,
      int defaultHeight) {
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, model);
    if (!(de instanceof IShapeUIModel)) {
      model.delete();
      throw new IllegalStateException(
          "Visual Paradigm did not create a shape for '" + model.getName() + "'");
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    shape.setBounds(shape.getX(), shape.getY(), defaultWidth, defaultHeight);
    // Fresh shapes render blank until the caption is reset; fit afterwards so the size follows the
    // rendered name and keyword.
    shape.resetCaption();
    boolean flat = model instanceof INode;
    if (flat) {
      DeploymentLayoutProfile.flatten(shape);
    }
    if (width <= 0 || height <= 0) {
      if (flat) {
        int[] title = DeploymentLayoutProfile.titleSize(shape);
        shape.setBounds(shape.getX(), shape.getY(), title[0], title[1]);
      } else {
        fitShape(shape);
      }
    }
    int w = DeploymentDiagramUtils.resolveSize(width, shape.getWidth(), defaultWidth);
    int h = DeploymentDiagramUtils.resolveSize(height, shape.getHeight(), defaultHeight);
    shape.setBounds(shape.getX(), shape.getY(), w, h);
    keepSize(shape);
    if (parent != null) {
      nestChild(parent, shape, x, y);
    } else {
      placeNew(diagram, shape, x, y);
    }
    if (width > 0 || height > 0) {
      holdFit(diagram, shape); // the size asked for stays
    }
    relayout(diagram);
    return new Placement(shape, parent != null, parent != null && shape.getParent() == null);
  }

  // --- Lookup ---

  private IDiagramUIModel findDeploymentDiagram(String diagramName) {
    IDiagramUIModel diagram =
        DiagramUtils.findDiagramByName(diagramName, IDeploymentDiagramUIModel.class);
    if (diagram != null) {
      return diagram;
    }
    IDiagramUIModel any = DiagramUtils.findDiagramByName(diagramName);
    if (any != null
        && IDiagramTypeConstants.DIAGRAM_TYPE_DEPLOYMENT_DIAGRAM.equals(any.getType())) {
      return any;
    }
    return null;
  }

  /** Diagram-scoped lookup of a shape whose model element has the given type and name. */
  private IShapeUIModel findShape(
      IDiagramUIModel diagram, String name, Class<? extends IModelElement> type) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement model = shape.getModelElement();
      if (type.isInstance(model) && name.equals(model.getName())) {
        return shape;
      }
    }
    return null;
  }

  /** A node, or else an artifact, called {@code name} on the diagram. */
  private IShapeUIModel findHost(IDiagramUIModel diagram, String name) {
    IShapeUIModel node = findShape(diagram, name, INode.class);
    return node != null ? node : findShape(diagram, name, IArtifact.class);
  }

  /** A component, artifact or node called {@code name} on the diagram. */
  private IShapeUIModel findAnyShape(IDiagramUIModel diagram, String name) {
    IShapeUIModel shape = findShape(diagram, name, IComponent.class);
    if (shape == null) {
      shape = findShape(diagram, name, IArtifact.class);
    }
    return shape != null ? shape : findShape(diagram, name, INode.class);
  }

  /**
   * Every shape on the diagram, nested ones included. The diagram iterator is walked and each
   * shape's children are followed too, in case the iterator only lists top-level shapes.
   */
  private List<IShapeUIModel> allShapes(IDiagramUIModel diagram) {
    List<IShapeUIModel> shapes = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IShapeUIModel) {
        IShapeUIModel shape = (IShapeUIModel) obj;
        if (seen.add(shape.getId())) {
          shapes.add(shape);
        }
        collectDescendants(shape, shapes, seen);
      }
    }
    return shapes;
  }

  private static List<IShapeUIModel> childrenOf(IShapeUIModel shape) {
    List<IShapeUIModel> result = new ArrayList<>();
    IShapeUIModel[] children = shape.toChildArray();
    if (children != null) {
      for (IShapeUIModel child : children) {
        if (child != null) {
          result.add(child);
        }
      }
    }
    return result;
  }

  private static void collectDescendants(
      IShapeUIModel shape, List<IShapeUIModel> out, Set<String> seen) {
    for (IShapeUIModel child : childrenOf(shape)) {
      if (seen.add(child.getId())) {
        out.add(child);
        collectDescendants(child, out, seen);
      }
    }
  }

  private static IShapeUIModel parentShapeOf(IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    return parent instanceof IShapeUIModel ? (IShapeUIModel) parent : null;
  }

  private static boolean isAncestor(IShapeUIModel ancestor, IShapeUIModel shape) {
    Set<String> seen = new HashSet<>();
    IShapeUIModel current = parentShapeOf(shape);
    while (current != null && seen.add(current.getId())) {
      if (sameShape(current, ancestor)) {
        return true;
      }
      current = parentShapeOf(current);
    }
    return false;
  }

  // --- Small helpers ---

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IDiagramElement a, IDiagramElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  private static String describeNode(NodeKind kind) {
    String keyword = DeploymentDiagramUtils.keyword(kind.getStereotype());
    return keyword.isEmpty() ? "node" : keyword + " node";
  }

  private static String labelText(PathLabel label) {
    String keyword = DeploymentDiagramUtils.keyword(label.getStereotype());
    String name = label.getName() == null ? "" : label.getName();
    return (keyword + " " + name).trim();
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
