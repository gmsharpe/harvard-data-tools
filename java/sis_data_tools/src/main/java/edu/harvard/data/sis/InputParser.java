package edu.harvard.data.sis;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.s3.model.S3ObjectId;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataTable;
import edu.harvard.data.FormatLibrary;
import edu.harvard.data.FormatLibrary.Format;
import edu.harvard.data.TableFormat;
import edu.harvard.data.TableFormat.Compression;
import edu.harvard.data.io.FileTableReader;
import edu.harvard.data.io.JsonFileReader;
import edu.harvard.data.io.TableWriter;
import edu.harvard.data.sis.SisDataConfig;
import edu.harvard.data.sis.bindings.phase0.Phase0CourseCatalog;
import edu.harvard.data.sis.bindings.phase0.Phase0Classes;
import edu.harvard.data.sis.bindings.phase0.Phase0CourseMap;
import edu.harvard.data.sis.bindings.phase0.Phase0PrimeCourseEnroll;
import edu.harvard.data.sis.bindings.phase0.Phase0CourseEnroll;
import edu.harvard.data.pipeline.InputTableIndex;

public class InputParser {

  private static final Logger log = LogManager.getLogger();

  private final SisDataConfig config;
  private final AwsUtils aws;
  private final S3ObjectId inputObj;
  
  private File dataproductFile;
  private S3ObjectId dataproductOutputObj;
  
  private final String key;
  private final String filename;
  
  private final String currentDataProduct;
  private final String dataproductPrefix;
  private final String dataproductFiletype;

  private File originalFile;

  private final TableFormat inFormat;
  private final TableFormat outFormat; 
  private final S3ObjectId catalogOutputDir;
  private final S3ObjectId classesOutputDir;
  private final S3ObjectId coursemapOutputDir;
  private final S3ObjectId primeEnrollOutputDir;
  private final S3ObjectId enrollOutputDir;


  public InputParser(final SisDataConfig config, final AwsUtils aws,
      final S3ObjectId inputObj, final S3ObjectId outputLocation) {
    this.config = config;
    this.aws = aws;
    this.inputObj = inputObj;
    this.key = inputObj.getKey();
	this.filename = key.substring(key.lastIndexOf("/") + 1);	  
    this.dataproductPrefix = "PrepSIS-";
    this.dataproductFiletype = ".json.gz";
    this.currentDataProduct = getDataProduct();
    this.catalogOutputDir = AwsUtils.key(outputLocation, "CourseCatalog");
    this.classesOutputDir = AwsUtils.key(outputLocation, "Classes");
    this.coursemapOutputDir = AwsUtils.key(outputLocation, "CourseMap");
    this.primeEnrollOutputDir = AwsUtils.key( outputLocation, "PrimeCourseEnroll" );
    this.enrollOutputDir = AwsUtils.key( outputLocation, "CourseEnroll" );
    final FormatLibrary formatLibrary = new FormatLibrary();
    this.inFormat = formatLibrary.getFormat(Format.Sis);
    final ObjectMapper jsonMapper = new ObjectMapper();
    jsonMapper.setSerializationInclusion(Include.NON_NULL);    
    this.inFormat.setJsonMapper(jsonMapper);
    this.outFormat = formatLibrary.getFormat(config.getPipelineFormat());
    this.outFormat.setCompression(Compression.Gzip);
  }

  public InputTableIndex parseFile() throws IOException {
    final InputTableIndex dataIndex = new InputTableIndex();
    try {
      getFileName();
      aws.getFile(inputObj, originalFile);
      parse();
      verify();
      // Add product check here
      aws.putFile(dataproductOutputObj, dataproductFile);
      // Add product check here
      dataIndex.addFile(currentDataProduct, dataproductOutputObj, dataproductFile.length());
    } finally {
      cleanup();
    }
    return dataIndex;
  }
  
  private final String getDataProduct() {
    final String dataproduct = filename.substring( filename.lastIndexOf(dataproductPrefix)+dataproductPrefix.length() ).replace(dataproductFiletype, "");
    return dataproduct;
  }
  
  private void getFileName() {
    originalFile = new File(config.getScratchDir(), filename);

    final String dataproductFilename = currentDataProduct + ".gz";
    dataproductFile = new File(config.getScratchDir(), dataproductFilename );
    
    if (currentDataProduct.equals("CourseCatalog") ) {
        dataproductOutputObj = AwsUtils.key(catalogOutputDir, dataproductFilename );  
    } else if ( currentDataProduct.equals("Classes") ) {
        dataproductOutputObj = AwsUtils.key(classesOutputDir, dataproductFilename);        
    } else if ( currentDataProduct.equals("CourseMap") ) {
        dataproductOutputObj = AwsUtils.key(coursemapOutputDir, dataproductFilename);   
    } else if ( currentDataProduct.equals("PrimeCourseEnroll") ) {
        dataproductOutputObj = AwsUtils.key(primeEnrollOutputDir, dataproductFilename);
    } else if ( currentDataProduct.equals("CourseEnroll") ) {
        dataproductOutputObj = AwsUtils.key(enrollOutputDir, dataproductFilename);
    }
    
    log.info("Parsing " + filename + " to " + dataproductFile);
    log.info("DataProduct Key: " + dataproductOutputObj );
    
  }

