package org.jerkar.samples;

import org.jerkar.api.depmanagement.JkDependencySet;
import org.jerkar.api.depmanagement.JkJavaDepScopes;
import org.jerkar.api.depmanagement.JkPopularModules;
import org.jerkar.api.java.JkJavaVersion;
import org.jerkar.api.java.project.JkJavaProject;
import org.jerkar.tool.JkRun;
import org.jerkar.tool.JkInit;

/**
 * This should be run with org.jerkar.samples as working dir.
 */
public class PureApiBuild extends JkRun {

    public void doDefault() {
        JkJavaProject javaProject = JkJavaProject.ofMavenLayout(this.getBaseDir());

        // We want to output stuff in another place than build/output
        javaProject.getMaker().setOutLayout(javaProject.getMaker().getOutLayout().withOutputDir("build/output/alt-output"));

        JkDependencySet deps = JkDependencySet.of().and(JkPopularModules.JUNIT, "4.12",JkJavaDepScopes.TEST);

        javaProject.setDependencies(deps).setSourceVersion(JkJavaVersion.V6);

        javaProject.getMaker().clean();
        javaProject.getMaker().makeArtifact(javaProject.get().getMainArtifactId());
        //javaProject.getMaker().getPackTasks().makeJavadocJar(Paths.get("javadoc.jar"));
    }

    public static void main(String[] args) {
        JkInit.instanceOf(PureApiBuild.class, args).doDefault();
    }
}