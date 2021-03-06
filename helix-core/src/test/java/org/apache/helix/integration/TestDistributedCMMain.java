package org.apache.helix.integration;

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

import java.util.Date;

import org.apache.helix.TestHelper;
import org.apache.helix.ZNRecord;
import org.apache.helix.PropertyKey.Builder;
import org.apache.helix.controller.HelixControllerMain;
import org.apache.helix.manager.zk.ZKHelixDataAccessor;
import org.apache.helix.manager.zk.ZkBaseDataAccessor;
import org.apache.helix.mock.controller.ClusterController;
import org.apache.helix.mock.participant.MockParticipant;
import org.apache.helix.model.LiveInstance;
import org.apache.helix.tools.ClusterSetup;
import org.apache.helix.tools.ClusterStateVerifier;
import org.apache.helix.tools.ClusterStateVerifier.BestPossAndExtViewZkVerifier;
import org.testng.Assert;
import org.testng.annotations.Test;


public class TestDistributedCMMain extends ZkIntegrationTestBase
{

  @Test
  public void testDistributedCMMain() throws Exception
  {
    String className = TestHelper.getTestClassName();
    String methodName = TestHelper.getTestMethodName();
    String clusterNamePrefix = className + "_" + methodName;
    final int n = 5;
    final int clusterNb = 10;

    System.out.println("START " + clusterNamePrefix + " at "
        + new Date(System.currentTimeMillis()));

    // setup 10 clusters
    for (int i = 0; i < clusterNb; i++)
    {
      String clusterName = clusterNamePrefix + "0_" + i;
      String participantName = "localhost" + i;
      String resourceName = "TestDB" + i;
      TestHelper.setupCluster(clusterName, ZK_ADDR, 12918, // participant port
                              participantName, // participant name prefix
                              resourceName, // resource name prefix
                              1, // resources
                              8, // partitions per resource
                              n, // number of nodes
                              3, // replicas
                              "MasterSlave",
                              true); // do rebalance
    }

    // setup controller cluster
    final String controllerClusterName = "CONTROLLER_" + clusterNamePrefix;
    TestHelper.setupCluster("CONTROLLER_" + clusterNamePrefix, ZK_ADDR, 0, // controller
                                                                           // port
                            "controller", // participant name prefix
                            clusterNamePrefix, // resource name prefix
                            1, // resources
                            clusterNb, // partitions per resource
                            n, // number of nodes
                            3, // replicas
                            "LeaderStandby",
                            true); // do rebalance

    // start distributed cluster controllers
    ClusterController[] controllers = new ClusterController[n + n];
    for (int i = 0; i < n; i++)
    {
      controllers[i] =
          new ClusterController(controllerClusterName,
                                "controller_" + i,
                                ZK_ADDR,
                                HelixControllerMain.DISTRIBUTED.toString());
      controllers[i].syncStart();
    }

    boolean result =
        ClusterStateVerifier.verifyByZkCallback(new ClusterStateVerifier.BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                                      controllerClusterName),
                                                30000);
    Assert.assertTrue(result, "Controller cluster NOT in ideal state");

    // start first cluster
    MockParticipant[] participants = new MockParticipant[n];
    final String firstClusterName = clusterNamePrefix + "0_0";
    for (int i = 0; i < n; i++)
    {
      String instanceName = "localhost0_" + (12918 + i);
      participants[i] =
          new MockParticipant(firstClusterName, instanceName, ZK_ADDR, null);
      participants[i].syncStart();
    }

    result =
        ClusterStateVerifier.verifyByZkCallback(new BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                 firstClusterName));
    Assert.assertTrue(result, "first cluster NOT in ideal state");
    
    
    // add more controllers to controller cluster
    ClusterSetup setupTool = new ClusterSetup(ZK_ADDR);
    for (int i = 0; i < n; i++)
    {
      String controller = "controller:" + (n + i);
      setupTool.addInstanceToCluster(controllerClusterName, controller);
    }
    setupTool.rebalanceStorageCluster(controllerClusterName, clusterNamePrefix + "0", 6);
    for (int i = n; i < 2 * n; i++)
    {
      controllers[i] =
          new ClusterController(controllerClusterName,
                                "controller_" + i,
                                ZK_ADDR,
                                HelixControllerMain.DISTRIBUTED.toString());
      controllers[i].syncStart();
    }

    
    // verify controller cluster
    result =
        ClusterStateVerifier.verifyByZkCallback(new ClusterStateVerifier.BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                                      controllerClusterName));
    Assert.assertTrue(result, "Controller cluster NOT in ideal state");
    
    // verify first cluster
    result =
        ClusterStateVerifier.verifyByZkCallback(new BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                 firstClusterName));
    Assert.assertTrue(result, "first cluster NOT in ideal state");

    
    // stop controller_0-5
    ZkBaseDataAccessor<ZNRecord> baseAccessor =
        new ZkBaseDataAccessor<ZNRecord>(_gZkClient);
    ZKHelixDataAccessor accessor = new ZKHelixDataAccessor(controllerClusterName, baseAccessor);
    Builder keyBuilder = accessor.keyBuilder();
    for (int i = 0; i < n; i++)
    {
      LiveInstance leader = accessor.getProperty(keyBuilder.controllerLeader());
      String leaderName = leader.getId();
      int j = Integer.parseInt(leaderName.substring(leaderName.lastIndexOf('_') + 1));
      controllers[j].syncStop();
      
      result =
          ClusterStateVerifier.verifyByZkCallback(new ClusterStateVerifier.BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                                        controllerClusterName));
      Assert.assertTrue(result, "Controller cluster NOT in ideal state");
      
      result =
          ClusterStateVerifier.verifyByZkCallback(new BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                   firstClusterName));
      Assert.assertTrue(result, "first cluster NOT in ideal state");
    }

    
    // clean up
    // wait for all zk callbacks done
    System.out.println("Cleaning up...");
    Thread.sleep(2000);
    for (int i = 0; i < 5; i++)
    {
      result =
          ClusterStateVerifier.verifyByZkCallback(new ClusterStateVerifier.BestPossAndExtViewZkVerifier(ZK_ADDR,
                                                                                                        controllerClusterName));
      controllers[i].syncStop();
    }
    
    // Thread.sleep(2000);
    for (int i = 0; i < 5; i++)
    {
      participants[i].syncStop();
    }

    System.out.println("END " + clusterNamePrefix + " at "
        + new Date(System.currentTimeMillis()));

  }
}
