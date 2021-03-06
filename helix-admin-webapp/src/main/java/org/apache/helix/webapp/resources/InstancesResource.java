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
import java.util.Map;

import org.apache.helix.HelixDataAccessor;
import org.apache.helix.HelixException;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.InstanceConfig;
import org.apache.helix.model.LiveInstance;
import org.apache.helix.tools.ClusterSetup;
import org.apache.helix.webapp.RestAdminApplication;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;


public class InstancesResource extends Resource
{
  private final static Logger LOG = Logger.getLogger(InstancesResource.class);

  public InstancesResource(Context context, Request request, Response response)
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
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      presentation = getInstancesRepresentation(clusterName);
    }

    catch (Exception e)
    {
      String error = ClusterRepresentationUtil.getErrorAsJsonStringFromException(e);
      presentation = new StringRepresentation(error, MediaType.APPLICATION_JSON);

      LOG.error("", e);
    }
    return presentation;
  }

  StringRepresentation getInstancesRepresentation(String clusterName) throws JsonGenerationException,
      JsonMappingException,
      IOException
  {
    ZkClient zkClient =
        (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);

    HelixDataAccessor accessor =
        ClusterRepresentationUtil.getClusterDataAccessor(zkClient, clusterName);
    Map<String, LiveInstance> liveInstancesMap =
        accessor.getChildValuesMap(accessor.keyBuilder().liveInstances());
    Map<String, InstanceConfig> instanceConfigsMap =
        accessor.getChildValuesMap(accessor.keyBuilder().instanceConfigs());

    for (String instanceName : instanceConfigsMap.keySet())
    {
      boolean isAlive = liveInstancesMap.containsKey(instanceName);
      instanceConfigsMap.get(instanceName)
                        .getRecord()
                        .setSimpleField("Alive", isAlive + "");
    }

    StringRepresentation representation =
        new StringRepresentation(ClusterRepresentationUtil.ObjectToJson(instanceConfigsMap.values()),
                                 MediaType.APPLICATION_JSON);

    return representation;
  }

  @Override
  public void acceptRepresentation(Representation entity)
  {
    try
    {
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      JsonParameters jsonParameters = new JsonParameters(entity);
      String command = jsonParameters.getCommand();

      ZkClient zkClient =
          (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
      ClusterSetup setupTool = new ClusterSetup(zkClient);

      if (command.equalsIgnoreCase(ClusterSetup.addInstance)
          || JsonParameters.CLUSTERSETUP_COMMAND_ALIASES.get(ClusterSetup.addInstance)
                                                        .contains(command))
      {
        if (jsonParameters.getParameter(JsonParameters.INSTANCE_NAME) != null)
        {
          setupTool.addInstanceToCluster(clusterName,
                                         jsonParameters.getParameter(JsonParameters.INSTANCE_NAME));
        }
        else if (jsonParameters.getParameter(JsonParameters.INSTANCE_NAMES) != null)
        {
          setupTool.addInstancesToCluster(clusterName,
                                          jsonParameters.getParameter(JsonParameters.INSTANCE_NAMES)
                                                        .split(";"));
        }
        else
        {
          throw new HelixException("Missing Json paramaters: '"
              + JsonParameters.INSTANCE_NAME + "' or '" + JsonParameters.INSTANCE_NAMES
              + "' ");
        }
      }
      else if (command.equalsIgnoreCase(ClusterSetup.swapInstance))
      {
        if (jsonParameters.getParameter(JsonParameters.NEW_INSTANCE) == null
            || jsonParameters.getParameter(JsonParameters.OLD_INSTANCE) == null)
        {
          throw new HelixException("Missing Json paramaters: '"
              + JsonParameters.NEW_INSTANCE + "' or '" + JsonParameters.OLD_INSTANCE
              + "' ");
        }
        setupTool.swapInstance(clusterName,
                               jsonParameters.getParameter(JsonParameters.OLD_INSTANCE),
                               jsonParameters.getParameter(JsonParameters.NEW_INSTANCE));
      }
      else
      {
        throw new HelixException("Unsupported command: " + command
            + ". Should be one of [" + ClusterSetup.addInstance + ", "
            + ClusterSetup.swapInstance + "]");
      }

      getResponse().setEntity(getInstancesRepresentation(clusterName));
      getResponse().setStatus(Status.SUCCESS_OK);
    }
    catch (Exception e)
    {
      getResponse().setEntity(ClusterRepresentationUtil.getErrorAsJsonStringFromException(e),
                              MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
      LOG.error("", e);
    }
  }
}
