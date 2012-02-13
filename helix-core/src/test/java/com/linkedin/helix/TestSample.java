package com.linkedin.helix;

import org.apache.zookeeper.Watcher.Event.EventType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.linkedin.helix.manager.zk.ZKDataAccessor;

/**
 *
 * @author kgopalak
 *
 */

public class TestSample
{

  @Test ()
  public final void testCallbackHandler()
  {
    ZKDataAccessor client = null;
    String path = null;
    Object listener = null;
    EventType[] eventTypes = null;

  }

  @BeforeMethod ()
  public void asd()
  {
    System.out.println("In Set up");
  }

  @Test ()
  public void testB()
  {
    System.out.println("In method testB");

  }

  @Test ()
  public void testA()
  {
    System.out.println("In method testA");

  }

  @AfterMethod ()
  public void sfds()
  {
    System.out.println("In tear down");
  }
}
