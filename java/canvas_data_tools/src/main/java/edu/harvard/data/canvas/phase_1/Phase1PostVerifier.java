package edu.harvard.data.canvas.phase_1;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataConfiguration;
import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.FormatLibrary;
import edu.harvard.data.FormatLibrary.Format;
import edu.harvard.data.HadoopJob;
import edu.harvard.data.TableFormat;
import edu.harvard.data.VerificationException;
import edu.harvard.data.Verifier;
import edu.harvard.data.canvas.HadoopMultipleJobRunner;
import edu.harvard.data.identity.IdentityMap;
import edu.harvard.data.io.FileTableReader;

public class Phase1PostVerifier implements Verifier {
  private static final Logger log = LogManager.getLogger();
  private final DataConfiguration config;
  private final Configuration hadoopConfig;
  private final URI hdfsService;
  private final String dataDir;
  private final String verifyDir;
  private final TableFormat format;

  public Phase1PostVerifier(final DataConfiguration config, final Configuration hadoopConfig,
      final URI hdfsService, final String dataDir, final String verifyDir) {
    this.config = config;
    this.hadoopConfig = hadoopConfig;
    this.hdfsService = hdfsService;
    this.dataDir = dataDir;
    this.verifyDir = verifyDir;
    this.format = new FormatLibrary().getFormat(Format.CanvasDataFlatFiles);
  }

  @Override
  public void verify() throws VerificationException, IOException, DataConfigurationException {
    log.info("Running post-verifier for phase 1");
    log.info("Data directory: " + dataDir);
    log.info("Verify directory: " + verifyDir);

    updateInterestingTables();

    final HadoopMultipleJobRunner jobRunner = new HadoopMultipleJobRunner(hadoopConfig);
    final List<Job> jobs = setupJobs();
    jobRunner.runParallelJobs(jobs);
  }

  private List<Job> setupJobs() throws IOException {
    final AwsUtils aws = new AwsUtils();
    final List<Job> jobs = new ArrayList<Job>();
    jobs.add(
        new PostVerifyRequestsJob(hadoopConfig, aws, hdfsService, dataDir, verifyDir).getJob());
    return jobs;
  }

  private void updateInterestingTables() throws IOException {
    final FileSystem fs = FileSystem.get(hdfsService, hadoopConfig);
    final Map<Long, IdentityMap> identities = new HashMap<Long, IdentityMap>();
    for (final Path path : HadoopJob.listFiles(hdfsService, dataDir + "/identity_map")) {
      try (final FSDataInputStream inStream = fs.open(path);
          FileTableReader<IdentityMap> in = new FileTableReader<IdentityMap>(IdentityMap.class,
              format, inStream)) {
        log.info("Loading IDs for " + this);
        for (final IdentityMap id : in) {
          identities.put(id.getCanvasDataID(), id);
        }
      }
    }

    for (final Path path : HadoopJob.listFiles(hdfsService, verifyDir + "/requests")) {
      try (FSDataInputStream in = fs.open(path);
          FSDataOutputStream out = fs.create(new Path(path.toString() + "_updated"))) {
        String line = in.readLine();
        while (line != null) {
          final String[] parts = line.split("\t");
          final Long oldId = Long.parseLong(parts[1]);
          if (!identities.containsKey(oldId)) {
            throw new RuntimeException("Verification error: Canvas Data ID " + oldId + " missing from identity map");
          }
          final Long newId = identities.get(oldId).getCanvasDataID();
          out.writeBytes(parts[0] + "\t" + newId + "\n");
          line = in.readLine();
        }
      }
    }
  }
}
