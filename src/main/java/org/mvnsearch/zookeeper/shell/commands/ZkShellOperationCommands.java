package org.mvnsearch.zookeeper.shell.commands;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.data.Stat;
import org.fusesource.jansi.Ansi;
import org.mvnsearch.zookeeper.shell.service.ZooKeeperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;

/**
 * ZooKeeper shell operation commands
 *
 * @author linux_china
 */
@ShellComponent
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZkShellOperationCommands {
    public static String currentPath = "/";
    public static String previousPath = "/";
    /**
     * log
     */
    private Logger log = LoggerFactory.getLogger(ZkShellOperationCommands.class);
    /**
     * The platform-specific line separator.
     */
    public static final String LINE_SEPARATOR = System.lineSeparator();

    @Autowired
    private ZooKeeperService zooKeeperService;

    /**
     * init method: load current bucket
     */
    @PostConstruct
    public void init() {
        System.out.println(connect("localhost:2181"));
    }

    /**
     * config command to save aliyun OSS information
     *
     * @return result
     */
    @ShellMethod(key = "connect", value = "Connect zookeeper, format as localhost:2181")
    public String connect(@ShellOption(help = "ZooKeeper Hosts") String hosts) {
        try {
            if (StringUtils.isEmpty(hosts)) {
                hosts = "localhost:2181";
            }
            zooKeeperService.connect(hosts);
        } catch (Exception e) {
            log.error("connect", e);
            return wrappedAsRed(e.getMessage());
        }
        return "Connected with " + hosts;
    }

    /**
     * stop aria2
     *
     * @return stop status
     */
    @ShellMethod(key = "cd", value = "Change Path")
    public String cd(@ShellOption(help = "Directory") String path) {
        try {
            System.out.println("input path: " + path + " current path: " + currentPath);
            String destPath = getAbsolutePath(path);
            Stat stat = zooKeeperService.getCurator().checkExists().forPath(destPath);
            if (stat != null) {
                previousPath = currentPath;
                currentPath = destPath;
            } else {
                return wrappedAsRed("Destination not existed: " + destPath);
            }
        } catch (Exception e) {
            log.error("cd", e);
            return wrappedAsRed(e.getMessage());
        }
        return null;
    }

    /**
     * stop aria2
     *
     * @return stop status
     */
    @ShellMethod(key = "ls", value = "List directories or files")
    public String ls(@ShellOption(help = "Node path", defaultValue = "") String path) {
        try {
            String destPath = getAbsolutePath(path);
            List<String> children = zooKeeperService.getCurator().getChildren().forPath(destPath);
            if (children.isEmpty()) {
                return "No children found!";
            }
            return StringUtils.join(children, LINE_SEPARATOR);
        } catch (Exception e) {
            log.error("ls", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    /**
     * stop aria2
     *
     * @return stop status
     */
    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    @ShellMethod(key = "stat", value = "Show node or server stat")
    public String stat(@ShellOption(help = "Node name") String path) {
        try {
            if (StringUtils.isEmpty(path)) {
                return zooKeeperService.executeCommand("stat");
            }
            Stat stat = zooKeeperService.getCurator().checkExists().forPath(getAbsolutePath(path));
            StringBuilder buf = new StringBuilder();
            buf.append("cZxid = " + stat.getCzxid() + LINE_SEPARATOR);
            buf.append("ctime = " + new Date(stat.getCtime()).toString() + LINE_SEPARATOR);
            buf.append("mZxid = " + stat.getMzxid() + LINE_SEPARATOR);
            buf.append("mtime = " + new Date(stat.getMtime()).toString() + LINE_SEPARATOR);
            buf.append("pZxid = " + stat.getPzxid() + LINE_SEPARATOR);
            buf.append("cversion = " + stat.getCversion() + LINE_SEPARATOR);
            buf.append("dataVersion = " + stat.getVersion() + LINE_SEPARATOR);
            buf.append("aclVersion = " + stat.getAversion() + LINE_SEPARATOR);
            if (stat.getEphemeralOwner() > 0) {
                buf.append("ephemeralOwner = " + stat.getEphemeralOwner() + LINE_SEPARATOR);
            }
            buf.append("dataLength = " + stat.getDataLength() + LINE_SEPARATOR);
            buf.append("numChildren = " + stat.getNumChildren());
            return buf.toString();
        } catch (Exception e) {
            log.error("stat", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @ShellMethod(key = "cat", value = "Show node content")
    public String cat(@ShellOption(help = "Node name") String path) {
        try {
            byte[] content = zooKeeperService.getCurator().getData().forPath(getAbsolutePath(path));
            return new String(content);
        } catch (Exception e) {
            log.error("cat", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @ShellMethod(key = "server", value = "Execute command")
    public String server(@ShellOption(help = "Command name") final ZkCommand command) {
        try {
            return zooKeeperService.executeCommand(command.getName());
        } catch (Exception e) {
            log.error("server", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @ShellMethod(key = "touch", value = "Create node")
    public String touch(
            @ShellOption(value = {"mode"}, help = "Node name") ZkNodeCreateMode mode,
            @ShellOption(help = "Node name") String name) {
        try {
            zooKeeperService.getCurator().create().withMode(mode.toZkMode()).forPath(getAbsolutePath(name), new byte[0]);
            return stat(name);
        } catch (Exception e) {
            log.error("cat", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @ShellMethod(key = "mkdir", value = "Create node")
    public String mkdir(@ShellOption(help = "Node name") String name) {
        return touch(ZkNodeCreateMode.persistent, name);
    }

    @ShellMethod(key = "echo", value = "Update content")
    public String echo(@ShellOption(value = {"node"}, help = "Node Name") String nodePath,
                       @ShellOption(help = "Node Content") String content) {
        try {
            String absolutePath = getAbsolutePath(nodePath);
            Stat stat = zooKeeperService.getCurator().checkExists().forPath(absolutePath);
            if (stat == null) {
                zooKeeperService.getCurator().create().forPath(absolutePath, content.getBytes());
                return "'" + absolutePath + "' created with given content!";
            } else {
                zooKeeperService.getCurator().setData().forPath(absolutePath, content.getBytes());
                return "'" + absolutePath + "' updated with given content!";
            }
        } catch (Exception e) {
            log.error("echo", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @ShellMethod(key = "watch", value = "Watch node")
    public String watch(@ShellOption(help = "Node name") String name) {
        try {
            final String absolutePath = getAbsolutePath(name);
            Stat stat = zooKeeperService.getCurator().checkExists().forPath(absolutePath);
            if (stat == null) {
                return "'" + absolutePath + "' not exists.";
            } else {
                zooKeeperService.getCurator().getData().usingWatcher(new CuratorWatcher() {
                    @Override
                    public void process(WatchedEvent watchedEvent) throws Exception {
                        System.out.println("'" + absolutePath + "' " + watchedEvent.getType().name());
                    }
                }).forPath(absolutePath);
                return "'" + absolutePath + "' Watched";
            }
        } catch (Exception e) {
            log.error("echo", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    /**
     * wrapped as red with Jansi
     *
     * @param text text
     * @return wrapped text
     */
    private String wrappedAsRed(String text) {
        return Ansi.ansi().fg(Ansi.Color.RED).a(text).toString();
    }


    /**
     * wrapped as yellow with Jansi
     *
     * @param text text
     * @return wrapped text
     */
    private String wrappedAsYellow(String text) {
        return Ansi.ansi().fg(Ansi.Color.YELLOW).a(text).toString();
    }

    public String getAbsolutePath(String path) {
        if (StringUtils.isEmpty(path)) {
            return currentPath;
        }
        String destPath;
        // cd absolute path
        if (path.startsWith("/")) {
            destPath = path;
        } else if (path.equals("-")) {  //previous path
            destPath = previousPath;
        } else if (path.startsWith(".")) {  //relative path
            if (path.startsWith("../") || path.startsWith("..")) {
                destPath = currentPath.substring(0, currentPath.lastIndexOf("/"));
            } else {
                destPath = endWith(currentPath, "/") + path.substring(currentPath.lastIndexOf("./") + 2);
            }
        } else {
            destPath = endWith(currentPath, "/") + path;
        }
        if (destPath.isEmpty()) {
            destPath = "/";
        }
        return destPath;
    }

    public String endWith(String path, String end) {
        return path.endsWith(end) ? path : path + end;
    }


}
