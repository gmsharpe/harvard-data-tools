package edu.harvard.data.data_tools;

import java.io.IOException;

import edu.harvard.data.client.DataConfiguration;
import edu.harvard.data.client.DataConfigurationException;
import edu.harvard.data.client.schema.UnexpectedApiResponseException;

public interface Command {

  String getDescription();

  ReturnStatus execute(DataConfiguration config) throws IOException, UnexpectedApiResponseException,
  DataConfigurationException, VerificationException;

}