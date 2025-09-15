# Visual Paradigm MCP Plugin

A Model Context Protocol (MCP) server plugin for Visual Paradigm that provides
AI applications with seamless integration to Visual Paradigm's modeling and
diagramming capabilities.

## Architecture

- **Spring AI 1.1.0-M1**: Model Context Protocol integration
- **Visual Paradigm 17.2**: CAD/UML modeling platform integration
- **Maven**: Build automation and dependency management
- **MCP Transport**: Streamable HTTP communication with MCP clients
- **Visual Paradigm Plugin API**: Integration with Visual Paradigm's modeling capabilities
- **JUnit**: Use JUnit for unit tests
- **Mockito**: Use Mockito for mocks tests

## Features

### MCP (Planned)

#### Prompts

To be defined.

#### Resources

- Server Status: Monitor MCP server health and connection status
- Project Information: Retrieve current project details (name, ID, version)
- Case Diagrams: View Diagrams, Actors, Use Cases and Relationships

#### Tools

To be refined:

- Full support for Use Case Diagrams:
  - Diagram: Create, Update, Delete
  - Actors: Create, Update, Delete
  - Use Cases: Create, Update, Delete
  - Relationships: Create, Update, Delete

### Plugin User Interface

- **Toggle MCP Server**: Start/stop the MCP server from Visual Paradigm toolbar
- **Server Status**: View detailed MCP server status and capabilities

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

4. **Start Visual Paradigm** and look for the MCP toolbar actions

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

#### Unit and Integrations Tests

Run all tests:

```bash
./run test
```

### MCP Inspector (Planned)

Test with MCP Inspector for protocol validation:

```bash
./run inspector
```

### Show Available Tools

Display all MCP features and their descriptions:

```bash
./run inspector list
```

### Debugging

Enable detailed logging:

```bash
java -jar visual-paradigm-mcp-plugin.jar --logging.level.com.brunnen.vp=DEBUG
```

### Support

- **MCP Protocol**: [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-06-18/architecture)
- **Spring AI**: [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-overview.html)
- **Visual Paradigm**: [Plugin API Documentation](https://www.visual-paradigm.com/support/documents/pluginjavadoc/)
