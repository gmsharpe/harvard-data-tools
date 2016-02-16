package edu.harvard.canvas_data.aws_data_tools.cli;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.args4j.Argument;

import com.amazonaws.services.s3.model.S3ObjectId;

import edu.harvard.canvas_data.aws_data_tools.DumpInfo;
import edu.harvard.canvas_data.aws_data_tools.Verifier;
import edu.harvard.data.client.AwsUtils;
import edu.harvard.data.client.DataConfiguration;
import edu.harvard.data.client.DataConfigurationException;
import edu.harvard.data.client.FormatLibrary;
import edu.harvard.data.client.TableFactory;
import edu.harvard.data.client.TableFormat;
import edu.harvard.data.client.canvas.phase0.CanvasTableFactory;
import edu.harvard.data.client.schema.UnexpectedApiResponseException;

public class VerifyDumpCommand implements Command {

  private static final Logger log = LogManager.getLogger();

  @Argument(index = 0, usage = "UUID for the dump, generated by the Canvas Data API.", metaVar = "uuid", required = true)
  public String dumpId;

  @Override
  public ReturnStatus execute(final DataConfiguration config)
      throws IOException, DataConfigurationException, UnexpectedApiResponseException {
    final AwsUtils aws = new AwsUtils();
    final TableFactory factory = new CanvasTableFactory();
    final FormatLibrary formats = new FormatLibrary();
    final TableFormat format = formats.getFormat(FormatLibrary.Format.CanvasDataFlatFiles);

    final DumpInfo info = DumpInfo.find(dumpId);
    if (!info.getVerified()) {
      log.info("Verifying dump sequence " + info.getSequence() + " at " + info.getS3Location());
      final S3ObjectId dumpObj = AwsUtils.key(info.getBucket(), info.getKey());
      final Verifier verifier = new Verifier(aws, factory, format);
      final long errors = verifier.verifyDump(dumpObj);
      if (errors > 0) {
        log.error("Encountered " + errors + " errors when verifying dump at " + dumpObj);
        return ReturnStatus.VERIFICATION_FAILURE;
      }
      info.setVerified(true);
      info.save();
    }
    return ReturnStatus.OK;
  }

  @Override
  public String getDescription() {
    return "Verify the file structure of a Canvas data dump.";
  }

}
