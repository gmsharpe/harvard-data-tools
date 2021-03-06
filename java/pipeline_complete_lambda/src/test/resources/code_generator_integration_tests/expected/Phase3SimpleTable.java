// This file was generated automatically. Do not edit. 

package edu.harvard.data.integration.bindings.phase3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.csv.CSVRecord;
import edu.harvard.data.DataTable;
import edu.harvard.data.TableFormat;
import edu.harvard.data.integration.bindings.phase2.Phase2SimpleTable;

public class Phase3SimpleTable implements DataTable {

  private Integer intColumn;
  private String stringColumn;

  public Phase3SimpleTable() {}

  public Phase3SimpleTable(final TableFormat format, final CSVRecord record) {
    String $intColumn = record.get(0);
    if ($intColumn != null && $intColumn.length() > 0) {
      this.intColumn = Integer.valueOf($intColumn);
    }
    this.stringColumn = record.get(1);
  }

  public Phase3SimpleTable(final TableFormat format, final Map<String, Object> map) {
    this.intColumn = (Integer) map.get("int_column");
    this.stringColumn = (String) map.get("string_column");
  }

  public Phase3SimpleTable(Phase2SimpleTable phase2SimpleTable) {
    this.intColumn = phase2SimpleTable.getIntColumn();
    this.stringColumn = phase2SimpleTable.getStringColumn();
  }
  
  public Phase3SimpleTable(
        Integer intColumn,
        String stringColumn) {
    this.intColumn = intColumn;
    this.stringColumn = stringColumn;
  }

  /**
   * This is a column description.
   */
  public Integer getIntColumn() {
    return this.intColumn;
  }

  /**
   * This is a column description.
   */
  public void setIntColumn(Integer intColumn) {
    this.intColumn = intColumn;
  }

  public String getStringColumn() {
    return this.stringColumn;
  }

  public void setStringColumn(String stringColumn) {
    this.stringColumn = stringColumn;
  }

  @Override
  public List<String> getFieldNames() {
    final List<String> fields = new ArrayList<String>();
    fields.add("int_column");
    fields.add("string_column");
    return fields;
  }
  @Override
  public List<Object> getFieldsAsList(final TableFormat formatter) {
    final List<Object> fields = new ArrayList<Object>();
    fields.add(intColumn);
    fields.add(stringColumn);
    return fields;
  }
  @Override
  public Map<String, Object> getFieldsAsMap() {
    Map<String, Object> $map = new HashMap<String, Object>();
    $map.put("int_column", intColumn);
    $map.put("string_column", stringColumn);
    return $map;
  }
}
