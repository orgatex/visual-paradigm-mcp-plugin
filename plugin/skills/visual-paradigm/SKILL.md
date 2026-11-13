---
name: visual-paradigm
description: Draw UML, ERD, BPMN, DFD and flowchart diagrams inside Visual Paradigm (VP) through the Visual Paradigm MCP plugin, check the layout, and export the diagrams as PNG images. Use when the user wants a use case, class, sequence, state, activity, communication, component, deployment, package, object, ER, BPMN, data flow or flowchart diagram drawn in Visual Paradigm, mentions Visual Paradigm or VP, wants a diagram built from code or requirements instead of PlantUML or Mermaid text, or wants VP diagram images exported.
---

# Visual Paradigm MCP

Drive Visual Paradigm (VP) through the 124 tools of the Visual Paradigm MCP plugin: create diagrams, add elements and relationships, let the plugin lay them out, verify the result, export PNG images, save the project.

Full tool catalogue (name, parameters in order, purpose, per diagram family): [references/tools.md](references/tools.md). Read the section of the family you are drawing before the first call.

## 1. Prerequisites and connection

1. Visual Paradigm is running with the plugin installed and a project open. The plugin starts its MCP server by itself. After installing or updating the plugin, quit VP completely and start it again (VP keeps the old plugin in memory).
2. The server is streamable HTTP at `http://localhost:2026/mcp` (POST only, JSON-RPC). The `visual-paradigm` plugin that ships this skill registers it already. Without the plugin, register it by hand:
   - Claude Code: `claude mcp add --transport http --scope user visual-paradigm http://localhost:2026/mcp`
   - Codex: `codex mcp add visual-paradigm --url http://localhost:2026/mcp`
   - Restart the client so it picks up the tool list. The tool list of a session is fixed at start; tools added by a plugin update do not appear until restart.
3. Check that the server answers:

```bash
curl -s -X POST http://localhost:2026/mcp -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}'
```

   The reply contains `serverInfo` (`visual-paradigm-mcp-server`) and its version. No reply means VP is not running or the plugin is not loaded.
4. Plain HTTP fallback, useful when the MCP tool list is stale or the client has no MCP support:
   - `GET http://localhost:2026/api/tools` lists all tools with input schemas.
   - `POST http://localhost:2026/api/execute` with `{"toolName":"listDiagrams","arguments":{"type":""}}` runs one tool and answers `{"result":"..."}` or `{"error":"..."}`.
5. Before drawing, call `getProjectInfo` to confirm the right `.vpp` project is open.

## 2. Calling conventions

- Arguments are passed by NAME and the schema marks EVERY parameter required. For an unused parameter pass `""` (string), `0` (int) or `false` (boolean). Never pass null.
- `x`, `y` > 0 pin a shape at that coordinate; `0` lets the layout place it. `width`, `height` = `0` means fit to content.
- Every tool returns a STRING and does not throw. A failure starts with `Error`, or says `... not found`, `... already exists`, `Unknown ...`. Read the result of every call; a silent assumption that a call worked is the most common source of broken diagrams.
- Diagram names must be unique; `create*` refuses an empty or duplicate name. Call `listDiagrams(type)` first when reusing a project.
- Names are handles. Actions, states, shapes, events, lifelines, nodes, ports and similar names are used by later calls to find the element, so they must be unique on the diagram. Where the same text must appear twice (two "End" terminators, the same action in two frames), add a trailing ` #2`: it is part of the handle only and is not displayed.
- Tool parameter order is not uniform. Watch for: `addActor(actorName, diagramName, ...)` and `addUseCase(useCaseName, diagramName, ...)` (name first); `addAttribute(className, ..., diagramName)` and `addOperation(className, ..., diagramName)` (class first, diagram last); `addCommunicationMessage(..., sequenceNumber, messageText)` (number before text, the reverse of `addMessage`); all other tools start with `diagramName`.
- Examples below use positional shorthand; `D` is the diagram name; `""`/`0` for unused slots are written out only where it matters.

## 3. Layout: what the plugin does, and what not to fight

