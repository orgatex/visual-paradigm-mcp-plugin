package com.brunnen.vp.mcp.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as an MCP tool. Java 11-compatible replacement for Spring AI's @Tool. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {
  String name() default "";

  String description() default "";
}
