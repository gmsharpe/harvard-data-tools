package edu.harvard.data.client.redshift;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.harvard.data.client.schema.DataSchemaColumn;
import edu.harvard.data.client.schema.DataSchemaTable;

public class RedshiftTable extends DataSchemaTable {

  private final String name;
  private final List<DataSchemaColumn> columns;
  private final Map<String, DataSchemaColumn> columnsByName;

  protected RedshiftTable(final String name, final Map<Integer, DataSchemaColumn> columnsByPosition)
      throws SQLException {
    super(false, null);
    this.name = name;
    this.columns = new ArrayList<DataSchemaColumn>(columnsByPosition.size());
    this.columnsByName = new HashMap<String, DataSchemaColumn>();
    for (int pos = 1; pos <= columnsByPosition.size(); pos++) {
      final DataSchemaColumn col = columnsByPosition.get(pos);
      columns.add(col);
      columnsByName.put(col.getName(), col);
    }
  }

  public RedshiftTable(final RedshiftTable original) {
    super(original.newlyGenerated, original.owner);
    this.name = original.name;
    this.columns = new ArrayList<DataSchemaColumn>();
    this.columnsByName = new HashMap<String, DataSchemaColumn>();
    for (final DataSchemaColumn c : original.columns) {
      final DataSchemaColumn columnCopy = c.copy();
      this.columns.add(columnCopy);
      this.columnsByName.put(columnCopy.getName(), columnCopy);
    }
  }

  @Override
  public String getTableName() {
    return name;
  }

  @Override
  public List<DataSchemaColumn> getColumns() {
    return columns;
  }

  @Override
  public String getLikeTable() {
    return null;
  }

  @Override
  public DataSchemaTable copy() {
    return new RedshiftTable(this);
  }

  @Override
  public String toString() {
    String s = name + (newlyGenerated ? " *" : "");
    for (final DataSchemaColumn column : columns) {
      s += "\n    " + column;
    }
    return s;
  }

  @Override
  public DataSchemaColumn getColumn(final String name) {
    return columnsByName.get(name);
  }

}
