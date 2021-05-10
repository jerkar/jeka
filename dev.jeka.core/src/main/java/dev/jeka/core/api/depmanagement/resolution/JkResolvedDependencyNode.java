package dev.jeka.core.api.depmanagement.resolution;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.api.utils.JkUtilsString;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;

/**
 * A representation of a node in a dependency tree.
 *
 * @author Jerome Angibaud
 */
public class JkResolvedDependencyNode {

    private static final String INDENT = "    ";

    private final JkNodeInfo nodeInfo;

    private final List<JkResolvedDependencyNode> children;

    private final JkVersionProvider resolvedVersions;

    private JkResolvedDependencyNode(JkNodeInfo nodeInfo, List<JkResolvedDependencyNode> children) {
        this.nodeInfo = nodeInfo;
        this.children = children;
        this.resolvedVersions = compute(nodeInfo, children);
    }

    /**
     * Returns an empty instance of tree.
     */
    static JkResolvedDependencyNode ofEmpty(JkNodeInfo nodeInfo) {
        return new JkResolvedDependencyNode(nodeInfo, new LinkedList<>());
    }

    /**
     * Constructs a node for the specified versioned module having the specified
     * direct flatten.
     */
    public static JkResolvedDependencyNode ofModuleDep(JkModuleNodeInfo moduleNodeInfo, List<JkResolvedDependencyNode> children) {
        return new JkResolvedDependencyNode(moduleNodeInfo, Collections.unmodifiableList(children));
    }

    public static JkResolvedDependencyNode ofFileDep(JkFileDependency dependency, Set<String> configurations) {
        final JkNodeInfo moduleInfo = JkFileNodeInfo.of(configurations, dependency);
        return new JkResolvedDependencyNode(moduleInfo, Collections.unmodifiableList(new LinkedList<>()));
    }

    JkResolvedDependencyNode mergeNonModules(List<? extends JkDependency> dependencies) {
        final List<JkResolvedDependencyNode> result = new LinkedList<>();
        final Set<JkFileDependency> addedFileDeps = new HashSet<>();
        for (final JkResolvedDependencyNode node : this.children) {
                    if (node.isModuleNode()) {
                        addFileDepsToTree(dependencies, result, addedFileDeps, node.moduleId());
                result.add(node);
            }
        }
        addFileDepsToTree(dependencies, result, addedFileDeps, null);
        return new JkResolvedDependencyNode(this.nodeInfo,result);
    }

    /**
     * Returns all files resulting of this dependency node (this node itself plus all descendants).
     */
    public List<Path> getResolvedFiles() {
        final List<Path> list = new LinkedList<>();
        JkUtilsIterable.addAllWithoutDuplicate(list, this.nodeInfo.getFiles());
        for (final JkResolvedDependencyNode child : children) {
            JkUtilsIterable.addAllWithoutDuplicate(list, child.getResolvedFiles());
        }
        return list;
    }

    /**
     * Returns true if this node stands for a module dependency. It returns <code>false</code> if
     * it stands for a file dependency.
     */
    public boolean isModuleNode() {
        return this.nodeInfo instanceof JkModuleNodeInfo;
    }

    /**
     * Convenient method to return relative information about this node, assuming this node stands for a module dependency.
     */
    public JkModuleNodeInfo getModuleInfo() {
        if (this.nodeInfo instanceof JkModuleNodeInfo) {
            return (JkModuleNodeInfo) this.nodeInfo;
        }
        throw new IllegalStateException("The current node is type of " + this.nodeInfo.getClass().getName()
                + " (for " + this.nodeInfo + "), so is not a module dependency as expected. Caller must check if type is correct before calling this method.");
    }

    /**
     * Returns information relative to this dependency node.
     */
    public JkNodeInfo getNodeInfo() {
        return this.nodeInfo;
    }

    /**
     * Returns the children nodes for this node in the tree structure.
     */
    public List<JkResolvedDependencyNode> getChildren() {
        return children;
    }

