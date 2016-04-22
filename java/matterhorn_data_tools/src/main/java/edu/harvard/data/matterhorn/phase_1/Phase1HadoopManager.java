package edu.harvard.data.matterhorn.phase_1;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.FormatLibrary.Format;
import edu.harvard.data.identity.HadoopIdentityKey;
import edu.harvard.data.identity.IdentifierType;
import edu.harvard.data.identity.StringIdentityReducer;
import edu.harvard.data.matterhorn.HadoopMultipleJobRunner;
import edu.harvard.data.matterhorn.identity.MatterhornIdentityHadoopManager;

public class Phase1HadoopManager {
  private static final Logger log = LogManager.getLogger();

  private final String inputDir;
  private final String outputDir;
  private final URI hdfsService;
  private final MatterhornIdentityHadoopManager hadoopManager;

  public Phase1HadoopManager(final String inputDir, final String outputDir, final URI hdfsService) {
    this.inputDir = inputDir;
    this.outputDir = outputDir;
    this.hdfsService = hdfsService;
    hadoopManager = new MatterhornIdentityHadoopManager();
  }

  public void runJobs(final Configuration hadoopConfig) throws IOException, DataConfigurationException {
    hadoopConfig.set("format", Format.DecompressedCanvasDataFlatFiles.toString());
    hadoopConfig.set("mainIdentifier", IdentifierType.HUID.toString());
    runMapJobs(hadoopConfig);
    runScrubJobs(hadoopConfig);
  }

  @SuppressWarnings("rawtypes")
  private void runMapJobs(final Configuration hadoopConfig) throws IOException {
    final Job job = Job.getInstance(hadoopConfig, "matterhorn-identity-map");
    job.setJarByClass(Phase1HadoopManager.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(NullWritable.class);
    job.setReducerClass(StringIdentityReducer.class);
    job.setMapOutputKeyClass(Text.class);
    job.setMapOutputValueClass(HadoopIdentityKey.class);
    job.setInputFormatClass(TextInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);

    for (final Path path : listHdfsFiles(hadoopConfig, new Path(inputDir + "/identity_map"))) {
      log.info("Adding identity file " + path + " to map job cache");
      job.addCacheFile(path.toUri());
    }

    final List<String> tables = hadoopManager.getIdentityTableNames();
    final List<Class<? extends Mapper>> mapperClasses = hadoopManager.getMapperClasses();
    for (int i = 0; i < mapperClasses.size(); i++) {
      final Path path = new Path(inputDir + "/" + tables.get(i) + "/");
      MultipleInputs.addInputPath(job, path, TextInputFormat.class, mapperClasses.get(i));
      log.info("Adding mapper for path " + path);
    }
    FileOutputFormat.setOutputPath(job, new Path(outputDir + "/identity_map"));
    try {
      job.waitForCompletion(true);
    } catch (ClassNotFoundException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("rawtypes")
  private void runScrubJobs(final Configuration hadoopConfig)
      throws IOException, DataConfigurationException {
    final List<String> tables = hadoopManager.getIdentityTableNames();
    final List<Class<? extends Mapper>> scrubberClasses = hadoopManager.getScrubberClasses();
    final List<Job> jobs = new ArrayList<Job>();
    for (int i = 0; i < scrubberClasses.size(); i++) {
      jobs.add(buildScrubJob(hadoopConfig, tables.get(i), scrubberClasses.get(i)));
    }
    final HadoopMultipleJobRunner jobRunner = new HadoopMultipleJobRunner(hadoopConfig);
    jobRunner.runParallelJobs(jobs);
  }

  @SuppressWarnings("rawtypes")
  private Job buildScrubJob(final Configuration hadoopConfig, final String tableName,
      final Class<? extends Mapper> cls) throws IOException {
    final Job job = Job.getInstance(hadoopConfig, "matterhorn-" + tableName + "-scrubber");
    job.setJarByClass(Phase1HadoopManager.class);
    job.setMapperClass(cls);
    job.setMapOutputKeyClass(Text.class);
    job.setMapOutputValueClass(NullWritable.class);
    job.setNumReduceTasks(0);

    job.setInputFormatClass(TextInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);
    setPaths(job, hdfsService, inputDir + "/" + tableName, outputDir + "/" + tableName);
    for (final Path path : listHdfsFiles(hadoopConfig, new Path(outputDir + "/identity_map"))) {
      job.addCacheFile(path.toUri());
    }
    return job;
  }

  private List<Path> listHdfsFiles(final Configuration hadoopConfig, final Path path)
      throws IOException {
    final List<Path> files = new ArrayList<Path>();
    final FileSystem fs = FileSystem.get(hadoopConfig);
    for (final FileStatus fileStatus : fs.listStatus(path)) {
      files.add(fileStatus.getPath());
    }
    return files;
  }

  private void setPaths(final Job job, final URI hdfsService, final String in, final String out)
      throws IOException {
    final Configuration conf = new Configuration();
    final FileSystem fs = FileSystem.get(hdfsService, conf);
    final FileStatus[] fileStatus = fs.listStatus(new Path(in));
    for (final FileStatus status : fileStatus) {
      FileInputFormat.addInputPath(job, status.getPath());
      System.out.println("Input path: " + status.getPath().toString());
    }

    System.out.println("Output path: " + out);
    FileOutputFormat.setOutputPath(job, new Path(out));
  }

}