# Visual Paradigm MCP tool catalogue (124 tools)

Source of truth: the `@Tool(name=...)` methods in `src/main/java/com/brunnen/vp/mcp/tools/*.java` of the plugin (server version 1.33.0.0). Tool names and parameter order below were checked against that source; regenerate this file when the plugin changes. If a tool you need is missing from the session's tool list (the list is fixed when the MCP client starts), call it over plain HTTP, see "Calling conventions".

## Calling conventions

- Call by parameter NAME. The schema marks EVERY parameter as required: for an unused parameter pass `""` (String), `0` (int) or `false` (boolean). Never pass null.
- Parameter column: `int` and `bool` are marked after a colon, everything else is a String.
- `x`, `y`: > 0 pins the top-left corner at that diagram coordinate; 0 lets the layout place the shape. `width`, `height`: > 0 sets the size; 0 = fit to content (default size).
- Tools return a string and do not throw. A failure is a string that starts with `Error`, or says `... not found`, `... already exists`, `Unknown ...`. Always read the result of every call.
- `create*` tools refuse an empty or duplicate diagram name. Calling a tool on a diagram of the wrong type answers `Diagram 'X' is a StateDiagram, ...`.
- Plain HTTP fallback: `curl -s -X POST http://localhost:2026/api/execute -H 'Content-Type: application/json' -d '{"toolName":"listDiagrams","arguments":{"type":""}}'` returns `{"result":"..."}` or `{"error":"..."}`. `GET http://localhost:2026/api/tools` lists every tool with its input schema.

## Project (5)

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `getProjectInfo` | (none) | Name and file path of the open project. Call it before editing to confirm the right `.vpp` is open. |
| `saveProject` | (none) | Save the project to its current file. |
| `saveProjectAs` | filePath | Save to a new `.vpp`; the parent folder must exist and an existing file is NOT overwritten; the new file becomes the open project. |
| `openProject` | filePath | Open another `.vpp`, replacing the open project (save first; a VP save dialog can block the call). |
| `newProject` | (none) | Close the current project and start an empty one (unsaved changes are lost). |

## Any diagram type (12)

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `listDiagrams` | type | List diagrams, filtered by type: UseCase, Class, Sequence, Communication, State, Activity, Component, Deployment, Package, Object, ER, BPMN, Flowchart, DFD (`""` = all). Exact type match. |
| `getDiagramElements` | diagramName | Every shape and connector with name, type, details and position. Connectors print as `Type <<stereotype>> name: from -> to [guard]`. |
| `getDiagramGeometry` | diagramName | Geometry dump: bounds of every shape (nested shapes indented), points of every connector, bounds and text of every caption. A caption too small for VP to draw all its text is marked `TRUNCATED`. |
| `getElementCounts` | diagramName | Count of elements by type. |
| `autoLayoutDiagram` | diagramName | Lay the whole diagram out again (ELK layered, orthogonal) and RELEASE every x/y pin. Sequence and BPMN keep their own layout. Needed only after pinning or moving shapes, because every `add*` already re-lays out. |
| `removeDiagramElement` | diagramName, elementName | Remove a shape or connector from the diagram by model name. A connector is also found by the text `getDiagramElements` prints (`A -> B`, `A -> B [guard]`) and a message by its number (`5: label`); a text matching several connectors removes none and lists them. Removes the VIEW only (with attached connectors and nested shapes); the model element stays in the project, except lifelines, links, messages, activity flows and state transitions, which exist only on their diagram. |
| `addNote` | diagramName, text, attachedTo, x:int, y:int | Add a note (folded-corner box) to any diagram. `attachedTo` = a shape name to anchor it with a dashed line (`""` = free-standing). Without x/y it goes to the first free spot beside the attached shape. Text wraps at about 30 characters. |
| `setElementBounds` | diagramName, elementName, x:int, y:int, width:int, height:int | Move/resize a shape and PIN it. x or y <= 0 keeps the current coordinate (resize only: `0, 0, w, h`); width or height <= 0 fits to content. A container (package, node, component, lane) takes its nested shapes along and never shrinks below its content; a sequence lifeline takes its messages and activation bar along; control nodes, pseudo-states and BPMN/flowchart/DFD symbols keep their notation size. |
| `rerouteConnectors` | diagramName | Redraw every connector with the orthogonal obstacle-aware router; shape positions stay. |
| `layoutConnectorLabels` | diagramName | Place connector labels (end multiplicities, name beside the longest segment) so they avoid shapes, lines and other labels; lines are re-routed, shapes stay. |
| `renameElement` | diagramName, elementName, newName | Rename an element the shape displays (class, package, node, state, action, use case, actor, object, lifeline, table...). `newName` must be unique on the diagram; every diagram showing the element follows; the shape is re-fitted. Not for connectors (add them again) or ERD columns (`renameColumn`). |
| `exportDiagramImage` | diagramName, filePath | Open the diagram and write a `.png`/`.jpg`/`.jpeg` to an ABSOLUTE path. Always open the image afterwards and look at it. |

