package org.jerkar.api.tooling;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jerkar.api.depmanagement.JkDepExclude;
import org.jerkar.api.depmanagement.JkDependencies;
import org.jerkar.api.depmanagement.JkDependencyExclusions;
import org.jerkar.api.depmanagement.JkModuleDependency;
import org.jerkar.api.depmanagement.JkModuleId;
import org.jerkar.api.depmanagement.JkRepos;
import org.jerkar.api.depmanagement.JkScope;
import org.jerkar.api.depmanagement.JkScopedDependency;
import org.jerkar.api.depmanagement.JkVersion;
import org.jerkar.api.depmanagement.JkVersionProvider;
import org.jerkar.api.depmanagement.JkVersionedModule;
import org.jerkar.api.utils.JkUtilsIterable;
import org.jerkar.api.utils.JkUtilsXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class EffectivePom {

    private final Document pomDoc;

    private EffectivePom(Document pomDoc) {
        super();
        this.pomDoc = pomDoc;
    }

    public static EffectivePom of(File file) {
        final Document document = JkUtilsXml.documentFrom(file);
        return new EffectivePom(document);
    }

    private Element dependenciesElement() {
        return JkUtilsXml.directChild(projectEl(), "dependencies");
    }

    private Element dependencyManagementEl() {
        return JkUtilsXml.directChild(projectEl(), "dependencyManagement");
    }

    private Element repositoriesEl() {
        return JkUtilsXml.directChild(projectEl(), "repositories");
    }

    private Element projectEl() {
        return pomDoc.getDocumentElement();
    }

    public String groupId() {
        return JkUtilsXml.directChildText(projectEl(), "groupId");
    }

    public String artifactId() {
        return JkUtilsXml.directChildText(projectEl(), "artifactId");
    }

    public String version() {
        return JkUtilsXml.directChildText(projectEl(), "version");
    }

    public JkDependencies dependencies() {
        return dependencies(dependenciesElement());
    }

    public JkVersionProvider versionProvider() {
        final List<JkVersionedModule> versionedModules = new LinkedList<JkVersionedModule>();
        final Element dependenciesEl = JkUtilsXml.directChild(dependencyManagementEl(),
                "dependencies");
        final JkDependencies dependencies = dependencies(dependenciesEl);
        for (final JkScopedDependency scopedDependency : dependencies) {
            final JkModuleDependency moduleDependency = (JkModuleDependency) scopedDependency
                    .dependency();
            final JkVersionedModule versionedModule = JkVersionedModule.of(
                    moduleDependency.moduleId(),
                    JkVersion.ofName(moduleDependency.versionRange().definition()));
            versionedModules.add(versionedModule);
        }
        return JkVersionProvider.of(versionedModules);
    }

    public JkDependencyExclusions dependencyExclusion() {
        final JkDependencyExclusions.Builder builder = JkDependencyExclusions.builder();
        final Element dependenciesEl = JkUtilsXml.directChild(dependencyManagementEl(),
                "dependencies");
        final JkDependencies dependencies = dependencies(dependenciesEl);
        for (final JkScopedDependency scopedDependency : dependencies) {
            final JkModuleDependency moduleDependency = (JkModuleDependency) scopedDependency
                    .dependency();
            if (!moduleDependency.excludes().isEmpty()) {
                builder.on(moduleDependency.moduleId(), moduleDependency.excludes());
            }
        }
        return builder.build();
    }

    public JkRepos repos() {
        final List<String> urls = new LinkedList<String>();
        for (final Element repositoryEl : JkUtilsXml.directChildren(repositoriesEl(), "repository")) {
            urls.add(JkUtilsXml.directChildText(repositoryEl, "url"));
        }
        return JkRepos.maven(JkUtilsIterable.arrayOf(urls, String.class));
    }

    private JkDependencies dependencies(Element dependenciesEl) {
        final JkDependencies.Builder builder = JkDependencies.builder();
        for (final Element dependencyEl : JkUtilsXml.directChildren(dependenciesEl, "dependency")) {
            builder.on(jkDependency(dependencyEl));
        }
        return builder.build();
    }

    private JkScopedDependency jkDependency(Element mvnDependency) {
        final String groupId = JkUtilsXml.directChildText(mvnDependency, "groupId");
        final String artifactId = JkUtilsXml.directChildText(mvnDependency, "artifactId");
        final String version = JkUtilsXml.directChildText(mvnDependency, "version");
        JkModuleDependency moduleDependency = JkModuleDependency.of(groupId, artifactId, version);
        final String type = JkUtilsXml.directChildText(mvnDependency, "type");
        if (type != null) {
            moduleDependency = moduleDependency.ext(type);
        }
        final String classifier = JkUtilsXml.directChildText(mvnDependency, "classifier");
        if (classifier != null) {
            moduleDependency = moduleDependency.classifier(classifier);
        }
        final Element exclusionsEl = JkUtilsXml.directChild(mvnDependency, "exclusions");
        if (exclusionsEl != null) {
            for (final Element exclusionElement : JkUtilsXml.directChildren(exclusionsEl,
                    "exclusion")) {
                moduleDependency = moduleDependency.andExclude(jkDepExclude(exclusionElement));
            }
        }
        final String scope = JkUtilsXml.directChildText(mvnDependency, "scope");
        final JkScope jkScope = (scope == null) ? JkScope.of("compile") : JkScope.of(scope);
        return JkScopedDependency.of(moduleDependency, jkScope);
    }

    private JkDepExclude jkDepExclude(Element exclusionEl) {
        final String groupId = JkUtilsXml.directChildText(exclusionEl, "groupId");
        final String artifactId = JkUtilsXml.directChildText(exclusionEl, "artifactId");
        return JkDepExclude.of(groupId, artifactId);

    }

    public String jerkarSourceCode() {
        final JkCodeWriterForBuildClass codeWriter = new JkCodeWriterForBuildClass();
        codeWriter.moduleId = JkModuleId.of(groupId(), artifactId());
        codeWriter.dependencies = dependencies();
        codeWriter.dependencyExclusions = dependencyExclusion();
        codeWriter.extendedClass = "JkJavaBuild";
        codeWriter.imports.clear();
        codeWriter.imports.addAll(JkCodeWriterForBuildClass.importsFoJkJavaBuild());
        codeWriter.repos = repos();
        codeWriter.version = version();
        codeWriter.versionProvider = versionProvider();
        final VersionConstanter constanter = VersionConstanter.of(codeWriter.versionProvider);
        for (final Map.Entry<String, String> entry : constanter.groupToVersion().entrySet()) {
            codeWriter.addGroupVersionVariable(entry.getKey(), entry.getValue());
        }
        return codeWriter.wholeClass() + codeWriter.endClass();
    }

}
