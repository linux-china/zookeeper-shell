package org.mvnsearch.zookeeper.shell.service.impl;

import org.apache.commons.io.IOUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.mvnsearch.zookeeper.shell.service.ZooKeeperService;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.Socket;

/**
 * Zoo Keeper Service implementation
 *
 * @author linux_china
 */
@Component("zooKeeperService")
public class ZooKeeperServiceImpl implements ZooKeeperService {
    private CuratorFramework curator;
    private String server;

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
        this.server = server;
    }

    public CuratorFramework getCurator() {
        return this.curator;
    }

    public String executeCommand(String command) throws Exception {
        String[] parts = server.split(":");
        Socket sock = new Socket(parts[0], Integer.valueOf(parts[1]));
        IOUtils.write(command.getBytes(), sock.getOutputStream());
        String content = IOUtils.toString(sock.getInputStream());
        if (!sock.isClosed()) {
            sock.close();
        }
        return content.trim();
    }
}
