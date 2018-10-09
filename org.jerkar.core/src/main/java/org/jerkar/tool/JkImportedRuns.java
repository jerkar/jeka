package org.jerkar.tool;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jerkar.api.utils.JkUtilsReflect;

/**
 * A build class can import one or several build classes. It is an important mechanism to reuse builds across projects.
 * This class holds imported builds within a build class.
 *
 * @author Jerome Angibaud
 */
public final class JkImportedRuns {

    private static final ThreadLocal<Map<ImportedBuildRef, JkRun>> IMPORTED_BUILD_CONTEXT = new ThreadLocal<>();

    static JkImportedRuns of(Path masterRootDir, JkRun masterBuild) {
        return new JkImportedRuns(masterRootDir, getDirectImportedBuilds(masterBuild));
    }

    private final List<JkRun> directImportedBuilds;

    private List<JkRun> transitiveImportedBuilds;

    private final Path masterBuildBaseDir;

    private JkImportedRuns(Path masterDir, List<JkRun> buildDeps) {
        super();
        this.masterBuildBaseDir = masterDir;
        this.directImportedBuilds = Collections.unmodifiableList(buildDeps);
    }

    /**
     * Returns only the direct slave of this master build.
     */
    public List<JkRun> directs() {
        return Collections.unmodifiableList(directImportedBuilds);
    }

    /**
     * Returns direct and transitive importedRuns.
     */
    public List<JkRun> all() {
        if (transitiveImportedBuilds == null) {
            transitiveImportedBuilds = resolveTransitiveBuilds(new HashSet<>());
        }
        return transitiveImportedBuilds;
    }

    /**
     * Same as {@link #all()} but only returns run instance of the specified class or its subclasses.
     */
    public <T extends JkRun> List<T> allOf(Class<T> ofClass) {
        final List<T> result = new LinkedList<>();
        for (final JkRun build : all()) {
            if (ofClass.isAssignableFrom(build.getClass())) {
                result.add((T) build);
            }
        }
        return result;
    }

    private List<JkRun> resolveTransitiveBuilds(Set<Path> files) {
        final List<JkRun> result = new LinkedList<>();
        for (final JkRun build : directImportedBuilds) {
            final Path dir = build.baseDir();
            if (!files.contains(dir)) {
                result.addAll(build.importedRuns().resolveTransitiveBuilds(files));
                result.add(build);
                files.add(dir);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<JkRun> getDirectImportedBuilds(JkRun masterBuild) {
        final List<JkRun> result = new LinkedList<>();
        final List<Field> fields = JkUtilsReflect.getAllDeclaredFields(masterBuild.getClass(), JkImportRun.class);

        for (final Field field : fields) {
            final JkImportRun jkProject = field.getAnnotation(JkImportRun.class);
            final JkRun importedBuild = createImportedBuild(
                    (Class<? extends JkRun>) field.getType(), jkProject.value(), masterBuild.baseDir());
            try {
                JkUtilsReflect.setFieldValue(masterBuild, field, importedBuild);
            } catch (final RuntimeException e) {
                Path currentClassBaseDir = Paths.get(masterBuild.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
                while (!Files.exists(currentClassBaseDir.resolve("build/def")) && currentClassBaseDir != null) {
                    currentClassBaseDir = currentClassBaseDir.getParent();
                }
                if (currentClassBaseDir == null) {
                    throw new IllegalStateException("Can't inject slave build instance of type " + importedBuild.getClass().getSimpleName()
                            + " into field " + field.getDeclaringClass().getName()
                            + "#" + field.getName() + " from directory " + masterBuild.baseDir()
                            + " while working dir is " + Paths.get("").toAbsolutePath());
                }
                throw new IllegalStateException("Can't inject slave build instance of type " + importedBuild.getClass().getSimpleName()
                        + " into field " + field.getDeclaringClass().getName()
                        + "#" + field.getName() + " from directory " + masterBuild.baseDir()
                        + "\nBuild class is located in " + currentClassBaseDir
                        + " while working dir is " + Paths.get("").toAbsolutePath()
                        + ".\nPlease set working dir to " + currentClassBaseDir, e);
            }
            result.add(importedBuild);
        }
        return result;
    }

    /*
     * Creates an instance of <code>JkRun</code> for the given project and
     * build class. The instance field annotated with <code>JkOption</code> are
     * populated as usual.
     */
    @SuppressWarnings("unchecked")
    private static <T extends JkRun> T createImportedBuild(Class<T> importedBuildClass, String relativePath, Path masterBuildPath) {
        final Path projectDir = masterBuildPath.resolve(relativePath).normalize();
        final ImportedBuildRef projectRef = new ImportedBuildRef(projectDir, importedBuildClass);
        Map<ImportedBuildRef, JkRun> map = IMPORTED_BUILD_CONTEXT.get();
        if (map == null) {
            map = new HashMap<>();
            IMPORTED_BUILD_CONTEXT.set(map);
        }
        final T cachedResult = (T) IMPORTED_BUILD_CONTEXT.get().get(projectRef);
        if (cachedResult != null) {
            return cachedResult;
        }
        final Engine engine = new Engine(projectDir);
        final T result = engine.getRun(importedBuildClass);
        IMPORTED_BUILD_CONTEXT.get().put(projectRef, result);
        return result;
    }

    private static class ImportedBuildRef {

        final String canonicalFileName;

        final Class<?> clazz;

        ImportedBuildRef(Path projectDir, Class<?> clazz) {
            super();
            this.canonicalFileName = projectDir.normalize().toAbsolutePath().toString();
            this.clazz = clazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ImportedBuildRef that = (ImportedBuildRef) o;

            if (!canonicalFileName.equals(that.canonicalFileName)) {
                return false;
            }
            return clazz.equals(that.clazz);
        }

        @Override
        public int hashCode() {
            int result = canonicalFileName.hashCode();
            result = 31 * result + clazz.hashCode();
            return result;
        }
    }

}
