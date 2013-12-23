package org.mvnsearch.zookeeper.shell.service.impl;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.mvnsearch.zookeeper.shell.service.ZooKeeperService;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * Zoo Keeper Service implementation
 *
 * @author linux_china
 */
@Component("zooKeeperService")
public class ZooKeeperServiceImpl implements ZooKeeperService {
    private CuratorFramework curator;

    @PreDestroy
    public void close() {
        if (curator != null) {
            curator.close();
        }
    }

    public void connect(String server) {
        if (this.curator != null) {
            this.curator.close();
        }
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 1);
        this.curator = CuratorFrameworkFactory.newClient(server, retryPolicy);
        this.curator.start();
    }

    public CuratorFramework getCurator() {
        return this.curator;
    }
}
