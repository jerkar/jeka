package org.jerkar.tool;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jerkar.api.java.JkClassLoader;
import org.jerkar.api.utils.JkUtilsString;

/**
 * Contains description for all concrete plugins in current classpath.
 * <p>
 * Jerkar offers a very simple, yet powerful, plugin mechanism.<br/>
 * Basically it offers to discover every classes in the classpath that inherit
 * to a given class and that respect a certain naming convention.<br/>
 * <p>
 * The naming convention is as follow : The class simple name should be prefixed
 * with 'JkPlugin'.<br/>
 * For example, 'my.package.JkPluginXxxxx' will be discovered as a plugin named Xxxxx.
 *
 * @author Jerome Angibaud
 * 
 * @see {@link JkPluginDescription}
 */
final class PluginDictionary {

    private Set<JkPluginDescription> plugins;

    /**
     * Returns all the plugins present in classpath for this template class.
     */
    Set<JkPluginDescription> getAll() {
        if (plugins == null) {
            synchronized (this) {
                final Set<JkPluginDescription> result = loadAllPlugins();
                this.plugins = Collections.unmodifiableSet(result);
            }
        }
        return this.plugins;
    }

    /**
     * Returns the plugin having a full name equals to the specified name. If
     * not found, returns the plugin having a short name equals to the specified
     * name. Note that the short name is capitalized for you so using
     * "myPluging" or "MyPlugin" is equal. If not found, returns
     * <code>null</code>.
     */
    JkPluginDescription loadByName(String name) {
        if (!name.contains(".")) {
            final JkPluginDescription result = loadPluginHavingShortName(
                    JkUtilsString.capitalize(name));
            if (result != null) {
                return result;
            }
        }
        return loadPluginsHavingLongName(name);
    }

    public JkPluginDescription loadByNameOrFail(String name) {
        final JkPluginDescription result = loadByName(name);
        if (result == null) {
            throw new IllegalArgumentException("No class found having name "
                    + simpleClassName(name) + " for plugin '" + name + "'.");
        }
        return result;
    }

    private static String simpleClassName(String pluginName) {
        return JkPlugin.class.getSimpleName() + JkUtilsString.capitalize(pluginName);
    }

    @Override
    public String toString() {
        if (this.plugins == null) {
            return "Not loaded.";
        }
        return this.plugins.toString();
    }

    private static <T> Set<JkPluginDescription> loadAllPlugins() {
        final String nameSuffix = JkPlugin.class.getSimpleName();
        return loadPlugins( "**/" + nameSuffix + "*", nameSuffix, "**/*$" + nameSuffix + "*",
                "*$" + nameSuffix + "*");
    }

    private static JkPluginDescription loadPluginHavingShortName(String shortName) {
        final String simpleName = simpleClassName(shortName);
        final Set<JkPluginDescription> set = loadPlugins( "**/" + simpleName, simpleName);
        set.addAll(loadPlugins( "**/*$" + simpleName, "*$" + simpleName ));
        if (set.size() > 1) {
            throw new JkException("Several plugin have the same short name : '" + shortName
                    + "'. Please disambiguate with using plugin long name (full class name)."
                    + " Following plugins have same shortName : " + set);
        }
        if (set.isEmpty()) {
            return null;
        }
        return set.iterator().next();
    }

    private static JkPluginDescription loadPluginsHavingLongName(String longName) {
        final Class<? extends JkPlugin> pluginClass = JkClassLoader.current().loadIfExist(longName);
        if (pluginClass == null) {
            return null;
        }
        return new JkPluginDescription(pluginClass);
    }

    private static Set<JkPluginDescription> loadPlugins(String... patterns) {
        final Set<Class<?>> matchingClasses = JkClassLoader.of(JkPlugin.class).loadClasses(patterns);
        final Set<Class<?>> result = new HashSet<>();
        return toPluginSet(matchingClasses);
    }

    @SuppressWarnings("unchecked")
    private static Set<JkPluginDescription> toPluginSet(Iterable<Class<?>> classes) {
        final Set<JkPluginDescription> result = new TreeSet<>();
        for (final Class<?> clazz : classes) {
            result.add(new JkPluginDescription((Class<? extends JkPlugin>) clazz));
        }
        return result;
    }

    /**
     * Give the description of a plugin class as its name, its purpose and its
     * base class.
     * 
     * @author Jerome Angibaud
     */
    static class JkPluginDescription implements Comparable<JkPluginDescription> {

        private static String shortName(Class<?> clazz) {
            return JkUtilsString.uncapitalize(JkUtilsString.substringAfterFirst(JkPlugin.class.getSimpleName(),
                    clazz.getSimpleName()));
        }

        private static String longName(Class<?> clazz) {
            return clazz.getName();
        }

        private final String shortName;

        private final String fullName;

        private final Class<? extends JkPlugin> clazz;

        public JkPluginDescription(Class<? extends JkPlugin> clazz) {
            super();
            this.shortName = shortName(clazz);
            this.fullName = longName(clazz);
            this.clazz = clazz;
        }

        public String shortName() {
            return this.shortName;
        }

        public String fullName() {
            return this.fullName;
        }

        public Class<? extends JkPlugin> pluginClass() {
            return clazz;
        }

        public List<String> explanation() {
            if (this.clazz.getAnnotation(JkDoc.class) == null) {
                return Collections.emptyList();
            }
            return Arrays.asList(this.clazz.getAnnotation(JkDoc.class).value());
        }

        @Override
        public String toString() {
            return "name=" + this.shortName + "(" + this.fullName + ")";
        }

        @Override
        public int compareTo(JkPluginDescription o) {
            return this.shortName.compareTo(o.shortName);
        }
    }

}