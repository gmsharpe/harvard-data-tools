package edu.harvard.data.io;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import com.amazonaws.services.s3.model.S3ObjectId;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataTable;
import edu.harvard.data.TableFormat;

public class S3TableReader<T extends DataTable> implements TableReader<T> {

  TableReader<T> reader;
  private final S3ObjectId obj;
  private final TableFormat format;
  private final Class<T> tableType;
  private final File tempFile;
  private final AwsUtils aws;

  public S3TableReader(final AwsUtils aws, final Class<T> tableType, final TableFormat format,
      final S3ObjectId obj, final File tempDir) {
    this.aws = aws;
    this.tableType = tableType;
    this.format = format;
    this.obj = obj;
    this.tempFile = new File(tempDir, UUID.randomUUID().toString());
  }

  private TableReader<T> getReader() throws IOException {
    if (reader == null) {
      if (tempFile.exists()) {
        tempFile.delete();
      }
      tempFile.getParentFile().mkdirs();
      aws.getFile(obj, tempFile);
      reader = new FileTableReader<T>(tableType, format, tempFile);
    }
    return reader;
  }

  @Override
  public Iterator<T> iterator() {
    try {
      return getReader().iterator();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws IOException {
    if (reader != null) {
      reader.close();
      tempFile.delete();
    }
    reader = null;
  }

}
