package org.apache.solr.cloud;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.zookeeper.CreateMode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base test class for ZooKeeper tests.
 */
public abstract class AbstractZkTestCase extends SolrTestCaseJ4 {

  static final int TIMEOUT = 10000;

  private static final boolean DEBUG = false;

  protected static Logger log = LoggerFactory
      .getLogger(AbstractZkTestCase.class);

  
  public static File SOLRHOME;
  static {
    try {
      SOLRHOME = new File(TEST_HOME());
    } catch (RuntimeException e) {
      log.warn("TEST_HOME() does not exist - solrj test?");
      // solrj tests not working with TEST_HOME()
      // must override getSolrHome
    }
  }
  
  protected static ZkTestServer zkServer;

  protected static String zkDir;


  @BeforeClass
  public static void azt_beforeClass() throws Exception {
    System.out.println("azt beforeclass");
    createTempDir();
    zkDir = dataDir.getAbsolutePath() + File.separator
        + "zookeeper/server1/data";
    zkServer = new ZkTestServer(zkDir);
    zkServer.run();
    
    System.setProperty("solrcloud.skip.autorecovery", "true");
    System.setProperty("zkHost", zkServer.getZkAddress());
    System.setProperty("jetty.port", "0000");
    
    buildZooKeeper(zkServer.getZkHost(), zkServer.getZkAddress(), SOLRHOME,
        "solrconfig.xml", "schema.xml");
    
    initCore("solrconfig.xml", "schema.xml");
  }

  static void buildZooKeeper(String zkHost, String zkAddress, String config,
      String schema) throws Exception {
    buildZooKeeper(zkHost, zkAddress, SOLRHOME, config, schema);
  }
  
  // static to share with distrib test
  static void buildZooKeeper(String zkHost, String zkAddress, File solrhome, String config,
      String schema) throws Exception {
    SolrZkClient zkClient = new SolrZkClient(zkHost, AbstractZkTestCase.TIMEOUT);
    zkClient.makePath("/solr", false, true);
    zkClient.close();

    zkClient = new SolrZkClient(zkAddress, AbstractZkTestCase.TIMEOUT);

    Map<String,Object> props = new HashMap<String,Object>();
    props.put("configName", "conf1");
    final ZkNodeProps zkProps = new ZkNodeProps(props);
    
    zkClient.makePath("/collections/collection1", ZkStateReader.toJSON(zkProps), CreateMode.PERSISTENT, true);
    zkClient.makePath("/collections/collection1/shards", CreateMode.PERSISTENT, true);
    zkClient.makePath("/collections/control_collection", ZkStateReader.toJSON(zkProps), CreateMode.PERSISTENT, true);
    zkClient.makePath("/collections/control_collection/shards", CreateMode.PERSISTENT, true);

    putConfig(zkClient, solrhome, config);
    putConfig(zkClient, solrhome, schema);
    putConfig(zkClient, solrhome, "solrconfig.xml");
    putConfig(zkClient, solrhome, "stopwords.txt");
    putConfig(zkClient, solrhome, "protwords.txt");
    putConfig(zkClient, solrhome, "currency.xml");
    putConfig(zkClient, solrhome, "open-exchange-rates.json");
    putConfig(zkClient, solrhome, "mapping-ISOLatin1Accent.txt");
    putConfig(zkClient, solrhome, "old_synonyms.txt");
    putConfig(zkClient, solrhome, "synonyms.txt");
    
    zkClient.close();
  }

  private static void putConfig(SolrZkClient zkClient, File solrhome, final String name)
      throws Exception {
    String path = "/configs/conf1/" + name;
    File file = new File(solrhome, "collection1"
        + File.separator + "conf" + File.separator + name);
    if (!file.exists()) {
      log.info("skipping " + file.getAbsolutePath() + " because it doesn't exist");
      return;
    }
    
    log.info("put " + file.getAbsolutePath() + " to " + path);
    zkClient.makePath(path, file, false, true);  
  }

  @Override
  public void tearDown() throws Exception {
    if (DEBUG) {
      printLayout(zkServer.getZkHost());
    }

    super.tearDown();
  }
  
  @AfterClass
  public static void azt_afterClass() throws Exception {
    System.clearProperty("zkHost");
    System.clearProperty("solr.test.sys.prop1");
    System.clearProperty("solr.test.sys.prop2");
    System.clearProperty("solrcloud.skip.autorecovery");
    System.clearProperty("jetty.port");

    zkServer.shutdown();

    // wait just a bit for any zk client threads to outlast timeout
    Thread.sleep(2000);
  }

  protected void printLayout(String zkHost) throws Exception {
    SolrZkClient zkClient = new SolrZkClient(zkHost, AbstractZkTestCase.TIMEOUT);
    zkClient.printLayoutToStdOut();
    zkClient.close();
  }

  public static void makeSolrZkNode(String zkHost) throws Exception {
    SolrZkClient zkClient = new SolrZkClient(zkHost, TIMEOUT);
    zkClient.makePath("/solr", false, true);
    zkClient.close();
  }
  
  public static void tryCleanSolrZkNode(String zkHost) throws Exception {
    tryCleanPath(zkHost, "/solr");
  }
  
  static void tryCleanPath(String zkHost, String path) throws Exception {
    SolrZkClient zkClient = new SolrZkClient(zkHost, TIMEOUT);
    if (zkClient.exists(path, true)) {
      zkClient.clean(path);
    }
    zkClient.close();
  }
}
