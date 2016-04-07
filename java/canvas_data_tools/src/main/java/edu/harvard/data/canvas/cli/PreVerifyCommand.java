package edu.harvard.data.canvas.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.ReturnStatus;
import edu.harvard.data.VerificationException;
import edu.harvard.data.Verifier;
import edu.harvard.data.canvas.CanvasDataConfiguration;
import edu.harvard.data.canvas.phase_0.Phase0PreVerifier;
import edu.harvard.data.canvas.phase_1.Phase1PreVerifier;
import edu.harvard.data.canvas.phase_2.Phase2PreVerifier;
import edu.harvard.data.canvas.phase_3.Phase3PreVerifier;
import edu.harvard.data.schema.UnexpectedApiResponseException;

public class PreVerifyCommand implements Command {

  private static final Logger log = LogManager.getLogger();

  @Argument(index = 0, usage = "Verification phase.", metaVar = "0", required = true)
  public int phase;

  @Option(name = "-i", usage = "UUID for the dump, generated by the Canvas Data API. Required for phase 0.", metaVar = "uuid", required = false)
  public String dumpId;

  @Option(name = "-in", usage = "Location of data files from the previous phase. Required for phases > 0.", metaVar = "/path/to/data", required = false)
  public String inputDir;

  @Option(name = "-out", usage = "Temporary location to store verification data. Required for phases > 0.", metaVar = "/path/to/temp", required = false)
  public String outputDir;

  @Override
  public ReturnStatus execute(final CanvasDataConfiguration config, final ExecutorService exec)
      throws IOException, DataConfigurationException, UnexpectedApiResponseException,
      ArgumentError {
    if (!checkArguments()) {
      return ReturnStatus.ARGUMENT_ERROR;
    }
    try {
      final Verifier verifier = getVerifier(config, exec);
      verifier.verify();
    } catch (final VerificationException e) {
      log.error("Verification Exception", e);
      return ReturnStatus.VERIFICATION_FAILURE;
    }
    return ReturnStatus.OK;
  }

  private Verifier getVerifier(final CanvasDataConfiguration config, final ExecutorService exec)
      throws ArgumentError, DataConfigurationException {
    final URI hdfsService;
    try {
      hdfsService = new URI("hdfs///");
    } catch (final URISyntaxException e) {
      throw new DataConfigurationException(e);
    }
    switch (phase) {
    case 0:
      return new Phase0PreVerifier();
    case 1:
      return new Phase1PreVerifier(config, hdfsService, inputDir, outputDir);
    case 2:
      return new Phase2PreVerifier();
    case 3:
      return new Phase3PreVerifier();
    }
    throw new ArgumentError("Invalid phase " + phase);
  }

  private boolean checkArguments() {
    if (phase == 0) {
      if (dumpId == null) {
        log.error("Dump ID is required for Phase 0");
        return false;
      }
    } else {
      if (inputDir == null) {
        log.error("Input directory is required for all phases other than zero.");
        return false;
      }
      if (outputDir == null) {
        log.error("Output directory is required for all phases other than zero.");
        return false;
      }
      if (!inputDir.toLowerCase().startsWith("hdfs://")) {
        inputDir = "hdfs://" + inputDir;
      }
      if (!outputDir.toLowerCase().startsWith("hdfs://")) {
        outputDir = "hdfs://" + outputDir;
      }
    }
    return true;
  }

  @Override
  public String getDescription() {
    return "Perform any initial operations to set up verification before a phase.";
  }

}
