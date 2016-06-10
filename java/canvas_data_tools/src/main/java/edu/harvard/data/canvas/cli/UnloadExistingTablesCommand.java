package edu.harvard.data.canvas.cli;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.args4j.Argument;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.DumpInfo;
import edu.harvard.data.ReturnStatus;
import edu.harvard.data.UnloadExistingTables;
import edu.harvard.data.VerificationException;
import edu.harvard.data.canvas.CanvasCodeGenerator;
import edu.harvard.data.canvas.CanvasDataConfig;
import edu.harvard.data.canvas.data_api.ApiClient;
import edu.harvard.data.schema.DataSchema;
import edu.harvard.data.schema.UnexpectedApiResponseException;
import edu.harvard.data.schema.existing.ExistingSchema;

public class UnloadExistingTablesCommand implements Command {
  private static final Logger log = LogManager.getLogger();

  @Argument(index = 0, usage = "UUID for the current dump, generated by the Canvas Data API.", metaVar = "dump_id", required = true)
  public String dumpId;

  @Argument(index = 1, usage = "S3 location to store unloaded files.", metaVar = "s3://bucket/location", required = true)
  public String s3Location;

  @Override
  public ReturnStatus execute(final CanvasDataConfig config, final ExecutorService exec)
      throws IOException, UnexpectedApiResponseException, DataConfigurationException,
      VerificationException {
    final AwsUtils aws = new AwsUtils();
    final ApiClient api = new ApiClient(config.getCanvasDataHost(), config.getCanvasApiKey(),
        config.getCanvasApiSecret());
    final DumpInfo info = DumpInfo.find(dumpId);
    if (!s3Location.toLowerCase().startsWith("s3://")) {
      s3Location = "s3://" + s3Location;
    }

    final DataSchema base = api.getSchema(info.getSchemaVersion());
    final CanvasCodeGenerator generator = new CanvasCodeGenerator(null, null,
        null, config);
    final ExistingSchema existingSchema = ExistingSchema
        .readExistingSchemas(CanvasCodeGenerator.PHASE_ZERO_TABLES_JSON);
    final DataSchema schema0 = generator.transformSchema(base).get(0);

    try {
      new UnloadExistingTables(existingSchema, schema0).unload(aws, config, s3Location,
          info.getDownloadStart());
    } catch (final SQLException e) {
      log.fatal("Error while unloading existing tables", e);
      return ReturnStatus.IO_ERROR;
    }
    return ReturnStatus.OK;
  }

  @Override
  public String getDescription() {
    return "Unload tables from the Redshift database and store to S3.";
  }

}
