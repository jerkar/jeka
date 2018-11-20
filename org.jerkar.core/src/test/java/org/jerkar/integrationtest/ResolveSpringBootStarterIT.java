package org.jerkar.integrationtest;

import org.jerkar.api.depmanagement.*;
import org.jerkar.api.system.JkLog;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.jerkar.api.depmanagement.JkJavaDepScopes.COMPILE;

public class ResolveSpringBootStarterIT {

    private static final String SPRINGBOOT_STARTER = "org.springframework.boot:spring-boot-starter:1.5.13.RELEASE";

    private static final JkModuleId SLF4J_API = JkModuleId.of("org.slf4j:slf4j-api");

    @Test
    public void resolveCompile() {
        JkLog.registerHierarchicalConsoleHandler();
        JkLog.setVerbosity(JkLog.Verbosity.VERBOSE);
        JkResolveResult result = resolver().resolve(
                JkDependencySet.of(SPRINGBOOT_STARTER, COMPILE)
        );
        System.out.println(resolver().getParams().getScopeMapping());
        System.out.println(result.getDependencyTree().toStringTree());
        result.getFiles().forEach(System.out::println);
        List<JkDependencyNode> slf4japiNodes = result.getDependencyTree().toFlattenList().stream()
                .filter(node -> node.getModuleInfo().getModuleId().equals(SLF4J_API)).collect(Collectors.toList());
        for (JkDependencyNode slf4japiNode : slf4japiNodes) {
            System.out.println("---------------------");
            System.out.println(slf4japiNode);
            slf4japiNode.getResolvedFiles().forEach(System.out::println);
        }

        // Does not contains test-jars
        Assert.assertFalse(result.getFiles().getEntries().stream().anyMatch(path -> path.getFileName().toString().endsWith("-tests.jar")));


    }

    private JkDependencyResolver resolver() {
        JkDependencyResolver resolver = JkDependencyResolver.of(JkRepo.ofMavenCentral());
        JkScopeMapping mapping = JkScopeMapping.of("compile").to("compile");
        resolver = resolver.withParams(resolver.getParams().withScopeMapping(mapping));
        return resolver;
    }
}
