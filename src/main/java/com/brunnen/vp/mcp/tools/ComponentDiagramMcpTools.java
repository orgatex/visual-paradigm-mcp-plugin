package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ComponentDiagramUtils;
import com.brunnen.vp.mcp.util.ComponentDiagramUtils.Kind;
import com.brunnen.vp.mcp.util.ComponentLayoutProfile;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IComponentDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IComponentInterfaceUIModel;
import com.vp.plugin.diagram.shape.IComponentUIModel;
import com.vp.plugin.diagram.shape.IPortUIModel;
import com.vp.plugin.model.IComponent;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IJavaClassCodeDetail;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IPort;
import com.vp.plugin.model.IRealization;
import com.vp.plugin.model.IUsage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm UML component diagrams.
 *
 * <p>Models the UML 2 component elements: {@link IComponent} for components (optionally nested in a
 * parent component or a layer {@link IPackage} frame, and stereotyped, e.g. {@code <<subsystem>>}),
 * interfaces drawn as {@code <<interface>>} boxes, {@link IPort} for ports on a component's border,
 * and the connectors between them: {@link IRealization} for a provided interface (dashed line,
 * hollow triangle at the interface), {@link IUsage} for a required interface and {@link
 * IDependency} for a plain dependency between components.
 *
 * <p>Visual Paradigm has no separate interface model on component diagrams: an interface is an
 * {@link IComponent} with the {@code interface} keyword drawn as a plain box. VP's lollipop shape
 * ({@code ComponentInterface}) is not rendered when it is created through the API.
 *
 * <p>Shapes are fitted to their text (VP fit-size); frames and components that hold nested
 * components are containers sized around their contents. The diagram is laid out by the shared
 * layered layout ({@link ComponentLayoutProfile}): shapes placed with x/y stay where they are,
 * every connector is a horizontal or vertical line, a port stands on the border of its component on
 * the side its lines lead away to.
 */
