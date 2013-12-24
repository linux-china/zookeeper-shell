package org.mvnsearch.zookeeper.shell.commands;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.zookeeper.data.Stat;
import org.fusesource.jansi.Ansi;
import org.mvnsearch.zookeeper.shell.service.ZooKeeperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;

/**
 * ZooKeeper shell operation commands
 *
 * @author linux_china
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZkShellOperationCommands implements CommandMarker {
    public static String currentPath = "/";
    public static String previousPath = "/";
    /**
     * log
     */
    private Logger log = LoggerFactory.getLogger(ZkShellOperationCommands.class);
    /**
     * The platform-specific line separator.
     */
    public static final String LINE_SEPARATOR = SystemUtils.LINE_SEPARATOR;

    @Autowired
    private ZooKeeperService zooKeeperService;

    /**
     * init method: load current bucket
     */
    @PostConstruct
    public void init() {
        String message = connect("localhost:2181");
    }

    /**
     * config command to save aliyun OSS information
     *
     * @return result
     */
    @CliCommand(value = "connect", help = "Connect with aria2 through xml-rpc")
    public String connect(@CliOption(key = {"server"}, mandatory = false, help = "Server") String server) {
        try {
            if (StringUtils.isEmpty(server)) {
                server = "localhost:2181";
            }
            zooKeeperService.connect(server);
        } catch (Exception e) {
            log.error("connect", e);
            return wrappedAsRed(e.getMessage());
        }
        return "Connected with " + server;
    }

    /**
     * stop aria2
     *
     * @return stop status
     */
    @CliCommand(value = "cd", help = "Change Path")
    public String cd(@CliOption(key = {""}, mandatory = true, help = "Directory") String path) {
        try {
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
    @CliCommand(value = "ls", help = "List directories or files")
    public String ls(@CliOption(key = {""}, mandatory = false, help = "Node path") String path) {
        try {
            String destPath = getAbsolutePath(path);
            List<String> children = zooKeeperService.getCurator().getChildren().forPath(destPath);
            if (children.isEmpty()) {
                return "No children found!";
            }
            return StringUtils.join(children, SystemUtils.LINE_SEPARATOR);
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
    @CliCommand(value = "stat", help = "Show node or server stat")
    public String stat(@CliOption(key = {""}, mandatory = false, help = "Node name") String path) {
        try {
            if (StringUtils.isEmpty(path)) {
                return zooKeeperService.executeCommand("stat");
            }
            Stat stat = zooKeeperService.getCurator().checkExists().forPath(getAbsolutePath(path));
            StringBuilder buf = new StringBuilder();
            buf.append("cZxid = " + stat.getCzxid() + SystemUtils.LINE_SEPARATOR);
            buf.append("ctime = " + new Date(stat.getCtime()).toString() + SystemUtils.LINE_SEPARATOR);
            buf.append("mZxid = " + stat.getMzxid() + SystemUtils.LINE_SEPARATOR);
            buf.append("mtime = " + new Date(stat.getMtime()).toString() + SystemUtils.LINE_SEPARATOR);
            buf.append("pZxid = " + stat.getPzxid() + SystemUtils.LINE_SEPARATOR);
            buf.append("cversion = " + stat.getCversion() + SystemUtils.LINE_SEPARATOR);
            buf.append("dataVersion = " + stat.getVersion() + SystemUtils.LINE_SEPARATOR);
            buf.append("aclVersion = " + stat.getAversion() + SystemUtils.LINE_SEPARATOR);
            if (stat.getEphemeralOwner() > 0) {
                buf.append("ephemeralOwner = " + stat.getEphemeralOwner() + SystemUtils.LINE_SEPARATOR);
            }
            buf.append("dataLength = " + stat.getDataLength() + SystemUtils.LINE_SEPARATOR);
            buf.append("numChildren = " + stat.getNumChildren());
            return buf.toString();
        } catch (Exception e) {
            log.error("stat", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @CliCommand(value = "cat", help = "Show node content")
    public String cat(@CliOption(key = {""}, mandatory = true, help = "Node name") String path) {
        try {
            byte[] content = zooKeeperService.getCurator().getData().forPath(getAbsolutePath(path));
            return new String(content);
        } catch (Exception e) {
            log.error("cat", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @CliCommand(value = "server", help = "Execute command")
    public String server(@CliOption(key = {""}, mandatory = true, help = "Command name") final ZkCommand command) {
        try {
            return zooKeeperService.executeCommand(command.getName());
        } catch (Exception e) {
            log.error("server", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @CliCommand(value = "touch", help = "Create node")
    public String touch(@CliOption(key = {""}, mandatory = true, help = "Node name") String name) {
        try {
            zooKeeperService.getCurator().create().forPath(getAbsolutePath(name), new byte[0]);
            return stat(name);
        } catch (Exception e) {
            log.error("cat", e);
            return wrappedAsRed(e.getMessage());
        }
    }

    @CliCommand(value = "mkdir", help = "Create node")
    public String mkdir(@CliOption(key = {""}, mandatory = true, help = "Node name") String name) {
        return touch(name);
    }

    @CliCommand(value = "echo", help = "Update content")
    public String echo(@CliOption(key = {""}, mandatory = true, help = "Node name") String content) {
        try {
            return content;
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
        String destPath = "/";
        // cd abolute path
        if (path.startsWith("/")) {
            destPath = path;
        } else if (path.equals("-")) {  //previous path
            destPath = previousPath;
        } else if (path.startsWith(".")) {  //relative path
            if (path.startsWith("../")) {
                destPath = currentPath.substring(0, currentPath.lastIndexOf("/"));
            } else {
                destPath = endWith(currentPath, "/") + path.substring(currentPath.lastIndexOf("./") + 2);
            }
        } else {
            destPath = endWith(currentPath, "/") + path;
        }
        return destPath;
    }

    public String endWith(String path, String end) {
        return path.endsWith(end) ? path : path + end;
    }


}
