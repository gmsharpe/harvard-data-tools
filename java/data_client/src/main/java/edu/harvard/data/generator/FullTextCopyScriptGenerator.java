package edu.harvard.data.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import edu.harvard.data.AwsUtils;
import edu.harvard.data.DataConfig;
import edu.harvard.data.FormatLibrary;
import edu.harvard.data.pipeline.InputTableIndex;
import edu.harvard.data.schema.fulltext.FullTextSchema;
import edu.harvard.data.schema.fulltext.FullTextTable;

public class FullTextCopyScriptGenerator {

  private final File dir;
  private final InputTableIndex dataIndex;
  private final DataConfig config;
  private final FullTextSchema textSchema;

  public FullTextCopyScriptGenerator(final File dir, final DataConfig config,
      final FullTextSchema textSchema, final InputTableIndex dataIndex) {
    this.config = config;
    this.dir = dir;
    this.textSchema = textSchema;
    this.dataIndex = dataIndex;
  }

  public void generate() throws IOException {
    final File scriptFile = new File(dir, config.getFullTextScriptFile());

    try (final PrintStream out = new PrintStream(new FileOutputStream(scriptFile))) {
      boolean data = false;
      for (final String table : textSchema.tableNames()) {
        if (dataIndex.containsTable(table)) {
          generateTable(out, table, config.getEmrLogDir() + "/full_text_copy.out");
          data = true;
        }
      }
      if (data) {
        out.println("aws s3 cp --recursive " + config.getFullTextDir() + "/ "
            + AwsUtils.uri(config.getFullTextLocation()));
      }
    }
  }

  private void generateTable(final PrintStream out, final String tableName, final String logFile ) {
    
	if (dataIndex.isPartial(tableName)) {
	    out.println("if hadoop fs -test -e " + "/current" + "; then ");	       
    	generateMergeTable(out, tableName, "cur_", logFile );
        out.println("fi");
    	generateMergeTable(out, tableName, "in_", logFile );
    	generatePartialTable(out, tableName, "merged_");
    	generateFullTable(out, tableName, "merged_");
    } else {
    	generateMergeTable(out, tableName, "in_", logFile );
    	generatePartialTable( out, tableName, "merged_");
    	generateFullTable(out, tableName, "merged_");
        out.println();
    }
  }
  
  private void generatePartialTable( final PrintStream out, final String tableName, 
		  final String outputFrom ) {
	final FullTextTable table = textSchema.get(tableName);
    out.println("mkdir -p " + config.getFullTextDir() + "/" + tableName);
    for (final String column : table.getColumns()) {
      final String filename = config.getFullTextDir() + "/" + tableName + "/" + column;
      out.print("sudo hive -S -e \"SELECT " + table.getKey() + ", ");
      extractField( out, column, true );
      out.print(" FROM " + outputFrom + tableName + ";\" > " + filename);
      out.println("");
      out.println("gzip " + filename);
    }  
  }
	  
  private void generateFullTable( final PrintStream out, final String tableName, 
		  final String outputFrom ) {
    final FullTextTable table = textSchema.get(tableName);
    out.println("mkdir -p " + config.getFullTextDir() + "/" + tableName + "/fulltable");
    final String filename = config.getFullTextDir() + "/" + tableName + "/fulltable/" + tableName;
    out.print("sudo hive -S -e \"SELECT " );
    extractFields(out, table, tableName, outputFrom, true );
    out.println(" FROM " + outputFrom + tableName + ";\" > " + filename);
    out.println("gzip " + filename);
  }
  
  private void generateMergeTable( final PrintStream out, final String tableName, final String copyFrom,
		  final String logFile ) {
	final FullTextTable table = textSchema.get(tableName);
    out.println("sudo hive -e \"");
	out.println("  MERGE INTO merged_" + tableName );
	out.println("  USING " + copyFrom + tableName + " ON merged_" + tableName
			    + "." + table.getKey() + " = " + copyFrom + tableName + "." + table.getKey() );
	matchFields( out, table, tableName, copyFrom, true);
    out.println("  WHEN NOT MATCHED THEN");
    out.println("  INSERT VALUES (");
	insertFields(out, table, tableName, copyFrom, true );
	out.println("    ); ");
	out.println("");
	out.println("\" >> " + logFile + " 2>&1");    
  }
  
  private void extractFields( final PrintStream out, final FullTextTable table,
		  final String tableName, final String extractFrom, final boolean addMetadata ) {
	String finalstring = new String();
	List<String> listofstrings = new ArrayList<String>();
	List<String> listofmeta = new ArrayList<String>();
	String separator = ",\n";
    listofstrings.add(0, table.getKey());
	for (final String column : table.getColumns() ) {
	  listofstrings.add( column );
	  if ( addMetadata && !column.equals( table.getKey()) ) {
		  listofmeta.add( addTimestamp( column));
	  }
	}
	List<String> orderList = new ArrayList<String>(listofstrings);
	if (addMetadata) orderList.addAll(listofmeta);    
	finalstring = StringUtils.join( orderList, separator );
	out.println(finalstring);  
  }
  
