package edu.harvard.data.leases;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import edu.harvard.data.FormatLibrary;

/**
 * Mapping class that reads and writes the Lease table on DynamoDB. During setup
 * the table name will be provided from a DataConfig setting, so the name passed
 * in the DynamoDBTable annotation is never used.
 */
@DynamoDBTable(tableName = "DummyTableName")
public class Lease {

  @DynamoDBHashKey(attributeName = "name")
  private String name;

  @DynamoDBAttribute(attributeName = "owner")
  private String owner;

  @DynamoDBAttribute(attributeName = "expires")
  private String expires;

  @DynamoDBAttribute(attributeName = "version")
  private Long version;

  private static final DateFormat DATE_FORMAT = new SimpleDateFormat(
      FormatLibrary.JSON_DATE_FORMAT_STRING);

  Lease(final String name, final String owner, final int seconds, final long version) {
    this.name = name;
    this.owner = owner;
    this.version = version;
    this.setTimeRemainingSeconds(seconds);
  }

  public Lease() {
  }

  public int timeRemainingSeconds() {
    if (expires == null) {
      return 0;
    }
    try {
      final long remaining = DATE_FORMAT.parse(expires).getTime() - new Date().getTime();
      return (int) (remaining / 1000L);
    } catch (final ParseException e) {
      throw new RuntimeException(e);
    }
  }

  void setTimeRemainingSeconds(final int seconds) {
    final long expiration = new Date().getTime() + (seconds * 1000);
    this.expires = DATE_FORMAT.format(new Date(expiration));
  }

  public void setName(final String name) {
    this.name = name;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  public void setExpires(final String expires) {
    this.expires = expires;
  }

  public void setVersion(final Long version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public String getOwner() {
    return owner;
  }

  public String getExpires() {
    return expires;
  }

  public Long getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return "Lease ID: " + name + " owned by " + owner + ". Expires: " + expires + ", version: "
        + version;
  }

}