    /**
     * Returns <code>true</code> if this node or one of its descendant stand for the specified module.
     * Evicted nodes are not taken in account.
     */
    public boolean contains(JkModuleId moduleId) {
        if (this.isModuleNode() && moduleId.equals(this.getModuleInfo().getModuleId()) && !this.getModuleInfo().isEvicted()) {
            return true;
        }
        for (final JkResolvedDependencyNode child : this.children) {
            final boolean contains = child.contains(moduleId);
            if (contains) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the resolved version for this node and all its children.
     */
    public JkVersionProvider getResolvedVersions() {
        return this.resolvedVersions;
    }

    /**
     * Returns the versioned modules which with this result has been created.
     */
    public Set<JkVersionedModule> getChildModules() {
        return resolvedModules(true);
    }

    private Set<JkVersionedModule> resolvedModules(boolean root) {
        final Set<JkVersionedModule> result = new HashSet<>();
        if (!root && this.isModuleNode() && !this.getModuleInfo().isEvicted()) {
            result.add(this.getModuleInfo().moduleId.withVersion(this.getModuleInfo().resolvedVersion.getValue()));
        }
        for (final JkResolvedDependencyNode child : this.children) {
            result.addAll(child.resolvedModules(false));
        }
        return result;
    }

    /**
     * Returns the children nodes for this node having the specified getModuleId.
     */
    public List<JkResolvedDependencyNode> getChildren(JkModuleId moduleId) {
        final List<JkResolvedDependencyNode> result = new LinkedList<>();
        for (final JkResolvedDependencyNode child : getChildren()) {
            if (child.getModuleInfo().getModuleId().equals(moduleId)) {
                result.add(child);
            }
        }
        return result;
    }

    /**
     * Returns the getChild node having the specified getModuleId.
     */
    public JkResolvedDependencyNode getChild(JkModuleId moduleId) {
        for (final JkResolvedDependencyNode node : children) {
            if (node.moduleId().equals(moduleId)) {
                return node;
            }
        }
        return null;
    }

    /**
     * Returns a merge of this dependency node with the specified one. The
     * children of the merged node is a union of the two node children.
     */
    public JkResolvedDependencyNode withMerging(JkResolvedDependencyNode other) {
        final List<JkResolvedDependencyNode> resultChildren = new LinkedList<>(this.children);
        for (final JkResolvedDependencyNode otherNodeChild : other.children) {
            if (!otherNodeChild.isModuleNode() || !directChildrenContains(otherNodeChild.getModuleInfo().getModuleId())) {
                resultChildren.add(otherNodeChild);
            }
        }
        return new JkResolvedDependencyNode(this.nodeInfo, resultChildren);
    }

    private static void addFileDepsToTree(List<? extends JkDependency> dependencies, List<JkResolvedDependencyNode> result,
                                          Set<JkFileDependency> addedFileDeps, JkModuleId moduleId) {
        for (final JkDependency dependency : depsUntilLast(dependencies, moduleId)) {
            final JkFileDependency fileDep = (JkFileDependency) dependency;
            if (!addedFileDeps.contains(fileDep)) {
                final JkResolvedDependencyNode fileNode = JkResolvedDependencyNode.ofFileDep(fileDep, Collections.emptySet());
                addedFileDeps.add(fileDep);
                result.add(fileNode);
            }
        }
    }

    /**
     * Returns all nodes descendant of this one, deep first.
     */
    public List<JkResolvedDependencyNode> toFlattenList() {
        final List<JkResolvedDependencyNode> result = new LinkedList<>();
        for (final JkResolvedDependencyNode child : this.getChildren()) {
            result.add(child);
            result.addAll(child.toFlattenList());
        }
        return result;
    }

    /**
     * Returns first node descendant of this one standing for the specified getModuleId, deep first.
     */
    public JkResolvedDependencyNode getFirst(JkModuleId moduleId) {
        if (this.isModuleNode() && moduleId.equals(this.moduleId())) {
            return this;
        }
        for (final JkResolvedDependencyNode child : this.toFlattenList()) {
            if (child.isModuleNode() && moduleId.equals(child.moduleId())) {
                return child;
            }
        }
        return null;
    }

    private boolean directChildrenContains(JkModuleId moduleId) {
        for (final JkResolvedDependencyNode dependencyNode : this.children) {
            if (dependencyNode.isModuleNode() && dependencyNode.moduleId().equals(moduleId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of lines standing for the representation of this
     * dependency tree.
     */
    public List<String> toStrings() {
        if (this.isModuleNode()) {
            return this.toStrings(false, -1, new HashSet<>());
        }
        return Collections.singletonList(this.getModuleInfo().toString());
    }

    private List<String> toStrings(boolean showRoot, int indentLevel, Set<JkModuleId> extendedModules) {
        final List<String> result = new LinkedList<>();
        if (showRoot) {
            final String label = nodeInfo.toString();
            result.add(JkUtilsString.repeat(INDENT, indentLevel) + label);
        }
        if (this.nodeInfo == null || (this.isModuleNode() && !extendedModules.contains(this.moduleId()))) {
            if (this.nodeInfo != null) {
                extendedModules.add(this.moduleId());
            }
            for (final JkResolvedDependencyNode child : children) {
                result.addAll(child.toStrings(true, indentLevel+1, extendedModules));
            }
        }
        return result;
    }

    /**
     * Returns a complete representation string of the tree.
     */
    public String toStringTree() {
        final StringBuilder builder = new StringBuilder();
        for (final String line: toStrings()) {
            builder.append(line).append("\n");
        }
        return builder.toString();
    }

    private JkModuleId moduleId() {
        return getModuleInfo().getModuleId();
    }

    @Override
    public String toString() {
        return this.getNodeInfo().toString();
    }

    public interface JkNodeInfo extends Serializable {

        List<Path> getFiles();

        Set<String> getDeclaredConfigurations();

    }

    public static final class JkModuleNodeInfo implements Serializable, JkNodeInfo {

        private static final long serialVersionUID = 1L;

        private final JkModuleId moduleId;
        private final JkVersion declaredVersion;
        private final Set<String> declaredConfigurations;  // the left conf mapping side in the caller dependency description
        private final Set<String> rootConfigurations; // configurations fetching this node to baseTree
        private final JkVersion resolvedVersion;
        private final List<File> artifacts; // Path is not serializable
        private final boolean treeRoot;

        JkModuleNodeInfo(JkModuleId moduleId, JkVersion declaredVersion, Set<String> declaredConfigurations,
                         Set<String> rootConfigurations, JkVersion resolvedVersion, List<Path> artifacts) {
            this(moduleId, declaredVersion, declaredConfigurations, rootConfigurations, resolvedVersion, artifacts, false);
        }

        JkModuleNodeInfo(JkModuleId moduleId, JkVersion declaredVersion, Set<String> declaredConfigurations,
                         Set<String> rootConfigurations, JkVersion resolvedVersion, List<Path> artifacts, boolean treeRoot) {
            this.moduleId = moduleId;
            this.declaredVersion = declaredVersion;
            this.declaredConfigurations = declaredConfigurations;
            this.rootConfigurations = rootConfigurations;
            this.resolvedVersion = resolvedVersion;
            this.artifacts = Collections.unmodifiableList(new LinkedList<>(JkUtilsPath.toFiles(artifacts)));
            this.treeRoot = treeRoot;
        }

        static JkModuleNodeInfo ofAnonymousRoot() {
            return new JkModuleNodeInfo(JkModuleId.of("anonymousGroup:anonymousName"), JkVersion.UNSPECIFIED,
                    new HashSet<>(), new HashSet<>(), JkVersion.UNSPECIFIED, new LinkedList<>());
        }

        public static JkModuleNodeInfo ofRoot(JkVersionedModule versionedModule) {
            return new JkModuleNodeInfo(versionedModule.getModuleId(), versionedModule.getVersion(),
                    new HashSet<>(), new HashSet<>(), versionedModule.getVersion(), new LinkedList<>(), true);
        }

        public static JkModuleNodeInfo of(JkModuleId moduleId, JkVersion declaredVersion,
                                          Set<String> declaredConfigurations,
                                          Set<String> rootConfigurations,
                                          JkVersion resolvedVersion,
                                          List<Path> artifacts) {
            return new JkModuleNodeInfo(moduleId, declaredVersion, declaredConfigurations, rootConfigurations,
                    resolvedVersion, artifacts);
        }

        public JkModuleId getModuleId() {
            return moduleId;
        }

        /**
         * Shorthand for {@link #moduleId} + {@link #getResolvedVersion()}
         */
        public JkVersionedModule getResolvedVersionedModule() {
            return moduleId.withVersion(resolvedVersion.getValue());
        }

        public JkVersion getDeclaredVersion() {
            return declaredVersion;
        }

        @Override
        public Set<String> getDeclaredConfigurations() {
            return declaredConfigurations;
        }

        public Set<String> getRootConfigurations() {
            return rootConfigurations;
        }

        public JkVersion getResolvedVersion() {
            return resolvedVersion;
        }

        @Override
        public String toString() {
            if (treeRoot) {
                return "Root";
            }
            final String resolvedVersionName = isEvicted() ? "(evicted)" : resolvedVersion.getValue();
            final String declaredVersionLabel = getDeclaredVersion().getValue().equals(resolvedVersionName) ? "" : " as " + getDeclaredVersion();
            return moduleId + ":" + resolvedVersion
                    + " (present in " + rootConfigurations + ")"
                    + " (declared" + declaredVersionLabel + " " + declaredConfigurations + ")";
        }

        public boolean isEvicted() {
            return resolvedVersion == null;
        }

        @Override
        public List<Path> getFiles() {
            return JkUtilsPath.toPaths(artifacts);
        }

    }

    private static List<JkDependency> depsUntilLast(List<? extends JkDependency> dependencies, JkModuleId to) {
        final List<JkDependency> result = new LinkedList<>();
        final List<JkDependency> partialResult = new LinkedList<>();
        for (final JkDependency dependency : dependencies) {
            if (dependency instanceof JkModuleDependency) {
                final JkModuleDependency moduleDependency = (JkModuleDependency) dependency;
                if (moduleDependency.getModuleId().equals(to)) {
                    result.addAll(partialResult);
                    partialResult.clear();
                }
            } else {
                partialResult.add(dependency);
            }
        }
        if (to == null) {
            result.addAll(partialResult);
        }
        return result;
    }

    private static JkVersionProvider compute(JkNodeInfo nodeInfo, List<JkResolvedDependencyNode> children) {
        JkVersionProvider result = JkVersionProvider.of();
        if (nodeInfo instanceof JkModuleNodeInfo) {
            final JkModuleNodeInfo moduleNodeInfo = (JkModuleNodeInfo) nodeInfo;
            if (!moduleNodeInfo.treeRoot && !moduleNodeInfo.isEvicted()) {
                result = result.and(moduleNodeInfo.moduleId, moduleNodeInfo.resolvedVersion);
            }
        }
        for (final JkResolvedDependencyNode child : children) {
            result = result.and(compute(child.getNodeInfo(), child.getChildren()));
        }
        return result;
    }

    public static final class JkFileNodeInfo implements Serializable, JkNodeInfo {

        private static final long serialVersionUID = 1L;

        public static JkFileNodeInfo of(Set<String> configurations, JkFileDependency dependency) {
            if (dependency instanceof JkComputedDependency) {
                final JkComputedDependency computedDependency = (JkComputedDependency) dependency;
                return new JkFileNodeInfo(computedDependency.getFiles(), configurations, computedDependency);
            }
            return new JkFileNodeInfo(dependency.getFiles() ,configurations, null);
        }

        // for serialization we need to use File class instead of Path
        private final List<File> files;

        private final Set<String> configurations;

        private final JkComputedDependency computationOrigin;

        private JkFileNodeInfo(List<Path> files, Set<String> configurations, JkComputedDependency origin) {
            this.files = Collections.unmodifiableList(new LinkedList<>(JkUtilsPath.toFiles(files)));
            this.configurations = Collections.unmodifiableSet(new HashSet<>(configurations));
            this.computationOrigin = origin;
        }

        /**
         * Returns <code>true</code> if this node come from a computed dependency
         */
        public boolean isComputed() {
            return computationOrigin != null;
        }

        /**
         * If this node comes from a computed dependency, it returns computed dependency in question.
         */
        public JkComputedDependency computationOrigin() {
            return computationOrigin;
        }

        @Override
        public List<Path> getFiles() {
            return JkUtilsPath.toPaths(files);
        }

        @Override
        public Set<String> getDeclaredConfigurations() {
            return configurations;
        }

        @Override
        public String toString() {
            return files + (isComputed() ? " (computed)" : "");
        }
    }

}
