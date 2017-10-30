package org.jerkar.api.depmanagement;


import java.io.Serializable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jerkar.api.utils.JkUtilsIterable;

/**
 * A set ofMany {@link JkRepo}
 *
 * @author Jerome Angibaud
 */
public final class JkRepos implements Iterable<JkRepo>, Serializable {

    private static final long serialVersionUID = 1L;

    private InternalDepResolver ivyResolver;

    /**
     * Crates a {@link JkRepos} from the specified {@link JkRepo}s
     */
    public static JkRepos of(JkRepo... jkRepositories) {
        return new JkRepos(Arrays.asList(jkRepositories));
    }

    /**
     * Crates an empty {@link JkRepos}
     */
    public static JkRepos empty() {
        return new JkRepos(new LinkedList<>());
    }

    /**
     * Crates a {@link JkRepos} from the specified {@link JkRepo}s
     */
    public static JkRepos of(List<String> urls) {
        final List<JkRepo> list = new LinkedList<>();
        for (final String url : urls) {
            list.add(JkRepo.of(url));
        }
        return new JkRepos(list);
    }

    /**
     * Crates a {@link JkRepos} from the specified {@link JkRepo}s
     */
    public static JkRepos of(String ... urls) {
        return of(Arrays.asList(urls));
    }

    /**
     * Creates a {@link JkRepos} from Maven repositories having specified file roots.
     */
    public static JkRepos maven(Path... files) {
        final List<JkRepo> list = new LinkedList<>();
        for (final Path file : files) {
            list.add(JkRepo.maven(file));
        }
        return new JkRepos(list);
    }

    /**
     * Creates a {@link JkRepos} from Ivy repositories having specified urls.
     */
    public static JkRepos ivy(String... urls) {
        final List<JkRepo> list = new LinkedList<>();
        for (final String url : urls) {
            list.add(JkRepo.ivy(url));
        }
        return new JkRepos(list);
    }

    /**
     * Creates a {@link JkRepos} from Maven repositories having specified Ivy files.
     */
    public static JkRepos ivy(Path... files) {
        final List<JkRepo> list = new LinkedList<>();
        for (final Path file : files) {
            list.add(JkRepo.ivy(file));
        }
        return new JkRepos(list);
    }

    /**
     * Creates a {@link JkRepos} from the single Maven central repository.
     */
    public static JkRepos mavenCentral() {
        return new JkRepos(JkUtilsIterable.listOf(JkRepo.mavenCentral()));
    }

    /**
     * Creates a {@link JkRepos} from the single Ivy JCenter repository.
     */
    public static JkRepos mavenJCenter() {
        return new JkRepos(JkUtilsIterable.listOf(JkRepo.mavenJCenter()));
    }

    private final List<JkRepo> repos;

    private JkRepos(List<JkRepo> repos) {
        super();
        this.repos = Collections.unmodifiableList(repos);
    }

    /**
     * Returns a {@link JkRepos} identical to this one but adding specified {@link JkRepo} on.
     */
    public JkRepos and(Iterable<JkRepo> jkRepos) {
        return and(JkUtilsIterable.arrayOf(jkRepos, JkRepo.class));
    }

    /**
     * Returns a {@link JkRepos} identical to this one but adding specified {@link JkRepo} on.
     */
    public JkRepos and(JkRepo... jkRepoArray) {
        final List<JkRepo> list = new LinkedList<>(this.repos);
        list.addAll(Arrays.asList(jkRepoArray));
        return new JkRepos(list);
    }

    /**
     * Same as {@link #and(JkRepo...)} but only effective if the specified condition is true.
     */
    public JkRepos andIf(boolean condition, JkRepo... repos) {
        if (condition) {
            return and(repos);
        }
        return this;
    }

    /**
     * Same as {@link #and(JkRepo...)} but only effective if this one is currently empty.
     */
    public JkRepos andIfEmpty(JkRepo... repos) {
        return andIfEmpty(Arrays.asList(repos));
    }

    /**
     * Same as {@link #and(Iterable)} but only effective if this one is currently empty.
     */
    public JkRepos andIfEmpty(Iterable<JkRepo> repos) {
        if (this.isEmpty()) {
            return and(repos);
        }
        return this;
    }

    /**
     * Returns a {@link JkRepos} identical to this one but adding Ivy {@link JkRepo} having specified baseTree files.
     */
    public JkRepos andIvy(Path... files) {
        final List<JkRepo> list = new LinkedList<>(this.repos);
        list.addAll(JkRepos.ivy(files).repos);
        return new JkRepos(list);
    }

    /**
     * Returns a {@link JkRepos} identical to this one but adding Maven {@link JkRepo} having specified baseTree files.
     */
    public JkRepos andMaven(Path... files) {
        final List<JkRepo> list = new LinkedList<>(this.repos);
        list.addAll(maven(files).repos);
        return new JkRepos(list);
    }

    /**
     * Returns a {@link JkRepos} identical to this one but adding Maven central repository..
     */
    public JkRepos andMavenCentral() {
        final List<JkRepo> list = new LinkedList<>(this.repos);
        list.add(JkRepo.mavenCentral());
        return new JkRepos(list);
    }

    /**
     * Returns a {@link JkRepos} identical to this one but adding Ivy Jcennter repository..
     */
    public JkRepos andMavenJCenter() {
        final List<JkRepo> list = new LinkedList<>(this.repos);
        list.add(JkRepo.mavenJCenter());
        return new JkRepos(list);
    }

    /**
     * Returns <code>true</code> is this {@link JkRepos} does not ontains and {@link JkRepo}.
     */
    public boolean isEmpty() {
        return this.repos.isEmpty();
    }

    @Override
    public Iterator<JkRepo> iterator() {
        return repos.iterator();
    }

    @Override
    public String toString() {
        return repos.toString();
    }



    /**
     * Retrieves directly the file embodying the specified the external dependency.
     */
    public Path get(JkModuleDependency moduleDependency) {
        final InternalDepResolver depResolver = ivyResolver();
        return depResolver.get(moduleDependency).toPath();
    }


    /**
     * Short hand for {@link #get(JkModuleDependency)}
     */
    public Path get(JkModuleId moduleId, String version) {
        return get(JkModuleDependency.of(moduleId, version));
    }

    /**
     * Short hand for {@link #get(JkModuleDependency)}
     */
    public Path get(String moduleGroup, String moduleName, String version) {
        return get(JkModuleId.of(moduleGroup, moduleName), version);
    }

    private InternalDepResolver ivyResolver() {
        if (ivyResolver == null) {
            ivyResolver = InternalDepResolvers.ivy(this);
        }
        return ivyResolver;
    }

}
