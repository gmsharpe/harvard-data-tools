package edu.harvard.data.canvas.phase_2;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.data.VerificationException;
import edu.harvard.data.Verifier;

public class Phase2PreVerifier implements Verifier {
  private static final Logger log = LogManager.getLogger();

  @Override
  public void verify() throws VerificationException, IOException {
    log.info("Running pre-verifier for phase 2");
  }

}
