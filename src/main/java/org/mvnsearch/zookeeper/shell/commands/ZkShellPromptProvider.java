package org.mvnsearch.zookeeper.shell.commands;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.shell.plugin.support.DefaultPromptProvider;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;

/**
 * ZooKeeper Shell prompt provider
 *
 * @author linux_china
 */
@Component
public class ZkShellPromptProvider extends DefaultPromptProvider implements InitializingBean {
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
