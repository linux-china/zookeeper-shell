package org.mvnsearch.zookeeper.shell.commands;

import org.fusesource.jansi.Ansi;
import org.mvnsearch.zookeeper.shell.service.ZooKeeperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.stereotype.Component;


/**
 * ZooKeeper admin command
 *
 * @author linux_china
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZkAdminCommands implements CommandMarker {
    private Logger log = LoggerFactory.getLogger(ZkShellOperationCommands.class);
    @Autowired
    private ZooKeeperService zooKeeperService;

    @CliCommand(value = "conf", help = "Print details about serving configuration")
    public String conf() {
        return executeCommand("conf");
    }

    @CliCommand(value = "cons", help = "List full connection/session details for all clients connected to this server")
    public String cons() {
        return executeCommand("cons");
    }

    @CliCommand(value = "crst", help = "crst")
    public String crst() {
        return executeCommand("crst");
    }

    @CliCommand(value = "dump", help = "Lists the outstanding sessions and ephemeral nodes. This only works on the leader.")
    public String dump() {
        return executeCommand("dump");
    }

    @CliCommand(value = "envi", help = "List full connection/session details for all clients connected to this server")
    public String envi() {
        return executeCommand("envi");
    }

    @CliCommand(value = "ruok", help = "Tests if server is running in a non-error state")
    public String ruok() {
        return executeCommand("ruok");
    }

    @CliCommand(value = "srst", help = "Reset server statistics")
    public String srst() {
        return executeCommand("srst");
    }

    @CliCommand(value = "srvr", help = "Lists full details for the server")
    public String srvr() {
        return executeCommand("srvr");
    }

    @CliCommand(value = "wchs", help = "Lists brief information on watches for the server")
    public String wchs() {
        return executeCommand("wchs");
    }

    @CliCommand(value = "wchc", help = "Lists detailed information on watches for the server, by session")
    public String wchc() {
        return executeCommand("wchc");
    }

    @CliCommand(value = "wchp", help = "Lists detailed information on watches for the server, by path")
    public String wchp() {
        return executeCommand("wchp");
    }

    @CliCommand(value = "mntr", help = "Outputs a list of variables that could be used for monitoring the health of the cluster")
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