## Use case (6)

Actors and use cases with a name already in the project are the SAME element (a new view is drawn).

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createUseCaseDiagram` | diagramName | Create a use case diagram (the "extension points" compartment is hidden). |
| `addActor` | actorName, diagramName, x:int, y:int | Add an actor. Default: a column on the left; pin x/y for e.g. an abstract parent actor above its children. NOTE: actor name comes first. |
| `addUseCase` | useCaseName, diagramName, x:int, y:int | Add a use case. NOTE: use case name comes first. |
| `addRelationship` | diagramName, sourceName, targetName, relationshipType | `relationshipType` = Include \| Extend \| Generalization \| Association. Include: source = base use case, target = included. Extend: source = extending (optional) use case, target = base. Generalization: source = child, target = parent (hollow triangle at the parent). A duplicate pair is reported, not redrawn. |
| `addSystemBoundary` | diagramName, systemName | Draw the system/module frame around all use cases (actors end up outside). Call after the use cases are final; calling again renames and re-fits the frame. |
| `generateUseCaseReport` | diagramName | Use case analysis report with element counts. |

## Class (23, not counting Project and any-diagram tools)

The class name is the lookup key. `addClass` reuses an existing class of the same name (at project root, or inside `modelPackage`) instead of creating `Class2`. `extendsClass` / `implementsInterfaces` must already be on the diagram. `addAttribute` / `addOperation` search the whole project unless `diagramName` is given.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createClassDiagram` | diagramName | Create a class diagram (auto-fit on). |
| `addClass` | diagramName, className, packageName, packageColor, stereotype, isAbstract:bool, extendsClass, implementsInterfaces, x:int, y:int, modelPackage | Add a class. Plain white box: leave `packageName`, `packageColor`, `stereotype` as `""`. With `packageName` the class is drawn inside that package frame. `extendsClass` also draws the generalization. `modelPackage` = model package that owns the class (no frame is drawn), used to tell apart same-named classes. |
| `addAttribute` | className, attributeName, attributeType, visibility, diagramName | Add an attribute. `attributeType` may be empty. `visibility` = public \| private \| protected \| package (or `+ - # ~`). With `diagramName`, only the class shown on that diagram is used. NOTE: class name comes first. |
| `addOperation` | className, operationName, returnType, params, visibility, isAbstract:bool, diagramName | Add an operation; `params` = `name: Type, name2: Type2`. Same lookup rule as `addAttribute`. |
| `removeAttribute` | diagramName, className, attributeName | Remove every attribute of that name from the class on this diagram; the box re-fits. To correct one: remove then add. |
| `removeOperation` | diagramName, className, operationName | Remove every overload of that name; the box re-fits. |
| `addAssociation` | diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity, name | Association. Multiplicity: a number, `*`, `n`, `m`, or `a..b`. `name` = relationship name. |
| `addGeneralization` | diagramName, fromClass, toClass | `fromClass` = CHILD, `toClass` = PARENT (hollow triangle at the parent). |
| `addAggregation` | diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity | Hollow diamond; `fromClass` = WHOLE (diamond at the from end), `toClass` = part. |
| `addComposition` | diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity | Filled diamond; `fromClass` = WHOLE, `toClass` = part. |
| `addDependency` | diagramName, fromClass, toClass | Dashed open arrow; `fromClass` depends on `toClass`. |
| `addRealization` | diagramName, fromClass, toClass | `fromClass` = implementing class, `toClass` = interface (hollow triangle at the interface). |
| `addInterface` | diagramName, interfaceName | Add an interface to the class diagram. |
| `addPackage` | diagramName, packageName, backgroundColor | Package with a background colour. Only when colour is wanted. |
| `addPackageShape` | diagramName, packageName, parentPackage, modelPackage, x:int, y:int, width:int, height:int, backgroundColor | Draw a package frame at given bounds; `parentPackage` nests it in another frame; `modelPackage` picks the owning model package; `backgroundColor` = `#RRGGBB` or `""`. For layer frames such as View/Controller/DAO/Model. |
| `moveIntoPackage` | diagramName, elementName, packageName | Put a class (or package) into a package frame on the same diagram: the package becomes owner in the model and container on the diagram; bounds are kept. |
| `addUsage` | diagramName, fromName, toName | `<<use>>` dependency (dashed, labelled) between two classes or two packages. |
| `setClassColor` | diagramName, className, backgroundColor | Set the fill colour of a class box. |
| `generateClassReport` | diagramName | Class diagram report: child classes first, visibility symbols, relationship kinds with multiplicities. |
| `addStereotypeToClasses` | diagramName, classNames, stereotype, robustnessIcon | Apply a stereotype (for example `ORM Persistable`) to a comma-separated class list, or `*` for all. `robustnessIcon` true/false draws boundary/control/entity as icons or as class boxes with members (`""` = unchanged). |
| `removeRelationship` | diagramName, fromClass, toClass, relationshipType | Delete relationships between two elements from the MODEL, on ANY diagram type. Class diagrams: Generalization (from = child) \| Association (either direction) \| Aggregation \| Composition \| Dependency \| Realization. Other diagram types (use case, activity, state, flowchart, DFD, BPMN, component, deployment, package...): `fromClass`/`toClass` are the two shape names and `relationshipType` (`""` or `*` = any) narrows by connector kind, stereotype or label; every matching connector between the two shapes in either direction is removed (this is how an unnamed connector is deleted). |
| `setAssociationProperties` | diagramName, fromClass, toClass, name, fromMultiplicity, toMultiplicity, fromRole, toRole, fromNavigable, toNavigable | Edit an existing association/aggregation/composition (either direction): name, multiplicities, roles (`-` clears a role), navigability true \| false \| unspecified. Empty string = keep. |
| `getRelationshipDetails` | diagramName | JSON audit dump: classes (abstract, stereotype, owner, attributes, operations, bounds) and every relationship (multiplicity, aggregation kind, role, navigability, connector points). |

