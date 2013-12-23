package org.mvnsearch.zookeeper.shell.commands;

/**
 * zookeeper command
 *
 * @author linux_china
 */
public enum ZkCommand {
    conf("conf"),
    cons("cons"),
    crst("crst"),
    dump("dump"),
    envi("envi"),
    ruok("ruok"),
    srst("srst"),
    srvr("srvr"),
    stat("stat"),
    wchs("wchs"),
    wchc("wchc"),
    wchp("wchp"),
    mntr("mntr");

    private String name;

    private ZkCommand(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


    @Override
    public String toString() {
        return name;
    }
}