  private void parse() throws IOException {
    log.info("Parsing file " + originalFile);
    if (currentDataProduct.equals("CourseCatalog")) {
        log.info("Parsing data product " + currentDataProduct);
    	try (
    	        final JsonFileReader in = new JsonFileReader(inFormat, originalFile,
    	            new EventJsonDocumentParser(inFormat, true, currentDataProduct));
    	    	TableWriter<Phase0CourseCatalog> catalogs = new TableWriter<Phase0CourseCatalog>(Phase0CourseCatalog.class, outFormat,
    	            dataproductFile);) {
    		for (final Map<String, List<? extends DataTable>> tables : in) {
    	              catalogs.add((Phase0CourseCatalog) tables.get("CourseCatalog").get(0));
    		}
    	}
	} else if (currentDataProduct.equals("Classes")) {
        log.info("Parsing data product " + currentDataProduct);
    	try (
    	        final JsonFileReader in = new JsonFileReader(inFormat, originalFile,
    	            new EventJsonDocumentParser(inFormat, true, currentDataProduct));
    	    	TableWriter<Phase0Classes> classes = new TableWriter<Phase0Classes>(Phase0Classes.class, outFormat,
    	                dataproductFile);) {
    		for (final Map<String, List<? extends DataTable>> tables : in) {
    			  classes.add((Phase0Classes) tables.get("Classes").get(0));
    		}
    	}
	} else if (currentDataProduct.equals("CourseMap")) {
        log.info("Parsing data product " + currentDataProduct);
    	try (
    	        final JsonFileReader in = new JsonFileReader(inFormat, originalFile,
    	            new EventJsonDocumentParser(inFormat, true, currentDataProduct));
    	    	TableWriter<Phase0CourseMap> coursemap = new TableWriter<Phase0CourseMap>(Phase0CourseMap.class, outFormat,
    	                dataproductFile);) {
    		for (final Map<String, List<? extends DataTable>> tables : in) {
    			coursemap.add((Phase0CourseMap) tables.get("CourseMap").get(0));
    		}
    	}
	} else if (currentDataProduct.equals("PrimeCourseEnroll")) {
        log.info("Parsing data product " + currentDataProduct);
    	try (
    	        final JsonFileReader in = new JsonFileReader(inFormat, originalFile,
    	            new EventJsonDocumentParser(inFormat, true, currentDataProduct));
    	    	TableWriter<Phase0PrimeCourseEnroll> primeenroll = new TableWriter<Phase0PrimeCourseEnroll>(Phase0PrimeCourseEnroll.class, outFormat,
    	                dataproductFile);) {
    		for (final Map<String, List<? extends DataTable>> tables : in) {
    			  primeenroll.add((Phase0PrimeCourseEnroll) tables.get("PrimeCourseEnroll").get(0));
    		}
    	}			
	} else if (currentDataProduct.equals("CourseEnroll")) {
        log.info("Parsing data product " + currentDataProduct);
    	try (
    	        final JsonFileReader in = new JsonFileReader(inFormat, originalFile,
    	            new EventJsonDocumentParser(inFormat, true, currentDataProduct));
    	    	TableWriter<Phase0CourseEnroll> enroll = new TableWriter<Phase0CourseEnroll>(Phase0CourseEnroll.class, outFormat,
    	                dataproductFile);) {
    		for (final Map<String, List<? extends DataTable>> tables : in) {
    		      enroll.add((Phase0CourseEnroll) tables.get("CourseEnroll").get(0));
    		}
    	}			
	}
    log.info("Done Parsing file " + originalFile);
  }

  @SuppressWarnings("unused") // We run through each table's iterator, but don't
  // need the values.
  private void verify() throws IOException {
    if (currentDataProduct.equals("CourseCatalog")) {

	    try(FileTableReader<Phase0CourseCatalog> in = new FileTableReader<Phase0CourseCatalog>(Phase0CourseCatalog.class,
		    outFormat, dataproductFile)) {
	      log.info("Verifying file " + dataproductFile);	
	      for (final Phase0CourseCatalog i : in ) {
	      }
	    }
    } else if (currentDataProduct.equals("Classes")) {
	    try(FileTableReader<Phase0Classes> in = new FileTableReader<Phase0Classes>(Phase0Classes.class,
			outFormat, dataproductFile)) {
		  log.info("Verifying file " + dataproductFile);	
	      for (final Phase0Classes i : in ) {
	      }
	    }	    
    } else if (currentDataProduct.equals("CourseMap")) {
	    try(FileTableReader<Phase0CourseMap> in = new FileTableReader<Phase0CourseMap>(Phase0CourseMap.class,
			outFormat, dataproductFile)) {
		  log.info("Verifying file " + dataproductFile);
	      for (final Phase0CourseMap i : in ) {
	      }
	    }
    } else if (currentDataProduct.equals("PrimeCourseEnroll")) {
	    try(FileTableReader<Phase0PrimeCourseEnroll> in = new FileTableReader<Phase0PrimeCourseEnroll>(Phase0PrimeCourseEnroll.class,
			outFormat, dataproductFile)) {
		  log.info("Verifying file " + dataproductFile);	
	      for (final Phase0PrimeCourseEnroll i : in ) {
	      }
	    }
    } else if (currentDataProduct.equals("CourseEnroll")) {
	    try(FileTableReader<Phase0CourseEnroll> in = new FileTableReader<Phase0CourseEnroll>(Phase0CourseEnroll.class,
			outFormat, dataproductFile)) {
		  log.info("Verifying file " + dataproductFile);	
	      for (final Phase0CourseEnroll i : in ) {
	      }
	    }
    }
    
  }

  private void cleanup() {
    if (originalFile != null && originalFile.exists()) {
      originalFile.delete();
    }
    // STart
    if (dataproductFile != null && dataproductFile.exists()) {
    	dataproductFile.delete();
    }
  }

}