## ERD (13)

Table and column names are SQL identifiers (letters, digits, `_`; no spaces or accents). A table with a name already in the project is the SAME table. Four tables per row.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createErd` | diagramName | Create an ER diagram. |
| `addTable` | diagramName, tableName | Add a table. |
| `addColumn` | diagramName, tableName, columnName, columnType, length:int, scale:int, isPrimaryKey:bool, isNullable:bool, isUnique:bool | Add a column to a table on the diagram. `columnType` is a SQL type (`varchar`, `integer`, `date`, `decimal(10,2)`; `nvarchar`, `datetime`, `bit` are mapped). A type with an inline length beats `length`/`scale`; `varchar` without length = 255; `isUnique` draws a "U" by the key. `diagramName` comes first. |
| `addForeignKey` | diagramName, fromTable, toTable, fromColumn, toColumn, relationshipName | `fromTable` = CHILD table that holds `fromColumn`, `toTable` = PARENT (`toColumn` empty = its primary key). An existing `fromColumn` becomes the FK column; empty makes VP add one named after the parent. Crow's foot at the child. |
| `addTableRelationship` | diagramName, fromTable, toTable, type, fromMultiplicity, toMultiplicity | `fromTable` = PARENT, `toTable` = CHILD (the OPPOSITE of `addForeignKey`). `type` = identifying \| nonIdentifying \| `""`. Parent multiplicity `1` \| `0..1`; child `0..1` \| `0..*` \| `1` \| `1..*`. Also creates the FK column; a table pair that already has a relationship is refused. |
| `removeColumn` | diagramName, tableName, columnName | Delete a column from the project (an FK column needs `removeForeignKey` first). |
| `renameColumn` | diagramName, tableName, columnName, newName | Rename a column. |
| `removeTable` | diagramName, tableName | Delete a table from the project (all diagrams lose it); refused while it takes part in an FK. |
| `removeForeignKey` | diagramName, parentTable, childTable | Remove the FK between the tables; the FK column stays in the child. |
| `setPrimaryKey` | diagramName, tableName, columnNames | Make exactly these columns (comma-separated = composite) the key; the FK columns of the child tables follow (stale ones removed, new ones named after the key). |
| `syncClassesToErd` | classDiagramName, erdName, primaryKeys | Class diagram to ERD: each `<<ORM Persistable>>` class becomes a table on `erdName` (created when missing, existing tables reused). `primaryKeys` = `"Class=attr1,attr2; Class2=attr"` or `""` (keys by name: id, `<class>Id`, `ma<Class>`, `<<PK>>`; else an `ID` column). 1-* = FK in the many side, 1-1 = FK in the optional side, *-* = join table, generalization = shared key. Returns warnings for classes without the stereotype, associations to non-persistable classes, missing multiplicities, untyped/unknown/multi-valued attributes. |
| `generateSql` | diagramName, database, statements, outputFile | SQL for the tables of an ERD. `database`: MySQL (default), MariaDB, PostgreSQL/postgres, MSSQLServer/sqlserver, Oracle, SQLite, H2, ... `statements`: comma list of create (default), drop, select, insert, update, delete. VP's generator when configured, else the plugin's dialect generator. `outputFile`: absolute path or `""`. |
| `generateErdReport` | diagramName | ERD report (also lists generated columns). |

## Code generation (1)

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `generateCode` | diagramName, language, packageName, outputDir, jpa:bool, accessors:bool, primaryKeys | Instant generator. `diagramName` is required: one class diagram or several, comma-separated. `language`: java (default), csharp, python, typescript, php. Fields for attributes and navigable association ends, operation stubs, superclass, interfaces. `jpa` (Java): `<<ORM Persistable>>` classes get `@Entity`, `@Id`, relationship annotations matching `syncClassesToErd` (composite key = `@IdClass`). `accessors`: getters/setters or C# properties. `outputDir`: absolute folder, or `""` to return the code. |

## Sequence (7)

Messages refer to lifelines by `lifelineName`. A sequence number that is already used is refused. Never type `N:` in a label (VP prints the number itself).

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createSequenceDiagram` | diagramName | Create a sequence diagram. |
| `addLifeline` | diagramName, lifelineName, className, lifelineType, alias | Add a participant. `lifelineName` = head label and handle (unique on the diagram). `className` empty = use `lifelineName`; an existing class or actor of that name is reused, not modified. `lifelineType` = actor \| boundary \| control \| entity (icon for this lifeline only) or `""`/participant = plain box; an actor is always drawn as a box. Leave `alias` empty. |
| `addActivation` | diagramName, lifelineName | Add an activation bar (optional: messages create bars themselves and the tool merges them into one solid bar). |
| `addMessage` | diagramName, fromLifeline, toLifeline, messageName, sequenceNumber, messageType | `messageType` = synch/sync (default, solid, filled head) \| asynch/async (open head) \| create (first message to the lifeline, dashed, lowers the lifeline head to the arrow) \| destroy (last message to a lifeline; no X is drawn). `sequenceNumber` empty = next top-level number; `5.1` style is supported. `from == to` is a self-message. A label wider than its arrow pushes the lifelines to the right apart. |
| `addReturnMessage` | diagramName, fromLifeline, toLifeline, messageName, sequenceNumber | Return message (dashed, open head); `sequenceNumber` as in `addMessage`. |
| `addCombinedFragment` | diagramName, operator, guard, coveredLifelines, fromSeq:int, toSeq:int, operandStarts | Frame alt/opt/loop/break/par around the messages `fromSeq`..`toSeq` (top-level numbers, including 4.1, 4.2...; 0 = first/last). Add the MESSAGES first. Several operands: separate guards with `\|`; `operandStarts` = first message number of operands 2..n (empty = split evenly); `coveredLifelines` empty = all. |
| `generateSequenceReport` | diagramName | Sequence report (lifeline and message counts). |

