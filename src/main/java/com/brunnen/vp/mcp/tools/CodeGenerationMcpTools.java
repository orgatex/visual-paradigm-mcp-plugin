package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ClassModelReader;
import com.brunnen.vp.mcp.util.CodeGenerator;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.OrmErdPlanner;
import com.vp.plugin.diagram.IClassDiagramUIModel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Code generation from a class diagram (an instant generator). Visual Paradigm's own Instant
 * Generator is not part of the plugin API, so the skeletons are written by {@link CodeGenerator}
 * from the classes, attributes, operations and relationships shown on the diagram.
 */
public class CodeGenerationMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "generateCode",
      description =
          "Instant generator: write source code skeletons for the classes, interfaces and"
              + " enumerations of the chosen class diagram(s). diagramName is required: one class"
              + " diagram, or several separated by commas (listDiagrams with type class lists"
              + " them); a class shown on two of them is written once. language: java (default),"
              + " csharp, python, typescript or php. One file per class with fields for the"
              + " attributes and the navigable association ends (a many end is a list), method"
              + " stubs for the operations, the superclass (generalization) and the interfaces"
              + " (realization). packageName is the package/namespace (\"\" for none). jpa (Java):"
              + " classes with the <<ORM Persistable>> stereotype get @Entity, @Id and"
              + " @OneToMany/@ManyToOne/@ManyToMany annotations whose columns match"
              + " syncClassesToErd. primaryKeys chooses the key attributes (@Id) like"
              + " syncClassesToErd: \"KhachHang=maKhachHang; ChiTiet=maDon,maMon\" (a composite key"
              + " gets an @IdClass); \"\" takes id, <class>Id, ma<Class> or a <<PK>> attribute."
              + " accessors: getters/setters (Java, PHP) or properties (C#). outputDir: an"
              + " absolute folder to write the files into (Java files go into package folders,"
              + " existing files are overwritten); empty returns the code in the result instead")
  public String generateCode(
      String diagramName,
      String language,
      String packageName,
      String outputDir,
      boolean jpa,
      boolean accessors,
      String primaryKeys) {
    try {
      if (diagramName == null || diagramName.trim().isEmpty()) {
        return "diagramName is required: choose the class diagram(s) to generate from, comma"
            + " separated (listDiagrams with type class lists them)";
      }
      String lang = CodeGenerator.normalizeLanguage(language);
      if (lang == null) {
        return "Unknown language '"
            + language
            + "': use one of "
            + String.join(", ", CodeGenerator.LANGUAGES);
      }
      java.util.Map<String, List<String>> keys = OrmErdPlanner.parseKeys(primaryKeys);
      List<String> names = new ArrayList<>();
      for (String n : diagramName.split(",")) {
        if (!n.trim().isEmpty()) {
          names.add(n.trim());
        }
      }
      List<String> missing = new ArrayList<>();
      List<CodeGenerator.CgClass> classes =
          runOnEdt(
              () -> {
                List<IClassDiagramUIModel> diagrams = new ArrayList<>();
                for (String n : names) {
                  IClassDiagramUIModel d =
                      (IClassDiagramUIModel)
                          DiagramUtils.findDiagramByName(n, IClassDiagramUIModel.class);
                  if (d == null) {
                    missing.add(n);
                  } else {
                    diagrams.add(d);
                  }
                }
                return ClassModelReader.codeClasses(
                    ClassModelReader.shown(diagrams.toArray(new IClassDiagramUIModel[0])), keys);
              });
      if (!missing.isEmpty()) {
        return "Class diagram not found: "
            + String.join(", ", missing)
            + " (listDiagrams with type class lists the class diagrams)";
      }
      if (classes.isEmpty()) {
        return "No classes on diagram: " + diagramName;
      }
      List<String> unknownKeys = new ArrayList<>();
      for (String cls : keys.keySet()) {
        CodeGenerator.CgClass found = null;
        for (CodeGenerator.CgClass c : classes) {
          if (c.name.equals(ClassModelReader.codeName(cls))) {
            found = c;
          }
        }
        if (found == null || found.fields.stream().noneMatch(f -> f.key)) {
          unknownKeys.add(cls + "=" + String.join(",", keys.get(cls)));
        }
      }
      Map<String, String> files =
          CodeGenerator.generate(lang, packageName, classes, jpa, accessors);
      StringBuilder out = new StringBuilder();
      String notes = notes(lang, jpa, classes);
      if (!unknownKeys.isEmpty()) {
        notes +=
            "; warning: primaryKeys matched no attribute for "
                + String.join("; ", unknownKeys)
                + " (class or attribute not on the diagram)";
      }
      if (outputDir == null || outputDir.trim().isEmpty()) {
        out.append("Generated ").append(files.size()).append(' ').append(lang).append(" file(s)");
        out.append(notes).append("\n");
        for (Map.Entry<String, String> f : files.entrySet()) {
          out.append("\n===== ").append(f.getKey()).append(" =====\n").append(f.getValue());
        }
        return out.toString();
      }
      File root = new File(outputDir.trim());
      if (!root.isAbsolute()) {
        return "outputDir must be an absolute path: " + outputDir;
      }
      for (Map.Entry<String, String> f : files.entrySet()) {
        File target = new File(root, f.getKey());
        Files.createDirectories(target.getParentFile().toPath());
        Files.write(target.toPath(), f.getValue().getBytes(StandardCharsets.UTF_8));
      }
      out.append("Wrote ")
          .append(files.size())
          .append(' ')
          .append(lang)
          .append(" file(s) to ")
          .append(root.getAbsolutePath())
          .append(": ")
          .append(String.join(", ", files.keySet()))
          .append(notes);
      return out.toString();
    } catch (Exception e) {
      return "Error generating code: " + e.getMessage();
    }
  }

  /** Warnings about the options: JPA asked for but nothing persistable, or not Java. */
  private static String notes(String lang, boolean jpa, List<CodeGenerator.CgClass> classes) {
    if (!jpa) {
      return "";
    }
    if (!lang.equals("java")) {
      return "; jpa applies to java only, ignored";
    }
    StringBuilder missing = new StringBuilder();
    boolean any = false;
    for (CodeGenerator.CgClass c : classes) {
      if (c.persistable) {
        any = true;
      } else if (c.kind.equals("class")) {
        missing.append(missing.length() == 0 ? "" : ", ").append(c.name);
      }
    }
    if (!any) {
      return "; warning: no class has the <<ORM Persistable>> stereotype, so no JPA annotations"
          + " were written (add it with addStereotypeToClasses)";
    }
    return missing.length() == 0
        ? ""
        : "; warning: no <<ORM Persistable>> stereotype, written without JPA: " + missing;
  }
}
