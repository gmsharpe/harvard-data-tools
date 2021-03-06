package edu.harvard.data.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.services.datapipeline.model.PipelineObject;
import com.amazonaws.services.s3.model.S3ObjectId;
import com.amazonaws.services.elasticmapreduce.model.Configuration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataConfig;
import edu.harvard.data.leases.AcquireLeaseTask;
import edu.harvard.data.leases.ReleaseLeaseTask;
import edu.harvard.data.leases.RenewLeaseTask;

public class PipelineFactory {
  private final DataConfig config;
  private final List<PipelineObjectBase> allObjects;
  private final String runId;
  private final String pipelineId;

  public PipelineFactory(final DataConfig config, final String pipelineId, final String runId) {
    this.config = config;
    this.pipelineId = pipelineId;
    this.allObjects = new ArrayList<PipelineObjectBase>();
    this.runId = runId;
  }

  public PipelineObjectBase getSchedule() {
    final PipelineObjectBase obj = new PipelineObjectBase(config, "DefaultSchedule", "Schedule");
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
    obj.set("role", config.getDataPipelineRole());
    obj.set("resourceRole", config.getDataPipelineResourceRoleArn());
    obj.set("pipelineLogUri", "s3://" + config.getLogBucket());
    obj.set("schedule", schedule);
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getSns(final String id, final String subject, final String msg,
      final String topicArn) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "SnsAlarm");
    obj.set("role", config.getDataPipelineRole());
    obj.set("subject", subject);
    obj.set("message", msg);
    obj.set("topicArn", topicArn);
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getRedshift() {
    final PipelineObjectBase obj = new PipelineObjectBase(config, "RedshiftDatabase",
        "RedshiftDatabase");
    obj.set("clusterId", config.getRedshiftCluster());
    obj.set("username", config.getRedshiftUserName());
    obj.set("*password", config.getRedshiftPassword());
    obj.set("databaseName", config.getRedshiftDatabase());
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getEmr(final String name) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, name, "EmrCluster");
    obj.set("useOnDemandOnLastAttempt", "true");
    obj.set("keyPair", config.getKeypair());
    obj.set("releaseLabel", config.getEmrReleaseLabel());
    obj.set("terminateAfter", config.getEmrTerminateAfter());
    obj.set("subnetId", config.getSubnetId());
    obj.set("masterInstanceType", config.getEmrMasterInstanceType());
    if (config.getEmrMasterBidPrice() != null) {
      obj.set("masterInstanceBidPrice", config.getEmrMasterBidPrice());
    }
    if (Integer.parseInt(config.getEmrCoreInstanceCount()) > 0) {
      obj.set("coreInstanceType", config.getEmrCoreInstanceType());
      obj.set("coreInstanceCount", config.getEmrCoreInstanceCount());
      if (config.getEmrCoreBidPrice() != null) {
        obj.set("coreInstanceBidPrice", config.getEmrCoreBidPrice());
      }
    }
    if (Integer.parseInt(config.getEmrTaskInstanceCount()) > 0) {
      obj.set("taskInstanceType", config.getEmrTaskInstanceType());
      obj.set("taskInstanceCount", config.getEmrTaskInstanceCount());
      if (config.getEmrTaskBidPrice() != null) {
        obj.set("taskInstanceBidPrice", config.getEmrTaskBidPrice());
      }
    }
    if (config.getEmrConfiguration() != null ) {
      obj.set("configuration", testEmrHiveConfiguration() );
      //obj.set("ebsRootVolumeSize", "500" );
      //obj.set("masterEbsConfiguration", testMasterEbsConfig() );
      //obj.set("coreEbsConfiguration", testMasterEbsConfig() );
    }
    allObjects.add(obj);
    return obj;
  }

  public PipelineObjectBase getSynchronizationBarrier(final String id,
      final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    obj.set("command", "ls");
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getEmrActivity(final String id, final PipelineObjectBase infrastructure,
      final Class<?> cls, final List<String> args) {
    return getEmrActivity(id, infrastructure, cls.getCanonicalName(),
        args.toArray(new String[] {}));
  }

  public PipelineObjectBase getShellActivity(final String id, final String cmd,
      final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    obj.set("command", cmd);
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getJavaShellActivity(final String id, final String jar,
      final Class<?> cls, final List<String> args, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    final String params = StringUtils.join(args, " ");
    final String cmd = "java -cp " + jar + " " + cls.getCanonicalName() + " " + params;
    obj.set("command", cmd);
    obj.set("retryDelay", "2 Minutes");
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getS3CopyActivity(final String id, final S3ObjectId src,
      final String dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    obj.set("command", "aws s3 cp " + AwsUtils.uri(src) + " " + dest + " --recursive");
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getS3CopyActivity(final String id, final String src,
      final S3ObjectId dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    obj.set("command", "aws s3 cp " + src + " " + AwsUtils.uri(dest) + " --recursive");
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getS3CopyActivity(final String id, final S3ObjectId src,
      final S3ObjectId dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    obj.set("command",
        "aws s3 cp " + AwsUtils.uri(src) + " " + AwsUtils.uri(dest) + " --recursive");
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getUnloadActivity(final String id, final S3ObjectId scriptLocation,
      final PipelineObjectBase database, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "SqlActivity");
    setupActivity(id, obj, infrastructure);
    obj.set("scriptUri", AwsUtils.uri(scriptLocation));
    obj.set("database", database);
    return obj;
  }

  public PipelineObjectBase getSqlScriptActivity(final String id, final S3ObjectId script,
      final PipelineObjectBase database, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "SqlActivity");
    setupActivity(id, obj, infrastructure);
    obj.set("scriptUri", AwsUtils.uri(script));
    obj.set("database", database);
    return obj;
  }

  public PipelineObjectBase getS3DistCpActivity(final String id, final String manifest,
      final S3ObjectId src, final String dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    final String cmd = "s3-dist-cp --copyFromManifest --previousManifest=file://" + manifest
        + " --dest=hdfs://" + cleanHdfs(dest) + " --src=" + AwsUtils.uri(src)
        + " --outputCodec=none";
    obj.set("command", cmd);
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getS3DistCpActivity(final String id, final S3ObjectId src,
      final String dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    final String cmd = "s3-dist-cp --src=" + AwsUtils.uri(src) + " --dest=hdfs://" + cleanHdfs(dest)
    + " --outputCodec=none";
    obj.set("command", cmd);
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getS3DistCpActivity(final String id, final String src,
      final S3ObjectId dest, final PipelineObjectBase infrastructure) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "ShellCommandActivity");
    setupActivity(id, obj, infrastructure);
    final String cmd = "s3-dist-cp --src=hdfs://" + cleanHdfs(src) + " --dest=" + AwsUtils.uri(dest)
    + " --outputCodec=gzip";
    obj.set("command", cmd);
    setStdOut(obj, id);
    return obj;
  }

  public PipelineObjectBase getAcquireLeaseActivity(final String id, final String leaseTable,
      final String leaseName, final String owner, final int seconds,
      final PipelineObjectBase infrastructure) {
    final List<String> args = new ArrayList<String>();
    args.add(leaseTable);
    args.add(leaseName);
    args.add(owner);
    args.add("" + seconds);
    final String jar = config.getEmrCodeDir() + "/" + config.getDataToolsJar();
    return getJavaShellActivity(id, jar, AcquireLeaseTask.class, args, infrastructure);
  }

  public PipelineObjectBase getRenewLeaseActivity(final String id, final String leaseTable,
      final String leaseName, final String owner, final int seconds,
      final PipelineObjectBase infrastructure) {
    final List<String> args = new ArrayList<String>();
    args.add(leaseTable);
    args.add(leaseName);
    args.add(owner);
    args.add("" + seconds);
    final String jar = config.getEmrCodeDir() + "/" + config.getDataToolsJar();
    return getJavaShellActivity(id, jar, RenewLeaseTask.class, args, infrastructure);
  }

  public PipelineObjectBase getReleaseLeaseActivity(final String id, final String leaseTable,
      final String leaseName, final String owner, final PipelineObjectBase infrastructure) {
    final List<String> args = new ArrayList<String>();
    args.add(leaseTable);
    args.add(leaseName);
    args.add(owner);
    final String jar = config.getEmrCodeDir() + "/" + config.getDataToolsJar();
    return getJavaShellActivity(id, jar, ReleaseLeaseTask.class, args, infrastructure);
  }

  ////////////////

  private void setStdOut(final PipelineObjectBase obj, final String id) {
    final S3ObjectId out = AwsUtils.key(config.getLogBucket(), runId, id + ".out");
    final S3ObjectId err = AwsUtils.key(config.getLogBucket(), runId, id + ".err");
    obj.set("stdout", AwsUtils.uri(out));
    obj.set("stderr", AwsUtils.uri(err));
  }

  private void setupActivity(final String id, final PipelineObjectBase obj,
      final PipelineObjectBase infrastructure) {
    obj.set("runsOn", infrastructure);
    obj.set("maximumRetries", config.getEmrMaximumRetries());
    final PipelineCompletionMessage completion = new PipelineCompletionMessage(pipelineId, runId,
        config.getReportBucket(), config.getFailureSnsArn(), config.getPipelineDynamoTable(), id);
    String failMsg;
    try {
      failMsg = new ObjectMapper().writeValueAsString(completion);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    final String failSubj = "Pipeline " + runId + " Failed";
    obj.set("onFail",
        getSns(id + "FailureSnsAlert", failSubj, failMsg, config.getCompletionSnsArn()));

    allObjects.add(obj);
  }

  private PipelineObjectBase getEmrActivity(final String id,
      final PipelineObjectBase infrastructure, final String cls, final String[] args) {
    final PipelineObjectBase obj = new PipelineObjectBase(config, id, "EmrActivity");
    setupActivity(id, obj, infrastructure);
    final String params = StringUtils.join(args, ",");
    final String jar = config.getEmrCodeDir() + "/" + config.getDataToolsJar();
    obj.set("step", jar + "," + cls + "," + params);
    obj.set("retryDelay", "2 Minutes");
    return obj;
  }

  private String cleanHdfs(final String path) {
    if (path.toLowerCase().startsWith("hdfs://")) {
      return path.substring("hdfs://".length());
    }
    return path;
  }

  public List<PipelineObject> getAllObjects() {
    final List<PipelineObject> objects = new ArrayList<PipelineObject>();
    for (final PipelineObjectBase obj : allObjects) {
      objects.add(obj.getPipelineObject());
    }
    return objects;
  }
  
  //testing
  public PipelineObjectBase testEmrConfiguration() {
	  final PipelineObjectBase obj = new PipelineObjectBase(config, "TestEmrConfiguration", "EmrConfiguration");
	  obj.set("ref", testEmrHiveConfiguration() );
	  allObjects.add(obj);
	  return obj;
  }
  
  public PipelineObjectBase testEmrHiveConfiguration() {
	  
	  final PipelineObjectBase obj = new PipelineObjectBase(config, "hivesite", "EmrConfiguration"); 

	  obj.set("classification", "hive-site" );
	  obj.set("property", testCreateEmrConfigObjects() );
	  
	  allObjects.add(obj);
	  return obj;
  }
  
  public PipelineObjectBase testMasterEbsConfig() {
	  final PipelineObjectBase obj = new PipelineObjectBase(config, "EBSConfiguration", "EbsConfiguration"); 
	  obj.set("ebsBlockDeviceConfig", testEbsBlockDeviceConfig());
	  if (!allObjects.contains(obj)) allObjects.add(obj);	  
	  return obj;
  }
  
  public PipelineObjectBase testEbsBlockDeviceConfig() {
	  final PipelineObjectBase obj = new PipelineObjectBase(config, "EbsBlkDeviceConfig", "EbsBlockDeviceConfig");
	  obj.set("volumesPerInstance", "1");
	  obj.set("volumeSpecification", testVolumeSpecConfig() );
	  if (!allObjects.contains(obj)) allObjects.add(obj);	  
	  return obj;
  }
  
  public PipelineObjectBase testVolumeSpecConfig() {
	  final PipelineObjectBase obj = new PipelineObjectBase(config, "VolSpecification", "VolumeSpecification");
      obj.set("sizeInGB", "500");
      obj.set("volumeType", "standard");
	  if (!allObjects.contains(obj)) allObjects.add(obj);	  
      return obj;
  }
  
  public ArrayList<PipelineObjectBase> testCreateEmrConfigObjects() {

	  ArrayList<PipelineObjectBase> listconfigobjects = new ArrayList<PipelineObjectBase>();
	  
	  // Create Properties
	  Map<String, String> hProperties = new HashMap<String,String>();
	  hProperties.put("hive-support-concurrency", "true");
	  hProperties.put("hive-enforce-bucketing", "true");
	  hProperties.put("hive-exec-dynamic-partition-mode", "nonstrict");
	  hProperties.put("hive-txn-manager", "org.apache.hadoop.hive.ql.lockmgr.DbTxnManager");
	  hProperties.put("hive-compactor-initiator-on", "true");
	  hProperties.put("hive-compactor-worker-threads", "2");
	  for( final String hProperty: hProperties.keySet() ) {
	  	listconfigobjects.add( testCreateEmrConfigObject(hProperty, hProperties.get( hProperty)));
	  }	 
	  return listconfigobjects;
  }

  public PipelineObjectBase testCreateEmrConfigObject(final String key, final String value ) {

	  final PipelineObjectBase obj = new PipelineObjectBase(config, key, "Property"); 
  	  obj.set("key", key.replace("-", ".") );
	  obj.set("value", value);
	  allObjects.add(obj);
	  return obj;
  }
  
  public PipelineObjectBase testEmrFsConfiguration() {
	  
	  final PipelineObjectBase obj = new PipelineObjectBase(config, "TestEmrFsConfiguration", "EmrConfiguration");

      Map<String,String> emrfsProperties = new HashMap<String,String>();
        emrfsProperties.put("fs.s3.enableServerSideEncryption","true");

	  Configuration customEmrConfig = new Configuration()
				.withClassification("emrfs-site")
				.withProperties(emrfsProperties);
	  obj.set("classification", customEmrConfig.getClassification());
	  obj.set("properties", customEmrConfig.getProperties());
	  return obj;
  }

}
