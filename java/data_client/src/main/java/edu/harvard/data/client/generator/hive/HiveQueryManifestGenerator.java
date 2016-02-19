package edu.harvard.data.client.generator.hive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.data.client.generator.SchemaPhase;
import edu.harvard.data.client.generator.SchemaTransformer;

public class HiveQueryManifestGenerator {
  private static final Logger log = LogManager.getLogger();

  private final File gitDir;
  private final SchemaTransformer schemaVersions;
  private final File dir;

  public HiveQueryManifestGenerator(final File gitDir, final File dir,
      final SchemaTransformer schemaVersions) {
    this.gitDir = gitDir;
    this.dir = dir;
    this.schemaVersions = schemaVersions;
  }

  public void generate() throws IOException {
    final File phase1File = new File(dir, "phase_1_hive.sh");
    final File phase2File = new File(dir, "phase_2_hive.sh");

    log.info("Phase 1 file: "+ phase1File);
    log.info("Phase 2 file: "+ phase2File);
    log.info("Phase 1 hive directory: "+ new File(gitDir, "hive/phase_1"));
    log.info("Phase 2 hive directory: "+ new File(gitDir, "hive/phase_2"));
    try (final PrintStream out = new PrintStream(new FileOutputStream(phase1File))) {
      generateHiveManifest(out, schemaVersions.getPhase(1), new File(gitDir, "hive/phase_1"));
    }
    try (final PrintStream out = new PrintStream(new FileOutputStream(phase2File))) {
      generateHiveManifest(out, schemaVersions.getPhase(1), new File(gitDir, "hive/phase_2"));
    }
  }

  private void generateHiveManifest(final PrintStream out, final SchemaPhase phase,
      final File queryDir) {
    if (queryDir.exists() && queryDir.isDirectory()) {
      for (final String fileName : queryDir.list()) {
        out.println("hive source $1/" + fileName + ";");
      }
    }
  }

}