  private void extractField( final PrintStream out, final String textcolumn, 
		  final boolean addMetadata ) {
	String finalstring = new String();
	List<String> listofstrings = new ArrayList<String>();
    List<String> listofmeta = new ArrayList<String>();
	String separator = ", ";
	listofstrings.add(textcolumn);
	if (addMetadata) {
	    listofstrings.add( addChecksum(textcolumn));
		listofmeta.add( addTimestamp(textcolumn));
	}
    List<String> orderList = new ArrayList<String>(listofstrings);
    if (addMetadata) orderList.addAll(listofmeta);    	
	finalstring = StringUtils.join( orderList, separator );
	out.println(finalstring);
  }
  
  private void matchFields( final PrintStream out, final FullTextTable table,
		  final String tableName, final String copyFrom, final boolean addMetadata ) {
	String finalstring = new String();
	List<String> listofstrings = new ArrayList<String>();
	List<String> listofmeta = new ArrayList<String>();
	String separator = ",\n";
	out.println("  WHEN MATCHED THEN UPDATE SET ");	
	for (final String column : table.getColumns() ) {
		listofstrings.add( "    " + column + "=" + "( CASE " +
					" WHEN ( " + "md5( merged_" + tableName + "." + column + " ) != md5( " + copyFrom + tableName + "." + column + " ) ) " +
				    " THEN " + copyFrom + tableName + "." + column + 
				    " ELSE " + "merged_" + tableName + "." + column + 
				    " END )");
		if (addMetadata) {
			String timevalue = new String();
			if (copyFrom.equals("in_")) {
				timevalue = "current_timestamp";
			} else {
				timevalue = copyFrom + tableName + "." + "time_" + column;
			}
			listofmeta.add( "    " + "time_" + column + "=" + "( CASE " +
					" WHEN ( " + "md5( merged_" + tableName + "." + column + " ) != md5( " + copyFrom + tableName + "." + column + " ) ) " +
				    " THEN " + timevalue + 
				    " ELSE " + "merged_" + tableName + "." + "time_" + column + 
				    " END )");		
		}
	}
    List<String> orderList = new ArrayList<String>(listofstrings);
    if (addMetadata) orderList.addAll(listofmeta);    	
	finalstring = StringUtils.join( orderList, separator );
	out.println(finalstring);	
  }

  private String addChecksum( final String fulltextfield ) {
	final FormatLibrary formatLibrary = new FormatLibrary();
	final boolean isQuotedFormat = formatLibrary.getFormat(config.getFulltextFormat())
							    .getCsvFormat()
							    .isQuoteCharacterSet();	  
	String checkSumString = new String();
	
	if (isQuotedFormat) {
		// If quoted, then remove quotes and replace escaped double quotes with single quotes
	    checkSumString = ("md5(substr(regexp_replace(" + fulltextfield + ", '\\" + "\"" + "\\" + "\"" + "', '" + "\\" + "\"" + "'), " +
	    				  "2, length(regexp_replace(" + fulltextfield + ", '\\" + "\"" + "\\" + "\"" + "', '" + "\\" + "\"" +
	    		          "')) -2) )");
	} else {
	    checkSumString = ("md5(" + fulltextfield + ")");
	}
	return checkSumString;
  }
  
  private String addTimestamp(final String fulltextfield ) {
	  return ("time_" + fulltextfield );
  }
    
  private void setFields( final PrintStream out, final FullTextTable table, 
		  final String tableName, final String copyFrom, final boolean addMetadata ) {
  	String finalstring = new String();
    List<String> listofstrings = new ArrayList<String>();
    List<String> listofmeta = new ArrayList<String>();
    String separator = ",\n";
    for (final String column : table.getColumns() ) {
      listofstrings.add("    " + column + "=" + copyFrom + tableName + "." + column );
      
      if ( addMetadata && !column.equals( table.getKey()) ) {
    	  listofmeta.add("    " + "time_" + column + "=" + "current_timestamp" );
      }
    }
    List<String> orderList = new ArrayList<String>(listofstrings);
    if (addMetadata) orderList.addAll(listofmeta);    
    finalstring = StringUtils.join( orderList, separator );
    out.println(finalstring);
  }
  
  private void insertFields(final PrintStream out, final FullTextTable table,
		  final String tableName, final String copyFrom, final boolean addMetadata ) {
	String finalstring = new String();
    List<String> listofstrings = new ArrayList<String>();
    List<String> listofmeta = new ArrayList<String>();
    listofstrings.add(0, copyFrom + tableName + "." + table.getKey());
    String separator = ", ";
    for (final String column : table.getColumns() ) {
      listofstrings.add( copyFrom + tableName + "." + column );
      
	  if ( addMetadata && !column.equals( table.getKey()) ) {
		  if (copyFrom.equals("cur_") ) {
			  listofmeta.add( copyFrom + tableName + "." + addTimestamp( column) );
		  } else listofmeta.add( "current_timestamp" );
	  }
      
    }
    List<String> orderList = new ArrayList<String>(listofstrings);
    if (addMetadata) orderList.addAll(listofmeta);
    finalstring = StringUtils.join( orderList, separator );
    out.println(finalstring);
  }  
  
}