After EVERY `add*` call the plugin re-lays out the diagram, for all families except sequence and BPMN (those two have their own layout). The layout is layered and orthogonal (the Eclipse Layout Kernel bundled with VP, plus an obstacle-aware router).

Guarantees you can rely on and should not fight:

- Every connector segment is horizontal or vertical (right-angle elbows; a self-loop is a rectangle). No diagonal or zigzag lines.
- Shapes never overlap; no line passes through a shape that is not its end point; no two lines run on top of each other.
- Connector labels sit beside their line. A label keeps the size VP gives its text and is never truncated to `...`; if it finds no room it moves further out.
- Deployment nodes, devices and execution environments are flat rectangles (keyword and name), not 3D boxes.
- Containers (packages, nodes, nested components, swimlanes, activity frames) stay fitted around their content; every tool re-fits shapes when it changes them.

How to steer it:

- Default to `x = y = 0` and let the layout work. Pinning (`x`/`y` > 0, or `setElementBounds`) fixes a shape; lines then route around it. Many pins make the result worse.
- `autoLayoutDiagram(D)` releases ALL pins and lays out again. Use it after pinning or moving shapes, or when geometry looks wrong.
- `rerouteConnectors(D)` only redraws the lines, shapes stay. `layoutConnectorLabels(D)` re-places connector labels.
- `setElementBounds(D, name, x, y, w, h)` moves/resizes one shape: x or y <= 0 keeps the current coordinate, width/height <= 0 fits to content. A container takes its nested shapes along; a sequence lifeline takes its messages along.
- Add the children of a container before the next sibling container (package frames, nodes) so frames are placed without overlap. Add `addSystemBoundary`, `addActivityFrame` and `addCombinedFragment` AFTER the content they enclose.

Verify, in this order, before calling a diagram done:

1. `getDiagramElements(D)`: structure and counts match the plan (elements, relationships, directions).
2. `getDiagramGeometry(D)`: shape bounds, connector points, caption bounds. Check that consecutive connector points share x or y, shapes do not overlap, and no caption is flagged `TRUNCATED`. On a violation call `autoLayoutDiagram(D)` rather than editing coordinates point by point.
3. `exportDiagramImage(D, "/absolute/path/name.png")`, then OPEN the PNG and look at it. Check labels, arrow heads and directions, missing stereotypes, boxes cut at the edge. Geometry data cannot show a wrong arrow direction or a missing label; the picture can.

## 4. Choosing the diagram type

| Need | Diagram |
|------|---------|
| Who uses the system and which goals | Use case |
| Static structure, classes, interfaces, design | Class |
| Database tables and keys | ERD |
| Interaction over time between participants | Sequence (communication for the same content, organised by links) |
| Life cycle of one object or screen flow | State machine |
| Workflow, algorithm, responsibilities per role | Activity (swimlanes by role); flowchart for classic flowchart symbols |
| Software modules and interfaces | Component |
| Hardware, servers, artifacts | Deployment |
| Layering and dependencies of packages | Package |
| Example instances and their slots | Object |
| Data movement between processes, stores, external entities | DFD |
| Business process with pools, lanes, events, gateways | BPMN |

## 5. Workflow per diagram family

General order everywhere: `listDiagrams` (name free?) -> `create*Diagram` -> add elements -> add relationships -> verify (section 3) -> export -> `saveProject`. Names of actors, use cases, classes, tables, packages that already exist in the project are the SAME element (VP draws another view), so reuse names on purpose and keep unrelated names distinct.

### Use case

```
createUseCaseDiagram(D)
addActor(actor, D, 0, 0); addUseCase(uc, D, 0, 0)      // name first
addRelationship(D, source, target, type)
getDiagramGeometry(D)
addSystemBoundary(D, "Module name")                    // last, once use cases are final
```

