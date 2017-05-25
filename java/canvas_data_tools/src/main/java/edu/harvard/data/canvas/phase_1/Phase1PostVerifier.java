package edu.harvard.data.canvas.phase_1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.FormatLibrary;
import edu.harvard.data.HadoopJob;
import edu.harvard.data.HadoopUtilities;
import edu.harvard.data.TableFormat;
import edu.harvard.data.VerificationException;
import edu.harvard.data.canvas.CanvasDataConfig;
import edu.harvard.data.identity.IdentifierType;
import edu.harvard.data.identity.IdentityMap;
import edu.harvard.data.io.HdfsTableReader;
import edu.harvard.data.leases.LeaseRenewalException;
import edu.harvard.data.leases.LeaseRenewalThread;

public class Phase1PostVerifier {
  private static final Logger log = LogManager.getLogger();
  private final Configuration hadoopConfig;
  private final URI hdfsService;
  private final String inputDir;
  private final String outputDir;
  private final String verifyDir;
  private final TableFormat format;
  private final HadoopUtilities hadoopUtils;
  private final CanvasDataConfig config;

  public static void main(final String[] args)
      throws IOException, DataConfigurationException, VerificationException, LeaseRenewalException {
    final String configPathString = args[0];
    final String runId = args[1];
    final CanvasDataConfig config = CanvasDataConfig.parseInputFiles(CanvasDataConfig.class,
        configPathString, true);
    final LeaseRenewalThread leaseThread = LeaseRenewalThread.setup(config.getLeaseDynamoTable(),
        config.getIdentityLease(), runId, config.getIdentityLeaseLengthSeconds());
    new Phase1PostVerifier(config).verify();
    leaseThread.checkLease();
  }

  public Phase1PostVerifier(final CanvasDataConfig config) throws DataConfigurationException {
    this.config = config;
    this.inputDir = config.getHdfsDir(0);
    this.outputDir = config.getHdfsDir(1);
    this.verifyDir = config.getVerifyHdfsDir(1);
    this.hadoopConfig = new Configuration();
    this.hadoopUtils = new HadoopUtilities();
    this.format = new FormatLibrary().getFormat(config.getPipelineFormat());
    try {
      this.hdfsService = new URI("hdfs///");
    } catch (final URISyntaxException e) {
      throw new DataConfigurationException(e);
    }
  }

  public void verify() throws VerificationException, IOException, DataConfigurationException {
    log.info("Running post-verifier for phase 1");
    log.info("Input directory: " + inputDir);
    log.info("Output directory: " + outputDir);
    log.info("Verify directory: " + verifyDir);

    new PostVerifyIdentityMap(hadoopConfig, hdfsService, inputDir + "/identity_map",
        outputDir + "/identity_map/identitymap", format).verify();
    //    updateInterestingTables();

    //    for (final HadoopJob job : setupJobs()) {
    //      job.runJob();
    //    }
  }

  private List<HadoopJob> setupJobs() throws IOException, DataConfigurationException {
    final List<HadoopJob> jobs = new ArrayList<HadoopJob>();
    jobs.add(new PostVerifyRequestsJob(config, 1));
    return jobs;
  }

  private void updateInterestingTables() throws IOException {
    final FileSystem fs = FileSystem.get(hdfsService, hadoopConfig);
    final Map<Long, IdentityMap> identities = new HashMap<Long, IdentityMap>();
    for (final Path path : hadoopUtils.listFiles(hdfsService, outputDir + "/identity_map/identitymap")) {
      try (HdfsTableReader<IdentityMap> in = new HdfsTableReader<IdentityMap>(IdentityMap.class,
          format, fs, path)) {
        for (final IdentityMap id : in) {
          identities.put((Long) id.get(IdentifierType.CanvasDataID), id);
        }
      }
    }

    for (final Path path : hadoopUtils.listFiles(hdfsService, verifyDir + "/requests")) {
      try (FSDataInputStream fsin = fs.open(path);
          BufferedReader in = new BufferedReader(new InputStreamReader(fsin));
          FSDataOutputStream out = fs
              .create(new Path(verifyDir + "/updated/requests/" + hadoopUtils.getFileName(path)))) {
        String line = in.readLine();
        while (line != null) {
          final String[] parts = line.split("\t");
          final Long oldId = Long.parseLong(parts[1]);
          if (!identities.containsKey(oldId)) {
            throw new RuntimeException(
                "Verification error: Canvas Data ID " + oldId + " missing from identity map");
          }
          final String newId = (String) identities.get(oldId).get(IdentifierType.ResearchUUID);
          out.writeBytes(parts[0] + "\t" + newId + "\n");
          line = in.readLine();
        }
      }
    }
  }
}