## State machine (5)

Needs plugin 1.28.4 or later. A state name is the handle used by `addTransition`.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createStateDiagram` | diagramName | Create a state machine diagram. |
| `addState` | diagramName, stateName, entryAction, doActivity, exitAction, x:int, y:int | Add a state. Non-empty `entryAction`/`doActivity`/`exitAction` show `entry / ...`, `do / ...`, `exit / ...` compartments; the box fits its text. Without x/y: a 3-column grid, a new state drops below the source of its first incoming transition. |
| `addInitialState` | diagramName, stateName, x:int, y:int | Initial pseudo-state (filled circle). VP always prints the model name under it, so pick a presentable one (`Start`). |
| `addFinalState` | diagramName, stateName, x:int, y:int | Final state (bull's-eye); name printed under it (`End`). |
| `addTransition` | diagramName, fromState, toState, label | Transition; `label` = `event [guard] / effect` (empty = no label). `fromState == toState` makes a rectangular self-loop; opposite transitions run side by side. |

## Activity (7)

`actionName`, `nodeName`, `objectName` are the handles used by `addActivityFlow`, so they must be unique on the diagram. A trailing ` #2` is part of the handle only and is not displayed.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createActivityDiagram` | diagramName | Create an activity diagram (shapes fit their text). |
| `addActivityPartition` | diagramName, partitionName, x:int, y:int, width:int, height:int | Add a vertical swimlane. The first lane creates the frame at x/y (default 40,40); later lanes attach on the right (x/y ignored); width/height > 0 resize (default 240x480); all lanes share one height and grow with their nodes. Add lanes BEFORE nodes. |
| `addActivityAction` | diagramName, actionName, partitionName, x:int, y:int | Add an action (rounded box). `partitionName` = lane (`""` = none). |
| `addActivityControlNode` | diagramName, kind, nodeName, partitionName, x:int, y:int | `kind` = initial \| final \| flowFinal \| decision \| merge \| fork \| join. A decision shows `nodeName` as its question (`Valid?`); initial/final show the name; fork/join are horizontal bars. |
| `addActivityObjectNode` | diagramName, objectName, state, partitionName, x:int, y:int | Object node; `state` is shown as `[state]` under the name. Flows to/from it become object flows. |
| `addActivityFlow` | diagramName, fromNode, toNode, guard, twoWay:bool | Connect two nodes by name. `guard` shows `[guard]` (put it on every edge leaving a decision). `twoWay=true` draws one double-headed pair of arrows (action <-> "XxxDAO: method()"). |
| `addActivityFrame` | diagramName, title, nodeNames, x:int, y:int, width:int, height:int | Rounded titled frame around nodes. `nodeNames` = comma-separated nodes to enclose (add AFTER nodes and flows are done); empty = use x/y/width/height. Same `title` again = re-fit. |