- `type` = `Association` | `Include` | `Extend` | `Generalization`. Directions: Association has no arrow, every actor links to at least one use case. Include: source = base use case, target = included one. Extend: source = extending (optional) use case, target = base. Generalization: source = child, target = parent, for actors and use cases alike.
- Add Include, then Extend, then Generalization, then the actor Association links. A duplicate pair is reported, not redrawn.
- Use case names are action phrases from the actor's point of view; an orphan use case (reachable from no actor) is a modelling error.
- The boundary frame contains the use cases; actors stay outside. Calling `addSystemBoundary` again renames and re-fits it.
- Look at the image for: missing `<<extend>>` labels, stray arrow heads on association lines, include/extend pointing the wrong way.

### Class

```
createClassDiagram(D)
addClass(D, "Order", "", "", "", false, "", "", 0, 0, "")   // packageName, packageColor, stereotype stay "" for a plain white box
addAttribute("Order", "total", "double", "private", D)
addOperation("Order", "getTotal", "double", "", "public", false, D)
addInterface(D, "Payable")
addGeneralization(D, child, parent)
addComposition(D, whole, part, "1", "1..*")
addAggregation(D, whole, part, "1", "*")
addAssociation(D, a, b, "1", "*", "places")
addDependency(D, client, supplier)
addRealization(D, implementation, interface)
getRelationshipDetails(D)                                    // JSON audit
```

- Create ALL classes (with attributes and operations) before relationships. `extendsClass` / `implementsInterfaces` in `addClass` need the parent already on the diagram; otherwise call `addGeneralization` / `addRealization` afterwards.
- Attributes and operations are looked up by class name across the WHOLE project unless you pass `diagramName`. Always pass `diagramName`, and keep class names unique across diagrams when classes are meant to be different; a reused class shares its members between diagrams.
- `addAttribute` visibility: `public | private | protected | package` (or `+ - # ~`); an empty type is allowed (analysis-level diagrams). `addOperation` `params` = `name: Type, other: Type`; `returnType` `void` or a type.
- Relationship direction (`from` -> `to`): Generalization from = child; Composition/Aggregation from = WHOLE (diamond at the from end); Dependency from = dependent; Realization from = implementation. Multiplicity accepts a number, `*`, `n`, `m`, `0..1`, `1..*`, `2..n`; an invalid value is refused. Judge direction from the arrow heads and diamonds in the image, not from the parameter order.
- Fix without rebuilding: `removeAttribute`, `removeOperation`, `removeRelationship`, `setAssociationProperties` (name, multiplicity, role, navigability; role `-` clears an auto-generated role name).
- Layer frames (for example View/Controller/DAO/Model): `addPackageShape` then `moveIntoPackage`; `addUsage` draws `<<use>>` dependencies between classes or packages. Do not colour classes or wrap them in coloured packages unless asked (`packageName`, `packageColor`, `setClassColor`, `addPackage`).
- `addStereotypeToClasses(D, "*", "ORM Persistable", "false")` stereotypes classes; `robustnessIcon` `true`/`false` shows boundary/control/entity as icons or as normal boxes with members.

### ERD

```
createErd(D)
addTable(D, "customer")
addColumn(D, "customer", "id", "integer", 10, 0, true, false, false)    // length, scale are ints
addColumn(D, "customer", "name", "varchar", 255, 0, false, false, false)
addForeignKey(D, "orders", "customer", "customer_id", "id", "fk_orders_customer")
generateSql(D, "MySQL", "create", "")                                  // or PostgreSQL, sqlserver, Oracle, SQLite
```

- Table and column names are SQL identifiers (letters, digits, `_`; no spaces or accents).
- Two FK tools with OPPOSITE direction: `addForeignKey(fromTable = CHILD, toTable = PARENT, fromColumn, toColumn, name)` and `addTableRelationship(fromTable = PARENT, toTable = CHILD, type, fromMult, toMult)`. Both create the relationship and the FK column in the child. Use exactly one per table pair; a second is refused.
- `addTableRelationship` `type`: `identifying` (FK inside the PK) | `nonIdentifying` | `""`. Parent multiplicity `1` or `0..1`; child `0..1`, `0..*`, `1`, `1..*`.
- Model many-to-many with a junction table holding two FKs. FK columns live on the "many" side.
- Correct without rebuilding: `renameColumn`, `removeColumn` (an FK column needs `removeForeignKey` first), `removeTable` (deletes from the project, all diagrams; refused while it has FKs). `removeDiagramElement` only hides the view.

