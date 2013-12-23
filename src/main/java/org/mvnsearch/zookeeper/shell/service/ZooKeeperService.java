package org.mvnsearch.zookeeper.shell.service;

import org.apache.curator.framework.CuratorFramework;

/**
 * ZooKeeper service
 *
 * @author linux_china
 */
public interface ZooKeeperService {

    public void connect(String connectionUrl);

    public CuratorFramework getCurator();

    public String executeCommand(String command) throws Exception;
}
