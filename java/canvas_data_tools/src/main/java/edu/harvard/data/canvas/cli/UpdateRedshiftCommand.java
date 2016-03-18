package edu.harvard.data.canvas.cli;

import static edu.harvard.data.canvas.CanvasCodeGenerator.PHASE_ONE_ADDITIONS_JSON;
import static edu.harvard.data.canvas.CanvasCodeGenerator.PHASE_THREE_ADDITIONS_JSON;
import static edu.harvard.data.canvas.CanvasCodeGenerator.PHASE_TWO_ADDITIONS_JSON;
import static edu.harvard.data.canvas.CanvasCodeGenerator.readExtensionSchema;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.args4j.Argument;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataConfiguration;
import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.DumpInfo;
import edu.harvard.data.ReturnStatus;
import edu.harvard.data.UpdateRedshift;
import edu.harvard.data.VerificationException;
import edu.harvard.data.canvas.data_api.ApiClient;
import edu.harvard.data.generator.SchemaTransformer;
import edu.harvard.data.schema.DataSchema;
import edu.harvard.data.schema.UnexpectedApiResponseException;

public class UpdateRedshiftCommand implements Command {
  private static final Logger log = LogManager.getLogger();

  @Argument(index = 0, usage = "UUID for the current dump, generated by the Canvas Data API.", metaVar = "uuid", required = false)
  public String dumpId;

  @Override
  public ReturnStatus execute(final DataConfiguration config, final ExecutorService exec)
      throws IOException, UnexpectedApiResponseException, DataConfigurationException,
      VerificationException {
    final AwsUtils aws = new AwsUtils();
    final ApiClient api = new ApiClient(config.getCanvasDataHost(),
        config.getCanvasApiKey(), config.getCanvasApiSecret());
    final DumpInfo info = DumpInfo.find(dumpId);

    final SchemaTransformer transformer = new SchemaTransformer();
    final DataSchema schema0 = api.getSchema(info.getSchemaVersion());
    final DataSchema schema1 = transformer.transform(schema0, readExtensionSchema(PHASE_ONE_ADDITIONS_JSON));
    final DataSchema schema2 = transformer.transform(schema1, readExtensionSchema(PHASE_TWO_ADDITIONS_JSON));
    final DataSchema schema3 = transformer.transform(schema2, readExtensionSchema(PHASE_THREE_ADDITIONS_JSON));
    try {
      new UpdateRedshift(schema3).update(aws, config);
    } catch (final SQLException e) {
      log.fatal("Error while updating Redshift schema", e);
      return ReturnStatus.IO_ERROR;
    }
    return ReturnStatus.OK;
  }

  @Override
  public String getDescription() {
    return "Update the schema in Redshift to match the current transformed schema";
  }

}