public class ComponentDiagramMcpTools extends AbstractDiagramMcpTools {

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return diagram instanceof IComponentDiagramUIModel ? new ComponentLayoutProfile() : null;
  }

  /**
   * Keyword of an interface. Lower case: with VP's own "Interface" stereotype VP would pick its
   * ComponentInterface (lollipop) shape, which it does not render when created through the API.
   */
  private static final String INTERFACE_STEREOTYPE = "interface";

  /** Keyword of the dependency drawn when VP cannot draw a usage on a component diagram. */
  private static final String USE_STEREOTYPE = "use";

  /** Default and largest believable side length of a port square after fit-size. */
  private static final int PORT_SIZE = 12;

  private static final int PORT_MAX = 30;

  @Tool(
      name = "createComponentDiagram",
      description =
          "Create a new UML component diagram in Visual Paradigm. Shapes added to it are fitted to"
              + " their text")
  public String createComponentDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMPONENT_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            dm.openDiagram(diagram);
            return "Created component diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating component diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponent",
      description =
          "Add a component to a component diagram. stereotype is optional (\"\" = none, e.g."
              + " \"subsystem\"; several may be comma-separated; <<component>> is drawn by Visual"
              + " Paradigm itself and ignored; a component that already exists in the project is"
              + " reused and keeps its own stereotypes). parentComponent (a component"
              + " already on the diagram, \"\" = none) nests the new component inside it, in the"
              + " model and on the diagram, and the parent is sized around it. packageName (a"
              + " layer frame added with addComponentPackage, \"\" = none) puts it in that"
              + " package frame instead. x/y (>0) place the component explicitly in diagram"
              + " coordinates and keep it there (the lines are routed around it), otherwise the"
              + " layout decides: components in layers by their dependencies (the client above"
              + " its supplier), inside their frame. The box is fitted to its text")
  public String addComponent(
      String diagramName,
      String componentName,
      String stereotype,
      String parentComponent,
      int x,
      int y,
      String packageName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(componentName)) {
              return "Component name must not be empty";
            }
            String name = componentName.trim();
            if (findShape(diagram, name, Kind.COMPONENT, Kind.INTERFACE) != null) {
              return "Component or interface already on diagram: " + name;
            }

            IShapeUIModel parent = null;
            if (ComponentDiagramUtils.isValidName(packageName)) {
              if (ComponentDiagramUtils.isValidName(parentComponent)) {
                return "Give parentComponent or packageName, not both";
              }
              parent = findShape(diagram, packageName.trim(), Kind.PACKAGE);
              if (parent == null) {
                return "Package not on diagram: " + packageName.trim();
              }
            }
            if (ComponentDiagramUtils.isValidName(parentComponent)) {
              String parentName = parentComponent.trim();
              parent = resolve(diagram, parentName, Kind.COMPONENT);
              if (parent == null) {
                return "Parent component not on diagram: " + parentName;
              }
              String kindError =
                  ComponentDiagramUtils.checkKind(kindOf(parent), Kind.COMPONENT, parentName);
              if (kindError != null) {
                return kindError;
              }
            }

            List<String> stereotypes = ComponentDiagramUtils.parseStereotypes(stereotype);
            // A component of that name in the same owner (the project root, the parent component
            // or the package) is the same component (a new view of it); a new nested one is
            // created inside its owner before it is named.
            IModelElement owner = parent != null ? parent.getModelElement() : null;
            IComponent component = ownedComponent(owner, name);
            if (component != null && isInterfaceModel(component)) {
              return "'"
                  + name
                  + "' is an interface of the project; pick another name for the component";
            }
            // the model is shared by every diagram that shows it: a stereotype given for a view of
            // an existing component would show up on all of them, so only a new one gets it
            boolean reused = component != null;
            if (component == null) {
              component = getModelElementFactory().createComponent();
              nameInOwner(component, owner, name);
              for (String s : stereotypes) {
                component.addStereotype(s);
              }
            }

            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, component);
            if (!(de instanceof IShapeUIModel)) {
              if (!reused) {
                component.delete(); // no orphan model when VP draws nothing
              }
              return "Error adding component: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            if (parent != null) {
              parent.addChild(shape);
            }
            showCaption(shape);

            if (parent != null) {
              nestChild(parent, shape, x, y);
            } else {
              placeNew(diagram, shape, x, y);
            }
            relayout(diagram);

            StringBuilder result = new StringBuilder("Added component '").append(name).append("'");
            if (reused) {
              result.append(" (existing component of the project, drawn again; its stereotypes");
              result.append(" are those of the model)");
            } else if (!stereotypes.isEmpty()) {
              result.append(' ').append(ComponentDiagramUtils.formatStereotypes(stereotypes));
            }
            if (parent != null) {
              result.append(" inside '").append(parent.getModelElement().getName()).append("'");
            }
            return result
                .append(" to diagram '")
                .append(diagramName)
                .append("' at ")
                .append(bounds(shape))
                .toString();
          });
    } catch (Exception e) {
      return "Error adding component: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponentInterface",
      description =
          "Add an interface to a component diagram, drawn as a box with the <<interface>>"
              + " keyword (Visual Paradigm does not render its lollipop shape when it is created"
              + " through the API). An interface of that name elsewhere in the project is reused."
              + " Connect it to components with addProvidedInterface (realization, hollow"
              + " triangle at the interface) / addRequiredInterface (<<use>> dependency). x/y"
              + " (>0) place it explicitly, otherwise the layout puts it between the component"
              + " that requires it and the one that provides it")
  public String addComponentInterface(String diagramName, String interfaceName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(interfaceName)) {
              return "Interface name must not be empty";
            }
            String name = interfaceName.trim();
            if (findShape(diagram, name, Kind.COMPONENT, Kind.INTERFACE) != null) {
              return "Component or interface already on diagram: " + name;
            }

            IComponent iface = findRootElement(name, IComponent.class);
            if (iface != null && !isInterfaceModel(iface)) {
              return "'"
                  + name
                  + "' is a component of the project; pick another name for the interface";
            }
            if (iface == null) {
              iface = getModelElementFactory().createComponent();
              nameInOwner(iface, null, name);
            }
            if (!iface.hasStereotype(INTERFACE_STEREOTYPE)) {
              iface.addStereotype(INTERFACE_STEREOTYPE);
            }
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, iface);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding interface: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            if (shape instanceof IComponentUIModel) {
              // a plain classifier box: no <<component>> keyword or component icon
              ((IComponentUIModel) shape)
                  .setShowOption(IComponentUIModel.SHOW_COMPONENT_OPTION_NONE);
            }
            showCaption(shape);
            placeNew(diagram, shape, x, y);
            relayout(diagram);

            return "Added interface '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + bounds(shape)
                + " (<<interface>> box)";
          });
    } catch (Exception e) {
      return "Error adding interface: " + e.getMessage();
    }
  }

  @Tool(
      name = "addProvidedInterface",
      description =
          "Show that a component provides (realizes) an interface: a dashed realization with"
              + " the hollow triangle at the <<interface>> box. componentName may also be a port"
              + " added with addPort. Both must already be on the diagram")
  public String addProvidedInterface(
      String diagramName, String componentName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel[] ends = resolveInterfaceLink(diagram, componentName, interfaceName);
            if (ends == null) {
              return interfaceLinkError(diagram, componentName, interfaceName);
            }
            IShapeUIModel source = ends[0];
            IShapeUIModel target = ends[1];

            IRealization realization = getModelElementFactory().createRealization();
            // VP stores a realization supplier -> client (proven on class diagrams): the
            // interface is the "from" end, so the realization ends on the interface.
            realization.setFrom(target.getModelElement());
            realization.setTo(source.getModelElement());
            IDiagramElement connector = connectAndLayout(diagram, realization, target, source);
            if (connector == null) {
              realization.delete();
              return "Error adding provided interface: Visual Paradigm could not draw the"
                  + " realization";
            }

            return "Added provided interface: '"
                + componentName.trim()
                + "' provides '"
                + interfaceName.trim()
                + "'";
          });
    } catch (Exception e) {
      return "Error adding provided interface: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRequiredInterface",
      description =
          "Show that a component requires (uses) an interface: a dashed <<use>> arrow from the"
              + " component to the <<interface>> box. componentName may also be a port added with"
              + " addPort. Both must already be on the diagram")
  public String addRequiredInterface(
      String diagramName, String componentName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel[] ends = resolveInterfaceLink(diagram, componentName, interfaceName);
            if (ends == null) {
              return interfaceLinkError(diagram, componentName, interfaceName);
            }
            IShapeUIModel source = ends[0];
            IShapeUIModel target = ends[1];

            String drawnAs = "usage";
            IUsage usage = getModelElementFactory().createUsage();
            usage.setFrom(source.getModelElement());
            usage.setTo(target.getModelElement());
            IDiagramElement connector = connectAndLayout(diagram, usage, source, target);
            if (connector == null) {
              // Component diagrams do not list the Usage connector type; a dependency stereotyped
              // <<use>> is the same UML relationship.
              usage.delete();
              IDependency dependency = getModelElementFactory().createDependency();
              dependency.setFrom(source.getModelElement());
              dependency.setTo(target.getModelElement());
              dependency.addStereotype(USE_STEREOTYPE);
              connector = connectAndLayout(diagram, dependency, source, target);
              if (connector == null) {
                dependency.delete();
                return "Error adding required interface: Visual Paradigm could not draw the"
                    + " usage";
              }
              drawnAs = "<<use>> dependency";
            }

            return "Added required interface: '"
                + componentName.trim()
                + "' requires '"
                + interfaceName.trim()
                + "' ("
                + drawnAs
                + ")";
          });
    } catch (Exception e) {
      return "Error adding required interface: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponentDependency",
      description =
          "Add a dependency (dashed arrow) between two components, interfaces or ports of a"
              + " component diagram. stereotype is optional (\"\" = none, e.g. \"use\", \"call\";"
              + " several may be comma-separated) and shown as <<stereotype>> on the arrow. Every"
              + " line is horizontal or vertical; the diagram is laid out again after each"
              + " dependency (autoLayoutDiagram does it on demand)")
  public String addComponentDependency(
      String diagramName, String fromComponent, String toComponent, String stereotype) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(fromComponent)
                || !ComponentDiagramUtils.isValidName(toComponent)) {
              return "fromComponent and toComponent are required";
            }
            String fromName = fromComponent.trim();
            String toName = toComponent.trim();
            IShapeUIModel from =
                resolve(diagram, fromName, Kind.COMPONENT, Kind.INTERFACE, Kind.PORT);
            if (from == null) {
              return "Element not on diagram: " + fromName;
            }
            IShapeUIModel to = resolve(diagram, toName, Kind.COMPONENT, Kind.INTERFACE, Kind.PORT);
            if (to == null) {
              return "Element not on diagram: " + toName;
            }
            String error = ComponentDiagramUtils.checkDependencyEnd(kindOf(from), fromName);
            if (error == null) {
              error = ComponentDiagramUtils.checkDependencyEnd(kindOf(to), toName);
            }
            if (error != null) {
              return error;
            }
            if (sameShape(from, to)) {
              return "fromComponent and toComponent must be different elements";
            }
            if (isOwnPort(from, to) || isOwnPort(to, from)) {
              return "A port belongs to its component: a dependency between '"
                  + fromName
                  + "' and '"
                  + toName
                  + "' would run from the component to itself (a port connects to the outside, or"
                  + " to a component nested in its owner)";
            }

            List<String> stereotypes = ComponentDiagramUtils.parseStereotypes(stereotype);
            IDependency dependency = getModelElementFactory().createDependency();
            dependency.setFrom(from.getModelElement());
            dependency.setTo(to.getModelElement());
            for (String s : stereotypes) {
              dependency.addStereotype(s);
            }
            IDiagramElement connector = connectAndLayout(diagram, dependency, from, to);
            if (connector == null) {
              dependency.delete();
              return "Error adding dependency: Visual Paradigm could not draw the dependency";
            }

            String label = ComponentDiagramUtils.formatStereotypes(stereotypes);
            return "Added dependency from '"
                + fromName
                + "' to '"
                + toName
                + "'"
                + (label.isEmpty() ? "" : " " + label);
          });
    } catch (Exception e) {
      return "Error adding dependency: " + e.getMessage();
    }
  }

  @Tool(
      name = "addPort",
      description =
          "Add a port (small square on the border) to a component of a component diagram. The"
              + " layout puts a port on the side of its component its lines lead away to (its"
              + " name outside), and the lines leave it outwards. A port can provide or require"
              + " interfaces (addProvidedInterface / addRequiredInterface with the port name)")
  public String addPort(String diagramName, String componentName, String portName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(componentName)) {
              return "Component name must not be empty";
            }
            if (!ComponentDiagramUtils.isValidName(portName)) {
              return "Port name must not be empty";
            }
            String ownerName = componentName.trim();
            String name = portName.trim();
            IShapeUIModel owner = resolve(diagram, ownerName, Kind.COMPONENT);
            if (owner == null) {
              return "Component not on diagram: " + ownerName;
            }
            String kindError =
                ComponentDiagramUtils.checkKind(kindOf(owner), Kind.COMPONENT, ownerName);
            if (kindError != null) {
              return kindError;
            }
            if (!(owner.getModelElement() instanceof IComponent)) {
              return "'" + ownerName + "' is not a UML component";
            }
            for (IShapeUIModel existing : portsOf(owner)) {
              if (name.equals(existing.getModelElement().getName())) {
                return "Port already on component '" + ownerName + "': " + name;
              }
            }

            IPort port = getModelElementFactory().createPort();
            port.setName(name);
            ((IComponent) owner.getModelElement()).addPort(port);

            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, port);
            if (!(de instanceof IShapeUIModel)) {
              port.delete(); // no port left on the component when VP draws nothing
              return "Error adding port: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel portShape = (IShapeUIModel) de;
            owner.addChild(portShape);
            showCaption(portShape);
            // The name is drawn next to the square; never let fit-size stretch the square.
            int[] size =
                ComponentDiagramUtils.symbolSize(
                    portShape.getWidth(), portShape.getHeight(), PORT_SIZE, PORT_MAX);
            portShape.setBounds(portShape.getX(), portShape.getY(), size[0], size[1]);
            portShape.bringToFront();
            relayout(diagram);

            return "Added port '"
                + name
                + "' to component '"
                + ownerName
                + "' on diagram '"
                + diagramName
                + "' at "
                + bounds(portShape);
          });
    } catch (Exception e) {
      return "Error adding port: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponentPackage",
      description =
          "Add a package (layer) frame to a component diagram, e.g. \"Presentation Layer\","
              + " \"Data Layer\". Components added with packageName go inside it and the frame"
              + " is sized around them. parentPackage (\"\" = none) nests it in another package"
              + " frame. x/y (>0) place it explicitly and keep it there, otherwise the layout"
              + " stacks the frames by the dependencies between their components (a layer above"
              + " the layers it depends on); width/height (>0) size an explicitly placed frame"
              + " (it still grows around its components). backgroundColor is '#RRGGBB' for a tint of"
              + " its own (\"\" = the house blue, as the components in it). A package of that name"
              + " already in the project (same owner) is reused")
  public String addComponentPackage(
      String diagramName,
      String packageName,
      String parentPackage,
      int x,
      int y,
      int width,
      int height,
      String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(packageName)) {
              return "Package name must not be empty";
            }
            String name = packageName.trim();
            if (findShape(diagram, name, Kind.PACKAGE) != null) {
              return "Package already on diagram: " + name;
            }
            if (backgroundColor != null && !backgroundColor.trim().isEmpty()) {
              try {
                java.awt.Color.decode(backgroundColor.trim());
              } catch (NumberFormatException e) {
                return "Error adding package: backgroundColor must be #RRGGBB, got '"
                    + backgroundColor
                    + "'";
              }
            }
            IShapeUIModel parent = null;
            if (ComponentDiagramUtils.isValidName(parentPackage)) {
              parent = findShape(diagram, parentPackage.trim(), Kind.PACKAGE);
              if (parent == null) {
                return "Parent package not on diagram: " + parentPackage.trim();
              }
            }
            IPackage pkg =
                parent == null
                    ? findRootElement(name, IPackage.class)
                    : ownedPackage((IPackage) parent.getModelElement(), name);
            if (pkg == null) {
              pkg = getModelElementFactory().createPackage();
              nameInOwner(pkg, parent != null ? parent.getModelElement() : null, name);
            }
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, pkg);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding package: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            if (parent != null) {
              parent.addChild(shape);
            }
            if (backgroundColor != null && !backgroundColor.trim().isEmpty()) {
              shape.getFillColor().setColor1(java.awt.Color.decode(backgroundColor.trim()), true);
            }
            showCaption(shape);
            int w = width > 0 ? width : Math.max(PACKAGE_MIN_WIDTH, shape.getWidth());
            int h = height > 0 ? height : Math.max(PACKAGE_MIN_HEIGHT, shape.getHeight());
            shape.setBounds(shape.getX(), shape.getY(), w, h);
            if (width > 0 || height > 0) {
              holdFit(diagram, shape); // the size asked for stays
            }
            if (parent != null) {
              nestChild(parent, shape, x, y);
            } else {
              placeNew(diagram, shape, x, y);
            }
            shape.sendToBack();
            relayout(diagram);
            return "Added package '"
                + name
                + "'"
                + (parent != null ? " inside '" + parentPackage.trim() + "'" : "")
                + " to diagram '"
                + diagramName
                + "' at "
                + bounds(shape);
          });
    } catch (Exception e) {
      return "Error adding package: " + e.getMessage();
    }
  }

  /** Default size of a new package (layer) frame before components are added to it. */
  private static final int PACKAGE_MIN_WIDTH = 200;

  private static final int PACKAGE_MIN_HEIGHT = 100;

  /** The component called {@code name} owned by {@code owner} (the project root when null). */
  private static IComponent ownedComponent(IModelElement owner, String name) {
    if (owner == null) {
      return findRootElement(name, IComponent.class);
    }
    IModelElement[] children = owner.toChildArray();
    if (children != null) {
      for (IModelElement child : children) {
        if (child instanceof IComponent && name.equals(child.getName())) {
          return (IComponent) child;
        }
      }
    }
    return null;
  }

  private static IPackage ownedPackage(IPackage owner, String name) {
    IModelElement[] children = owner.toChildArray();
    if (children != null) {
      for (IModelElement child : children) {
        if (child instanceof IPackage && name.equals(child.getName())) {
          return (IPackage) child;
        }
      }
    }
    return null;
  }

  // --- Lookup helpers ---

  private IComponentDiagramUIModel findComponentDiagram(String diagramName) {
    return (IComponentDiagramUIModel)
        DiagramUtils.findDiagramByName(diagramName, IComponentDiagramUIModel.class);
  }

  /**
   * The source (component or port) and target (interface) shapes of a provided/required interface
   * link, or null when either is missing or of the wrong kind.
   */
  private IShapeUIModel[] resolveInterfaceLink(
      IDiagramUIModel diagram, String componentName, String interfaceName) {
    if (!ComponentDiagramUtils.isValidName(componentName)
        || !ComponentDiagramUtils.isValidName(interfaceName)) {
      return null;
    }
    IShapeUIModel source = resolve(diagram, componentName.trim(), Kind.COMPONENT, Kind.PORT);
    IShapeUIModel target = resolve(diagram, interfaceName.trim(), Kind.INTERFACE);
    if (source == null || target == null) {
      return null;
    }
    String error =
        ComponentDiagramUtils.checkInterfaceLink(
            kindOf(source), componentName.trim(), kindOf(target), interfaceName.trim());
    return error == null ? new IShapeUIModel[] {source, target} : null;
  }

  /** Why {@link #resolveInterfaceLink} returned null. */
  private String interfaceLinkError(
      IDiagramUIModel diagram, String componentName, String interfaceName) {
    if (!ComponentDiagramUtils.isValidName(componentName)
        || !ComponentDiagramUtils.isValidName(interfaceName)) {
      return "componentName and interfaceName are required";
    }
    IShapeUIModel source = resolve(diagram, componentName.trim(), Kind.COMPONENT, Kind.PORT);
    if (source == null) {
      return "Component not on diagram: " + componentName.trim();
    }
    IShapeUIModel target = resolve(diagram, interfaceName.trim(), Kind.INTERFACE);
    if (target == null) {
      return "Interface not on diagram: " + interfaceName.trim();
    }
    return ComponentDiagramUtils.checkInterfaceLink(
        kindOf(source), componentName.trim(), kindOf(target), interfaceName.trim());
  }

  /** Shape named {@code name} on the diagram, preferring the given kinds, else of any kind. */
  private IShapeUIModel resolve(IDiagramUIModel diagram, String name, Kind... preferred) {
    IShapeUIModel shape = findShape(diagram, name, preferred);
    return shape != null ? shape : findShape(diagram, name);
  }

  /**
   * First shape on the diagram (nested ones included) whose model element is named {@code name} and
   * whose kind is one of {@code kinds} (any kind when none are given).
   */
  private IShapeUIModel findShape(IDiagramUIModel diagram, String name, Kind... kinds) {
    if (name == null) {
      return null;
    }
    IShapeUIModel first = null;
    List<String> owners = new ArrayList<>();
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement model = shape.getModelElement();
      if (model != null && name.equals(model.getName()) && hasKind(shape, kinds)) {
        if (first == null) {
          first = shape;
        }
        if (kindOf(shape) == Kind.PORT) {
          IDiagramElement owner = shape.getParent();
          owners.add(
              owner == null || owner.getModelElement() == null
                  ? "?"
                  : owner.getModelElement().getName());
        }
      }
    }
    if (owners.size() > 1) {
      // two components have a port of that name: the handle is Owner.port
      throw new IllegalStateException(
          "'"
              + name
              + "' is the name of the ports of "
              + String.join(", ", owners)
              + ": write Owner."
              + name);
    }
    if (first != null) {
      return first;
    }
    // "Owner.port": the port (or any shape) named after the dot inside the shape named before it
    for (int dot = name.indexOf('.'); dot > 0; dot = name.indexOf('.', dot + 1)) {
      IShapeUIModel owner = findShape(diagram, name.substring(0, dot));
      if (owner == null) {
        continue;
      }
      IShapeUIModel[] children = owner.toChildArray();
      for (IShapeUIModel child : children == null ? new IShapeUIModel[0] : children) {
        IModelElement model = child.getModelElement();
        if (model != null
            && name.substring(dot + 1).equals(model.getName())
            && hasKind(child, kinds)) {
          return child;
        }
      }
    }
    return null;
  }

  private static boolean hasKind(IShapeUIModel shape, Kind... kinds) {
    if (kinds.length == 0) {
      return true;
    }
    Kind kind = kindOf(shape);
    for (Kind k : kinds) {
      if (k == kind) {
        return true;
      }
    }
    return false;
  }

  /** What a shape stands for: by its UI model type, then its shape type and model. */
  private static Kind kindOf(IDiagramElement de) {
    if (de.getModelElement() instanceof IPackage) {
      return Kind.PACKAGE;
    }
    if (de instanceof IComponentInterfaceUIModel) {
      return Kind.INTERFACE;
    }
    if (de instanceof IPortUIModel) {
      return Kind.PORT;
    }
    Kind kind =
        de instanceof IComponentUIModel
            ? Kind.COMPONENT
            : ComponentDiagramUtils.kindOfShapeType(de.getShapeType());
    // An interface VP drew as a plain component box is still an interface.
    if (kind == Kind.COMPONENT && isInterfaceModel(de.getModelElement())) {
      return Kind.INTERFACE;
    }
    return kind;
  }

  private static boolean isInterfaceModel(IModelElement model) {
    if (!(model instanceof IComponent)) {
      return false;
    }
    IComponent component = (IComponent) model;
    String[] stereotypes = component.toStereotypeArray();
    if (stereotypes != null) {
      for (String s : stereotypes) {
        if (INTERFACE_STEREOTYPE.equalsIgnoreCase(s)) {
          return true;
        }
      }
    }
    IJavaClassCodeDetail javaDetail = component.getJavaDetail();
    return javaDetail != null && javaDetail.isJavaInterface();
  }

  /**
   * Every shape on the diagram, nested ones included. The diagram iterator is walked and each
   * shape's children are followed too, in case the iterator only lists top-level shapes.
   */
  private static List<IShapeUIModel> allShapes(IDiagramUIModel diagram) {
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

  private static void collectDescendants(
      IShapeUIModel shape, List<IShapeUIModel> out, Set<String> seen) {
    for (IShapeUIModel child : childrenOf(shape)) {
      if (seen.add(child.getId())) {
        out.add(child);
        collectDescendants(child, out, seen);
      }
    }
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

  /** Port shapes drawn on a component, in creation order. */
  private static List<IShapeUIModel> portsOf(IShapeUIModel component) {
    List<IShapeUIModel> ports = new ArrayList<>();
    for (IShapeUIModel child : childrenOf(component)) {
      if (child.getModelElement() != null && kindOf(child) == Kind.PORT) {
        ports.add(child);
      }
    }
    return ports;
  }

  /** Whether {@code port} is a port of the component {@code owner}. */
  private static boolean isOwnPort(IShapeUIModel port, IShapeUIModel owner) {
    IDiagramElement parent = port.getParent();
    return kindOf(port) == Kind.PORT && parent != null && sameShape(parent, owner);
  }

  private static String bounds(IDiagramElement de) {
    return de.getX() + "," + de.getY() + " " + de.getWidth() + "x" + de.getHeight();
  }

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IDiagramElement a, IDiagramElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  // --- Rendering helpers ---

  /**
   * Render a fresh shape's caption and fit the shape to it. VP draws new shapes blank until the
   * caption is reset, and the fit has to follow so it measures the rendered text.
   */
  private void showCaption(IShapeUIModel shape) {
    shape.resetCaption();
    fitShape(shape);
  }
}