## Component (8)

VP prints `<<component>>` itself; do not pass it as stereotype. An interface is an `<<interface>>` box, never a lollipop.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createComponentDiagram` | diagramName | Create a component diagram. |
| `addComponent` | diagramName, componentName, stereotype, parentComponent, x:int, y:int, packageName | Add a component. `stereotype` empty or e.g. `subsystem` (comma-separated for several); `parentComponent` nests it in another component; `packageName` places it in a layer frame made by `addComponentPackage`. An existing project component is reused. |
| `addComponentInterface` | diagramName, interfaceName, x:int, y:int | Interface drawn as an `<<interface>>` box; an interface of the same name is reused. |
| `addProvidedInterface` | diagramName, componentName, interfaceName | Component provides the interface: dashed realization with a hollow triangle at the interface box. `componentName` may be a port. |
| `addRequiredInterface` | diagramName, componentName, interfaceName | Component requires the interface: dashed `<<use>>` arrow to the interface box. `componentName` may be a port. |
| `addComponentDependency` | diagramName, fromComponent, toComponent, stereotype | Dashed dependency between components, interfaces or ports; `stereotype` (`use`, `call`...) is shown as `<<stereotype>>`. |
| `addPort` | diagramName, componentName, portName | Port (small square on the border); the layout puts it on the side its lines lead away to. A dependency between a port and its own component is refused. |
| `addComponentPackage` | diagramName, packageName, parentPackage, x:int, y:int, width:int, height:int, backgroundColor | Layer frame ("Presentation Layer"...). `parentPackage` nests frames; components added with `packageName` sit inside and the frame grows. Without x/y the layout stacks a layer above the layers it depends on. `backgroundColor` = `#RRGGBB` (`""` = default). A package of the same name is reused. |

