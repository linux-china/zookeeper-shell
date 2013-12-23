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
            Stat stat = zooKeeperService.getCurator().checkExists().forPath(destPath);
            if (stat != null) {
                previousPath = currentPath;
                currentPath = destPath;
            } else {
                return wrappedAsRed("Destination not existed: " + destPath);
            }
        } catch (Exception e) {
            log.error("start", e);
            return wrappedAsRed(e.getMessage());
        }
        return "";
    }

    /**
     * stop aria2
     *
     * @return stop status
     */
    @CliCommand(value = "ls", help = "List directories or files")
    public String ls(@CliOption(key = {""}, mandatory = false, help = "Directory") String path) {
        try {
            List<String> children = zooKeeperService.getCurator().getChildren().forPath(currentPath);
            if (children.isEmpty()) {
                return "No children found!";
            }
            return StringUtils.join(children, SystemUtils.LINE_SEPARATOR);
        } catch (Exception e) {
            log.error("start", e);
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

    public String endWith(String path, String end) {
        return path.endsWith(end) ? path : path + end;
    }


}