### Sequence

```
createSequenceDiagram(D)
addLifeline(D, "Customer", "Customer", "actor", "")
addLifeline(D, "OrderForm", "OrderForm", "boundary", "")
addLifeline(D, "Order", "Order", "entity", "")
addMessage(D, "Customer", "OrderForm", "submit order", "1", "sync")
addMessage(D, "OrderForm", "Order", "create order", "2", "sync")
addMessage(D, "Order", "Order", "validate()", "3", "sync")       // from == to: self-message
addReturnMessage(D, "Order", "OrderForm", "order id", "4")
addReturnMessage(D, "OrderForm", "Customer", "confirmation", "5")
getDiagramElements(D)
```

- Add lifelines first, then messages in order, combined fragments last.
- Leave `alias` empty and refer to lifelines everywhere by `lifelineName`. An alias makes VP report the alias as the name and message lookups by `lifelineName` then fail, producing messages that float at x=0.
- Message anchoring: messages are anchored on the lifelines themselves, not on activation bars; the plugin merges the activation bars. Calling `addActivation` is optional.
- Number every message: pass `sequenceNumber` (`"1"`, `"2"`, `"5.1"`); empty means the next top-level number. A number already used is refused. Do not type `1:` into the label; VP prints the number and you would get `1: 1:`. A return message needs its own, next number.
- `lifelineType`: `actor` | `boundary` | `control` | `entity` draws the icon (actor: see limitations); `""` or `participant` gives a plain box. It cannot be changed after messages exist; recreate the diagram instead.
- `messageType`: `sync` (solid, filled head), `async` (open head), `create` (dashed, lowers the lifeline head to the arrow), `destroy` (see limitations).
- `addCombinedFragment(D, operator, guard, coveredLifelines, fromSeq, toSeq, operandStarts)`: operator `alt | opt | loop | break | par`; frames the messages numbered `fromSeq`..`toSeq` including sub-numbers (4.1, 4.2); several operands use guards `a|else` and `operandStarts` for the first message of operands 2..n. Add the messages first.
- `autoLayoutDiagram` does nothing useful on sequence diagrams; to move a lifeline use `setElementBounds` (its messages follow). A label wider than its arrow pushes the lifelines to its right apart, so keep message labels short.
- Look at the image for: arrows drifting to the left edge, doubled numbers, call (solid) vs return (dashed) styles, every call answered by a return.

### State machine

```
createStateDiagram(D)
addInitialState(D, "Start", 0, 0)
addState(D, "Pending", "", "", "", 0, 0)        // entry, do, exit actions optional
addState(D, "Approved", "notify()", "", "", 0, 0)
addFinalState(D, "End", 0, 0)
addTransition(D, "Start", "Pending", "")
addTransition(D, "Pending", "Approved", "approve [valid] / log")
addTransition(D, "Pending", "Pending", "retry")             // self-loop
```

- Exactly one initial state, at least one final state, every state reachable, every transition labelled `event [guard] / effect` (empty label allowed for the initial transition).
- State names are handles; VP always prints the pseudo-state name below the circle, so give it a presentable name.
- Long labels can crowd neighbours: let the layout place states, or pin them about 300 px apart. Composite states, choice, history and internal transitions are not supported; use flat states.

### Activity

```
createActivityDiagram(D)
addActivityPartition(D, "Customer", 0, 0, 0, 0); addActivityPartition(D, "System", 0, 0, 0, 0)   // lanes first
addActivityControlNode(D, "initial", "Start", "Customer", 0, 0)
addActivityAction(D, "Enter order", "Customer", 0, 0)
addActivityControlNode(D, "decision", "Valid?", "System", 0, 0)
addActivityObjectNode(D, "Order", "created", "System", 0, 0)
addActivityFlow(D, "Start", "Enter order", "", false)
addActivityFlow(D, "Valid?", "Save order", "yes", false)
addActivityFlow(D, "Valid?", "Reject", "no", false)
addActivityControlNode(D, "final", "End", "System", 0, 0)
addActivityFrame(D, "Process order", "Enter order,Save order", 0, 0, 0, 0)    // optional, after nodes and flows
```