## Deployment (7)

Node, artifact and component names are unique on the diagram. Nodes are flat rectangles with keyword and name.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createDeploymentDiagram` | diagramName | Create a deployment diagram. |
| `addDeploymentNode` | diagramName, nodeName, kind, parentNode, x:int, y:int, width:int, height:int | `kind` = node \| device \| executionEnvironment (`""` = node). `parentNode` nests it (Tomcat inside Web Server). Create the parent first. |
| `addDeploymentArtifact` | diagramName, artifactName, nodeName, x:int, y:int | Artifact (`app.war`, `schema.sql`). `nodeName` empty = stand-alone (link with `addDeployDependency`); a node name = deployed inside that node; an artifact name = drawn inside that artifact. |
| `addDeploymentComponent` | diagramName, componentName, nodeName, x:int, y:int | Component inside a node or artifact; components of one host follow their dependencies left to right and the host grows. |
| `addCommunicationPath` | diagramName, fromNode, toNode, label | Solid line between two nodes; `label` = protocol (`1: HTTP/HTTPS`); a `<<TCP/IP>>` label shows as a stereotype. |
| `addDeployDependency` | diagramName, artifactName, nodeName | `<<deploy>>` dependency from a stand-alone artifact to a node. |
| `addDeploymentDependency` | diagramName, fromElement, toElement, label | Dashed dependency between components, artifacts or nodes; a `<<manifest>>`-style label becomes a stereotype. |

## Package and object (8)

Add the children of a package before the next top-level package so the frames are placed without overlap.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createPackageDiagram` | diagramName | Create a package diagram. |
| `addPackageElement` | diagramName, packageName, parentPackage, x:int, y:int, width:int, height:int, backgroundColor | Package frame; `parentPackage` nests it (the parent grows). Without x/y a package is placed above the packages it depends on; minimum 100x50. `backgroundColor` = `#RRGGBB`. A package of the same name is reused. |
| `addPackageDependency` | diagramName, fromPackage, toPackage, kind | `kind` = use \| import \| access \| merge \| line (`""` = use). `line` = plain line, no arrow, no keyword; `use` = dashed `<<use>>` with an open arrow. An end may be a class box that sits inside a package. |
| `addClassInPackage` | diagramName, packageName, className | Name-only class box inside a package frame; the class belongs to that package in the model. |
| `createObjectDiagram` | diagramName | Create an object diagram (slots visible). |
| `addObject` | diagramName, objectName, className, slots, x:int, y:int, modelPackage | Instance `objectName : ClassName` (underlined). Empty `objectName` + `className` = anonymous `: ClassName` (handle = class name). `slots` = `attr=value;attr2=value2`, each attr must be an attribute of the class or a superclass. A missing class is created. `modelPackage` disambiguates same-named classes. |
| `addObjectLink` | diagramName, fromObject, toObject, label | Solid link between two objects; empty `label` = none. |
| `setObjectSlot` | diagramName, objectName, attribute, value | Set, change or clear (`value` = `""`) one slot; the box re-fits. Use it instead of deleting the object. |

## Communication (5)

