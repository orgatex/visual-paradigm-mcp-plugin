# PROJECT_STATUS.md

## Current State: Custom MCP Server (Java 11, Undertow)

### Overview
Replaced Spring Boot/Spring AI MCP stack with a custom lightweight MCP server using Undertow HTTP + Jackson. The server runs on Java 11 (compatible with Visual Paradigm's JVM) and implements the MCP JSON-RPC protocol over the streamable HTTP transport.

### Architecture

- **MCP Server**: Custom `McpServer.java` using Undertow embedded HTTP server
- **Transport**: streamable HTTP - POST `/mcp`, one JSON-RPC message per request, the response is
  the JSON body (notifications get 202, GET gets 405: the server never pushes). `initialize` echoes
  the client's protocol version when it is 2025-06-18, 2025-03-26 or 2024-11-05. The legacy SSE
  transport (`/sse` + `/mcp/messages`) is removed; Codex only speaks stdio or streamable HTTP.
- **Plain HTTP API**: GET `/api/tools`, POST `/api/execute` (used by the Docker proxy)
- **Claude Code / Codex plugin**: `.claude-plugin/marketplace.json` (marketplace
  `visual-paradigm-mcp`) lists `plugin/` (plugin `visual-paradigm`): `.claude-plugin/plugin.json`,
  `.codex-plugin/plugin.json`, `.mcp.json` (type http, `http://localhost:2026/mcp`) and the
  English skill `skills/visual-paradigm`. Codex reads the Claude marketplace file; tested with an
  isolated `CODEX_HOME`: marketplace add, plugin add, `codex mcp list` shows the server, and
  `codex exec` sees all tools.
- **Protocol**: MCP JSON-RPC 2.0 (initialize, tools/list, tools/call)
- **Tool Discovery**: Custom `@Tool` annotation + Java reflection (replaces Spring AI)
- **Port**: 2026 (configurable)

### MCP Tool Services (124 tools total)

| Category | Tools | Count |
|----------|-------|-------|
| Management | listDiagrams, getDiagramElements, getDiagramGeometry, autoLayoutDiagram, removeDiagramElement, getElementCounts, addNote | 7 |
| Use Case | create, addActor, addUseCase, addRelationship, addSystemBoundary, generateReport | 6 |
| Class | create, addClass, addAttribute, addOperation, addAssociation, addGeneralization, addAggregation, addComposition, addDependency, addRealization, addInterface, addPackage, setClassColor, generateReport, setElementBounds, addStereotypeToClasses, removeRelationship, setAssociationProperties, getRelationshipDetails, rerouteConnectors, layoutConnectorLabels, exportDiagramImage, addPackageShape, moveIntoPackage, addUsage | 25 |
| Project | newProject, openProject, saveProject, saveProjectAs, getProjectInfo | 5 |
| ERD | create, addTable, addColumn, removeColumn, renameColumn, addForeignKey, removeForeignKey, addTableRelationship, removeTable, setPrimaryKey, syncClassesToErd, generateSql, generateReport | 13 |
| Code generation | generateCode | 1 |
| Sequence | create, addLifeline, addActivation, addMessage, addReturnMessage, addCombinedFragment, generateReport | 7 |
| State machine | createStateDiagram, addState, addInitialState, addFinalState, addTransition | 5 |
| Activity | createActivityDiagram, addActivityPartition, addActivityAction, addActivityControlNode, addActivityObjectNode, addActivityFlow, addActivityFrame | 7 |
| Component | createComponentDiagram, addComponent, addComponentInterface, addProvidedInterface, addRequiredInterface, addComponentDependency, addPort, addComponentPackage | 8 |
| Deployment | createDeploymentDiagram, addDeploymentNode, addDeploymentArtifact, addDeploymentComponent, addCommunicationPath, addDeployDependency, addDeploymentDependency | 7 |
| Package / Object | createPackageDiagram, addPackageElement, addPackageDependency, addClassInPackage, createObjectDiagram, addObject, addObjectLink | 7 |
| Communication | createCommunicationDiagram, addCommunicationLifeline, addCommunicationLink, addCommunicationMessage, moveCommunicationLifeline | 5 |
| Flowchart / DFD | createFlowchart, addFlowchartShape, addFlowchartFlow, createDataFlowDiagram, addDfdProcess, addDfdExternalEntity, addDfdDataStore, addDfdDataFlow | 8 |
| BPMN | createBpmnDiagram, addBpmnPool, addBpmnLane, addBpmnTask, addBpmnEvent, addBpmnGateway, addBpmnDataObject, addBpmnSequenceFlow, addBpmnMessageFlow | 9 |

### Class to ERD, ERD to SQL, code generation (server version 1.33.0.0)

- **`syncClassesToErd(classDiagramName, erdName, primaryKeys)`**: Visual Paradigm's "Synchronize
  to ERD" is not in the plugin API, so `OrmErdPlanner` (pure, unit tested) plans it: only
  `<<ORM Persistable>>` classes become tables; attribute types map to SQL types; the key is the
  `primaryKeys` choice (`Class=a,b; Class2=c`, composite allowed), else `id`/`<class>Id`/
  `ma<Class>`/`<<PK>>`, else an added `ID`. 1-* puts the FK into the many side, 1-1 into the
  optional side, *-* makes a join table (identifying FKs), a generalization an identifying FK
  (table per subclass). The tool creates/reuses tables, links class -> table and attribute ->
  column (`IORMClassDetail.setTableModel`, `IORMAttributeDetail.setColumnModel`), names the FK
  columns after the referenced key (`maKhachHang`, `phongHatId` for `id`) and returns warnings
  (no stereotype, association to a non-persistable class, missing multiplicity - VP reports it as
  "Unspecified" -, untyped/unknown/multi-valued attributes). Re-running is idempotent.
- **Key changes** (`setPrimaryKey`, a re-sync with another key) go through `rekey`: VP adds an FK
  column to every child for a new key column but leaves the old one behind, so the stale columns
  are deleted, the new ones renamed, and identifying children pass the change on.
- **`generateSql(diagramName, database, statements, outputFile)`** (replaces `generateDdl`):
  `DatabaseManager.generate*SQL` throws "Dialect class not found" until the database is set up in
  Tools > DB > Database Configuration, then `SqlGenerator` (pure, unit tested) writes the dialect
  itself: MySQL/MariaDB, PostgreSQL, SQL Server (NVARCHAR), Oracle, SQLite, generic; CREATE in
  dependency order with named PK/FK constraints, DROP/DELETE dependents first, parameterized CRUD.
- **`generateCode(diagramName, language, packageName, outputDir, jpa, accessors, primaryKeys)`**:
  VP's Instant Generator is not in the API and its CLI (`scripts/InstantGenerator.sh`) does not
  start on the bundled JRE 11 (`-XX:MaxPermSize`) and throws NPE headless, so `CodeGenerator`
  (pure, unit tested) writes Java/C#/Python/TypeScript/PHP. `diagramName` is required and takes a
  comma list. JPA (Java, persistable classes): `@Entity`, `@Id` (+ `@IdClass` for a composite
  key), `@ManyToOne`/`@OneToMany(mappedBy)`/`@ManyToMany` + `@JoinTable`, column names equal to
  `syncClassesToErd`'s. Generated Java compiles with javac, Python with py_compile.
- `newProject` answers VP's "Do you want to save the project?" with No (it blocked the EDT).
- Tests: `OrmErdPlannerTest`, `SqlGeneratorTest`, `CodeGeneratorTest`, `ToolResultsTest` (303 total).
  Live check on a karaoke model (7 classes, 1-*, *-*, generalization, a non-persistable class).

### Reference-style rendering (matches the course's Visual Paradigm samples)

- **Blue fill (#7AD2FF)** is applied automatically to actors, use cases, lifelines and
  activation bars (use-case and sequence diagrams) via `applyConventionalFill`. Class boxes and
  ERD tables keep Visual Paradigm's default white — matching the reference exports in
  `exports/services/screenshots` and `exports/account/screenshots`.
- **`addSystemBoundary(diagramName, systemName)`** wraps all use cases of a UC diagram in a
  labeled system rectangle (the module box). Call it AFTER `autoLayoutDiagram` so the box encloses
  the laid-out use cases; it computes the use-case bounding box, reparents the use cases into an
  `ISystem`, and sends the rectangle to back. Actors stay outside the box.

### Layout overhaul, step "core" (server version 1.32.5.x)

The user rejected the round-4 PNGs (diagonal/zigzag lines, 3-D nodes, overlaps). Hard rules:
A every connector segment is exactly horizontal or vertical, B no 3-D shapes, C no overlaps
(shapes, lines on lines, lines through shapes, labels). Measured with
`scratchpad/layout_check.py` (SLANT, OVERLAP, THROUGH, SHARED, LABEL defects; CROSS and LABELLINE
metrics; it now also reads the multiplicity rectangles that `getDiagramGeometry` prints as
`mult x,y wxh`).

**Design (one pipeline, `util/` + `AbstractDiagramMcpTools`)**
- ELK 0.8.1 is inside VP (`lib02.jar`). pom: system-scope dependency like openapi, excluded from the
  assembly (VP provides it; proved live with a 3-node probe: the plugin sees it through the app
  class loader). Java 8 bytecode, runs fine on VP's JRE 11 and on JDK 26 for the JUnit tests.
- `ElkLayout` (pure): layered, ORTHOGONAL, INCLUDE_CHILDREN containers (the options must be set on
  every container node too, otherwise the layer gap inside a container shrinks to ~20 px), fixed
  node sizes, head/tail labels, FIRST/LAST layer constraints, actor names as outside bottom node
  labels. Routes are snapped to exact H/V ints. Centre labels are NOT given to ELK (each one adds a
  dummy layer and 3x the layer gap); the layer gap is raised to label width + 96 instead and
  `LabelPlacer` puts them on the finished routes.
- `RouteTidy` (pure): removes the 5-25 px jogs ELK leaves at connector ends by sliding the end along
  its side (corner margin 8, port gap 12), drops collinear points.
- `OrthogonalRouter` (pure): grid Dijkstra with port cost (bend 30, crossing 70, clash with a parallel segment
  closer than 12 px 20000), stub of 16 px at every shape, ports every 12 px along the sides (aligned
  with the other end), ends >= 12 px apart, shortest connector first, rectangular self loops, two
  lanes for A->B and B->A, soft containers, side hints. Used for pinned shapes, setElementBounds,
  rerouteConnectors, deferred connectors (actor generalization) and the deployment diagram.
- `ShapeOutline`: stubs end ON the outline of an ellipse/diamond (the end slides inwards along its
  own axis, so the line stays H/V).
- `LabelPlacer` (pure): tail/head/centre labels beside their line (gap 3, 14, 26 px), cost: shape or
  label 3, line 1; fallbacks past either end of a short leg and around the whole route.
- `VpGraphLayout.layout/route` (VP side) + `LayoutProfile` per family (`ClassLayoutProfile`,
  `UseCaseLayoutProfile`, `DeploymentLayoutProfile`). A profile says which shapes are nodes and
  containers, how each connector points (reverse, priority, deferred, sides), its labels and where
  they are written (multiplicity rectangles of an association, captions), what hangs outside a shape.
- `AbstractDiagramMcpTools`: `layoutProfile` hook, `relayout(diagram)` (full ELK layout of a diagram
  the tools created and that has no pinned shape, otherwise route-only), `pin`, `placeNew`,
  `connectShapes`, `profileOf`; `connectCentered` now routes through `relayout` when the family has
  a profile (other families still use the old centering until their step). `autoLayoutDiagram` runs
  the layered layout for every family with a profile and releases all pins; a failing ELK run falls
  back to route-only.
- Class: generalization/realization decide the layers (priority 10) and leave the bottom of the
  general class for the top of the specialization (soft side preference in the router); an association
  keeps its declared direction (the class it is drawn from stands above: JSP above DAO above entity,
  whole above part; a level based reversal was tried and dropped because it put the JSPs below
  the DAOs of the design class diagram); dependencies point downwards; notes are placed after their
  target. Use case: DIRECTION RIGHT, actors FIRST layer in one column, use cases are the
  children of the system boundary (also as VP children, `addChild`), include/extend labels beside
  the line, actor generalization is a deferred connector routed as a bracket left of the actor column
  (both ends on the west sides; ELK would otherwise spread the actors over layers).
- `VpGraphLayout.repair`: after the layered layout every route is checked with `RouteChecks`
  (through a shape that is not an end or a container of an end, on top of another route); offenders
  are routed again by the router around the shapes and the good routes. `relayout` also schedules a
  second layout from `afterVpSettles` when a shape changed size after the call (VP fits the members
  of a class that is shown again, a renamed element).
- Explicit x/y and setElementBounds pin a shape and `avoidOverlap` moves it to the nearest spot that
  keeps 48 px from the sibling shapes (packages, nodes and the boundary do not count).
- Rule B: `setPresentationOption(PRESENTATION_OPTION_PRIMITIVE)` + `PRIMITIVE_SHAPE_TYPE_RECTANGLE`
  draws a node flat; VP then prints the stereotype keyword from the model and the *custom text* as the
  name (set it to the model name; `renameElement` already keeps it in step), the caption box keeps the
  width it had at creation and wraps the keyword - `syncCaption` sets it to the node width, also after
  VP's own fit of a container (which runs after the tool call: `settle` re-syncs and re-routes from
  `afterVpSettles`). Node sizes come from `TextMetrics` (Dialog 12 bold + padding).

**VP facts learned (all probed live with a temporary devEval tool that compiles a Java body inside VP;
the bundled JRE has javac but no zipfs, so the classpath must be directories)**
- `setBounds` through the API never reroutes connectors: stored points stay where they were.
- Pins: `setFromPinType/ToPinType(PINTYPE_RATIO)` + `setPinRatio` moves the end point to
  `shape.x + ratio*w` immediately and keeps it through later calls. The ratio is (offset + 0.25)/size
  so a floor or a round gives the same pixel. Without pins VP re-anchors at the centers.
- `CS_RECTI_LINEAR` decision: set on every connector the plugin draws. A connector with that style and
  `setRequestRebuild(true)` is rebuilt by VP itself with H/V segments (probed: A bottom -> down ->
  right into B's left side), so a later drag in the GUI cannot slant it; the plugin's own routes are
  explicit points with pinned ends and `setRequestRebuild(false)`.
- A use case shape is not a child of the system boundary unless `addChild` is called (the model
  ownership `system.addUseCase` is separate).
- ELK: FIRST layer nodes may have no incoming edge from a non-FIRST node (hence the deferred actor
  generalization); `SPACING_PORTS_SURROUNDING` / node `MARGINS` are ignored for free ports, ports
  cluster around the middle of a side at `SPACING_PORT_PORT` (14) - which suits ellipses and stick
  figures; outside node labels are honoured; self loops of one node are spread with
  `SelfLoopDistributionStrategy.EQUALLY`.
- `fmt-maven-plugin` reformats the sources on every build (python edits that match unformatted text
  fail after the first build).

**Verified live (F5c_ diagrams, checker output in the task result)**: class (4 long names with
members added later, generalization, composition, aggregation, association with multiplicities,
6 classes / 8 relations, DAO pattern, 10 classes / 17 relations, packages, notes, pinned x/y with
setElementBounds and rerouteConnectors), use case (karaoke, overview with 7 actors / 17 use cases,
include with actor-specific login, extend, notes, boundary), deployment MVC (flat nodes, H/V paths,
move/resize).

**Final measurements (server 1.32.5.28, layout_check.py)**: all F5c_ diagrams clean (SLANT, OVERLAP,
THROUGH, SHARED, LABEL = 0, LABELLINE = 0): F5c_Class1..5, Pin1, Pkg1, Ext, Big (28 classes / 44
relations, CROSS 14), F5c_UC1..4, Note1, F5c_Dep1. Random fuzz through the tools (scratchpad/fuzz.py):
class 100+, use case 100+, pinned class 95 diagrams; the only defects left were 1 SHARED in a use case
diagram whose actor has 11 associations (an actor figure offers about 10 distinct ends) and name
labels of self loops / crowded explicit placements in 2 pinned diagrams. 256 JUnit tests green.

**Remaining limits**: an actor with more than about 10 connectors shares an end (a stick figure has room
for four ends per side plus head and feet); the other families were converted in the behavioral and structural steps;
actor generalization is a bracket on the west side (parent not forced above its children); a
deployment node asked to be larger than its content is refitted by VP (setElementBounds on a
container keeps its size); relayout after every add re-places the whole diagram (diagrams opened
from a file are never re-placed, only routed); a use case overview with many actors has 20+
crossings (inherent to one actor column).

### Layout overhaul, step "behavioral" (server version 1.32.5.29 .. 1.32.5.52)

Scope: activity, state, communication and flowchart diagrams on top of the core pipeline (`VpGraphLayout`,
`ElkLayout`, `OrthogonalRouter`, `LabelPlacer`). Each family is now a `LayoutProfile`
(`ActivityLayoutProfile`, `StateLayoutProfile`, `CommunicationLayoutProfile`, `FlowchartLayoutProfile`)
and its tools only create shapes/connectors, pin explicit x/y and call `relayout(diagram)`. Removed with
the old code: slot grids, `placeAfter`/`arrangeRow`, bar/lane/loop routing of the activity tool, state
`placeNext`/self loop geometry, communication `layeredLayout` use/`declutterMessages`/slot code,
flowchart branch/rejoin/column routing, `keepRoute`/`recenterPair`/`overlay`.

**What is new in the shared pipeline**
- `LayoutProfile.routesAll()`: ELK only decides where the shapes go, the router draws every connector
  (needed for diamonds, bars, loops, messages). `ownSides` (diamond ends get a vertex of their own by
  where the other shape stands: straight ends first, then left/right, next free side), `bar` (fork/join
  as wide as the span of the shapes they join, clamped to the lane by `barRange`), `size` (notation size
  instead of the current one, used to grow a circle/diamond that has more connectors than ports and to
  reset a bar), `row` (ELK layer choice: a role per row), `outsideLabel` + `ElkLayout.Side` (a name
  hanging beside a shape), `arrange` (swimlane columns), `laneGap`, `reopensDiagram`,
  `shiftsIntoCanvas`.
- `ElkLayout.Options`: `flowOrder()` (cycles break at the sources: DEPTH_FIRST + creation order of nodes and
  edges, so the first branch is on the left and a back transition is the one that turns round),
  `centerParents()` (no straight-edge bias: a decision with three branches stands over the middle one,
  with the bias it lines up with the first and the other two branches wrap around), `labelAllowance`,
  `edgeNodeGap`.
- `OrthogonalRouter`: lane gap per call (36 for communication links), shared end as last resort before the
  `direct` L that could run through a shape. `VpGraphLayout.removeJogs` slides route ends along their side
  with `RouteTidy` to remove 2-28 px jogs (reverted per route when that would run through a shape or on
  another line). Small pinned circles/diamonds grow in `route()` too (`growSmallShapes`).
- `LabelPlacer`: label size per leg orientation, preferred side, proximity cost (a label never sits right
  beside the line of another connector), the side with more room before the next parallel line (labels leave
  bundles), gaps up to 150, canvas minimum. `VpGraphLayout.draw`: a line through the label of another
  connector is routed again around the labels that stay (at most twice, kept only when it adds no crossing),
  a full layout of a profile with `shiftsIntoCanvas` lets labels go left of the first shape and then shifts the
  drawing right/down so everything is on the canvas.
- `avoidOverlap` (explicit x/y, setElementBounds) keeps the hanging name of a shape clear too (footprint).

**Per family**
- State: DOWN, initial FIRST layer / final LAST layer, names right of the circle (final: below right so the
  transitions can arrive on all four sides), a self transition gets room to the right for loop and label
  (rectangular loop by the router on a free side), opposite transitions are two lanes.
- Flowchart: DOWN, decision diamonds with `ownSides`, Yes/No beside the lines, flows back go around the column.
- Activity: ELK (flat) decides the rows; with swimlanes `ActivityLayoutProfile.arrange` snaps every node into
  the column of its lane (nodes of one row in one lane side by side, lane width = widest row, lanes sized by
  `ActivityDiagramMcpTools.LaneSet`, the lane shapes are moved before the nodes because VP drags children
  with their compartment); a node outside the lanes stands right of the frame. Decision/merge/initial/final
  names hang beside the node (decision: above, right of the entering flow), merge/fork/join show no name.
  Page frames (`addActivityFrame`) are ELK containers around the nodes they were made for (membership kept per
  diagram; a frame the tools did not make holds what lies inside it). twoWay draws two parallel arrows (the old
  single double-headed line was two connectors on top of each other).
- Communication: DOWN rows by role through ELK's layer choice constraint (actor, boundary, control, entity;
  plain objects follow their links); links inside a role are not given to ELK (it cannot lay out in-layer
  edges), only routed. A message is a label of its link: arrow box beside the line, text beyond it, placed by
  `LabelPlacer` on the longest leg, forward messages on one side and messages back on the other
  (`applyLabel` turns the compound rectangle into the 60x60 message box and its caption). Diagram is closed and
  opened once after every layout (`settleReopen`) because VP turns the arrows only then.

**VP facts learned (probed live with a temporary devEval tool, not committed)**
- ELK `PARTITIONING` orders partitions along the flow (layers), it cannot make swimlane columns; the layer
  choice constraint (`LAYERING_LAYER_CHOICE_CONSTRAINT`) puts nodes of a role in one layer; flat ELK
  centers the nodes of a layer on one line (so layers can be read back from the node centers).
- A communication message arrow is drawn at the center of its 60x60 box, turned along the link leg that is
  nearest, only after the diagram has been laid out by VP (close + open); link points survive that, loops of
  self links do not (the router draws everything again after the reopen).
- Names of shapes whose caption is detached (states, flowchart shapes, control nodes, actors, robustness icons)
  stay at the diagram origin after `setBounds`: `resetCaption()` after every move (profiles do it in `placed`).
- Flowchart shapes are no `INodeUIModel`: there is no presentation option, so the flowchart `database`
  symbol stays VP's flat 2D cylinder outline (no shadow/gradient; wontfix).

**Checker and geometry dump**: `getDiagramGeometry` now lists the nodes of an activity page frame under the frame
(they are no VP children of it, so the dump showed the frame as a leaf and the checker reported every guard
inside it as "caption on Activity"); with that the original `layout_check.py` is clean on every F5b_ diagram.
scratchpad/layout_check.py additionally treats lanes and shapes that hold other shapes as containers and
checks communication message captions + arrow boxes and the hanging names of actors/icons/pseudo states/
decisions as labels (original kept as layout_check.orig.py; both are clean).

**Measured (server 1.32.5.52, both checkers)**: every F5b_ diagram clean (SLANT, OVERLAP, THROUGH, SHARED, LABEL = 0,
LABELLINE = 0): Act_DatPhong (3 lanes, decision/merge/fork/join/object/flowFinal), Act_Loop, Act_MergeLoop,
Act_IfElse, Act_Lanes2, Act_Book/Book2/Book3 (page frames, DAO exchanges), Act_Edit, Act_Pin/Pin2 (pinned in
lanes and lane-less), Act_Free, Act_Three; State_Phong (self transition, opposite pair), State_Screen,
State_Edit, State_Pin; Comm_ThanhToan, Comm_Book (reference figure), Comm_Edit/Auto/Pin; FC_TinhTien,
FC_Moved (after setElementBounds), FC_Loop, FC_Kinds. Class/use case/deployment (F5c_) unchanged and clean,
sequence and BPMN smoke clean. Random fuzz (scratchpad/fuzz5b.py: state, activity with and without lanes,
flowchart, communication, pinned variants, 10-20 diagrams each): no SLANT/OVERLAP/THROUGH/SHARED left except
dense random communication diagrams (labels of 3+ messages on one link in a crowded area can still touch) and a
pinned final state that grew onto the name of the initial state, a pinned communication participant with more than
about eight links (they share an end). 250 JUnit tests green.

**Remaining limits**: (component/package/object/DFD were converted in the structural step below, which also
removed `CommunicationDiagramUtils.layeredLayout`; BPMN keeps its own routing.) A communication actor's links leave its sides (its name
hangs below the figure); a crowded communication diagram (many links across the same area) can still have
message labels beside a foreign line. A diagram that was not laid out by the tools in this session (opened
from a file) is only routed, never re-placed. Fork/join bars are clamped to their lane: a branch in another
lane is reached from the end of the bar.

