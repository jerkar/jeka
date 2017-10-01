package org.jerkar.distrib.all;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.jerkar.api.file.JkFileTree;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.system.JkProcess;
import org.jerkar.api.utils.JkUtilsAssert;
import org.jerkar.api.utils.JkUtilsPath;
import org.jerkar.api.utils.JkUtilsString;
import org.jerkar.api.utils.JkUtilsSystem;

class SampleTester {

    private final JkFileTree sampleBaseDir;

    private final JkFileTree sampleDependeeBaseDir;

    private final JkFileTree output;

    private Path launchScript;
    
    boolean restoreEclipseClasspathFile;

    SampleTester(JkFileTree buildDir) {
        super();
        this.sampleBaseDir = buildDir.go("../org.jerkar.samples");
        this.sampleDependeeBaseDir = buildDir.go("../org.jerkar.samples-dependee");
        this.output = sampleBaseDir.go("build/output");
        String scriptName = JkUtilsSystem.IS_WINDOWS ? "jerkar.bat" : "jerkar";
        launchScript = buildDir.root().resolve("build/output/dist/" + scriptName);
    }

    void doTest() throws Exception {
        testSamples("AClassicBuild");
        testSamples("AntStyleBuild");
        testSamples("MavenStyleBuild");
        testSamples("OpenSourceJarBuild");
        testSamples("HttpClientTaskBuild");
        File file = new File("build/output/test-out/mavenrepo");
        testSamples("", "doPublish", "-repo.publish.url=" + file.getAbsolutePath());
        File file2 = new File("build/output/test-out/mavenrepo-release");
        testSamples("", "doPublish", "-version=1.0-SNAPSHOT", "-repo.release.url=" + file2.getAbsolutePath());
        File file3 = new File("build/output/test-out/ivyrepo");
        testSamples("", "doPublish", "-repo.publish.url=ivy:" + file3.getAbsolutePath());
        // scaffoldAndEclipse();   // TODO
        testDependee("FatJarBuild");
        Path classpathFile = sampleBaseDir.get(".classpath");
        Path classpathFile2 = sampleBaseDir.get(".classpath2");
        Files.copy(classpathFile, classpathFile2);
        testSamples("", "eclipse#generateAll");
        if (restoreEclipseClasspathFile) {
            Files.move(classpathFile2, classpathFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.delete(classpathFile2);
        }
        testDependee("NormalJarBuild");
        testFork();
    }

    private void testSamples(String className, String... args) {
        JkLog.infoHeaded("Test " + className + " " + Arrays.toString(args));
        JkProcess.of(launchScript.toAbsolutePath().toString()).withWorkingDir(sampleBaseDir.root().toAbsolutePath().normalize())
                .withParametersIf(!JkUtilsString.isBlank(className), "-verbose=true -buildClass=" + className).andParameters(args)
                .failOnError(true).runSync();
    }

    private void testDependee(String className, String... args) {
        JkLog.infoHeaded("Test " + className + " " + Arrays.toString(args));
        JkProcess.of(launchScript.toAbsolutePath().toString()).withWorkingDir(this.sampleDependeeBaseDir.root())
                .withParametersIf(!JkUtilsString.isBlank(className), "-buildClass=" + className).andParameters(args)
                .failOnError(true).runSync();
    }

    private void scaffoldAndEclipse() {
        JkLog.startHeaded("Test scaffolding");
        Path scafoldedProject = output.root().resolve("scaffolded");
        JkProcess scaffoldProcess = process().withWorkingDir(scafoldedProject);
        JkUtilsPath.createDirectories(scafoldedProject);
        scaffoldProcess.withParameters("scaffold").runSync(); // scaffold
        // project
        scaffoldProcess.runSync(); // Build the scaffolded project
        JkLog.infoUnderlined("Test eclipse generation and compile            ");
        scaffoldProcess.withParameters("eclipse#generateAll").runSync();
        scaffoldProcess.withParameters("eclipse#").runSync(); // build using the .classpath for resolving classpath
        scaffoldProcess.withParameters("idea#generateIml", "idea#generateModulesXml").runSync();
        JkLog.done();
    }

    private JkProcess process() {
        return JkProcess.of(launchScript.toAbsolutePath().toString()).failOnError(true);
    }

    private void testFork() {
        testSamples("", "-tests.fork");
        JkUtilsAssert.isTrue(output.go("test-reports/junit").exists(), "No test report generated in test fork mode.");
    }

}
