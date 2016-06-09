package edu.harvard.data.canvas.phase_2;

import java.io.IOException;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import edu.harvard.data.DataConfig;
import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.FormatLibrary;
import edu.harvard.data.FormatLibrary.Format;
import edu.harvard.data.HadoopJob;
import edu.harvard.data.TableFormat;
import edu.harvard.data.canvas.CanvasDataConfig;
import edu.harvard.data.canvas.bindings.phase1.Phase1Requests;

class RequestPerFileJob extends HadoopJob {

  public static void main(final String[] args) throws IOException, DataConfigurationException {
    final String configPathString = args[0];
    final int phase = Integer.parseInt(args[1]);
    final CanvasDataConfig config = CanvasDataConfig.parseInputFiles(CanvasDataConfig.class, configPathString,
        true);
    new RequestPerFileJob(config, phase).runJob();
  }

  public RequestPerFileJob(final DataConfig config, final int phase) throws DataConfigurationException {
    super(config, phase);
  }

  @Override
  public Job getJob() throws IOException {
    final Job job = Job.getInstance(hadoopConf, "requests-files-hadoop");
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(LongWritable.class);
    job.setMapperClass(RequestFileMapper.class);
    job.setReducerClass(RequestFileReducer.class);
    job.setMapOutputKeyClass(Text.class);
    job.setMapOutputValueClass(LongWritable.class);
    job.setInputFormatClass(TextInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);
    hadoopUtils.setPaths(job, hdfsService, inputDir + "/requests", outputDir + "/requests_per_file");
    return job;
  }
}

class RequestFileMapper extends Mapper<Object, Text, Text, LongWritable> {

  private TableFormat format;

  @Override
  protected void setup(final Context context) {
    final Format formatName = Format.valueOf(context.getConfiguration().get("format"));
    this.format = new FormatLibrary().getFormat(formatName);
  }

  @Override
  public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
    final CSVParser parser = CSVParser.parse(value.toString(), format.getCsvFormat());
    for (final CSVRecord csvRecord : parser.getRecords()) {
      final Phase1Requests request = new Phase1Requests(format, csvRecord);

      final String url = request.getUrl().toLowerCase();
      if (url.contains("/files/")) {
        String fileKey = url;
        if (fileKey.contains("?")) {
          fileKey = fileKey.substring(0,  url.indexOf("?"));
          context.write(new Text(fileKey), new LongWritable(1));
        }
      }
    }
  }
}

class RequestFileReducer extends Reducer<Text, LongWritable, Text, LongWritable> {

  @Override
  public void reduce(final Text key, final Iterable<LongWritable> values,
      final Context context)
          throws IOException, InterruptedException {
    long sum = 0;
    for (final LongWritable count : values) {
      sum += count.get();
    }
    context.write(key, new LongWritable(sum));
  }
}
