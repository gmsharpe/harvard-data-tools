package edu.harvard.data.sis;

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.data.DataTable;
import edu.harvard.data.TableFormat;
import edu.harvard.data.VerificationException;
import edu.harvard.data.io.JsonDocumentParser;
import edu.harvard.data.sis.bindings.phase0.Phase0Classes;
import edu.harvard.data.sis.bindings.phase0.Phase0CourseCatalog;
import edu.harvard.data.sis.bindings.phase0.Phase0CourseMap;
import edu.harvard.data.sis.bindings.phase0.Phase0PrimeCourseEnroll;
import edu.harvard.data.sis.bindings.phase0.Phase0CourseEnroll;


public class EventJsonDocumentParser implements JsonDocumentParser {
  private static final Logger log = LogManager.getLogger();

  private final TableFormat format;
  private final boolean verify;
  private final String dataproduct;

  public EventJsonDocumentParser(final TableFormat format, final boolean verify, final String dataproduct) {
    this.format = format;
    this.verify = verify;
    this.dataproduct = dataproduct;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, List<? extends DataTable>> getDocuments(final Map<String, Object> values)
      throws ParseException, VerificationException {
    final Map<String, List<? extends DataTable>> tables = new HashMap<String, List<? extends DataTable>>();
    // Start
    // Course Catalogs
    final Phase0CourseCatalog coursecatalog = new Phase0CourseCatalog(format, values);
    final List<Phase0CourseCatalog> coursecatalogs = new ArrayList<Phase0CourseCatalog>();
    coursecatalogs.add(coursecatalog);
    tables.put("CourseCatalog", coursecatalogs);

    // Classes
    final Phase0Classes cclass = new Phase0Classes(format, values);
    final List<Phase0Classes> classes = new ArrayList<Phase0Classes>();
    classes.add(cclass);
    tables.put("Classes", classes);

    // CourseMap
    final Phase0CourseMap cmap = new Phase0CourseMap(format, values);
    final List<Phase0CourseMap> coursemap = new ArrayList<Phase0CourseMap>();
    coursemap.add(cmap);
    tables.put("CourseMap", coursemap);    
    
    // Prime Course Enroll
    final Phase0PrimeCourseEnroll primecourseenroll = new Phase0PrimeCourseEnroll(format, values);
    final List<Phase0PrimeCourseEnroll> primecourseenrollments = new ArrayList<Phase0PrimeCourseEnroll>();
    primecourseenrollments.add(primecourseenroll);
    tables.put("PrimeCourseEnroll", primecourseenrollments);

    // Course Enroll
    final Phase0CourseEnroll courseenroll = new Phase0CourseEnroll(format, values);
    final List<Phase0CourseEnroll> courseenrollments = new ArrayList<Phase0CourseEnroll>();
    courseenrollments.add(courseenroll);
    tables.put("CourseEnroll", courseenrollments);    
    
    // Verification Step (optional)
    if (verify) {
    	verifyParser(values, tables);
    }
    return tables;
  }

  public void verifyParser(final Map<String, Object> values,
      final Map<String, List<? extends DataTable>> tables) throws VerificationException {
    // Start
	final List<? extends DataTable> coursecatalogs = tables.get("CourseCatalog");
	final List<? extends DataTable> classes = tables.get("Classes");
	final List<? extends DataTable> coursemap = tables.get("CourseMap");
	final List<? extends DataTable> primecourseenrollments = tables.get("PrimeCourseEnroll");
	final List<? extends DataTable> courseenrollments = tables.get("CourseEnroll");

	final Map<String, Object> parsedCourseCatalogs = coursecatalogs.get(0).getFieldsAsMap();
	final Map<String, Object> parsedClasses = classes.get(0).getFieldsAsMap();
	final Map<String, Object> parsedCourseMap = coursemap.get(0).getFieldsAsMap();
	final Map<String, Object> parsedPrimeCourseEnrollments = primecourseenrollments.get(0).getFieldsAsMap();
	final Map<String, Object> parsedCourseEnrollments = courseenrollments.get(0).getFieldsAsMap();
	
	// Course Catalogs
	if (dataproduct.equals("CourseCatalog")) {
		
	    try {
		    compareMaps(values, parsedCourseCatalogs);
	    } catch (final VerificationException e) {
	        log.error("Failed to verify JSON document. " + e.getMessage());
	        log.error("Original map: " + values);
	        log.error("Parsed map:   " + parsedCourseCatalogs);
	        throw e;			
	    }
	}
	// Classes
	else if (dataproduct.equals("Classes")) {
		
		try {	
			compareMaps(values, parsedClasses);		
		} catch (final VerificationException e) {
		    log.error("Failed to verify JSON document. " + e.getMessage());
		    log.error("Original map: " + values);
		    log.error("Parsed map:   " + parsedClasses);
		    throw e;				
		}
	}
	// CourseMap
	else if (dataproduct.equals("CourseMap")) {
		
		try {
			compareMaps(values, parsedCourseMap);
		} catch (final VerificationException e) {
		    log.error("Failed to verify JSON document. " + e.getMessage());
		    log.error("Original map: " + values);
		    log.error("Parsed map:   " + parsedCourseMap);
		    throw e;
		}
	}
	// Prime Course Enroll
	else if (dataproduct.equals("PrimeCourseEnroll")) {

		try {
			compareMaps(values, parsedPrimeCourseEnrollments);
		} catch (final VerificationException e) {
		    log.error("Failed to verify JSON document. " + e.getMessage());
		    log.error("Original map: " + values);
		    log.error("Parsed map:   " + parsedPrimeCourseEnrollments);
		    throw e;			
		}
	}
	// Course Enroll
	else if (dataproduct.equals("CourseEnroll")) {

		try {
			compareMaps(values, parsedCourseEnrollments);
		} catch (final VerificationException e) {
		    log.error("Failed to verify JSON document. " + e.getMessage());
		    log.error("Original map: " + values);
		    log.error("Parsed map:   " + parsedCourseEnrollments);
		    throw e;			
		}
	}	
	
  }

  @SuppressWarnings("unchecked")
  private void compareMaps(final Map<String, Object> m1, final Map<String, Object> m2)
      throws VerificationException {
    for (final String key : m1.keySet()) {
      String m2Key = key;
      if (key.startsWith("@")) {
        m2Key = key.substring(1);
      }
      if (!m2.containsKey(m2Key)) {
        throw new VerificationException("Missing key: " + m2Key);
      }
      if (m1.get(key) == null && m2.get(m2Key) != null) {
          throw new VerificationException("Key " + key + " should be null, not " + m2.get(m2Key));
      } else if (m1.get(key) instanceof Map) {
        if (!(m2.get(m2Key) instanceof Map)) {
          throw new VerificationException("Incorrect type for key " + key);
        }
        compareMaps((Map<String, Object>) m1.get(key), (Map<String, Object>) m2.get(m2Key));
      } else {
        final String v1 = cleanValue( m1.get(key).toString() );
        if (m2.get(m2Key) == null) {
          throw new VerificationException("Key " + key + " should not be null");
        }
        if (m2.get(m2Key) instanceof Boolean) {
          if ((boolean) m2.get(m2Key)) {
            if (!(v1.equals("true") || v1.equals("1"))) {
              throw new VerificationException(
                  "Different values for key " + key + ". Original: " + v1 + ", new: true");
            }
          } else {
            if (!(v1.equals("false") || v1.equals("0"))) {
              throw new VerificationException(
                  "Different values for key " + key + ". Original: " + v1 + ", new: false");
            }
          }
        } else {
          final String v2 = cleanValue( convertToString(m2.get(m2Key)) );
          if (m2.get(m2Key) instanceof Timestamp) {
            compareTimestamps(v1, v2, key);
          } else if (m2.get(m2Key) instanceof Double) {
            compareDoubles(v1, v2, key);
          } else {
            if (!v1.equals(v2)) {
              throw new VerificationException(
                  "Different values for key " + key + ". Original: " + v1 + ", new: " + v2);
            }
          }
        }
      }
    }
  }

  private void compareDoubles(String v1, String v2, final String key) throws VerificationException {
    if (v1.endsWith(".0")) {
      v1 = v1.substring(0, v1.lastIndexOf("."));
    }
    if (v2.endsWith(".0")) {
      v2 = v2.substring(0, v2.lastIndexOf("."));
    }
    if (!v1.equals(v2)) {
      throw new VerificationException(
          "Different values for key " + key + ". Original: " + v1 + ", new: " + v2);
    }
  }

  private void compareTimestamps(String v1, String v2, final String key)
      throws VerificationException {
    if (v1.endsWith(".000Z")) {
      v1 = v1.substring(0, v1.lastIndexOf(".")) + "Z";
    }
    if (v2.endsWith(".000Z")) {
      v2 = v2.substring(0, v2.lastIndexOf(".")) + "Z";
    }
    if (!v1.equals(v2)) {
      throw new VerificationException(
          "Different values for key " + key + ". Original: " + v1 + ", new: " + v2);
    }
  }

  private String cleanValue(String value) throws VerificationException {
	    if (value != null) {
	      value = value.replaceAll("\\t", " ");
	    }
	    return value;
  }
  
  private String convertToString(final Object object) {
    if (object instanceof Timestamp) {
      return format.formatTimestamp(new Date(((Timestamp) object).getTime()));
    }
    return object.toString();
  }

}
