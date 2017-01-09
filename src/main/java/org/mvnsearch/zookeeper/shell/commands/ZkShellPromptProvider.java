package org.mvnsearch.zookeeper.shell.commands;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.Order;
import org.springframework.shell.plugin.PromptProvider;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;

/**
 * ZooKeeper Shell prompt provider
 *
 * @author linux_china
 */
@Component
@Order(1)
public class ZkShellPromptProvider implements PromptProvider, InitializingBean {
    /**
     * prompt
     */
    public static String prompt = "zk";
    /**
     * symbol
     */
    public static String symbol = "#";

    /**
     * init method
     *
     * @throws Exception exception
     */
    public void afterPropertiesSet() throws Exception {
        //if Windows OS, adjust symbo to '>'
        if ((OsUtils.isWindows())) {
            symbol = ">";
        }
    }

    /**
     * prompt
     *
     * @return prompt
     */
    @Override
    public String getPrompt() {
        String currentPath = StringUtils.defaultIfEmpty(ZkShellOperationCommands.currentPath, "/");
        return "[" + prompt + ":" + currentPath + "]" + symbol;
    }

    @Override
    public String getProviderName() {
        return "zookeeper-shell-java-cli-prompt-provider";
    }

}
