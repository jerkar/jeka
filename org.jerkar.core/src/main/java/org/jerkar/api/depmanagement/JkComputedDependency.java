package org.jerkar.api.depmanagement;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.jerkar.api.depmanagement.JkDependency.JkFileDependency;
import org.jerkar.api.java.JkJavaProcess;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.system.JkProcess;
import org.jerkar.api.utils.JkUtilsFile;
import org.jerkar.api.utils.JkUtilsIterable;

/**
 * Dependency on computed resource. More concretely, this is a file dependency on files that might not
 * be present at the time of the build and that has to be generated. Instances of this class are
 * responsible to generate the missing files. <p>
 * 
 * Computed dependencies are instantiated by providing files to expect and a {@link Runnable} that
 * generates these files in case one of them misses. <p>
 * 
 * This is yet simple but quite powerful mechanism, cause the runnable can be anything as Maven or ANT build
 * of another project, a Jerkar build of another project, ... <p>
 * 
 * This is the way for creating multi-projet (and multi-techno if desired) builds.
 * 
 */
public class JkComputedDependency extends JkFileDependency {

    /**
     * Creates a computed dependency from the specified files and {@link JkProcess} to run for
     * generating them.
     */
    public static final JkComputedDependency of(final JkProcess process, File... files) {
        final Set<File> fileSet = JkUtilsIterable.setOf(files);
        final Runnable runnable = new Runnable() {

            @Override
            public void run() {
                process.runSync();
            }

            @Override
            public String toString() {
                return process.toString();
            }
        };
        return new JkComputedDependency(runnable, fileSet);
    }

    /**
     * Creates a computed dependency from the specified files and the specified {@link Runnable} to run for
     * generating them.
     */
    public static final JkComputedDependency of(Runnable runnable, File... files) {
        final Set<File> fileSet = JkUtilsIterable.setOf(files);
        return new JkComputedDependency(runnable, fileSet);
    }

    /**
     * Identical to {@link #of(File, JkJavaProcess, String, String...)} but you specified a set of files
     * instead of a single one.
     */
    public static final JkComputedDependency of(Set<File> files, final JkJavaProcess process,
            final String className, final String... args) {
        final Set<File> fileSet = JkUtilsIterable.setOf(files);
        final Runnable runnable = new Runnable() {

            @Override
            public void run() {
                process.runClassSync(className, args);
            }
        };
        return new JkComputedDependency(runnable, fileSet);
    }

    /**
     * Creates a computed dependency from the specified file and the specified java program to run for
     * generating them.
     */
    public static final JkComputedDependency of(File file, final JkJavaProcess process,
            final String className, final String... args) {
        return of(JkUtilsIterable.setOf(file), process, className, args);
    }

    private static final long serialVersionUID = 1L;

    private final Runnable runnable;

    private final Set<File> files;

    /**
     * Constructs a computed dependency from the specified files and the specified {@link Runnable} to run for
     * generating them.
     */
    protected JkComputedDependency(Runnable runnable, Set<File> files) {
        super();
        this.runnable = runnable;
        this.files = files;
    }

    /**
     * Returns <code>true</code> if at least one of these files is missing or one of these directory is empty.
     */
    public final boolean hasMissingFilesOrEmptyDirs() {
        return !missingFilesOrEmptyDirs().isEmpty();
    }

    /**
     * Returns the missing files or empty directory for this dependency.
     */
    public final Set<File> missingFilesOrEmptyDirs() {
        final Set<File> files = new HashSet<File>();
        for (final File file : this.files) {
            if (!file.exists() || (file.isDirectory() && JkUtilsFile.filesOf(file, true).isEmpty())) {
                files.add(file);
            }
        }
        return files;
    }

    @Override
    public Set<File> files() {
        if (this.hasMissingFilesOrEmptyDirs()) {
            JkLog.startHeaded("Building depending project " + this);
            runnable.run();
            JkLog.done();
        }
        final Set<File> missingFiles = this.missingFilesOrEmptyDirs();
        if (!missingFiles.isEmpty()) {
            throw new IllegalStateException("Project " + this + " does not generate "
                    + missingFiles);
        }
        return files;
    }

    @Override
    public String toString() {
        return this.runnable.toString();
    }

}