### Layout overhaul, step "structural" (server version 1.32.5.53 .. 1.32.5.66)

Scope: component, package, object, deployment, ERD and data flow diagrams (all four DFD notations) on top
of the shared pipeline, then a regression of every family. Every one of them now goes through
`VpGraphLayout` (ELK decides the places, `OrthogonalRouter` / ELK draw the lines, `LabelPlacer` the labels);
there is no family left with its own centre-to-centre, bow or slot code except sequence and BPMN (both
accepted, untouched).

**Profiles (all in `util/`, one `LayoutProfile` each; the tools only create shapes/connectors, pin x/y and call
`relayout`)**
- `DfdLayoutProfile`: DIRECTION RIGHT, entities FIRST layer, stores LAST layer, Yourdon processes are ELLIPSE
  outlines, flow names are centre labels, lane gap 30 for two opposite flows. Same profile for the standard,
  Gane-Sarson, Yourdon-DeMarco and Yourdon-Coad diagram types.
- `ErdLayoutProfile`: RIGHT. Deviation from the brief ("FK edge from child to parent"): the ELK edge runs
  parent -> child (VP's own direction), so the referenced tables stand LEFT of the tables that hold the
  foreign key and the crow's foot is on the right-hand table (reads as 1 -> many).
- `ObjectLayoutProfile` (DOWN, link names as centre labels), `PackageLayoutProfile` (DOWN, packages are
  containers with `minSize` for the folder tab and a 44 px title band).
- `ComponentLayoutProfile`: DOWN, `routesAll()` (ELK only places the shapes, the router draws every line so
  that a line to a port leaves it outwards), layer frames and components holding components are containers,
  interface boxes are plain nodes (consumer -> interface -> provider by VP's own connector directions).
- `DeploymentLayoutProfile`: RIGHT, nodes/devices/execution environments/artifacts are containers sized around
  their children (title width and an asked width/height are the minimum), flat primitive rectangles
  (`flatten`, `titleSize`, `syncCaption` moved here from the tool), `placed()` re-syncs the caption.
  The tool's old slot / grow / push / `moveNextToNode` / `restoreMinimum` code is gone.

**New in the shared pipeline**
- Attached shapes (ports): `LayoutProfile.attachedTo` makes a shape no ELK node (its connectors lead to its
  owner), `VpGraphLayout.Graph.attach` runs after the owners are placed (also in route-only mode):
  `PortPlacer` (pure, tested) puts every port on the side its connectors lead away to, as near as it can to
  the coordinate of the other end, names outside (`placedAttached`/`hanging`: above-left of a west port, above
  right of an east/north one, below-right of a south one), and the connectors of a port get that side as a hard
  end side. A port that n lines end on is stretched along the border to 16 + 12(n-1) px so every line has an end of
  its own on its outer face. A side that is full (name pitch) sends the next port to another side.
- `NodeSpec.pad`: room around a shape that ELK keeps free (names of ports); removed from the result again, so
  only for `routesAll` profiles.
- `LayoutProfile.headerHeight`: the title band of a container is an obstacle for labels (package, node,
  component with children, UC boundary, class package). Labels no longer sit across a folder tab.
- Notes (`INOTE`) are laid out inside the container of the shape they explain (`LayoutProfile.container`).
- `holdSizes`: a diagram that holds containers or ports gets `setAutoFitShapesSize(false)` at its first layout
  (see VP facts). `holdFit` does the same for a container the tool sized explicitly.
- `nestChild` / `growToEnclose` (AbstractDiagramMcpTools): one way to put a child into a package/node/component:
  x/y pin it, otherwise the layout places it (diagram owned by the layout) or the first free spot of the body
  (pinned diagram or diagram opened from a file), the container grows to enclose it and pushes its siblings.
- `avoidOverlap` keeps containers clear of other containers (a leaf may still be placed inside one).
- Router: the rectangle of what hangs outside a shape (a port name) adds grid lines; as a last resort before the
  L fallback (which ignores shapes) the search runs again without the hanging names of its end shapes.
- `autoLayoutDiagram` runs the layered layout for every family with a profile; for a diagram type without one it
  says so and moves nothing (VP's generic layout, which draws slants, is gone).

**VP facts learned (probed live with the temporary devEval tool)**
- With the diagram's auto-fit ON, Visual Paradigm shrinks every container around its children and moves every
  port back to where it first drew it (east border, 14 px below the top) as soon as the tool call is over;
  `port.setBounds` is accepted at once and reverted by the next call, `setAutoFitShapesSize(true)` reverts it
  too. With auto-fit OFF the bounds stick (also for ports on the north/south border). `fitSize()` still works
  explicitly, so the tools keep fitting shapes themselves.
- Pins (`PINTYPE_RATIO` + ratio) work on ports like on any shape: a connector end keeps the point set.
- A tool that resets a connector's caption after `relayout` throws the label placement away (VP's default
  position): `addProvidedInterface`/`addRequiredInterface`/`addComponentDependency` did, now `connectShapes`
  resets once before the layout and nothing afterwards.
- A VP `Note` has an `INOTE` model element (never null) and a `getParent()` of null.

**Removed (no dead code)**: `centerConnector`, port/folder/bent-label/parallel-line variants, `recenterTree`,
`recenterConnectorsOf`, `resetCaptionsOfTree`, `makeRoomBetween`, `lineSegments`, `clearLabelSpot`,
`obstaclesBetween` and friends, the non-profile branch of `connectCentered` (renamed `connectAndLayout`),
`DiagramLayoutEngine.applyStructuredLayout`, `DiagramUtils.routeBends/labelSpotsAlong/contains`,
`FlowchartDfdUtils.Grid/freeCellCenter/placeShape/columnLayout`, `CommunicationDiagramUtils.layeredLayout`,
`ComponentDiagramUtils` grid/child/port geometry, `DeploymentDiagramUtils.nextFlowPosition/planPush/
clampInside/encloseChildren`, `PackageObjectDiagramUtils.flowPlace/clampIntoBody/encloseChildren` and their tests.

**Measured (server 1.32.5.66, scratchpad `layout_check.py`)**: all F5s_ diagrams clean (SLANT, OVERLAP, THROUGH,
SHARED, LABEL = 0): Comp_Basic/3Layer/Ports/Nested/Flat/Subsystem, Dep_Basic/MVC/Many, Pkg_MVC/Book/Book2,
Obj_Links/Pkg, ERD_Karaoke/Ref, DFD_Karaoke/GaneSarson/YourdonCoad/YourdonDeMarco/Ref (CROSS 0..4, the
karaoke DFD with both-direction flows 4). All F5c_ and F5b_ diagrams still clean (exit code 0 for the three
prefixes), after moving shapes with `setElementBounds`, `rerouteConnectors`, renaming and removing elements.
Sequence (F5b_Seq_*) clean; BPMN F4_fprocess_BPMN_Karaoke and _Black clean, _Inter and _Misc show the same
SHARED/THROUGH pairs they show on 1.32.5.52 (checked by building the previous commit: identical, untouched
code). Random fuzz through the tools (`scratchpad/fuzz5s.py`, up to 8 shapes, and SCALE=2.5/4/5 up to 58 shapes):
DFD x4 notations, ERD, object, package, deployment 25-30 diagrams each with no defect; pinned package 1 of 25
(a gap of 56 px between two pinned frames with four lines through it), component 1 of 25 and pinned component
2 of 25 (see limits). A 38-shape / 46-flow DFD builds in 5 s over 85 tool calls (about 60 ms per call, every call
re-lays the diagram).

**Remaining limits**: a crowded pinned diagram (shapes placed with x/y very close together, several lines
through a narrow gap, ports with long names) can still end with two lines in one lane (`SHARED`); lines may run
across the title band of a container they merely pass (labels never do); a component 40 px high holds one port
per east/west side (name pitch), more ports go to the north/south sides, then spread out; the interface lollipop
is not available (a box with `<<interface>>`); notes added to a pinned diagram take the first free spot
beside their target (may straddle a frame border); a diagram opened from a file is routed, never re-placed,
and its auto-fit is switched off when it holds containers or ports; BPMN `F4_..._Inter`/`_Misc` keep their
earlier SHARED/THROUGH (BPMN is outside this step).

### Round-6 audit fixes (server version 1.32.6.x)

Graded against the notation brief; every fix verified on F6_ diagrams (exported PNGs, `getDiagramGeometry`,
`layout_check.py`: all F6 diagrams of the behavioral, structural and process families are clean).
`scratchpad/f6/` holds the drivers (`runbeh.sh`, `runstruct.sh`, `runproc.sh` replay the R6 scenarios under
F6 names; `deploy.sh N` tests, builds, installs, restarts VP and waits for its project).

**Behavioral**
- Activity: rows of the lane layout are the nodes whose vertical extents overlap (a 10 px bar and a 40 px
  action of one ELK layer have centers 5 px apart: the old "centers within 2 px" test put them in different
  rows and centered both in the lane). A bar that still shares a row with another node moves out of it
  (`VpGraphLayout.clearBars`: toward the side with fewer connectors, so a fork goes above its row and a join
  below). `barRange` is the union of the lanes of the nodes a bar joins (flows meet it from above/below).
  Lane borders are guide lines (`LayoutProfile.guides`) the router treats like drawn routes (12 px clear).
  Hanging names (initial "Bat dau", decision question) count in the width of a row. Nodes are clamped into
  their lane in `beforeLayout`, `avoidOverlap` keeps a lane node in its lane (`horizontalRange` hook), notes
  stand in the lane column of the node they explain. `#2` works for actions and objects, a name that is only
  `#n` is a diamond without text. Action ports keep 14 px from the corner (`ShapeOutline.ROUNDED`).
- Pinned shapes (`LayoutProfile.pinAware`: activity, state, flowchart): the unpinned shapes are still laid
  out layered, the pinned ones are no ELK nodes, the unpinned ones are pushed down past them
  (`Graph.avoidPinned`), every connector is routed around everything. A diagram with containers/ports keeps
  the old route-only behaviour.
- Notes: `NoteAnchor` (pure) puts a sequence note under its lifeline (dashed line ends on the lifeline's
  dashed part) and draws every anchor as H/V or an L; communication notes are layout nodes now; BPMN anchors
  go through `OrthogonalRouter` (notes are nested in their lane, the pool grows around them).
- State: opposite transitions nest (the first of a pair keeps to the middle of its sides, the second is the
  same route one lane aside: `OrthogonalRouter.mirror`), the self-loop reserves room for its text
  (`LayoutProfile.reserved`).
- Labels (`LabelPlacer`): a label far from its own line costs, a label nearer to another line costs, a
  label with a space may wrap into two lines (`LabelSpec.wrappable`) when its box has no clear place;
  flowchart Yes/No are tail labels of the decision.
- BPMN: a gateway's 3rd/4th branch leaves from the edge beside a vertex (`EXIT_BOTTOM_LEFT` ...), the
  gateway name is the last vertex and moves aside when used, condition labels wrap above a short leg,
  `autoLayoutDiagram` compacts auto-placed elements left and narrows the pools.
- Sequence: lifeline moves never land on another head (note in the answer), nested fragments stand 12 px
  inside the outer one. The self message looked like an open bracket because VP puts its (opaque) caption
  box on the right edge of the loop, hiding the closing vertical line: the caption now stands 6 px right of
  the loop (`moveSelfCaption`), the loop is 16 px high.

**Structural / process**
- `ShapeOutline.FOLDER`: a connector that ends on the top of a package outside its tab ends on the body
  (tab = width/5, 20 px); east ends keep below the tab strip. Class (`addPackage`, `addPackageShape`,
  `addClass(packageName)`) reuse the project's package, check the name after creating, nest the class and
  validate colours first; `addComponentPackage`/`addPackageElement` have `backgroundColor`.
- Router: an end that is collinear with and within 12 px (end to end) of another route is a clash,
  `FIGURE` ports (actor boxes keep 4 px from the corner: 5 ends fit), corner margin grows with big boxes,
  `untangle` re-routes connectors that cross with a crossing price of 600 (also after the reopen of a
  communication diagram), small mid-route jogs (<= 6 px) are removed.
- Per-container layer gap from the labels inside that container (ELK `Node.layerGap`), deployment diagrams
  choose between flow right and flow down (`flexibleDirection`, rarely wins: a nested host chain stays
  wide), `applyBounds` grows the container of a resized/moved shape, an explicit node size is a minimum.
- UC: actors in generalization-tree order and use cases below their actor (`LayoutProfile.sortNodes` +
  ELK model order): crossings of the 7-actor/17-UC overview 31 -> 7; type-aware `addActor/addUseCase/
  addRelationship`, a use case in another system boundary is found again, no "X2" rename. ERD: foreign
  keys of a reused table are drawn (`showExistingForeignKeys`).
- Shared: `removeDiagramElement` accepts `A -> B`, `A -> B [guard]`, `N: label` and refuses an ambiguous
  text; failures start with `Error` (`ToolResults.normalize` in `McpServer`); listings print `:Class` for
  anonymous objects, note text, `N:` on messages; component handles `Owner.port`; `#n` handles for
  flowchart/DFD/BPMN names.

**VP facts learned**
- VP opens its last project (here `f5c_saved.vpp`) a few minutes after the MCP port is up and throws away
  everything built before that: wait for `getProjectInfo` before replaying scenarios.
- `fitSize()` on a freshly created note gives a size VP overrides a moment later (a note is auto-fitted
  later); do not report it.
- VP wraps a caption text inside the caption box width, so a narrower label box makes a two-line label
  (only when the box is two lines high and each line fits; see "Captions never shrink").
- A package's tab is width/5 x 20 px; the body border is drawn across the whole width.

**Measured (server 1.32.6.14)**: `layout_check.py` is clean (SLANT, OVERLAP, THROUGH, SHARED, LABEL = 0) on every
F6 diagram of the three audit families (all R6 scenarios replayed under F6 names: 80 diagrams; CROSS 0..7,
17 on the 24-component CmpPerf, 10 on the edited state diagram). `scratchpad/layout_check_plus.py` adds
FOLDEREND, CHILDOUT, COLLINEAR and CAPFAR: only crowded diagrams keep a CAPFAR (State_Edit, CmpPerf, DFD
with 18 flows), the bars of an activity span lanes by design (CHILDOUT) and a flow through a bar is
collinear with its other side by nature. 243 JUnit tests green (`Round6Test` is new).

**Remaining limits**: the deployment chain client -> web server (Tomcat > war > components) -> database is
2000+ px wide (nested hosts cannot be stacked separately from the top level by ELK's single hierarchical
run); the handles of "End #2" style names live in memory and are lost when VP is restarted (a reopened
project knows the shapes by their shown text only); a crowded state diagram can still have a label beside a
foreign line when no wrapped box fits; BPMN rows stay where they were put (compaction moves left only).

### Captions never shrink (server version 1.32.7.x, last build 1.32.7.5)

Defect (seen in the exported PNG of the 3-layer component diagram): two of its 15 `<<use>>` captions drew as
`<<use...` and `<<u...`. `getDiagramGeometry` showed their boxes: 60x14 and 46x14 (the other 13: 74x14).
Visual Paradigm cuts a caption whose box is narrower than its text plus a little padding with "...".

**Root causes**
- `LabelSpec.tight()` (commit 86f3d7a) let a caption that found no clear place shrink by 14 px, the padding VP
  adds to a caption (74 px box, 60 px of text): the label was drawn at the bare text width. Removed.
- A layout read the size of a label from the caption it found on the connector, i.e. from what the layout
  before it had left there. The 60 px box of the first layout became the input of the next one, which took
  14 px off again (46 px): a squeezed caption stayed squeezed and got worse. `VpGraphLayout.Graph.read` now
  resets every connector's caption (`CaptionFit.natural`) before the profile reads it, so a label is always
  the size VP gives its text (a connector that takes no part gets its caption bounds back).
- The two-line fallback (`wrappable`, and the BPMN condition label) used `w/2 + 8` as the width of the
  wrapped box: a line wider than that box is cut with "..." as well. Both now use `TextMetrics.twoLines`:
  the longer half of the text (split at the best space) plus the padding of a caption, two lines high.

**Calibrated live (exported PNGs, VP 1.32, Dialog 12)**
- A caption VP sizes itself is the text plus 14 px (`<<use>>`: 60 -> 74). The text is drawn in full from
  text + 4 px (`<<use>>` in 64 px; 63 px gives `<<use...`) and from 12 px high (11 clips the glyphs).
  Short texts need a few pixels more than `TextMetrics` measures ("gui": VP box 37, our measure 17 + 14 = 31),
  so the layout never uses the measure for a label VP can size: it resets the caption and reads the box.
- VP wraps a caption at spaces only when the box is two lines high (28) and each line fits (`Thanh toan hoa
  don bang the` at 100x28, 120x28, 140x28 = two lines, at 100x14 = `Thanh toan ho...`).
- A multiplicity box is never cut, whatever its width. `resetCaption()` on an association moves its
  multiplicity boxes (and `resetCaptionSize()` does nothing): reading code must not reset captions.

**Detection (cannot come back silently)**
- `CaptionFit` (pure, `util`): `fits(w, h, neededW, neededH, text)` and `check(connector)`. `getDiagramGeometry`
  prints ` text "<<use>>"` after every caption and ` TRUNCATED needs WxH` when the box is smaller than VP
  needs. The need is the size the layout got from VP minus the 10 px that are only padding (remembered per
  connector, bounded memory), or, for a caption no layout measured, the text plus 4 px; a taller box counts
  when the text wraps into its lines. The check never touches the diagram (a reset would move an
  association's multiplicities): `scratchpad/idem_check.py` proves geometry and PNG are the same before and
  after a read.
- `scratchpad/layout_check.py` counts TRUNCATED as defect `TRUNC` (exit code 1); the other checks are unchanged.
- Tests: `TextMetricsTest`, `CaptionFitTest`, `LabelSizeRuleTest`, a random `LabelPlacerTest` (a label comes
  out in one of the sizes it was given, never smaller) and `VpGraphLayoutTest` (a caption squeezed to 46 px
  is laid out at 74).

**Measured (server 1.32.7.x)**: all R6 scenarios replayed under F6 names (`runstruct.sh`, `runbeh.sh`,
`runproc.sh`: 113 diagram checks): `layout_check.py` clean (SLANT, OVERLAP, THROUGH, SHARED, LABEL, TRUNC = 0),
CROSS and LABELLINE the same as the build before (106 -> 106 and 1 -> 1 over the 113 checks, diagram by
diagram). An audit that reset
every connector caption and detached shape name of the final diagrams and compared the box with VP's size
(574 captions, all families) found nothing narrower than the text needs.

### Round-4 audit fixes (server version 1.32.4.x)

Graded against the course notation brief, verified live in VP 18.1 on F4_* diagrams (exported PNGs,
`getDiagramGeometry` dumps). 121 tools now (new: `renameElement`, `removeAttribute`,
`removeOperation`, `setObjectSlot`; `removeRelationship` works on every diagram type). What changed
and the VP quirks behind it:

- **Pins make explicit connector ends stick.** With the diagram's "center" connection points VP
  re-anchors a fresh connector at the shapes' centers (or at a package's bounding box, tab
  included) after the tool returns, whatever points were set. `setFromPinType/setToPinType(
  PINTYPE_RATIO)` + `setFromPinRatio/setToPinRatio` (a ratio of the shape's bounds) keeps an end
  where the tool put it: package ends on the border of the body, parallel DFD flows, nothing else
  needed. `afterVpSettles` (a later event) did NOT help for connectors.
- **VP shrinks shapes after the call.** A component is fitted to its caption again on the next
  repaint (ports and `setBounds` do not matter: `setRequestFitSize(false)` does not stop it), a
  container with children keeps a size set from `afterVpSettles`. Ports therefore sit tight
  (6 px top/gap, 12 px squares: four fit the 40 px of a component) instead of growing the box;
  node/frame sizes asked for are minimums restored by `restoreMinimum` / `encloseUpwards`.
- **obstaclesBetween** skipped the end shapes but not what they hold (the shape list is flat): a
  node with nested nodes, a package with classes got detours around its own children (vanished
  paths, flat bridges above frames). Descendants of both ends are no obstacles now.
- **Sequence / communication reroute**: `rerouteConnectors` redraws the messages from the lifeline
  centers (sequence) or re-lays links, loops and message slots (communication); `addNote` on a
  communication diagram repairs the layout through the `afterNoteAdded` hook (VP collapses the
  messages onto their links when a shape is added). Message labels of a communication diagram are
  placed by a global pass (`declutterMessages`: middle of the own link first, other side, further
  out; keeps off participants, names, other messages and other links) with a text-based label width.
- **addNote**: hard-wrapped text (30 columns), first free spot right/left/below/above the target
  (no shape, caption or line, inside the lane of a lane node), reports the box it asked for (VP
  auto-fits notes to their text a moment later).
- **Activity**: a fork/join bar's flows leave through their own port and run down along a free
  channel between the rows (`orthogonalBarRoute`), cross-lane action flows go down, across, down
  (`orthogonalLaneRoute`); branches of a fork/decision start below the whole row of the source;
  `addActivityFrame` moves an unlisted box out to a spot clear of other frames; an explicit x/y
  that was clamped warns about the box it overlaps; "End #2" is a handle that shows "End"
  (`handleOf` hook, found by every tool).
- **State**: `autoLayoutDiagram` lays the flow out top to bottom (`StateMachineUtils.flowLayers`,
  back transitions ignored, end at the bottom, branches side by side), self loops follow moved
  states, name-only states are 30 px high.
- **BPMN**: a node moved next to its source keeps clear of every other element (unconnected
  auto-placed ones included, names of events/gateways too) and the flow always runs left to right
  (the lane grows past the old 900 px row); names are fitted again after the lane grew; message
  flows of different pairs take different gap lines (`gapLegs`, three lines in the 90 px pool gap),
  a request/response pair differs in the blocked-leg fallback too; labels are placed clear of
  shapes, names, other labels and other flows; duplicate sequence/message flows and data
  associations are refused; a pool of height <= 60 is a black box (`setBlackBox`, its name is drawn
  horizontally); flows keep out of the names of their own ends.
- **Flowchart / DFD**: DFD `autoLayoutDiagram` activates the diagram first (a background diagram
  keeps the lines center to center), opposite DFD flows are two parallel straight arrows (pinned),
  data flow labels keep off every line, the third branch of a decision goes on the next row, the
  letter of a connector symbol is inside its circle.
- **Use case**: `autoLayoutDiagram` fits an existing system boundary around the laid-out use cases;
  keyword labels (<<Include>>, <<use>>...) of bent connectors are put beside the longest leg.
- **Deployment / component / package**: setElementBounds resets the captions of the connectors of a
  moved shape (class diagrams re-lay their association labels); path labels get room between nodes;
  artifacts and components of a node are reused on a second diagram; frames and nodes keep the
  size asked for; layer frames of a component diagram are stacked; nested components are 90 px
  apart; connectors with a port at one end leave it outwards and go around its component.
- **Shared**: `setElementBounds` treats x/y <= 0 as "keep"; `removeRelationship` and
  `removeDiagramElement` also delete activity flows and state transitions (models that exist on
  their diagram only); `routeBends` keeps bends 30 px inside the canvas; `autoLayoutDiagram` says
  what it does per family and does not touch diagrams that are laid out while elements are added.
- **ERD**: `generateDdl` / report write UNIQUE; `addForeignKey` creates a missing `fromColumn`.

Remaining limitations (round 4): a component keeps the 40 px VP gives it, so more than four ports
on one component hang below its border (VP fits the box again after every call); a connector
between two ports of sibling components in one frame goes around the components above the frame
(label above the frame) instead of through the gap below them; connectors leaving one class box
edge anchor near each other (a multiplicity can touch the next connector, e.g. composition + a
second association at one corner); a note beside a shape in a crowded diagram with no free spot
goes right of everything (long anchor line); more than three message flows whose horizontal legs
overlap share a gap line (the 90 px pool gap holds three); a BPMN flow in a very dense lane can
still cross a name; component auto-placement stays greedy (`autoLayoutDiagram` is the cleaner
layout for a crowded diagram).

### Round-3 audit fixes (server version 1.32.3.x)

Graded against the course notation brief and verified live in VP 18.1 on F3_* diagrams (exported
PNGs, `getDiagramGeometry` dumps). What changed and the quirks behind it:

- **Family hooks**: the cross-family tools no longer know the families. `AbstractDiagramMcpTools`
  keeps the registered instance of every family (`familyOf`, `families()`) and calls overridable
  hooks: `moveOwnShape` (setElementBounds), `rerouteOwnConnectors` (rerouteConnectors),
  `layoutOwnDiagram` (autoLayoutDiagram). Sequence, communication, activity, state, component,
  deployment, flowchart/DFD and BPMN implement them; every other diagram goes through
  `applyBounds` + `recenterTree`.
- **setElementBounds**: moving a container (package, node, artifact, component with ports,
  boundary) moves every nested shape (VP leaves them behind otherwise: `moveShapeTree`), a
  container is never made smaller than its contents, `width/height <= 0` fits only shapes without
  children, control nodes / pseudo-states keep their notation size and re-lay their detached
  caption, flowchart/DFD/BPMN symbols get the size the add* tools give. A sequence lifeline moves
  its messages and activation bars with it: VP drags the ends of attached messages to the head's
  corner on a bounds change and re-snaps them to the stub activations when it lays the diagram
  out, so the arrows are redrawn from the lifeline centers as the very last step, after the
  diagram was reopened (a setBounds on a bar or the head afterwards puts the old ends back).
- **removeDiagramElement**: removing a shape with connectors, nested shapes, ports or artifacts
  opens a modal "X has no referenced view. Delete X from the repository?" dialog per orphaned
  model, which blocks the EDT call forever. `answeringDeletePrompts` runs a `javax.swing.Timer`
  on the EDT that answers "No to all" (view goes, model stays); it also answers "Yes" to the
  "mark X stopped?" prompt of a destroy message. Never leave a prompt open: the tool returns.
- **Duplicates**: `connectCentered` refuses a second connector of the same kind, stereotypes and
  name between the same two shapes (and deletes the unplaced model). Unnamed associations are
  exempt (different multiplicities). Activity/state/flow tools have their own checks.
- **Package frames**: VP clips a connector at the shape's bounding box, which includes the folder
  tab: package ends get an explicit end on the border of the body (`FOLDER_TAB`, flags
  `setUseFromShapeCenter(false)`, no rebuild). The title band of a frame is forbidden for bend
  points (a label drawn at a bend must not read as part of the title); ports and the contents
  of the two end shapes are no obstacles. A grown package pushes its siblings away (tree-aware
  `pushApart`).
- **Class**: auto-placed classes reserve a 220x140 slot (members come after the class), three per
  row. Association names and multiplicities follow the drawn route (first/last leg, longest leg).
  The report names the subclass first, prints visibility symbols, aggregation/composition and
  both multiplicities.
- **Activity**: a merge/join is only moved below its branches while it has no outgoing flow (a loop
  back no longer drags it); a guarded flow gets 24 px more room; a twoWay box ("XxxDAO: method()")
  goes beside its action, 110 px away; `addActivityFrame` moves unlisted nodes it would cut or hold
  out to the side; identical flows are refused; a second initial node warns.
- **State**: a second incoming transition of an auto-placed state without outgoing transitions
  moves it below all its sources (`spotBelowAll`); sideways gaps grow with the label; the name of
  an initial/final state hangs on a side no transition uses; a diagram whose label would cross the
  canvas edge shifts right (`makeRoomLeft`); labels are sized by their text (the caption box can
  be narrower than the text).
- **Communication**: participants of a diagram whose participants were all auto-placed are laid
  out by their links (actor row, chain of boundaries, controls, entities under their boundary:
  `layeredLayout`, also behind `autoLayoutDiagram`); the caption is rendered before the message
  slot is computed; icon boxes are cut down to the icon and the diagram is not auto-fit (VP would
  widen the box to its name again).
- **Sequence**: nested fragments start below the guard of the frame around them and are inset; a
  label wider than its arrow moves the lifelines on the right apart; duplicate numbers, unknown
  message types and operators are refused; `create` lowers the head to the dashed arrow;
  `destroy` ends the lifeline (VP draws no X for an API-created lifeline); the invisible stub
  activations are left out of `getDiagramElements`.
- **Component / deployment / object**: `<<component>>` is never added as a stereotype (VP prints it)
  and a reused component keeps its own stereotypes; ports push their neighbours away; components of
  one deployment host stand in one row 90 px apart (`addDeploymentComponent` takes x/y); a node of
  that name in the same owner is reused; path labels avoid node borders; `addObject` takes
  `modelPackage` and prefers the class that has the slot attributes.
- **Flowchart / DFD / BPMN**: a bare DFD id is a process, `D1` a store; entities carry an a/b/c
  marker; DFD columns are ordered around the processes (`columnLayout`); flow labels sit beside
  the line; a flowchart loop back is an elbow around the column. BPMN flows leave a gateway or
  event through distinct vertices, avoid other elements and name labels, never start behind the
  element's own name; request/response message flows run side by side; event names stay inside
  their lane; long names wrap in tasks; nodes wrap into a second row at 900 px.
- **ERD**: four tables per row; removeColumn/renameColumn/removeForeignKey/removeTable; isUnique;
  SQL identifiers only; varchar defaults to 255; relationship results list the generated columns.
- **Use case**: use cases are 60 px apart and the system boundary encloses the detours and labels
  of the lines between them.

Remaining limitations: an operand guard of a combined fragment is drawn by VP at the operand's
top-left (operand shapes have no caption to move; probed with `getDiagramGeometry`), so it can
cross the first lifeline's bar; VP draws no X for the destroy message of an API-created lifeline
(`setStopped(true)` and a reopen change nothing: the lifeline just ends at the arrow); not
available: BPMN boundary events, text annotations, call activities and loop markers, UML composite
states / choice / history / internal transitions, activity send-signal / accept-event actions and
pins, sequence found/lost messages; the sequence actor still has a box head; the DFD, BPMN and
communication layouts are greedy (explicit x/y remain best for large diagrams);
`rerouteConnectors` on a diagram family without its own routing is straight center-to-center; the
audit's "roof" bend of a cross-lane decision flow did not reproduce after the placement changes
and has no dedicated fix; `removeRelationship` does not list Usage.

### Round-2 audit fixes (server version 1.32.2.x)

Graded against the course notation brief (BG HP TTTN 2 CNPM 2020 + lab samples) and verified live
in VP 18.1 on F2*/Z_* diagrams (exported PNGs). What changed and the quirks behind it:

- **Diagram names**: every `create*Diagram` goes through `newDiagram()`: a blank name or one any
  diagram already has is refused before VP creates anything (lookups take the last match, so a
  duplicate hid the first diagram), and a diagram VP refuses to name is deleted again.
- **Removing**: `IDiagramUIModel.removeDiagramElement()` leaves the shape on the diagram;
  `removeDiagramElement` now calls `IBaseDiagramElement.deleteViewOnly()` (model kept). Exception:
  lifelines, lifeline links and messages exist on their diagram only, and `deleteViewOnly()` on
  them opens VP's modal "has no referenced view. Delete from the repository?" dialog, which blocks
  the EDT call forever; they are removed with `deleteModel()` (the classifier stays).
- **Aggregation kind**: `IAssociationEnd.getAggregationKind()` returns "composite"/"shared" although
  it is set with `AGGREGATION_KIND_COMPOSITED`/`_AGGREGATION` ("Composited"/"Aggregation");
  `aggregationOf()` accepts both (element list, `removeRelationship`).
- **Routing**: `DiagramUtils.routeBends` takes the bend from the middle of the border-clipped
  segment, never inside or on an end shape (+15 px), treats nodes/artifacts/components/packages
  that hold neither end as obstacles and falls back to a two-bend orthogonal detour. `overlay()`
  draws two opposite connectors as one double-headed line.
- **Use case**: VP stores a generalization from the general element (triangle on the from end):
  `addRelationship(Generalization)` now sets from = target (parent). The "extension points"
  compartment is hidden with `IUseCaseDiagramUIModel.setShowUseCaseExtensionPoint(false)` and
  `IUseCaseUIModel.setShowExtensionPoint(false)` + refit. `addActor`/`addUseCase` take x/y;
  duplicate/self relationships are refused; a second `addSystemBoundary` updates the box.
- **Sequence**: rows follow the full sequence number (1, 1.1, 1.1.1, 1.2, 2). A lifeline's
  robustness icon comes from the lifeline's own stereotype, never from the shared class. VP's
  per-message activation stubs are drawn in front of the long bar whatever the z-order
  (`bringToFront` has no effect): they are made fully transparent (fill and line transparency 100)
  and the bar grows over a stub sticking out below it. Fragments reserve a header + guard row.
- **State**: auto-placement is top to bottom (the book's screen flow), avoiding self-loop areas and
  labels; a forward/back pair has its labels on the outer side of each bend.
- **Activity**: `addActivityFrame` (rounded titled `IActivity` shape, white, sent to back, sized
  around its nodes and the guards of their outgoing flows) and `addActivityFlow(..., twoWay)` for
  the book's design diagram. Initial/final nodes show their name. Lane relayout moves each row as
  a whole (`relocateRow`). Guards and the decision question take the first spot clear of nodes,
  labels, flow lines, frame borders and the canvas edge (6 px, then 16 px off the line); a crowded
  guard on an explicit layout is reported as a WARNING. VP anchors a slanted flow at a fork/join
  bar's corner: such flows get explicit ports 12 px in from the bar ends (`barPort`,
  `setRequestRebuild(false)`).
- **Communication**: VP re-routes every self link out of the top edge when it lays the diagram out
  on reopening; the loops are put back on a free side (below first) with their messages.
  `moveCommunicationLifeline` (and `setElementBounds` on a communication diagram) re-anchors the
  links and re-lays their message arrows. `addCommunicationLifeline(..., lifelineType)` draws
  boundary/control/entity icons via the lifeline's stereotype. Participants are found as
  `:Classifier` too.
- **Class**: `addClass` reuses a same-named class of the owner (no "Class2"); rows wrap after
  about three classes; named associations widen the gap for their labels; self/cyclic
  generalizations and unknown visibilities are refused.
- **Component / deployment / package / object**: `addComponentPackage` + `addComponent(...,
  packageName)` (layer frames); an auto-placed component moves next to its first connection at a
  spot clear of shapes and drawn lines. `addDeploymentComponent` accepts an artifact host and
  `addDeploymentDependency` draws dashed component/artifact dependencies (MVC inside the .war).
  New top-level nodes stay on the node row. `addPackageDependency(kind=line)` is a plain line;
  nested packages that exist are reused; `addObject` with an empty name is anonymous.
- **BPMN / flowchart / DFD / ERD**: message flows run in the gap between pools; gateway branches
  and joins use the top/bottom vertex; loop-backs go over the top; a labelled branch gets room and
  its label is placed clear of node names; data objects move under their task. Flowcharts are one
  column; a merge target returns to the column and its side branch is an elbow. DFD rows are 220
  px apart and labels slide along their line. Duplicate FKs and bad relationship types are refused.

Remaining limitations: a sequence actor lifeline keeps a box head (VP draws no stick figure for an
API-created lifeline that anchors messages); guards in a cramped explicit activity layout can
still touch a line (reported as a WARNING); VP may draw a moved communication link vertically near
a box corner when the two boxes overlap horizontally; component auto-placement is greedy (explicit
x/y remain best for large diagrams); a 2-3 px stub notch can remain at the very end of a sequence
bar.

### Round-1 audit fixes (server version 1.32.1.x)

Verified live in VP 18.1 on F1_* diagrams (exported PNGs). What changed and the quirks behind it:

- **Fit size needs the active diagram**: `IShapeUIModel.fitSize()` silently does nothing (shapes
  stay 0x0 or at their creation size) unless the shape's diagram is the active one in VP.
  `fitShape()` now opens the diagram first (`activate()`), so tools that edit a diagram that is not
  in front (after another create*/export call) still fit their shapes.
- **Connection points**: VP's default "round the shape" connection points snap every connector end
  to a shape corner. `useCenterConnectionPoints()` switches a diagram to "follow center"
  (`setConnectionPointStyle(IShapeUIModel.CONNECTION_POINT_TYPE_CENTER)`): ends are then clipped at
  the borders on the center-to-center line, also for null or explicit points, and a fork bar gets
  vertical drops. Set on class, use case, state, activity, ERD, flowchart and DFD diagrams and
  whenever the shared `connectCentered` / `centerConnector` run (component, deployment, package,
  object diagrams too). VP does not reroute connectors when a shape moves through the API:
  `recenterConnectorsOf()` after every move (`setElementBounds` too).
- **Connector routing** (`AbstractDiagramMcpTools.centerConnector` + `DiagramUtils.routeBend`):
  connectors joining the same two shapes are spread symmetrically with one bend point each
  (opposite flows no longer lie on top of each other, labels apart), and a line that would run
  through another leaf shape gets a bend around it (±60/120/180 px, fewest crossings otherwise).
  VP pitfalls: querying the diagram between `clearPoints()` and the `addPoint()` calls makes VP
  rebuild the connector with two end points and append later points behind them; points with a
  negative coordinate are dropped; a pending `setRequestRebuild(true)` straightens a bent route.
- **Overlap resolution**: `pushApart()` moves the sibling shapes a grown shape now overlaps (right
  for the same row, down below its middle) and re-anchors their connectors; used after class
  members are added (classes keep 80 px apart) and after ERD columns are added. Auto-placed
  classes and tables take the first free spot of their row (`freeSpot` / `firstFreeSpot`).
- **Unique names**: VP keeps model names unique per owner and model type and silently renames a
  clash ("Actor2", "Class2", "Package3", "Component4", "Artifact", "Entity"). Elements are now
  named inside their owner (`nameInOwner`, after `addChild`/`addArtifact`), and a root-level
  actor, use case, package, component/interface, artifact, table or lifeline classifier with the
  requested name is reused (a new view of it). `addClass` rejects a duplicate on the same diagram
  and reports a rename instead of hiding it.
- **getDiagramElements**: types come from `getModelType()` (no obfuscated class names), model-less
  shapes show their shape type, every connector prints `Type <<stereotypes>> 'name': from -> to
  [guard]`, links list their messages, visibility prints as + - # ~.
- **Wrong diagram type**: `findDiagram()` / `diagramNotFound()` say "Diagram 'X' is a
  StateDiagram, ..." instead of "Diagram not found".
- **State machine**: entry/do/exit are `IActivity` models (`setEntry/setDoActivity/setExit`), so VP
  draws the internal activity compartment (multi-line custom text is ignored). State names are
  detached labels: `resetCaption()` after the final bounds. Boxes are fitted (min 100x40),
  auto-placed on a 3-column grid, and an auto-placed state moves next to the source of its first
  incoming transition. Self-transitions are an explicit loop on the right edge with the label
  outside. Duplicate state names are rejected.
- **Activity**: object node `[state]` via `addInState(IState2)` + `IObjectNodeUIModel
  .setShowInStates(1)`; `addActivityObjectNode` takes `partitionName`. An auto-placed node moves
  next to the source of its first incoming flow: one row below it, branches of one fork/decision
  side by side in their lane, an object node beside its producer, a join below its lowest branch;
  unconnected auto-placed nodes do not block. Lanes grow to the row width and shrink back to their
  content; children keep their offset from the lane center. Fork/join bars span their branches in
  their lane. Guards sit beside the source end of the flow; a decision's question left of the
  diamond.
- **Communication arrows**: a message is an invisible box whose arrow VP draws at its center, only
  when the box is large enough (60x60 as in VP's own sample), and oriented along the link only
  when VP lays the diagram out on opening it; until then every arrow is horizontal. The tool
  therefore closes and reopens the diagram (`realCloseDiagram()` + `openDiagram()`) after each
  message. Requesting a rebuild of a link moves all its messages to the link's start point, so
  links are created with explicit center points and no rebuild. Captions are placed separately
  (`getCaptionUIModel().setBounds`): above/below the arrow beside horizontal links, beside it for
  vertical ones; self-link loops go on top of the box with their messages above.
- **Sequence fragments**: `addCombinedFragment(..., fromSeq, toSeq, operandStarts)` sizes the frame
  around the covered lifelines and messages; operands (`guard` "a|else") are
  `IInteractionOperand` shapes added as children of the frame; VP draws the dashed separators and
  re-lays the operands (17 px header) after the diagram is reopened. Lifeline heads are as wide as
  their name; `generateSequenceReport` resolves message ends via `getFrom()/getTo()`.
  `setAutoFitShapesSize` stays off on sequence diagrams (it would shrink lifelines/activations).
- **Use case**: `IExtend` runs from the extended (base) use case to the extension in VP (arrowhead
  and extension points on the from end), so Extend is stored base -> extension. All UC
  relationships use the shared connector (labels «include»/«extend» rendered). Actors are fitted
  to the figure; `addSystemBoundary` moves actors the box would cover outside it.
- **Component interfaces**: VP's `ComponentInterface` (lollipop) shape is not rendered when created
  through the API (by model or shape type, `DO_INTERFACE` or `DO_CLASS`, explicit 24..60 px sizes,
  after reopening the diagram: nothing is drawn, caption 0x0). Interfaces are therefore components
  stereotyped `<<interface>>` drawn as plain boxes (`IComponentUIModel.setShowOption(NONE)`), which
  realizations and usages accept. Port names are re-laid after the ports are snapped to the edge.
- **ERD**: `IDBColumn.setType` returns false for types the project's database does not know (VP
  then keeps "integer"): nvarchar/datetime/bit/... are mapped (`ErdUtils.typeCandidates`) and an
  unknown type is an error. `IDBForeignKey` runs from the parent (referenced) table to the child
  and VP adds a column named after the parent's key to the child: `addForeignKey(fromTable=child,
  toTable=parent, fromColumn)` renames that column to the child's own column (which it replaces)
  and `toColumn` re-targets the reference; DDL prints FOREIGN KEY clauses. Multiplicities use VP's
  constants (1 / 0..1 and 0..1, 0..*, 1, 1..*). `addColumn(diagramName, tableName, ...)` is
  diagram-scoped.
- **DFD / flowchart**: VP prints a data store's ID with its own "D", so only the number is stored
  ("D1"/"1" accepted). Level-0 IDs continue 1.0 -> 2.0. A decision's later branch moves beside it;
  diamonds get 1.5x their fitted size, processes some padding.
- **BPMN**: no diagram auto-fit (it shrank pools/lanes as soon as a connector was drawn). An
  auto-placed node moves right of the source of its first sequence flow (same row; a gateway's
  further branches one row down; cross-lane targets keep their lane at the source's next column).
  All pools are kept at the width of the widest one.
- **Communication**: `addCommunicationLifeline` with an empty `lifelineName` and a
  `classifierName` draws an anonymous object (": Classifier"); the classifier name is its handle.
- **Deployment**: a free artifact that was auto-placed moves under (or beside) the node of its
  `<<deploy>>` arrow.

Remaining limitations: no assembly/delegation connectors on component
diagrams; BPMN event labels can touch container borders; detours on long diagonals may run near
the diagram edge; labels of two nearly parallel connectors (not joining the same pair) can still
overlap.

### More diagram families (server version 1.32.0-diagrams.4)

Activity, component, deployment, package/object, communication, flowchart/DFD and BPMN tools, one
class per family (`tools/<Family>McpTools.java`) with pure helpers in `util/<Family>Utils.java`
covered by JUnit tests. All of them build on the same rules: `resetCaption()` on every new shape and
connector, connectors created with explicit center points, shapes fitted with `fitShape()` (control
symbols such as initial/final nodes, events and gateways keep fixed UML sizes), containers (lanes,
pools, packages, nodes) sized to enclose their children, diagram-scoped name lookups, and every
tool returns a readable string instead of throwing. Smoke-tested live in VP: every family creates a
diagram, adds shapes and connectors, and exports a readable PNG.

Quirks learned while integrating:

- **Activity swimlanes**: `IActivitySwimlane2NewUIModel.getVerticalPartitionIds()` and
  `getCompartmentIds()` return null for a frame built through the API (the setters do not round
  trip). Lanes are therefore read from the frame's children: each vertical
  `IActivityPartitionHeaderUIModel` plus the `IActivitySwimlane2CompartmentUIModel` whose
  `getVerticalPartitionId()` is that header's id. Nodes added to a compartment with `addChild` use
  absolute diagram coordinates.
- **Diagram auto-fit vs containers**: `setAutoFitShapesSize(true)` makes VP fit containers too. On an
  activity diagram it shrank the swimlane frame to 100x100 and each lane compartment to its nodes,
  so lanes no longer lined up with their headers. Creating the swimlane frame turns the diagram's
  auto-fit off; action and object boxes are still fitted one by one with `fitShape()`. There is no
  per-shape auto-fit switch in the API.
- **Detached captions do not follow `setBounds`**: flowchart shapes, decision nodes, communication
  actors and message arrows keep their label where it was laid out at creation (the diagram origin).
  Call `resetCaption()` again after the final `setBounds` (and after any later move).
- **Deployment**: an `IDeployment` from an artifact to a node is rejected with "Invalid connection.
  Node is not allowed with Deployment"; `addDeployDependency` falls back to an `IDependency` with
  the `<<deploy>>` stereotype.
- The open issues listed here for the first audit (message arrows, port labels, interface ball,
  object node state, guards) are resolved in 1.32.1.x, see above.

### State machine diagrams (server version 1.28.4)

- **`createStateDiagram(diagramName)`** creates a UML state machine diagram
  (`DIAGRAM_TYPE_STATE_DIAGRAM`).
- **`addState(diagramName, stateName, entryAction, doActivity, exitAction, x, y)`** adds an
  `IState2` box. The optional entry/do/exit texts become `IActivity` behaviours of the state
  (drawn as `entry / …`, `do / …`, `exit / …`); pass `""` to omit them. `x`/`y` > 0 place the box,
  otherwise it is auto-positioned.
- **`addInitialState` / `addFinalState`** add `IInitialPseudoState` / `IFinalState2` as 30x30
  shapes. `stateName` is both the handle used by `addTransition` and the label VP draws under the
  shape (VP always renders the model name there; blanking the custom text does not hide it), so
  pass a display-friendly name such as `Start` / `End`.
- **`addTransition(diagramName, fromState, toState, label)`** creates an `ITransition2` connector.
  The label is the UML text form `event [guard] / effect` and is stored as the transition name so
  VP renders it on the arrow; a blank label leaves the arrow unlabelled. Passing the same state
  twice draws a self-transition.
- **Captions need `resetCaption()`**: freshly created state shapes and transition connectors come
  out blank until `IBaseDiagramElement.resetCaption()` is called (same quirk as sequence messages).
- **Use case / sequence captions (1.31.1-uc)**: `addActor` and `addSystemBoundary` now call
  `resetCaption()` too (actor and system names were invisible in VP 18.1). An actor's name is a
  detached label, so `setElementBounds` resets the caption after moving an actor. Actor lifelines in
  `addLifeline` get a real `IActor` base classifier instead of a transparent white-text box, so the
  participant is visible (VP still draws it as a box, not a stick figure).
- **Build with JDK 26**: SpotBugs 4.7 cannot read class files of major version 70 and PMD crashes
  while parsing, so `./run install` needs `-Dspotbugs.skip=true -Dpmd.skip=true -Dcpd.skip=true`.
- **Layout**: `autoLayoutDiagram` uses a hierarchical LeftToRight layout for state diagrams, so the
  initial state ends up on the left and the final state on the right. Explicit `x`/`y` on `addState`
  is usually better for a readable flow — long transition labels collide when states sit close.
- **Pure helpers** live in `util/StateMachineUtils` (caption building, label normalization, name
  validation) and are covered by `StateMachineUtilsTest`.

### Class diagram editing, audit and project tools (server version 1.27.9)

- **Scoped lookups**: `addAttribute(..., diagramName)` only uses the class shown on that diagram, so
  same-named classes in other diagrams/packages are never modified. `addClass` accepts `x`, `y` and
  `modelPackage` (model-only package, no package shape) to keep same-named classes apart.
- **Generalization direction**: VP stores a generalization as from = general (parent), to =
  specific (child). `addGeneralization(fromClass = child, toClass = parent)` and
  `addClass(extendsClass)` now create it that way, so the triangle is drawn at the parent.
- **Connector anchoring**: with null points VP anchors connector ends at the shapes' top-left
  corners. Class connectors are created with explicit center points; `rerouteConnectors` re-anchors
  all connectors after moving shapes. VP then draws an axis-aligned line through the middle of the
  shapes' overlap, or a center-to-center diagonal when they do not overlap. Open (or export) a
  diagram before rerouting it after a restart, otherwise the ends fall back to the corners.
- **Labels**: `layoutConnectorLabels` places multiplicities next to each end (absolute diagram
  coordinates), the association name mid-line and hides role names; ends leaving a shape in a fan
  are staggered. Shape wrappers returned by the API are not canonical objects, compare them by id.
- **Data-model helpers**: `addStereotypeToClasses(diagram, "*", "ORM Persistable")`,
  `removeRelationship` (deletes the model element), `setAssociationProperties` (edits
  association/aggregation/composition in either direction; role `-` clears a role name; VP's ORM
  support may auto-name roles of associations created between persistable classes).
- **Design-diagram helpers (server version 1.29.0)**: `addOperation` takes `visibility`,
  `isAbstract` and a `diagramName` that restricts the class lookup to that diagram (same rule as
  `addAttribute`, so classes with the same name in other packages are left alone).
  `setAssociationProperties` takes `fromNavigable`/`toNavigable` (`true`, `false` or
  `unspecified`). New association ends are navigable on both sides and VP hides the arrows of a
  two-way association, so for a one-way arrow set the source end to `unspecified` and the target
  end to `true`. `addStereotypeToClasses` takes `robustnessIcon`: `false` keeps
  `boundary`/`control`/`entity` classes drawn as class boxes with their members instead of
  robustness icons.
- **Layered (MVC) diagrams, server version 1.30.0**: `addPackageShape` draws a package frame at
  given bounds, optionally nested in another frame (`parentPackage`, model and diagram) or owned
  by a model package. `moveIntoPackage` makes a package the owner of a class in the model and its
  container on the diagram, and brings the class to front (a frame created later would hide it).
  `addUsage` draws a `<<use>>` dependency between classes or packages; VP stores the keyword as a
  stereotype and only renders it after `resetCaption()` on the new connector.
- **Realization direction**: VP stores a realization supplier -> client, like a generalization.
  `addRealization(fromClass=impl, toClass=interface)` and `addClass(implementsInterfaces=...)`
  therefore store from = interface, so the hollow triangle ends on the interface.
- **Axis-aligned lines**: when two shapes' projections overlap, VP does not always draw the line
  through the middle of the overlap; it can use a shape's center clamped into the overlap. Layout
  checks should test every candidate position.
- **Audit/export**: `getRelationshipDetails` returns JSON (classes with abstract flag, stereotypes,
  owner, attributes, operations, robustness-icon flag, bounds; relationships with both ends'
  multiplicity, aggregation kind, role, navigability, connector points and label rectangles). `exportDiagramImage` writes a PNG with an empty
  watermark.
- **Project**: `newProject`, `openProject` (save first: VP may ask about unsaved changes),
  `saveProject`, `saveProjectAs` (never overwrites an existing file) and `getProjectInfo` (name
  and file path, useful as a guard before editing). VP reopens the last project on startup and
  ignores a file passed at launch, so switch files with `openProject`.
- **Known issue**: `GET /api/tools` returns HTTP 500; use `initialize` / `tools/call` or
  `POST /api/execute` instead.

### Key Files

| File | Purpose |
|------|---------|
| `McpServer.java` | Undertow-based MCP server with SSE transport |
| `tool/Tool.java` | Custom `@Tool` annotation |
| `tool/ToolDefinition.java` | Reflection-based tool scanning + JSON Schema generation (hierarchy-aware) |
| `VPMcpPlugin.java` | VP plugin entry point, registers tools with McpServer |
| `StandaloneServer.java` | Standalone entry for Docker (no VP dependency) |
| `tools/AbstractDiagramMcpTools.java` | Base class with zone-aware positioning, layout, and management tools |
| `tools/UseCaseMcpTools.java` | 5 use case diagram tools |
| `tools/ClassDiagramMcpTools.java` | 22 class diagram tools + 5 project tools |
| `tools/ErdMcpTools.java` | 7 ERD tools |
| `tools/SequenceDiagramMcpTools.java` | 7 sequence diagram tools |
| `tools/StateMachineDiagramMcpTools.java` | 5 state machine diagram tools |
| `tools/ActivityDiagramMcpTools.java` | 6 activity diagram tools (vertical swimlanes) |
| `tools/ComponentDiagramMcpTools.java` | 7 component diagram tools |
| `tools/DeploymentDiagramMcpTools.java` | 6 deployment diagram tools |
| `tools/PackageObjectDiagramMcpTools.java` | 4 package diagram + 3 object diagram tools |
| `tools/CommunicationDiagramMcpTools.java` | 4 communication diagram tools |
| `tools/FlowchartDfdMcpTools.java` | 3 flowchart + 5 data flow diagram tools |
| `tools/BpmnDiagramMcpTools.java` | 9 BPMN business process diagram tools |
| `util/DiagramUtils.java` | Shared VP API helpers (diagram/element lookup) |
| `util/DiagramLayoutEngine.java` | Zone-aware positioning + parameterized VP LayoutOption construction |

### Dependencies

- **Jackson 2.17.2** - JSON parsing
- **Undertow 2.2.30.Final** - Embedded HTTP server
- **VP OpenAPI 17.2** - Visual Paradigm plugin API (system scope)
- **Java 11** - Target runtime

### Docker

```bash
./run docker-build   # Build Docker image (Java 11)
./run docker-up      # Start MCP server on port 2026
./run docker-down    # Stop MCP server
./run docker-logs    # View server logs
```

Docker uses multi-stage build with VP API stub JAR for compilation.

### MCP Endpoints

- **SSE**: `http://localhost:2026/sse` - Establish SSE connection, returns session ID
- **Messages**: `http://localhost:2026/mcp/messages?sessionId=<id>` - Send JSON-RPC requests

### Verified

- [x] Custom MCP server compiles and runs on Java 11
- [x] SSE transport works (endpoint event, keep-alive, session management)
- [x] MCP protocol: initialize, tools/list, tools/call
- [x] 107 tools registered and invocable via JSON-RPC
- [x] Docker build succeeds with Java 11
- [x] VP plugin loads successfully (verified in VP log)
- [x] Current plugin build installed and reloaded in Visual Paradigm; Claude Code user-scope MCP
  `visual-paradigm` connects to `http://localhost:2026/sse`
- [x] Connectors use `createConnector()` with IDiagramElement refs (not `createDiagramElement`)
- [x] Diagram management tools: listDiagrams, getDiagramElements, autoLayoutDiagram, removeDiagramElement, getElementCounts
- [x] Zone-aware element positioning (actors left, UCs right; boundary/DAO/entity layers)
- [x] Parameterized VP LayoutOption: Hierarchical (UC/Class/Sequence), SmartOrganic (ERD)
- [x] UC diagram span reduced from 2300px to ~260px
- [x] Class diagram span reduced from 3375px to ~205px
- [x] Rich verification: getDiagramElements shows class attrs/ops, table columns, lifeline classifiers, connector from->to
- [x] Rich reports: generateUseCaseReport/generateClassReport/generateErdReport/generateSequenceReport show element names and details
- [x] UC addRelationship supports IActor + Association type + diagramName param
- [x] addForeignKey resolves column references via setIndexColumn
- [x] findDiagramElementByModel uses object identity instead of name matching
- [x] findOrCreateActivation returns last (most recent) activation
- [x] addCombinedFragment warns about unfound lifelines
- [x] New class/project tools exercised end-to-end in VP 18.1: two e-commerce class diagrams built,
  saved with saveProjectAs into three files, converted to data models, and checked against the
  saved .vpp files (SQLite) with zero differences