`addCommunicationMessage` takes `sequenceNumber` BEFORE `messageText` (the reverse of sequence `addMessage`).

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createCommunicationDiagram` | diagramName | Create a communication diagram (message numbers shown, shapes fit their text). |
| `addCommunicationLifeline` | diagramName, lifelineName, classifierName, isActor:bool, x:int, y:int, lifelineType | Add a participant. `lifelineName` = label and handle; `classifierName` makes `name : Classifier`; empty `lifelineName` + `classifierName` = anonymous `:Classifier` (handle = classifier name). `isActor=true` draws a stick figure. `lifelineType` = boundary \| control \| entity draws the robustness icon, `""` = plain box. |
| `addCommunicationLink` | diagramName, fromLifeline, toLifeline | Link the messages run along; same name at both ends = self link; a duplicate reports the existing link. |
| `addCommunicationMessage` | diagramName, fromLifeline, toLifeline, sequenceNumber, messageText | Numbered message drawn as an arrow beside its link (the link is created when missing), direction from -> to. `sequenceNumber` is a UML expression (`1`, `1.2`, `2a`, `1.1*`); empty = next top-level number. A leading `1.2:` in `messageText` is stripped. |
| `moveCommunicationLifeline` | diagramName, lifelineName, x:int, y:int | Move a participant (top-left) and pin it; its links and message arrows are laid out again. |

## Flowchart and DFD (8)

A DFD flow must touch a process: entity-to-store, store-to-store and entity-to-entity flows are refused.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createFlowchart` | diagramName | Create a flowchart. |
| `addFlowchartShape` | diagramName, shapeName, kind, x:int, y:int | `kind` = terminator \| process \| decision \| inputOutput \| document \| predefinedProcess \| database \| manualInput \| connector. `shapeName` is displayed and is the handle (unique). Default placement: one column top to bottom, 50 px apart. |
| `addFlowchartFlow` | diagramName, fromShape, toShape, label | Arrow between two shapes; `label` such as Yes/No. |
| `createDataFlowDiagram` | diagramName, notation | `notation` = `""`/standard \| ganeSarson \| yourdonDeMarco \| yourdonCoad. The `addDfd*` tools follow the diagram's notation. |
| `addDfdProcess` | diagramName, processName, processId, x:int, y:int | Process. `processId` = number shown on it (`1`, `1.0`, `1.1`); empty = next number. Refer to it later by name or ID. |
| `addDfdExternalEntity` | diagramName, entityName, entityId, x:int, y:int | External entity; `entityId` = marker a, b, c in the corner (empty = next letter). |
| `addDfdDataStore` | diagramName, storeName, storeId, x:int, y:int | Data store; `storeId` = `D1` or `1` (VP prints the D); empty = next number. |
| `addDfdDataFlow` | diagramName, fromElement, toElement, label | Data flow (by name or ID); `label` = the data moved (a noun). |

## BPMN (9)

Element names are handles for the flow tools and must be unique on the diagram. Not supported: boundary events, text annotations, call activities, loop markers.

| Tool | Parameters (in order) | Purpose |
|------|-----------------------|---------|
| `createBpmnDiagram` | diagramName | Create a BPMN 2.0 diagram. |
| `addBpmnPool` | diagramName, poolName, x:int, y:int, width:int, height:int | Pool as a horizontal band, name on the left; default 900x160 (height 40-60 = black-box pool); grows with its lanes and elements. |
| `addBpmnLane` | diagramName, poolName, laneName | Lane in a pool, stacked top to bottom in the order added. Add ALL lanes of a pool before placing elements. |
| `addBpmnTask` | diagramName, taskName, laneName, kind, x:int, y:int | `kind` = task \| user \| service \| manual \| send \| receive \| script \| subprocess. `laneName` = lane (or a pool without lanes); `""` = outside any pool. |
| `addBpmnEvent` | diagramName, eventName, laneName, position, trigger, x:int, y:int | `position` = start \| intermediate \| end; `trigger` = none (`""`) \| message \| timer \| error \| signal \| terminate (terminate only for end; end events take no timer). Name shown under the circle. |
| `addBpmnGateway` | diagramName, gatewayName, laneName, kind, x:int, y:int | `kind` = exclusive (`""`) \| parallel \| inclusive \| eventBased. The name is the question shown above the diamond. |
| `addBpmnDataObject` | diagramName, name, laneName, x:int, y:int | Data object (`Invoice`, `Order [approved]`); connect with `addBpmnSequenceFlow` to get a data association. |
| `addBpmnSequenceFlow` | diagramName, fromElement, toElement, condition | Solid flow inside ONE pool; `condition` labels the branch (`Yes`, `[in stock]`). BPMN rules are checked (no flow out of an end, into a start, or across pools). To/from a data object it becomes a dotted data association. |
| `addBpmnMessageFlow` | diagramName, fromElement, toElement, label | Dashed message flow between TWO different pools (open circle at the start); ends may be a task, sub-process, event or a pool name. |
