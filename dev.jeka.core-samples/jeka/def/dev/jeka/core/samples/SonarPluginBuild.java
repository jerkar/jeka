package dev.jeka.core.samples;

import dev.jeka.core.tool.JkClass;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.git.JkPluginGit;
import dev.jeka.core.tool.builtins.jacoco.JkPluginJacoco;
import dev.jeka.core.tool.builtins.java.JkPluginJava;
import dev.jeka.core.tool.builtins.sonar.JkPluginSonar;
import dev.jeka.core.tool.builtins.sonar.JkSonar;

import static dev.jeka.core.api.depmanagement.JkPopularModules.GUAVA;
import static dev.jeka.core.api.depmanagement.JkPopularModules.JUNIT;

/**
 * This build illustrate how to use SonarQube Plugin. <p>
 *
 * Jacoco plugin takes information from the {@link dev.jeka.core.api.java.project.JkJavaProject} instance of
 * Java plugin in order to send a complete request to a SonarQube server<p>.
 *
 * If your build does not relies upon Java plugin you can still easily use directly the {@link JkSonar} class
 * within the build class.<p>
 *
 * Note that SonarQube plugin combines quite well with the Jacoco one. It takes results generated by these one to
 * send it to the server.
 *
 */
public class SonarPluginBuild extends JkClass {

    JkPluginJava java = getPlugin(JkPluginJava.class);

    JkPluginSonar sonar = getPlugin(JkPluginSonar.class);

    JkPluginGit git = getPlugin(JkPluginGit.class);

    JkPluginJacoco jacoco = getPlugin(JkPluginJacoco.class);

    public boolean runSonar; // a flag to run sonar or not

    @JkDoc("Sonar server environment")
    public SonarEnv sonarEnv = SonarEnv.DEV;

    public SonarPluginBuild() {
        this.getPlugin(JkPluginSonar.class)
            .setProp(JkSonar.BRANCH, "myBranch");
    }
    
    @Override
    protected void setup() {
        sonar
            .setProp(JkSonar.HOST_URL, sonarEnv.url)
            .setProp(JkSonar.BRANCH, "myBranch");
        java.getProject().simpleFacade()
            .setCompileDependencies(deps -> deps
                .and(GUAVA.version("18.0")))
            .setTestDependencies(deps -> deps
                .and(JUNIT.version("4.13")))
            .setPublishedMavenVersion(git.getWrapper()::getVersionFromTags)
            .setPublishedMavenModuleId("org.jerkar:samples");
    }

    public void cleanPackSonar() {
        clean(); java.pack();
        if (runSonar) sonar.run();
    }

    enum SonarEnv {
        DEV("https://localhost:81"),
        QA("https://qa.myhost:81"),
        PROD("https://prod.myhost:80");

        public final String url;

        SonarEnv(String url) {
            this.url = url;
        }
    }

    public static void main(String[] args) {
        JkInit.instanceOf(SonarPluginBuild.class, args).cleanPackSonar();
    }

}
