package edu.harvard.data.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.services.datapipeline.model.PipelineObject;
import com.amazonaws.services.s3.model.S3ObjectId;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataConfig;

public class PipelineFactory {
  private final DataConfig config;
  private final List<PipelineObjectBase> allObjects;

  public PipelineFactory(final DataConfig config, final String pipelineId) {
    this.config = config;
    this.allObjects = new ArrayList<PipelineObjectBase>();
  }

  public PipelineObjectBase getSchedule() {
    final PipelineObjectBase obj = new PipelineObjectBase(config, "DefaultSchedule",
        "Schedule");
    obj.setName("RunOnce");
    obj.set("occurrences", "1");
    obj.set("startAt", "FIRST_ACTIVATION_DATE_TIME");
    obj.set("period", "1 Day");
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getDefault(final PipelineObjectBase schedule) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, "Default", "Default");
    obj.set("failureAndRerunMode", "CASCADE");
    obj.set("scheduleType", "cron");
    obj.set("role", config.dataPipelineRole);
    obj.set("resourceRole", config.dataPipelineResourceRoleArn);
    obj.set("pipelineLogUri", "s3://" + config.logBucket);
    obj.set("schedule", schedule);
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getSns(final String id, final String subject, final String msg,
      final String topicArn) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "SnsAlarm");
    obj.set("role", config.dataPipelineRole);
    obj.set("subject", subject);
    obj.set("message", msg);
    obj.set("topicArn", topicArn);
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getRedshift() {
    final PipelineObjectBase obj = new PipelineObjectBase(config, "RedshiftDatabase",
        "RedshiftDatabase");
    obj.set("clusterId", config.redshiftCluster);
    obj.set("username", config.redshiftUserName);
    obj.set("*password", config.redshiftPassword);
    obj.set("databaseName", config.redshiftDatabase);
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getEmr(final String name) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, name, "EmrCluster");
    obj.set("useOnDemandOnLastAttempt", "true");
    obj.set("keyPair", config.keypair);
    obj.set("releaseLabel", config.emrReleaseLabel);
    obj.set("terminateAfter", config.emrTerminateAfter);
    obj.set("subnetId", config.appSubnet);
    obj.set("masterInstanceType", config.emrMasterInstanceType);
    if (config.emrMasterBidPrice != null) {
      obj.set("masterInstanceBidPrice", config.emrMasterBidPrice);
    }
    if (Integer.parseInt(config.emrCoreInstanceCount) > 0) {
      obj.set("coreInstanceType", config.emrCoreInstanceType);
      obj.set("coreInstanceCount", config.emrCoreInstanceCount);
      if (config.emrCoreBidPrice != null) {
        obj.set("coreInstanceBidPrice", config.emrCoreBidPrice);
      }
    }
    if (Integer.parseInt(config.emrTaskInstanceCount) > 0) {
      obj.set("taskInstanceType", config.emrTaskInstanceType);
      obj.set("taskInstanceCount", config.emrTaskInstanceCount);
      if (config.emrTaskBidPrice != null) {
        obj.set("taskInstanceBidPrice", config.emrTaskBidPrice);
      }
    }
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getSynchronizationBarrier(final String id, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(obj, infrastructure);
    obj.set("command", "ls");
    return obj;
  }

  public PipelineObjectBase getEmrActivity(final String id, final PipelineObjectBase infrastructure,
      final Class<?> cls, final List<String> args) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "EmrActivity");
    setupActivity(obj, infrastructure);
    final String params = StringUtils.join(args, ",");
    final String jar = config.emrCodeDir + "/" + config.dataToolsJar;
    obj.set("step", jar + "," + cls.getCanonicalName() + "," + params);
    obj.set("retryDelay", "2 Minutes");
    return obj;
  }

  public PipelineObjectBase getS3CopyActivity(final String id, final S3ObjectId src,
      final String dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(obj, infrastructure);
    obj.set("command", "aws s3 cp " + AwsUtils.uri(src) + " " + dest + " --recursive");
    return obj;
  }

  public PipelineObjectBase getS3CopyActivity(final String id, final String src,
      final S3ObjectId dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(obj, infrastructure);
    obj.set("command", "aws s3 cp " + src + " " + AwsUtils.uri(dest) + " --recursive");
    return obj;
  }

  public PipelineObjectBase getUnloadActivity(final String id, final String sql,
      final S3ObjectId dest, final PipelineObjectBase database, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "SqlActivity");
    setupActivity(obj, infrastructure);
    final String query = "UNLOAD ('" + sql + "') TO '" + AwsUtils.uri(dest)
    + "/' WITH CREDENTIALS AS " + getCredentials() + " delimiter '\\t' GZIP;";
    obj.set("script", query);
    obj.set("database", database);
    return obj;
  }

  public PipelineObjectBase getSqlScriptActivity(final String id, final S3ObjectId script,
      final PipelineObjectBase database, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "SqlActivity");
    setupActivity(obj, infrastructure);
    obj.set("scriptUri", AwsUtils.uri(script));
    obj.set("database", database);
    return obj;
  }

  public PipelineObjectBase getS3DistCpActivity(final String id, final S3ObjectId src,
      final String dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(obj, infrastructure);
    final String cmd = "s3-dist-cp --src=" + AwsUtils.uri(src) + " --dest=hdfs://" + cleanHdfs(dest)
    + " --outputCodec=none";
    obj.set("command", cmd);
    return obj;
  }

  public PipelineObjectBase getS3DistCpActivity(final String id, final String src,
      final S3ObjectId dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(obj, infrastructure);
    final String cmd = "s3-dist-cp --src=hdfs://" + cleanHdfs(src) + " --dest=" + AwsUtils.uri(dest)
    + " --outputCodec=gzip";
    obj.set("command", cmd);
    return obj;
  }

  ////////////////

  private void setupActivity(final PipelineObjectBase obj, final PipelineObjectBase infrastructure) {
    obj.set("runsOn", infrastructure);
    obj.set("maximumRetries", config.maximumRetries);
    allObjects.add(obj);
  }

  private String cleanHdfs(final String path) {
    if (path.toLowerCase().startsWith("hdfs://")) {
      return path.substring("hdfs://".length());
    }
    return path;
  }

  private String getCredentials() {
    return "'aws_access_key_id=" + config.awsKeyId + ";aws_secret_access_key=" + config.awsSecretKey
        + "'";
  }

  public List<PipelineObject> getAllObjects() {
    final List<PipelineObject> objects = new ArrayList<PipelineObject>();
    for (final PipelineObjectBase obj : allObjects) {
      objects.add(obj.getPipelineObject());
    }
    return objects;
  }

}