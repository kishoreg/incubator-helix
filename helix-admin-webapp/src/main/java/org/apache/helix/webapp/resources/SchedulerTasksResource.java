package org.apache.helix.webapp.resources;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.helix.HelixDataAccessor;
import org.apache.helix.HelixException;
import org.apache.helix.InstanceType;
import org.apache.helix.PropertyPathConfig;
import org.apache.helix.PropertyType;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.LiveInstance;
import org.apache.helix.model.Message;
import org.apache.helix.model.Message.MessageType;
import org.apache.helix.tools.ClusterSetup;
import org.apache.helix.webapp.RestAdminApplication;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;


/**
 * This resource can be used to send scheduler tasks to the controller.
 * 
 * */
public class SchedulerTasksResource extends Resource
{
  private final static Logger LOG = Logger.getLogger(SchedulerTasksResource.class);

  public static String CRITERIA = "Criteria";
  public static String MESSAGETEMPLATE = "MessageTemplate";
  public SchedulerTasksResource(Context context,
          Request request,
          Response response) 
  {
    super(context, request, response);
    getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    getVariants().add(new Variant(MediaType.APPLICATION_JSON));
  }

  @Override
  public boolean allowGet()
  {
    return true;
  }
  
  @Override
  public boolean allowPost()
  {
    return true;
  }
  
  @Override
  public boolean allowPut()
  {
    return false;
  }
  
  @Override
  public boolean allowDelete()
  {
    return false;
  }
  
  @Override
  public Representation represent(Variant variant)
  {
    StringRepresentation presentation = null;
    try
    {
      presentation = getSchedulerTasksRepresentation();
    }
    
    catch(Exception e)
    {
      String error = ClusterRepresentationUtil.getErrorAsJsonStringFromException(e);
      presentation = new StringRepresentation(error, MediaType.APPLICATION_JSON);

      LOG.error("", e);
    }  
    return presentation;
  }
  
  StringRepresentation getSchedulerTasksRepresentation() throws JsonGenerationException, JsonMappingException, IOException
  {
    String clusterName = (String)getRequest().getAttributes().get("clusterName");
    String instanceName = (String)getRequest().getAttributes().get("instanceName");
    ZkClient zkClient = (ZkClient)getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
    ClusterSetup setupTool = new ClusterSetup(zkClient);
    List<String> instances = setupTool.getClusterManagementTool().getInstancesInCluster(clusterName);
    
    HelixDataAccessor accessor = ClusterRepresentationUtil.getClusterDataAccessor(zkClient, clusterName);
    LiveInstance liveInstance = accessor.getProperty(accessor.keyBuilder().liveInstance(instanceName));
    String sessionId = liveInstance.getSessionId();
    
    StringRepresentation representation = new StringRepresentation("");//(ClusterRepresentationUtil.ObjectToJson(instanceConfigs), MediaType.APPLICATION_JSON);
    
    return representation;
  }
  
  @Override
  public void acceptRepresentation(Representation entity)
  {
    try
    {
      String clusterName = (String)getRequest().getAttributes().get("clusterName");
      Form form = new Form(entity);
      ZkClient zkClient = (ZkClient)getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
      
      String msgTemplateString = ClusterRepresentationUtil.getFormJsonParameterString(form, MESSAGETEMPLATE);
      if(msgTemplateString == null)
      {
        throw new HelixException("SchedulerTasksResource need to have MessageTemplate specified.");
      }
      Map<String, String> messageTemplate = ClusterRepresentationUtil.getFormJsonParameters(form, MESSAGETEMPLATE);
      
      String criteriaString = ClusterRepresentationUtil.getFormJsonParameterString(form, CRITERIA);
      if(criteriaString == null)
      {
        throw new HelixException("SchedulerTasksResource need to have Criteria specified.");
      }
      
      Message schedulerMessage = new Message(MessageType.SCHEDULER_MSG, UUID.randomUUID().toString());
      schedulerMessage.getRecord().getSimpleFields().put(CRITERIA, criteriaString);
      
      schedulerMessage.getRecord().getMapFields().put(MESSAGETEMPLATE, messageTemplate);
      
      schedulerMessage.setTgtSessionId("*");
      schedulerMessage.setTgtName("CONTROLLER");
      schedulerMessage.setSrcInstanceType(InstanceType.CONTROLLER);
      
      HelixDataAccessor accessor = ClusterRepresentationUtil.getClusterDataAccessor(zkClient, clusterName);
      accessor.setProperty(accessor.keyBuilder().controllerMessage(schedulerMessage.getMsgId()), schedulerMessage);
      
      Map<String, String> resultMap = new HashMap<String, String>();
      resultMap.put("StatusUpdatePath", PropertyPathConfig.getPath(PropertyType.STATUSUPDATES_CONTROLLER, clusterName, MessageType.SCHEDULER_MSG.toString(),schedulerMessage.getMsgId()));
      resultMap.put("MessageType", Message.MessageType.SCHEDULER_MSG.toString());
      resultMap.put("MsgId", schedulerMessage.getMsgId());
      
      // Assemble the rest URL for task status update
      String ipAddress = InetAddress.getLocalHost().getCanonicalHostName();
      String url = "http://" + ipAddress+":" + getContext().getAttributes().get(RestAdminApplication.PORT)
          + "/clusters/" + clusterName+"/Controller/statusUpdates/SCHEDULER_MSG/" + schedulerMessage.getMsgId();
      resultMap.put("statusUpdateUrl", url);
      
      getResponse().setEntity(ClusterRepresentationUtil.ObjectToJson(resultMap), MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
    }
    catch(Exception e)
    {
      getResponse().setEntity(ClusterRepresentationUtil.getErrorAsJsonStringFromException(e),
          MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
      LOG.error("", e);
    }  
  }
}
