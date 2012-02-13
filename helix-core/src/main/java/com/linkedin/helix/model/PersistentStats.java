package com.linkedin.helix.model;


import java.util.Map;

import org.apache.log4j.Logger;
import com.linkedin.helix.ZNRecord;
import com.linkedin.helix.ZNRecordDecorator;

public class PersistentStats extends ZNRecordDecorator
{
  private static final Logger _logger = Logger.getLogger(PersistentStats.class.getName());

  public enum PersistentStatsProperty
	  {
	    SESSION_ID,
	    FIELDS
	  }
	
//  private final ZNRecord _record;
	
  public final static String nodeName = "PersistentStats";
	
  
  public PersistentStats(String id)
  {
    super(id);
  }
  
  public PersistentStats(ZNRecord record)
  {
//    _record = record;
    super(record);

  }

  /*
  public PersistentStats(ZNRecord record, Stat stat)
  {
    super(record, stat);
  }
*/

  public void setSessionId(String sessionId){
    _record.setSimpleField(PersistentStatsProperty.SESSION_ID.toString(), sessionId);
  }
  public String getSessionId()
  {
    return _record.getSimpleField(PersistentStatsProperty.SESSION_ID.toString());
  }

  public String getInstanceName()
  {
    return _record.getId();
  }

  /*
  public String getVersion()
  {
    return _record.getSimpleField(CLUSTER_MANAGER_VERSION.toString());
  }
  */
  
  
  public Map<String, Map<String, String>> getMapFields() {
	  return _record.getMapFields();
  }
  
  
  public Map<String, String> getStatFields(String statName) {
	  return _record.getMapField(statName);
  }

@Override
public boolean isValid() {
	// TODO Auto-generated method stub
	return false;
}
  
}


