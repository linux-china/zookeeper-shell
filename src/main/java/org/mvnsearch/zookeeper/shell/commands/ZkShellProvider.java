package org.mvnsearch.zookeeper.shell.commands;

import org.apache.commons.lang3.SystemUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.plugin.support.DefaultBannerProvider;
import org.springframework.stereotype.Component;

/**
 * ZooKeeper Shell Provider
 *
 * @author linux_china
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZkShellProvider extends DefaultBannerProvider implements CommandMarker {

    /**
     * get CLI banner
     *
     * @return banner text
     */
    @SuppressWarnings({"StringConcatenationInsideStringBufferAppend", "StringBufferReplaceableByString"})
    public String getBanner() {
        StringBuilder buf = new StringBuilder();
        buf.append("============================================" + SystemUtils.LINE_SEPARATOR);
        buf.append("*                                          *" + SystemUtils.LINE_SEPARATOR);
        buf.append("*           ZooKeeper Shell                *" + SystemUtils.LINE_SEPARATOR);
        buf.append("*                                          *" + SystemUtils.LINE_SEPARATOR);
        buf.append("===========================================");
        return buf.toString();
    }

    /**
     * display author information
     *
     * @return author information
     */
    @CliCommand(value = {"author"}, help = "Displays author information")
    public String author() {
        return "linux_china <linux_china@hotmail.com>, Please follow me: http://weibo.com/linux2china";
    }

    /**
     * current version
     *
     * @return version
     */
    public String getVersion() {
        return "1.0.0";
    }

    /**
     * welcome message
     *
     * @return welcome message
     */
    public String getWelcomeMessage() {
        return "Welcome to ZooKeeper Shell! Version: " + getVersion();
    }

    /**
     * commander name
     *
     * @return name
     */
    @Override
    public String name() {
        return "zookeeper-shell";
    }

}
