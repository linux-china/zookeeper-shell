package org.mvnsearch.zookeeper.shell.commands;

import org.fusesource.jansi.Ansi;
import org.mvnsearch.zookeeper.shell.service.ZooKeeperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

/**
 * ZooKeeper admin command
 *
 * @author linux_china
 */
@ShellComponent
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZkAdminCommands {
    private Logger log = LoggerFactory.getLogger(ZkShellOperationCommands.class);
    @Autowired
    private ZooKeeperService zooKeeperService;

    @ShellMethod(key = "conf", value = "Print details about serving configuration")
    public String conf() {
        return executeCommand("conf");
    }

    @ShellMethod(key = "cons", value = "List full connection/session details for all clients connected to this server")
    public String cons() {
        return executeCommand("cons");
    }

    @ShellMethod(key = "crst", value = "crst")
    public String crst() {
        return executeCommand("crst");
    }

    @ShellMethod(key = "dump", value = "Lists the outstanding sessions and ephemeral nodes. This only works on the leader.")
    public String dump() {
        return executeCommand("dump");
    }

    @ShellMethod(key = "envi", value = "List full connection/session details for all clients connected to this server")
    public String envi() {
        return executeCommand("envi");
    }

    @ShellMethod(key = "ruok", value = "Tests if server is running in a non-error state")
    public String ruok() {
        return executeCommand("ruok");
    }

    @ShellMethod(key = "srst", value = "Reset server statistics")
    public String srst() {
        return executeCommand("srst");
    }

    @ShellMethod(key = "srvr", value = "Lists full details for the server")
    public String srvr() {
        return executeCommand("srvr");
    }

    @ShellMethod(key = "wchs", value = "Lists brief information on watches for the server")
    public String wchs() {
        return executeCommand("wchs");
    }

    @ShellMethod(key = "wchc", value = "Lists detailed information on watches for the server, by session")
    public String wchc() {
        return executeCommand("wchc");
    }

    @ShellMethod(key = "wchp", value = "Lists detailed information on watches for the server, by path")
    public String wchp() {
        return executeCommand("wchp");
    }

    @ShellMethod(key = "mntr", value = "Outputs a list of variables that could be used for monitoring the health of the cluster")
    public String mntr() {
        return executeCommand("mntr");
    }

    public String executeCommand(String command) {
        try {
            return zooKeeperService.executeCommand(command);
        } catch (Exception e) {
            log.error(command, e);
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
}
