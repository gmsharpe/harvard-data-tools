package edu.harvard.data.client.generator.java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.data.client.generator.SchemaPhase;
import edu.harvard.data.client.generator.SchemaTransformer;
import edu.harvard.data.client.schema.DataSchemaTable;
import edu.harvard.data.client.schema.DataSchemaType;

public class JavaBindingGenerator {

  private static final Logger log = LogManager.getLogger();

  private static final String POM_XML_TEMPLATE = "pom.xml.template";

  private final SchemaTransformer schemaVersions;
  private final File dir;
  private final String projectName;

  public JavaBindingGenerator(final File dir, final SchemaTransformer schemaVersions, final String projectName) {
    this.dir = dir;
    this.schemaVersions = schemaVersions;
    this.projectName = projectName;
  }

  // Generates a new Maven project in the directory passed to the constructor.
  // The project has a pom.xml file and three sets of bindings (one for each
  // stage of data processing):
  //
  // Phase Zero bindings are generated from the JSON schema provided by
  // Instructure (passed to the class constructor).
  //
  // Phase One bindings are produced by the first EMR job which supplements the
  // existing data set with new calculated data. The new tables and fields are
  // specified in PHASE_ONE_ADDITIONS_JSON.
  //
  // Phase Two bindings are produced by the second EMR job, and result from the
  // merging of multiple data sets. The new tables and fields are specified in
  // PHASE_TWO_ADDITIONS_JSON.
  //
  public void generate() throws IOException {
    // Create the pom.xml file from a template in src/main/resources, with the
    // appropriate version number.
    copyPomXml(schemaVersions.getPhase(0));

    // Generate bindings for each step in the processing pipeline.
    generateTableSet(schemaVersions.getPhase(0), null);
    generateTableSet(schemaVersions.getPhase(1), schemaVersions.getPhase(0));
    generateTableSet(schemaVersions.getPhase(2), schemaVersions.getPhase(1));
  }

  // Generate the bindings for one step in the processing pipeline. There are
  // three generators used:
  //
  // TableGenerator creates the Table enum type with a constant for each table.
  //
  // TableFactoryGenerator creates the TableFactory subtype that lets us create
  // readers and writers dynamically.
  //
  // TableGenerator is run once per table, and creates the individual table
  // class.
  //
  private void generateTableSet(final SchemaPhase tableVersion, final SchemaPhase previousVersion)
      throws IOException {
    final File srcDir = tableVersion.getJavaSourceLocation();
    final String classPrefix = tableVersion.getPrefix();
    final String version = tableVersion.getSchema().getVersion();
    final Map<String, DataSchemaTable> tables = tableVersion.getSchema().getTables();

    // Create the base directory where all of the classes will be generated
    log.info("Generating tables in " + srcDir);
    if (srcDir.exists()) {
      log.info("Deleting: " + srcDir);
      FileUtils.deleteDirectory(srcDir);
    }
    srcDir.mkdirs();
    final List<String> tableNames = generateTableNames(tables);

    // Generate the Table enum.
    final File tableEnumFile = new File(srcDir,
        classPrefix + tableVersion.getTableEnumName() + ".java");
    try (final PrintStream out = new PrintStream(new FileOutputStream(tableEnumFile))) {
      new TableEnumGenerator(version, tableNames, tableVersion).generate(out);
    }

    // Generate the TableFactory class.
    final File tableFactoryFile = new File(srcDir,
        classPrefix + tableVersion.getTableEnumName() + "Factory.java");
    try (final PrintStream out = new PrintStream(new FileOutputStream(tableFactoryFile))) {
      new TableFactoryGenerator(version, tableNames, tableVersion).generate(out);
    }

    // Generate a model class for each table.
    for (final String name : tables.keySet()) {
      final String className = javaClass(tables.get(name).getTableName(), classPrefix);
      final File classFile = new File(srcDir, className + ".java");
      try (final PrintStream out = new PrintStream(new FileOutputStream(classFile))) {
        new ModelClassGenerator(version, tableVersion, previousVersion, tables.get(name))
        .generate(out);
      }
    }
  }