- Add lanes before nodes. `partitionName` `""` means no lane. Lanes are by role, not by screen.
- `kind` of `addActivityControlNode`: `initial | final | flowFinal | decision | merge | fork | join`. Exactly one initial, at least one final, every action has an incoming and an outgoing flow, every edge leaving a decision has a guard, fork and join are balanced.
- Flows to or from an object node become object flows. `twoWay=true` draws a double-headed pair (action <-> called operation box).
- Unsupported: send-signal and accept-event actions, pins.

### Communication

```
createCommunicationDiagram(D)
addCommunicationLifeline(D, "Customer", "", true, 0, 0, "")            // stick figure actor
addCommunicationLifeline(D, "OrderForm", "", false, 0, 0, "boundary")
addCommunicationLifeline(D, "", "Order", false, 0, 0, "entity")        // anonymous  :Order
addCommunicationLink(D, "Customer", "OrderForm")                        // one link per pair
addCommunicationMessage(D, "Customer", "OrderForm", "1", "submit")      // number BEFORE text
```

- One link per participant pair, no arrow head on links; two opposite messages on the same link are two arrows side by side, never a second link.
- `sequenceNumber` is a UML expression (`1`, `1.2`, `2a`, `1.1*`); empty means the next top-level number. Do not type the number into the text. A self-message uses the same participant at both ends.
- `moveCommunicationLifeline` pins a participant when you need a specific arrangement; otherwise the actor ends up on top, then boundaries, controls, entities in rows.

### Component

```
createComponentDiagram(D)
addComponentPackage(D, "Presentation Layer", "", 0, 0, 0, 0, "")   // layer frames, top to bottom
addComponent(D, "OrderForm", "", "", 0, 0, "Presentation Layer")    // last arg: containing frame
addComponentInterface(D, "IOrderService", 0, 0)
addProvidedInterface(D, "OrderService", "IOrderService")
addRequiredInterface(D, "OrderForm", "IOrderService")
addComponentDependency(D, "OrderForm", "OrderService", "use")
addPort(D, "OrderService", "pOrder")
```

- Do not pass `component` as stereotype; VP prints `<<component>>` itself. Interfaces are `<<interface>>` boxes (see limitations); provided = dashed realization, required = dashed `<<use>>`.
- Keep dependencies between adjacent layers only (presentation -> application -> data).

### Deployment

```
createDeploymentDiagram(D)
addDeploymentNode(D, "Web Server", "device", "", 0, 0, 0, 0)               // parent first
addDeploymentNode(D, "Tomcat", "executionEnvironment", "Web Server", 0, 0, 0, 0)
addDeploymentArtifact(D, "app.war", "Tomcat", 0, 0)
addDeploymentComponent(D, "OrderController", "app.war", 0, 0)
addDeploymentDependency(D, "OrderView", "OrderController", "submit")
addCommunicationPath(D, "Client PC", "Web Server", "1: HTTP/HTTPS")
addDeployDependency(D, "standalone.jar", "Web Server")                      // stand-alone artifacts only
```

- `kind`: `node | device | executionEnvironment`. Create parents before children. Label every communication path with its protocol and number them in time order. Deeply nested hosts produce a wide, flat strip because nested hosts stay on one row.

### Package and object

```
createPackageDiagram(D)
addPackageElement(D, "view", "", 0, 0, 0, 0, "")
addPackageElement(D, "customer", "view", 0, 0, 0, 0, "")      // children before the next top-level package
addPackageElement(D, "dao", "", 0, 0, 0, 0, "")
addClassInPackage(D, "dao", "OrderDao")
addPackageDependency(D, "view", "dao", "use")                  // use | import | access | merge | line

createObjectDiagram(D)
addObject(D, "o1", "Order", "total=10;status=new", 0, 0, "")   // attr names must exist on the class
addObjectLink(D, "o1", "c1", "placedBy")
setObjectSlot(D, "o1", "status", "paid")                       // "" clears a slot
```

