package com.brunnen.vp.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/** Describes a single MCP tool: name, description, input schema, and the method to invoke. */
public final class ToolDefinition {

  private final String name;
  private final String description;
  private final ObjectNode inputSchema;
  private final Object target;
  private final Method method;

  public ToolDefinition(String name, String description, ObjectNode inputSchema, Object target, Method method) {
    this.name = name;
    this.description = description;
    this.inputSchema = inputSchema;
    this.target = target;
    this.method = method;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ObjectNode getInputSchema() {
    return inputSchema;
  }

  public Object getTarget() {
    return target;
  }

  public Method getMethod() {
    return method;
  }

  /** Scan an object for @Tool-annotated methods and build ToolDefinitions. */
  public static java.util.List<ToolDefinition> scanTools(Object toolObject, ObjectMapper mapper) {
    java.util.List<ToolDefinition> defs = new java.util.ArrayList<>();
    for (Method method : toolObject.getClass().getDeclaredMethods()) {
      Tool annotation = method.getAnnotation(Tool.class);
      if (annotation == null) {
        continue;
      }
      String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
      String toolDesc = annotation.description();
      ObjectNode schema = buildInputSchema(method, mapper);
      defs.add(new ToolDefinition(toolName, toolDesc, schema, toolObject, method));
    }
    return defs;
  }

  private static ObjectNode buildInputSchema(Method method, ObjectMapper mapper) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode properties = mapper.createObjectNode();
    java.util.List<String> required = new java.util.ArrayList<>();

    Parameter[] params = method.getParameters();
    for (Parameter param : params) {
      String paramName = param.getName();
      ObjectNode propSchema = mapper.createObjectNode();
      Class<?> type = param.getType();
      if (type == String.class) {
        propSchema.put("type", "string");
      } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
        propSchema.put("type", "integer");
      } else if (type == boolean.class || type == Boolean.class) {
        propSchema.put("type", "boolean");
      } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
        propSchema.put("type", "number");
      } else {
        propSchema.put("type", "string");
      }
      properties.set(paramName, propSchema);
      required.add(paramName);
    }

    schema.set("properties", properties);
    com.fasterxml.jackson.databind.node.ArrayNode requiredArr = mapper.createArrayNode();
    for (String r : required) {
      requiredArr.add(r);
    }
    schema.set("required", requiredArr);
    return schema;
  }
}
