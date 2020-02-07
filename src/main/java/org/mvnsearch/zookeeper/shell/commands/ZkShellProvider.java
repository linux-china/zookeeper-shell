package org.mvnsearch.zookeeper.shell.commands;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

/**
 * ZooKeeper Shell Provider
 *
 * @author linux_china
 */
@ShellComponent
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZkShellProvider {

    /**
     * display author information
     *
     * @return author information
     */
    @ShellMethod(key = {"author"}, value = "Displays author information")
    public String author() {
        return "linux_china <linux_china@hotmail.com>, Please follow me: http://weibo.com/linux2china";
    }
}
