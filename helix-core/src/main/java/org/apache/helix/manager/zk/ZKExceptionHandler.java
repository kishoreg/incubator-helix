package org.apache.helix.manager.zk;

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

import org.I0Itec.zkclient.exception.ZkInterruptedException;
import org.apache.log4j.Logger;

public class ZKExceptionHandler
{
  private static ZKExceptionHandler instance = new ZKExceptionHandler();
  private static Logger logger = Logger.getLogger(ZKExceptionHandler.class);
  private ZKExceptionHandler()
  {

  }

  void handle(Exception e)
  {
    logger.error(Thread.currentThread().getName() + ". isThreadInterruped: " + Thread.currentThread().isInterrupted());

    if (e instanceof ZkInterruptedException)
    {
      logger.error("zk connection is interrupted.", e);
    }
    else
    {
      logger.error(e.getMessage(), e);
      // e.printStackTrace();
    }
  }

  public static ZKExceptionHandler getInstance()
  {
    return instance;
  }
}
