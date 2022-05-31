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
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

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
    private final Logger log = LoggerFactory.getLogger(ZkShellOperationCommands.class);
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

    @Bean
    public CommandRegistration cd() {
        return constructSinglePositionalCommand("cd", "Change Path", ctx -> {
            try {
                String path = "/";
                final List<String> args = ctx.getParserResults().positional();
                if (!args.isEmpty()) {
                    path = args.get(0);
                }
                String destPath = getAbsolutePath(path);
                Stat stat = zooKeeperService.getCurator().checkExists().forPath(destPath);
                if (stat != null) {
                    previousPath = currentPath;
                    currentPath = destPath;
                } else {
                    return wrappedAsRed("Destination not existed: " + destPath);
                }
            } catch (Exception e) {
                log.error("cat", e);
                return wrappedAsRed(e.getMessage());
            }
            return null;
        });
    }

    @Bean
    public CommandRegistration ls() {
        return constructSinglePositionalCommand("ls", "List directories or files", ctx -> {
            try {
                String path = "/";
                final List<String> args = ctx.getParserResults().positional();
                if (!args.isEmpty()) {
                    path = args.get(0);
                }
                String destPath = getAbsolutePath(path);
                List<String> children = zooKeeperService.getCurator().getChildren().forPath(destPath);
                if (children.isEmpty()) {
                    return "No children found!";
                }
                return StringUtils.join(children, LINE_SEPARATOR);
            } catch (Exception e) {
                log.error("cat", e);
                return wrappedAsRed(e.getMessage());
            }
        });
    }


    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    public String stat(String fullPath) {
        try {
            Stat stat = zooKeeperService.getCurator().checkExists().forPath(fullPath);
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


    @Bean
    public CommandRegistration stat() {
        return constructSinglePositionalCommand("stat", "Show node or server stat", ctx -> {
            try {
                String path = "/";
                final List<String> args = ctx.getParserResults().positional();
                if (!args.isEmpty()) {
                    path = args.get(0);
                } else {
                    return zooKeeperService.executeCommand("stat");
                }
                return stat(getAbsolutePath(path));
            } catch (Exception e) {
                log.error("stat", e);
                return wrappedAsRed(e.getMessage());
            }
        });
    }

    @Bean
    public CommandRegistration cat() {
        return constructSinglePositionalCommand("cat", "Show node content", ctx -> {
            try {
                String path = "/";
                final List<String> args = ctx.getParserResults().positional();
                if (!args.isEmpty()) {
                    path = args.get(0);
                }
                byte[] content = zooKeeperService.getCurator().getData().forPath(getAbsolutePath(path));
                return new String(content);
            } catch (Exception e) {
                log.error("cat", e);
                return wrappedAsRed(e.getMessage());
            }
        });
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

    @Bean
    public CommandRegistration touch() {
        return constructSinglePositionalCommand("touch", "Create node", ctx -> {
            try {
                String path = "/";
                final List<String> args = ctx.getParserResults().positional();
                if (!args.isEmpty()) {
                    path = args.get(0);
                }
                zooKeeperService.getCurator().create().withMode(ZkNodeCreateMode.persistent.toZkMode()).forPath(getAbsolutePath(path), new byte[0]);
                return stat(path);
            } catch (Exception e) {
                log.error("touch", e);
                return wrappedAsRed(e.getMessage());
            }
        });
    }

    @Bean
    public CommandRegistration echo() {
        return CommandRegistration.builder().command("echo").withOption().position(0).and().withOption().position(1).and().withTarget().function(ctx -> {
            final List<String> args = ctx.getParserResults().positional();
            try {
                String nodePath = args.get(0);
                String content = args.get(1);
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
        }).and().build();
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

    public static String getAbsolutePath(String path) {
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
        System.out.println("dest: " + destPath);
        return destPath;
    }

    CommandRegistration constructSinglePositionalCommand(String command, String description, Function<CommandContext, ?> function) {
        return CommandRegistration.builder().command(command).description(description).withOption().position(0).and().withTarget().function(function).and().build();
    }


    public static String endWith(String path, String end) {
        return path.endsWith(end) ? path : path + end;
    }


}
