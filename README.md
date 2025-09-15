# Visual Paradigm MCP Plugin

A Model Context Protocol (MCP) server plugin for Visual Paradigm that provides
AI applications with seamless integration to Visual Paradigm's modeling and
diagramming modeling capabilities. The MCP server is embedded directly within
the Visual Paradigm plugin, starting automatically when the plugin loads.

## Architecture

- **Spring AI 1.1.0-M1**: Model Context Protocol server integration
- **Visual Paradigm 17.2**: UML modeling platform integration
- **Maven**: Build automation and dependency management
- **MCP Transport**: Streamable HTTP communication with MCP clients
- **Visual Paradigm Plugin API**: Integration with Visual Paradigm's modeling capabilities
- **JUnit & Mockito**: Comprehensive testing framework

## Features

### MCP Server Integration

The plugin includes an embedded MCP server that:

- **Auto-starts** when Visual Paradigm plugin is loaded
- **Auto-stops** when Visual Paradigm plugin is unloaded
- Runs on **port 8080** with SSE endpoint `/mcp/messages`
- Provides **tool capabilities** for external MCP clients

#### Available MCP Tools

- **createUseCaseDiagram(diagramName)**: Create new use case diagrams
- **addActor(actorName, diagramName)**: Add actors to specific diagrams
- **addUseCase(useCaseName, diagramName)**: Add use cases to diagrams
- **addRelationship(actorName, useCaseName, relationshipType)**: Create relationships between actors and use cases
- **generateReport(diagramName)**: Generate reports for use case diagrams

#### Future MCP Features (Planned)

##### Resources

- Server Status: Monitor MCP server health and connection status
- Project Information: Retrieve current project details
- Diagram Metadata: Access diagram properties and structure

##### Prompts

- Use Case Templates: Generate standard use case patterns
- Diagram Validation: Check diagram completeness and consistency

### Plugin User Interface

- **Toggle MCP Server**: Start/stop the MCP server from Visual Paradigm toolbar
- **Server Status**: View detailed MCP server status and capabilities

### Plugin Integration

- **Automatic Lifecycle Management**: MCP server starts/stops with plugin
- **Error Handling**: Robust startup/shutdown with detailed logging
- **Spring Boot Integration**: Full Spring framework capabilities within Visual Paradigm

## Usage

### Installation

Build, test and install with the `./run` command:

1. **Build the plugin**:

   ```bash
   ./run build
   ```

2. **Package for distribution**:

   ```bash
   ./run package
   ```

3. **Install to Visual Paradigm**:

   ```bash
   ./run install
   ```

4. **Start Visual Paradigm** - the MCP server will start automatically

### Using the MCP Server

Once Visual Paradigm is running with the plugin:

- **MCP Server Endpoint**: `http://localhost:8080/mcp/messages` (SSE)
- **Server Name**: `visual-paradigm-use-case-mcp-server`
- **Available Tools**: 5 use case diagram operations

#### Connecting with Claude or MCP Clients

Configure your MCP client to connect to:

```json
{
  "transport": {
    "type": "sse",
    "url": "http://localhost:8080/mcp/messages"
  }
}
```

## Development

### Building

```bash
./run build
./run test
./run package
./run all          # Build, test, package, and install
```

### Code Quality

```bash
./run format       # Format code with Google Java Format
./run spotbugs     # Run SpotBugs static analysis
./run pmd          # Run PMD static analysis
```

### Testing

- **Unit and Integration Tests**: Comprehensive Mockito/JUnit tests for all components
- **System Tests**: MCP Inspector protocol validation
- **Manual Testing**: Visual Paradigm UI integration testing
- **Spring Context Tests**: Verify MCP server configuration
- **Tool Service Tests**: Validate all MCP tool implementations
- **Plugin Lifecycle Tests**: Test integration with Visual Paradigm

#### Unit and Integration Tests

```bash
./run test
```

#### MCP Protocol Testing (Future)

Test with MCP Inspector for protocol validation:

```bash
./run inspector test
```

Display all MCP features and their descriptions:

```bash
./run inspector list
```

### Debugging

**MCP Server Logging**: Check Visual Paradigm console output for:

- `"MCP Server started for Use Case Plugin"` - successful startup
- `"MCP Server stopped"` - clean shutdown
- Error messages if startup fails

**Configuration**: Located in `src/main/resources/application-mcp.properties`

```properties
logging.level.org.springframework.ai.mcp=DEBUG
logging.level.root=INFO
```

### Support

- **MCP Protocol**: [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-06-18/architecture)
- **Spring AI**: [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-overview.html)
- **Visual Paradigm**: [Plugin API Documentation](https://www.visual-paradigm.com/support/documents/pluginjavadoc/)
