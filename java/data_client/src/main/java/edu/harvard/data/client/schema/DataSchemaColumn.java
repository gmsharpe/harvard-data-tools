package edu.harvard.data.client.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class DataSchemaColumn {

  @JsonIgnore
  protected boolean newlyGenerated;

  public abstract String getName();

  public abstract String getDescription();

  public abstract DataSchemaType getType();

  public abstract Integer getLength();

  public abstract DataSchemaColumn copy();

  protected DataSchemaColumn(final boolean newlyGenerated) {
    this.newlyGenerated = newlyGenerated;
  }

  public boolean getNewlyGenerated() {
    return newlyGenerated;
  }

  public void setNewlyGenerated(final boolean newlyGenerated) {
    this.newlyGenerated = newlyGenerated;
  }

  protected String cleanColumnName(final String name) {
    final String clean = name;
    switch(name) {
    case "default":
      return "is_default";
    default:
      return clean;
    }
  }

}