- Dependencies point downward from the higher layer; avoid upward or layer-skipping dependencies. `line` draws a plain association-style line without arrow or keyword. An empty `objectName` with a class name gives an anonymous `: Class` instance.

### Flowchart and DFD

```
createFlowchart(D)
addFlowchartShape(D, "Start", "terminator", 0, 0)
addFlowchartShape(D, "In stock?", "decision", 0, 0)
addFlowchartFlow(D, "Start", "In stock?", "")
addFlowchartFlow(D, "In stock?", "Ship", "Yes")
```

`kind`: `terminator | process | decision | inputOutput | document | predefinedProcess | database | manualInput | connector`. Shape names are unique handles (use ` #2` for a repeated visible name).

```
createDataFlowDiagram(D, "ganeSarson")          // "" | ganeSarson | yourdonDeMarco | yourdonCoad
addDfdExternalEntity(D, "Customer", "", 0, 0)   // id "" = next letter a, b, c
addDfdProcess(D, "Place order", "1", 0, 0)      // id "" = next number
addDfdDataStore(D, "Orders", "1", 0, 0)         // "1" or "D1": VP prints the D itself
addDfdDataFlow(D, "Customer", "Place order", "order details")   // name or ID
```

- Every DFD flow must touch a process (entity-store, store-store, entity-entity are refused). A process has at least one input and one output. Process names are verb phrases, flow labels are nouns. A context diagram (level 0) has exactly one process; put the level in the diagram name.

### BPMN

```
createBpmnDiagram(D)
addBpmnPool(D, "Shop", 0, 0, 0, 0)
addBpmnLane(D, "Shop", "Sales"); addBpmnLane(D, "Shop", "Warehouse")     // ALL lanes before elements
addBpmnEvent(D, "Order received", "Sales", "start", "", 0, 0)
addBpmnTask(D, "Check stock", "Warehouse", "user", 0, 0)
addBpmnGateway(D, "In stock?", "Warehouse", "exclusive", 0, 0)
addBpmnDataObject(D, "Invoice", "Sales", 0, 0)
addBpmnSequenceFlow(D, "Order received", "Check stock", "")
addBpmnSequenceFlow(D, "In stock?", "Ship", "Yes")
addBpmnMessageFlow(D, "Customer", "Order received", "order")             // between DIFFERENT pools
```

- Sequence flows stay inside one pool; message flows connect two different pools (a start event only receives, an end event only sends). Element names are unique handles. Pool height 40-60 gives a black-box pool.
- Not supported: boundary events, text annotations, call activities, loop markers.

## 6. Notes, edits and cleanup

- `addNote(D, text, attachedTo, 0, 0)` adds a comment box next to a shape (any diagram family).
- `renameElement(D, old, new)` renames the model element everywhere it is shown.
- `removeDiagramElement(D, name)` removes the VIEW only; the model element stays in the project (lifelines, links, messages, activity flows and state transitions exist only on their diagram, so they are really deleted). `removeRelationship` deletes relationships from the MODEL on any diagram family (an unnamed connector too).
- Prefer correcting with the remove/rename/set tools over deleting and rebuilding a diagram.
- `setPrimaryKey(D, table, "a,b")` chooses a table's key (composite with commas); the FK columns of the child tables follow (stale ones are removed).

### Class diagram to ERD, SQL and code

```
addStereotypeToClasses(C, "KhachHang,DatPhong", "ORM Persistable", "")   // required: only persistable classes become tables
syncClassesToErd(C, "ERD - Karaoke", "KhachHang=maKhachHang; ChiTiet=maDon,maMon")   // "" = keys by name
generateSql("ERD - Karaoke", "sqlserver", "create,drop", "/abs/path/schema.sql")
generateCode(C, "java", "vn.karaoke.model", "/abs/path/src", true, true, "")    // or "C1, C2" for several diagrams
```

