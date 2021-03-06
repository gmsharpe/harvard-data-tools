package edu.harvard.data.canvas.phase_2;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import edu.harvard.data.DataConfig;
import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.FormatLibrary;
import edu.harvard.data.FormatLibrary.Format;
import edu.harvard.data.HadoopJob;
import edu.harvard.data.NoInputDataException;
import edu.harvard.data.TableFormat;
import edu.harvard.data.canvas.CanvasDataConfig;
import edu.harvard.data.canvas.bindings.phase1.Phase1Requests;
import edu.harvard.data.canvas.bindings.phase2.Phase2Requests;

public class RequestJob extends HadoopJob {

  public static void main(final String[] args) throws IOException, DataConfigurationException {
    final String configPathString = args[0];
    final int phase = Integer.parseInt(args[1]);
    final CanvasDataConfig config = CanvasDataConfig.parseInputFiles(CanvasDataConfig.class, configPathString,
        true);
    new RequestJob(config, phase).runJob();
  }

  public RequestJob(final DataConfig config, final int phase) throws DataConfigurationException {
    super(config, phase);
  }

  @Override
  public Job getJob() throws IOException, NoInputDataException {
    final Job job = Job.getInstance(hadoopConf, "requests-hadoop");
    job.setMapperClass(RequestMapper.class);
    job.setMapOutputKeyClass(Text.class);
    job.setMapOutputValueClass(NullWritable.class);
    job.setNumReduceTasks(0);

    job.setInputFormatClass(TextInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);
    final String inputDir = config.getHdfsDir(phase - 1);
    final String outputDir = config.getHdfsDir(phase);
    hadoopUtils.setPaths(job, hdfsService, inputDir + "/requests", outputDir + "/requests");
    return job;
  }
}

class RequestMapper extends Mapper<Object, Text, Text, NullWritable> {

  private TableFormat format;
  private Set<String> adminResearchIds;

  @Override
  protected void setup(final Context context) {
    final Format formatName = Format.valueOf(context.getConfiguration().get("format"));
    this.format = new FormatLibrary().getFormat(formatName);
    this.adminResearchIds = new HashSet<String>();
    adminResearchIds.add("19e44a79-b2a1-4d8b-a1f8-c5547c3d5a05");
    adminResearchIds.add("b80eda2a-3a0a-42ce-b6ad-c31b1638b785");
  }

  @Override
  public void map(final Object key, final Text value, final Context context)
      throws IOException, InterruptedException {
    final CSVParser parser = CSVParser.parse(value.toString(), format.getCsvFormat());
    for (final CSVRecord csvRecord : parser.getRecords()) {
      final Phase1Requests request = new Phase1Requests(format, csvRecord);
      if (request.getUserIdResearchUuid() == null
          || (!adminResearchIds.contains(request.getUserIdResearchUuid()))) {
        final Phase2Requests phase2 = new Phase2Requests(request);

        final StringWriter writer = new StringWriter();
        try (final CSVPrinter printer = new CSVPrinter(writer, format.getCsvFormat())) {
          printer.printRecord(phase2.getFieldsAsList(format));
        }
        final Text csvText = new Text(writer.toString().trim());
        context.write(csvText, NullWritable.get());
      }
    }
  }
}
