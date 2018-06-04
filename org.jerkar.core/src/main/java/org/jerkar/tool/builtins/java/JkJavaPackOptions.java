package org.jerkar.tool.builtins.java;

import org.jerkar.api.utils.JkUtilsString;
import org.jerkar.tool.JkDoc;

/**
 * Standard options for packaging java projects.
 */
public class JkJavaPackOptions {

    /** When true, the produced artifacts are signed with PGP */
    @JkDoc("When true, the produced artifacts are signed with PGP.")
    public boolean signWithPgp;

    /** When true, tests classes and sources are packed in jars.*/
    @JkDoc("When true, tests classes are packed in a jar.")
    public boolean tests;

    /** Comma separated list of algorithm to use to produce checksums (ex : 'sha-1,md5'). */
    @JkDoc("Comma separated list of algorithms to use to produce checksums (ex : 'sha-1,md5').")
    public String checksums;

    /** When true, javadoc is created and packed in a jar file.*/
    @JkDoc("When true, javadoc is created and packed in a jar file.")
    public boolean javadoc;

    /** When true, sources are packed in a jar file.*/
    @JkDoc("When true, sources are packed in a jar file.")
    public boolean sources;

    /**
     * Returns the checksums algorithms to checksum artifact files.
     */
    public String[] checksums() {
        return JkUtilsString.splitTrimed(checksums, ",");
    }


}