- `syncClassesToErd` maps attributes to typed columns (String -> varchar(255), int -> integer, Date -> date, decimal -> decimal(19,2), ...). Key: the `primaryKeys` choice, else an attribute named `id`, `<class>Id`, `ma<Class>` or with `<<PK>>`, else an `ID` column is added. One-to-many: FK in the many side (named after the referenced key: `maKhachHang`, or `phongHatId` for `id`); one-to-one: FK in the optional side; many-to-many: join table (the association name, else `A_B`); generalization: the subclass shares the superclass key. Re-running only adds what is missing and moves a changed key. Read the warnings it returns (no stereotype, association to a non-persistable class, missing multiplicity, untyped/unknown/multi-valued attribute) and fix the class diagram.
- `generateSql` uses Visual Paradigm's generator when Tools > DB > Database Configuration is set up for that database; otherwise it says so in a comment and writes the SQL itself.
- `generateCode` needs the class diagram(s) named; with `jpa` the `@JoinColumn`/`@JoinTable` names match `syncClassesToErd`, so pass the same `primaryKeys` to both.

## 7. Export and save

- `exportDiagramImage(D, "/absolute/path/diagram.png")` writes `.png`, `.jpg` or `.jpeg`. Use absolute paths and open the result. A community or evaluation edition of VP adds a footer or watermark to exported images; that is licensing, not a tool error.
- `generateUseCaseReport`, `generateClassReport`, `generateSequenceReport`, `generateErdReport`, `generateSql` give text output (counts, relationship list, SQL) when text is wanted instead of or beside the image. Other families: `getElementCounts(D)`.
- Finish with `saveProject` (or `saveProjectAs(filePath)` for a new `.vpp`; it does not overwrite an existing file). A modal VP dialog (save changes, delete from repository) can block `openProject` and quitting VP; the plugin answers the delete prompts itself, `newProject` answers the save prompt with No (it discards unsaved changes).

## 8. Proven limitations (do not retry)

These were tried exhaustively on Visual Paradigm Community Edition; they are limits of VP or its API, not mistakes in how you call the tools.

- Component diagram: a provided/required interface cannot be drawn as a lollipop or assembly connector. Interfaces are always `<<interface>>` boxes.
- Sequence diagram: a lifeline created through the API cannot be drawn as a stick figure. An `actor` lifeline is a box (a stick figure cannot anchor messages; they drift to x=0). If the user wants the figure they must change the shape by hand.
- Sequence diagram: `messageType="destroy"` ends the lifeline at the arrow but VP draws no destroy X.
- Sequence diagram: `lifelineType` cannot change after messages exist; the guard text of a combined fragment operand is drawn by VP at the operand's top-left corner and can touch the first lifeline; it has no movable caption.
- State diagram: VP always prints the name of an initial/final pseudo-state under the symbol; it cannot be hidden.
- Flowchart: the `database` symbol is VP's flat cylinder (no 3D, no gradient).
- Not available as tools: BPMN boundary events, text annotations, call activities, loop markers; UML composite states, choice, history, internal transitions; activity send-signal, accept-event and pins; sequence found/lost messages.
- Deep deployment nesting (machine -> server -> artifact) produces a wide strip about 5:1; dense communication diagrams with many pinned shapes can leave message labels tight.

Quirks:

- Fitting a shape to its content (`fitSize`, used internally and by width/height `0`) only takes effect on the diagram tab that is open; the tools open the diagram themselves, so avoid switching tabs by hand during a build.
- `setAssociationProperties` or a caption reset on an association can move its multiplicity labels; run `layoutConnectorLabels(D)` afterwards.
- A connector's end can look anchored at the top-left corner of a shape until the diagram is opened in VP; run `rerouteConnectors(D)` then.
- A diagram that holds containers or component ports has VP's own auto-fit switched off by the first layout; do not turn it back on.

## 9. Session checklist

1. Server answers (`initialize`), `getProjectInfo` shows the right project.
2. `listDiagrams` to avoid name clashes; plan elements, names and relationship directions first.
3. Create, add elements, add relationships; read every result string.
4. `getDiagramElements`, `getDiagramGeometry` (no `TRUNCATED`, only horizontal/vertical connector segments, no overlaps); `autoLayoutDiagram` on a violation.
5. `exportDiagramImage`, open and inspect the PNG; fix with remove/rename/set tools and re-export.
6. `saveProject`.
