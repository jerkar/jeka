package org.jerkar.api.depmanagement;

public class JkDependencyResolverRunner {

    public static void main(String[] args) {
        JkResolveResult resolveResult = JkDependencyResolver.of(JkRepo.ofMavenCentral())
                        .resolve(JkDependencySet.of("org.jerkar.plugins:spring-boot:2.0.0.RC1"));
        resolveResult.assertNoError();
        System.out.println(resolveResult);
    }
}