  // Generate the pom.xml file for the Maven project, based off a template in
  // the src/main/resources directory.
  private void copyPomXml(final SchemaPhase tableVersion) throws IOException {
    final File pomFile = new File(dir, "pom.xml");
    log.info("Creating pom.xml file at " + pomFile);
    try (
        InputStream inStream = this.getClass().getClassLoader()
        .getResourceAsStream(POM_XML_TEMPLATE);
        BufferedReader in = new BufferedReader(new InputStreamReader(inStream));
        BufferedWriter out = new BufferedWriter(new FileWriter(pomFile))) {
      String line = in.readLine();
      while (line != null) {
        // Replace the $artifact_id variable in the template.
        out.write(line.replaceAll("\\$artifact_id", projectName) + "\n");
        line = in.readLine();
      }
    }
  }

  // Generate a sorted list of table names for the switch tables in the enum and
  // factory classes
  static List<String> generateTableNames(final Map<String, DataSchemaTable> tables) {
    final List<String> tableNames = new ArrayList<String>();
    for (final String name : tables.keySet()) {
      tableNames.add(tables.get(name).getTableName());
    }
    Collections.sort(tableNames);
    return tableNames;
  }

  // Write a standard file header to warn future developers against editing the
  // generated files.
  static void writeFileHeader(final PrintStream out, final String version) {
    writeComment("This file was generated on "
        + new SimpleDateFormat("M-dd-yyyy hh:mm:ss").format(new Date()) + ". Do not manually edit.",
        0, out, false);
    writeComment("This class is based on Version " + version + " of the schema", 0, out,
        false);
    out.println();
  }

  // Output a comment string, propery formatted. Uses the double-slash format
  // unless 'javadoc' is set, in which case it will use the /**...*/ format.
  static void writeComment(final String text, final int indent, final PrintStream out,
      final boolean javadoc) {
    if (text == null) {
      return;
    }
    if (javadoc) {
      writeIndent(indent, out);
      out.println("/**");
    }
    final int maxLine = 80;
    startNewCommentLine(indent, out, javadoc);
    int currentLine = indent + 3;
    for (final String word : text.split(" ")) {
      currentLine += word.length() + 1;
      if (currentLine > maxLine) {
        out.println();
        startNewCommentLine(indent, out, javadoc);
        currentLine = indent + 3 + word.length();
      }
      out.print(word + " ");
    }
    if (javadoc) {
      out.println();
      writeIndent(indent, out);
      out.print(" */");
    }
    out.println();
  }

  // Helper to indent comments properly
  static void writeIndent(final int indent, final PrintStream out) {
    for (int i = 0; i < indent; i++) {
      out.print(" ");
    }
  }

  static int startNewCommentLine(final int indent, final PrintStream out, final boolean javadoc) {
    writeIndent(indent, out);
    if (javadoc) {
      out.print(" * ");
      return 2;
    } else {
      out.print("// ");
      return 3;
    }
  }

  // Format a String into the CorrectJavaClassName format.
  static String javaClass(final String str, final String classPrefix) {
    String className = classPrefix;
    for (final String part : str.split("_")) {
      if (part.length() > 0) {
        className += part.substring(0, 1).toUpperCase()
            + (part.length() > 1 ? part.substring(1) : "");
      }
    }
    return className;
  }

  // Format a String into the correctJavaVariableName format.
  static String javaVariable(final String name) {
    final String[] parts = name.split("_");
    String variableName = parts[0].substring(0, 1).toLowerCase() + parts[0].substring(1);
    for (int i = 1; i < parts.length; i++) {
      final String part = parts[i];
      variableName += part.substring(0, 1).toUpperCase() + part.substring(1);
    }
    if (variableName.equals("public")) {
      variableName = "_public";
    }
    if (variableName.equals("default")) {
      variableName = "_default";
    }
    return variableName;
  }

  // Convert the types specified in the schema.json format into Java types.
  static String javaType(final DataSchemaType dataType) {
    switch (dataType) {
    case BigInt:
      return Long.class.getSimpleName();
    case Boolean:
      return Boolean.class.getSimpleName();
    case Date:
      return Date.class.getSimpleName();
    case DateTime:
    case Timestamp:
      return Timestamp.class.getSimpleName();
    case DoublePrecision:
      return Double.class.getSimpleName();
    case Int:
    case Integer:
      return Integer.class.getSimpleName();
    case Text:
    case VarChar:
      return String.class.getSimpleName();
    default:
      throw new RuntimeException("Unknown data type: " + dataType);
    }
  }

}