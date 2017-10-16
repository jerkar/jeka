package org.jerkar.api.project.java;

import java.io.File;
import java.nio.file.Path;

import org.jerkar.api.depmanagement.JkArtifactFileId;
import org.jerkar.api.file.JkFileTree;
import org.jerkar.api.java.JkClasspath;
import org.jerkar.api.java.JkJarMaker;
import org.jerkar.api.utils.JkUtilsPath;

/**
 * Creates jar and others elements of a java project.
 */
public final class JkJavaProjectPackager {

    public static final JkJavaProjectPackager of(JkJavaProject project) {
        return new JkJavaProjectPackager(project);
    }

    private final JkJavaProject project;

    private JkJavaProjectPackager(JkJavaProject project) {
        this.project = project;
    }

    public File mainJar() {
        Path result = project.mainArtifactPath();
        JkJarMaker.jar(result, project.getManifest(), project.getOutLayout().classDir().toPath(), project.getExtraFilesToIncludeInJar());
        return result.toFile();
    }

    /**
     * @param classifier Can be <code>null</code>, id so the fat jar will stands for the main artifact file.
     */
    public File fatJar(String classifier) {
        JkClasspath classpath = JkClasspath.of(project.runtimeDependencies(project.mainArtifactFileId()));
        JkArtifactFileId artifactFileId = JkArtifactFileId.of(classifier, "jar");
        Path result = project.artifactPath(artifactFileId);
        JkJarMaker.fatJar(result, project.getManifest(), project.getOutLayout().classDir().toPath(),
                project.getExtraFilesToIncludeInJar(), JkUtilsPath.toPaths(classpath.entries()));
        return result.toFile();
    }

    public File sourceJar() {
        Path result = project.artifactPath(JkJavaProject.SOURCES_FILE_ID);
        project.getSourceLayout().sources().and(project.getOutLayout().generatedSourceDir().toPath()).zipTo(result);
        return result.toFile();
    }

    public File javadocJar() {
        File javadocDir = project.getOutLayout().getJavadocDir();
        if (!javadocDir.exists()) {
            throw new IllegalStateException("No javadoc has not been generated in " + javadocDir.getAbsolutePath() + " : can't create javadoc jar. Please, generate Javadoc prior to package it in jar.");
        }
        File result = project.artifactFile(JkJavaProject.JAVADOC_FILE_ID);
        JkFileTree.of(javadocDir.toPath()).zipTo(result.toPath());
        return  result;
    }

    public File testJar() {
        Path result = project.artifactPath(JkJavaProject.TEST_FILE_ID);
        JkJarMaker.jar(result, project.getManifest(), project.getOutLayout().testClassDir().toPath(),  null);
        return result.toFile();
    }

    public File testSourceJar() {
        Path result = project.artifactPath(JkJavaProject.SOURCES_FILE_ID);
        project.getSourceLayout().tests().zipTo(result);
        return result.toFile();
    }

}
