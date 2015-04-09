package org.jake.plugins.sonar;

import java.io.File;

import org.jake.JakeBuild;
import org.jake.JakeDoc;
import org.jake.JakeOptions;
import org.jake.JakePath;
import org.jake.java.build.JakeJavaBuild;
import org.jake.java.build.JakeJavaBuildPlugin;
import org.jake.java.testing.junit.JakeUnit.JunitReportDetail;
import org.jake.utils.JakeUtilsFile;

@JakeDoc({"Add SonarQube capability to a build.",
	"The ananlysis is performed when the 'verify' method is invoked.",
	"To parameterize this plugin just set the relevant sonar properies as options.",
	"For example you can launch the build whith '-sonar.host.url=http://myserver/..' to specify the SonarQube server url."})
public class JakeBuildPluginSonar extends JakeJavaBuildPlugin {
	
	public static JakeSonar configureSonarFrom(JakeJavaBuild build) {
		final File baseDir = build.baseDir().root();
		final JakePath libs = build.depsFor(JakeJavaBuild.COMPILE, JakeJavaBuild.PROVIDED);
		return JakeSonar.of(build.projectFullName(), build.projectName(), build.version())
				.withProperties(JakeOptions.getAllStartingWith("sonar."))
                .withProjectBaseDir(baseDir) 
                .withBinaries(build.classDir())
                .withLibraries(libs)
                .withSources(build.editedSourceDirs().roots())
                .withTest(build.testSourceDirs().roots())
                .withProperty(JakeSonar.WORKING_DIRECTORY, build.baseDir("build/.sonar").getPath())
                .withProperty(JakeSonar.JUNIT_REPORTS_PATH, JakeUtilsFile.getRelativePath(baseDir, new File(build.testReportDir(), "junit")))
                .withProperty(JakeSonar.SUREFIRE_REPORTS_PATH, JakeUtilsFile.getRelativePath(baseDir, new File(build.testReportDir(), "junit")))
                //.withProperty(JakeSonar.DYNAMIC_ANALYSIS, "reuseReports")
                .withProperty(JakeSonar.JACOCO_REPORTS_PATH, JakeUtilsFile.getRelativePath(baseDir, new File(build.testReportDir(), "jacoco/jacoco.exec")));
	}
	
	private JakeSonar jakeSonar;
	
	@Override
	public void configure(JakeBuild build) {
		final JakeJavaBuild javaBuild = (JakeJavaBuild) build;
		javaBuild.junitReportDetail = JunitReportDetail.FULL;
	    this.jakeSonar = configureSonarFrom(javaBuild);
	}
	
	@JakeDoc("Launch a Sonar analysis.")
	@Override
	public void verify() {
		jakeSonar.launch();
	}
	

}