package edu.harvard.data.canvas.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.args4j.Argument;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataConfiguration;
import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.ReturnStatus;
import edu.harvard.data.VerificationException;
import edu.harvard.data.canvas.phase_2.Phase2HadoopManager;
import edu.harvard.data.schema.UnexpectedApiResponseException;

public class HadoopCommand implements Command {

  private static final Logger log = LogManager.getLogger();

  @Argument(index = 0, usage = "Hadoop process phase.", metaVar = "0", required = true)
  public int phase;

  @Argument(index = 1, usage = "Input directory on HDFS.", metaVar = "/dump/directory", required = true)
  public String inputDir;

  @Argument(index = 2, usage = "Output directory on HDFS.", metaVar = "/output/directory", required = true)
  public String outputDir;

  @Override
  public ReturnStatus execute(final DataConfiguration config, final ExecutorService exec)
      throws IOException, UnexpectedApiResponseException, DataConfigurationException,
      VerificationException, ArgumentError {
    final Configuration hadoopConfig = new Configuration();
    log.info("Setting up Canvas Hadoop job");
    log.info("Phase: " + phase);
    log.info("Input directory: " + inputDir);
    log.info("Output directory: " + outputDir);

    final List<Job> jobs = setupJobs(hadoopConfig, config);

    for (final Job job : jobs) {
      job.setJarByClass(HadoopCommand.class);
      try {
        log.info("Submitted job " + job.getJobName());
        job.submit();
      } catch (final ClassNotFoundException e) {
        throw new DataConfigurationException(e);
      } catch (final InterruptedException e) {
        log.error("Job submission interrupted", e);
      }
    }
    for (final Job job : jobs) {
      while (!job.isComplete()) {
        try {
          Thread.sleep(Job.getCompletionPollInterval(hadoopConfig));
        } catch (final InterruptedException e) {
          log.error("Interrupted while waiting for job to complete.", e);
        }
      }
    }
    log.info("All jobs complete");
    return ReturnStatus.OK;
  }

  private List<Job> setupJobs(final Configuration hadoopConfig, final DataConfiguration config)
      throws DataConfigurationException, IOException, ArgumentError {
    final AwsUtils aws = new AwsUtils();
    final URI hdfsService;
    try {
      hdfsService = new URI("hdfs///");
    } catch (final URISyntaxException e) {
      throw new DataConfigurationException(e);
    }
    final List<Job> jobs = new ArrayList<Job>();
    switch (phase) {
    case 0:
      break;
    case 1:
      break;
    case 2:
      return Phase2HadoopManager.getJobs(aws, hadoopConfig, config, hdfsService, inputDir,
          outputDir);
    case 3:
      break;
    default:
      throw new ArgumentError("Invalid phase: " + phase);
    }
    return jobs;
  }

  @Override
  public String getDescription() {
    return "Create and run the Canvas hadoop jobs for a single phase";
  }

}
