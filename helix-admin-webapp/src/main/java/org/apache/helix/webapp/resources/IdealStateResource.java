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
import org.apache.helix.PropertyKey;
import org.apache.helix.ZNRecord;
import org.apache.helix.PropertyKey.Builder;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.IdealState;
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


public class IdealStateResource extends Resource
{
  private final static Logger LOG = Logger.getLogger(IdealStateResource.class);

  public IdealStateResource(Context context, Request request, Response response)
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
      String resourceName = (String) getRequest().getAttributes().get("resourceName");
      presentation = getIdealStateRepresentation(clusterName, resourceName);
    }

    catch (Exception e)
    {
      String error = ClusterRepresentationUtil.getErrorAsJsonStringFromException(e);
      presentation = new StringRepresentation(error, MediaType.APPLICATION_JSON);

      LOG.error("", e);
    }
    return presentation;
  }

  StringRepresentation getIdealStateRepresentation(String clusterName, String resourceName) throws JsonGenerationException,
      JsonMappingException,
      IOException
  {
    Builder keyBuilder = new PropertyKey.Builder(clusterName);
    ZkClient zkClient =
        (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);

    String message =
        ClusterRepresentationUtil.getClusterPropertyAsString(zkClient,
                                                             clusterName,
                                                             keyBuilder.idealStates(resourceName),
                                                             MediaType.APPLICATION_JSON);

    StringRepresentation representation =
        new StringRepresentation(message, MediaType.APPLICATION_JSON);

    return representation;
  }

  @Override
  public void acceptRepresentation(Representation entity)
  {
    try
    {
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      String resourceName = (String) getRequest().getAttributes().get("resourceName");
      ZkClient zkClient =
          (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
      ClusterSetup setupTool = new ClusterSetup(zkClient);

      JsonParameters jsonParameters = new JsonParameters(entity);
      String command = jsonParameters.getCommand();

      if (command.equalsIgnoreCase(ClusterSetup.addIdealState))
      {
        ZNRecord newIdealState = jsonParameters.getExtraParameter(JsonParameters.NEW_IDEAL_STATE);
        HelixDataAccessor accessor =
            ClusterRepresentationUtil.getClusterDataAccessor(zkClient, clusterName);

        accessor.setProperty(accessor.keyBuilder().idealStates(resourceName),
                             new IdealState(newIdealState));

      }
      else if (command.equalsIgnoreCase(ClusterSetup.rebalance))
      {
        int replicas = 
            Integer.parseInt(jsonParameters.getParameter(JsonParameters.REPLICAS));
        if (jsonParameters.getParameter(JsonParameters.RESOURCE_KEY_PREFIX) != null)
        {
          setupTool.rebalanceStorageCluster(clusterName,
                                            resourceName,
                                            replicas,
                                            jsonParameters.getParameter(JsonParameters.RESOURCE_KEY_PREFIX));
        }
        else
        {
          setupTool.rebalanceStorageCluster(clusterName, resourceName, replicas);
        }
      }
      else if (command.equalsIgnoreCase(ClusterSetup.expandResource))
      {
        setupTool.expandResource(clusterName, resourceName);
      }
      else if (command.equalsIgnoreCase(ClusterSetup.addResourceProperty))
      {
        Map<String, String> parameterMap = jsonParameters.cloneParameterMap();
        parameterMap.remove(JsonParameters.MANAGEMENT_COMMAND);
        for (String key : parameterMap.keySet())
        {
          setupTool.addResourceProperty(clusterName,
                                        resourceName,
                                        key,
                                        parameterMap.get(key));
        }
      }
      else
      {
        throw new HelixException("Unsupported command: " + command
            + ". Should be one of [" + ClusterSetup.addIdealState + ", "
            + ClusterSetup.rebalance + ", " + ClusterSetup.expandResource + ", "
            + ClusterSetup.addResourceProperty + "]");
      }

      getResponse().setEntity(getIdealStateRepresentation(clusterName, resourceName));
      getResponse().setStatus(Status.SUCCESS_OK);
    }
    catch (Exception e)
    {
      getResponse().setEntity(ClusterRepresentationUtil.getErrorAsJsonStringFromException(e),
                              MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
      LOG.error("Error in posting " + entity, e);
    }
  }
}
