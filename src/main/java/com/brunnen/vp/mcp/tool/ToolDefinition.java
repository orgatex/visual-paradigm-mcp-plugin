package com.brunnen.vp.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/** Describes a single MCP tool: name, description, input schema, and the method to invoke. */
public class ToolDefinition {

  private final String name;
  private final String description;
  private final ObjectNode inputSchema;
  private final Object target;
  private final Method method;

  public ToolDefinition(
      String name, String description, ObjectNode inputSchema, Object target, Method method) {
    this.name = name;
    this.description = description;
    this.inputSchema = inputSchema;
    this.target = target;
    this.method = method;
  }

  /** Constructor for proxy tools (no local method). */
  protected ToolDefinition(
      String name, String description, com.fasterxml.jackson.databind.JsonNode inputSchema) {
    this.name = name;
    this.description = description;
    if (inputSchema instanceof ObjectNode) {
      this.inputSchema = (ObjectNode) inputSchema;
    } else {
      // Deep-convert any JsonNode to ObjectNode
      ObjectMapper mapper = new ObjectMapper();
      this.inputSchema = mapper.convertValue(inputSchema, ObjectNode.class);
    }
    this.target = null;
    this.method = null;
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

  /** Execute this tool with the given arguments. Override in subclasses for proxy behavior. */
  public String execute(com.fasterxml.jackson.databind.JsonNode argsNode) throws Exception {
    method.setAccessible(true);
    java.lang.reflect.Parameter[] params = method.getParameters();
    Object[] args = new Object[params.length];

    for (int i = 0; i < params.length; i++) {
      String paramName = params[i].getName();
      Class<?> paramType = params[i].getType();
      com.fasterxml.jackson.databind.JsonNode argNode =
          argsNode != null ? argsNode.get(paramName) : null;

      if (argNode == null || argNode.isNull()) {
        args[i] = getDefaultValue(paramType);
      } else if (paramType == String.class) {
        args[i] = argNode.asText();
      } else if (paramType == int.class || paramType == Integer.class) {
        args[i] = argNode.asInt();
      } else if (paramType == boolean.class || paramType == Boolean.class) {
        args[i] = argNode.asBoolean();
      } else if (paramType == long.class || paramType == Long.class) {
        args[i] = argNode.asLong();
      } else if (paramType == double.class || paramType == Double.class) {
        args[i] = argNode.asDouble();
      } else {
        args[i] = argNode.asText();
      }
    }

    Object result = method.invoke(target, args);
    return result != null ? result.toString() : "OK";
  }

  private static Object getDefaultValue(Class<?> type) {
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == double.class) {
      return 0.0;
    }
    if (type == float.class) {
      return 0.0f;
    }
    return null;
  }

  /** Scan an object for @Tool-annotated methods and build ToolDefinitions. */
  public static java.util.List<ToolDefinition> scanTools(Object toolObject, ObjectMapper mapper) {
    java.util.List<ToolDefinition> defs = new java.util.ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    Class<?> clazz = toolObject.getClass();
    while (clazz != null && clazz != Object.class) {
      for (Method method : clazz.getDeclaredMethods()) {
        Tool annotation = method.getAnnotation(Tool.class);
        if (annotation == null) {
          continue;
        }
        String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
        if (!seen.add(toolName)) {
          continue;
        }
        String toolDesc = annotation.description();
        ObjectNode schema = buildInputSchema(method, mapper);
        defs.add(new ToolDefinition(toolName, toolDesc, schema, toolObject, method));
      }
      clazz = clazz.getSuperclass();
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
      } else if (type == int.class
          || type == Integer.class
          || type == long.class
          || type == Long.class) {
        propSchema.put("type", "integer");
      } else if (type == boolean.class || type == Boolean.class) {
        propSchema.put("type", "boolean");
      } else if (type == double.class
          || type == Double.class
          || type == float.class
          || type == Float.class) {
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
