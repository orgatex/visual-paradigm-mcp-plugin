# Visual Paradigm MCP Plugin

A Model Context Protocol (MCP) server plugin for Visual Paradigm that provides
AI applications with seamless integration to Visual Paradigm's modeling and
diagramming capabilities.

## Architecture

- **MCP Java SDK**: Standardized protocol implementation for AI client communication
- **MCP Transport**: Streamable HTTP communication with MCP clients
- **Visual Paradigm Plugin API**: Integration with Visual Paradigm's modeling capabilities
- **JUnit**: Use JUnit for unit tests
- **Mockito**: Use Mockito for mocks tests

## Project Status

### Phase 1: Use Case Plugin (COMPLETED)
- Replaced confluence plugin with working Use Case Plugin example
- Implemented 4 action controllers with toolbar buttons
- Full build, test, and deployment pipeline working
- Demonstrates Visual Paradigm Plugin API usage

### Phase 2: Implement MCP server functionality
### Phase 3: Add MCP tools for use case diagram manipulation
### Phase 4: Add MCP resources for diagram viewing
### Phase 5: Add MCP prompts for AI-assisted modeling

## Features

### MCP (Planned)

#### Prompts

To be defined.

#### Resources

To be defined:

- Full support for Use Case Diagrams:
  - View Diagram, Use Cases, Relationships

#### Tools

To be refined:

- Full support for Use Case Diagrams:
  - Diagram: Create, View, Update, Delete
  - Use Cases: Create, View, Update, Delete
  - Relationships: Create, View, Update, Delete

### Plugin User Interface

**Phase 1 - Use Case Plugin Implementation:**
- **Create Use Case Diagram**: Creates new use case diagrams
- **Add Use Case**: Adds use case elements to active diagrams
- **Add Actor**: Adds actor elements to active diagrams
- **Generate Report**: Creates summary reports of project use cases and actors

**Planned MCP Interface:**
- **MCP Status**: Check server status
- **Toggle MCP Server**: Start/stop the server
- **MCP Settings**: View configuration information

## Usage

Build, test and install with the `./run` command:

```bash
./run all      # Complete build, test, package and install process
./run build    # Compile the source code
./run test     # Run unit tests
./run package  # Create the plugin package
./run install  # Install to Visual Paradigm
```

### Configuration

#### Visual Paradigm Plugin
The current Use Case Plugin is automatically installed to `~/.config/VisualParadigm/plugins/` and provides a working example of Visual Paradigm Plugin API usage.

#### Claude Desktop Configuration (Planned)
MCP server integration will be configured here once MCP functionality is implemented.
