package dev.jeka.core.api.depmanagement;

import dev.jeka.core.api.utils.JkUtilsIterable;

import java.util.List;

/**
 * In Maven repositories, modules are published along a pom.xml metadata containing
 * the transitive dependencies of the module. Here, transitive dependencies can be
 * published with only 2 scopes : either 'compile' nor 'runtime'.<p>
 * This enum specifies how a dependency must take in account its transitive ones.
 */
public class  JkTransitivity {

    private final String value;

    private JkTransitivity(String value) {
        this.value = value;
    }

    /**
     * Dependency will be fetched without any transitive dependencies
     */
    public static final JkTransitivity NONE = new JkTransitivity("NONE");

    /**
     * Dependency will be fetch along transitive dependencies declared as 'compile'
     */
    public static final JkTransitivity COMPILE = new JkTransitivity("COMPILE");

    /**
     * Dependency will be fetch along transitive dependencies declared as 'runtime'
     * or 'compile'
     */
    public static final JkTransitivity RUNTIME = new JkTransitivity("RUNTIME");

    private static final List<JkTransitivity> ORDER = JkUtilsIterable.listOf(NONE, COMPILE, RUNTIME);

    public static JkTransitivity ofDeepest(JkTransitivity left, JkTransitivity right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return ORDER.indexOf(left) > ORDER.indexOf(right)? left : right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JkTransitivity that = (JkTransitivity) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
