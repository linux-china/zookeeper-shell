package org.mvnsearch.zookeeper.shell.commands;

import org.mvnsearch.zookeeper.shell.service.ZooKeeperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ValueProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;


@Component
public class ZkPathValueProvider implements ValueProvider {
    @Autowired
    private ZooKeeperService zooKeeperService;
    private static final List<String> ALLOWED_METHODS = List.of("cd", "stat", "cat", "watch");

    @Override
    public boolean supports(MethodParameter parameter, CompletionContext completionContext) {
        final Method method = parameter.getMethod();
        return method != null && method.getDeclaringClass() == ZkShellOperationCommands.class && ALLOWED_METHODS.contains(method.getName());
    }

    @Override
    public List<CompletionProposal> complete(MethodParameter parameter, CompletionContext completionContext, String[] hints) {
        String destPath = ZkShellOperationCommands.getAbsolutePath("");
        try {
            List<String> children = zooKeeperService.getCurator().getChildren().forPath(destPath);
            if (!children.isEmpty()) {
                return children.stream().map(CompletionProposal::new).toList();
            }
        } catch (Exception ignore) {
        }
        return Collections.emptyList();
    }
}
