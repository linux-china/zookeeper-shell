package org.mvnsearch.zookeeper.shell.commands;

import org.apache.zookeeper.CreateMode;

/**
 * ZooKeeper create mode
 *
 * @author linux_china
 */
public enum ZkNodeCreateMode {
    persistent("persistent"),
    persistent_sequential("persistent_sequential"),
    ephemeral("ephemeral"),
    ephemeral_sequential("ephemeral_sequential");

    private String name;

    private ZkNodeCreateMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


    @Override
    public String toString() {
        return name;
    }

    public CreateMode toZkMode() {
        if (name.equals("persistent_sequential")) {
            return CreateMode.EPHEMERAL_SEQUENTIAL;
        } else if (name.equals("ephemeral")) {
            return CreateMode.EPHEMERAL;
        } else if (name.equals("ephemeral_sequential")) {
            return CreateMode.EPHEMERAL_SEQUENTIAL;
        } else {
            return CreateMode.PERSISTENT;
        }
    }
}
