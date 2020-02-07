package org.mvnsearch.zookeeper.shell.commands;

import org.apache.commons.lang3.StringUtils;
import org.jline.utils.AttributedString;
import org.jline.utils.OSUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.Order;
import org.springframework.shell.jline.PromptProvider;
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
        if ((OSUtils.IS_WINDOWS)) {
            symbol = ">";
        }
    }

    /**
     * prompt
     *
     * @return prompt
     */
    @Override
    public AttributedString getPrompt() {
        String currentPath = StringUtils.defaultIfEmpty(ZkShellOperationCommands.currentPath, "/");
        return new AttributedString("[" + prompt + ":" + currentPath + "]" + symbol);
    }


}
