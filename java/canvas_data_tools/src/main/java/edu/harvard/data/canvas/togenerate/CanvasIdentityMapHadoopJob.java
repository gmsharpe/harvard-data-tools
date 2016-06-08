package edu.harvard.data.canvas.togenerate;

import java.io.IOException;

import edu.harvard.data.DataConfigurationException;
import edu.harvard.data.identity.IdentityMapHadoopJob;

public class CanvasIdentityMapHadoopJob extends IdentityMapHadoopJob {

  public static void main(final String[] args) throws IOException, DataConfigurationException {
    new CanvasIdentityMapHadoopJob(args[0]).run();
  }

  public CanvasIdentityMapHadoopJob(final String configPathString)
      throws IOException, DataConfigurationException {
    super(configPathString, new CanvasCodeManager());
  }

}
