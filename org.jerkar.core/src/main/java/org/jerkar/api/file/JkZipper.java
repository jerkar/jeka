package org.jerkar.api.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.JkUtilsAssert;
import org.jerkar.api.utils.JkUtilsFile;
import org.jerkar.api.utils.JkUtilsIO;
import org.jerkar.api.utils.JkUtilsIterable;
import org.jerkar.api.utils.JkUtilsThrowable;

/**
 * Defines elements to embed in a zip archive and methods to write archive on
 * disk.
 */
public final class JkZipper {

    public enum JkCompressionLevel {

        BEST_COMPRESSION(Deflater.BEST_COMPRESSION), BEST_SPEED(Deflater.BEST_SPEED), DEFAULT_COMPRESSION(
                Deflater.DEFAULT_COMPRESSION), NO_COMPRESSION(Deflater.NO_COMPRESSION), ;

        private final int level;

        private JkCompressionLevel(int level) {
            this.level = level;
        }

    }

    public enum JkCompressionMethod {

        DEFLATED(ZipEntry.DEFLATED), STORED(0);

        private final int method;

        private JkCompressionMethod(int method) {
            this.method = method;
        }
    }

    private final List<? extends Object> itemsToZip;

    private final List<File> archivestoMerge;

    private final JkCompressionLevel jkCompressionLevel;

    private final JkCompressionMethod jkCompressionMethod;

    private JkZipper(List<? extends Object> itemsToZip, List<File> archivestoMerge,
            JkCompressionLevel level, JkCompressionMethod method) {
        this.itemsToZip = itemsToZip;
        this.archivestoMerge = archivestoMerge;
        this.jkCompressionLevel = level;
        this.jkCompressionMethod = method;
    }

    /**
     * Creates a {@link JkZipper} from an array of directories.
     */
    public static JkZipper of(File... dirs) {
        final List<File> archivestoMerges = new LinkedList<File>();
        final List<Object> items = new LinkedList<Object>();
        for (final File file : dirs) {
            if (file.isDirectory()) {
                items.add(file);
            } else {
                archivestoMerges.add(file);
            }
        }
        return new JkZipper(items, archivestoMerges, JkCompressionLevel.DEFAULT_COMPRESSION,
                JkCompressionMethod.DEFLATED);
    }

    @SuppressWarnings("unchecked")
    static JkZipper of(JkFileTreeSet... jkDirSets) {
        return new JkZipper(Arrays.asList(jkDirSets), Collections.EMPTY_LIST,
                JkCompressionLevel.DEFAULT_COMPRESSION, JkCompressionMethod.DEFLATED);
    }

    @SuppressWarnings("unchecked")
    static JkZipper of(JkFileTree... jkDirs) {
        return new JkZipper(Arrays.asList(jkDirs), Collections.EMPTY_LIST,
                JkCompressionLevel.DEFAULT_COMPRESSION, JkCompressionMethod.DEFLATED);
    }

    @SuppressWarnings("unchecked")
    public JkZipper merge(Iterable<File> archiveFiles) {
        return new JkZipper(itemsToZip, JkUtilsIterable.concatLists(this.archivestoMerge,
                archiveFiles), this.jkCompressionLevel, this.jkCompressionMethod);
    }

    public JkZipper merge(File... archiveFiles) {
        return merge(Arrays.asList(archiveFiles));
    }

    /**
     * Append the content of this zipper to the specified archive file. If the
     * specified file does not exist, it will be created under the hood.
     */
    public JkCheckSumer appendTo(File archive) {
        final File temp = JkUtilsFile.tempFile(archive.getName(), "");
        JkUtilsFile.move(archive, temp);
        final JkCheckSumer jkCheckSumer = this.merge(temp).to(archive);
        temp.delete();
        return jkCheckSumer;
    }

    public JkZipper with(JkCompressionLevel level) {
        return new JkZipper(this.itemsToZip, this.archivestoMerge, level, this.jkCompressionMethod);
    }

    public JkZipper with(JkCompressionMethod method) {
        return new JkZipper(this.itemsToZip, this.archivestoMerge, this.jkCompressionLevel, method);
    }

    /**
     * As {@link #to(File)} but specifying compression level.
     */
    public JkCheckSumer to(File zipFile) {
        JkLog.start("Creating zip file : " + zipFile);
        final ZipOutputStream zos = JkUtilsIO.createZipOutputStream(zipFile,
                this.jkCompressionLevel.level);
        zos.setMethod(this.jkCompressionMethod.method);

        // Adding files to archive
        for (final Object item : this.itemsToZip) {
            if (item instanceof File) {
                final File file = (File) item;
                JkUtilsIO.addZipEntry(zos, file, file.getParentFile(), storedMethod());
            } else if (item instanceof EntryFile) {
                final EntryFile entryFile = (EntryFile) item;
                JkUtilsIO.addZipEntry(zos, entryFile.file, entryFile.path, storedMethod());
            } else if (item instanceof JkFileTree) {
                final JkFileTree dirView = (JkFileTree) item;
                addFileTree(zos, dirView);
            } else if (item instanceof JkFileTreeSet) {
                final JkFileTreeSet dirViews = (JkFileTreeSet) item;
                for (final JkFileTree dirView : dirViews.fileTrees()) {
                    addFileTree(zos, dirView);
                }
            } else {
                throw new IllegalStateException("Items of class " + item.getClass()
                        + " not handled.");
            }
        }

        // Merging archives to this archive
        for (final File archiveToMerge : this.archivestoMerge) {
            final ZipFile file;
            try {
                file = new ZipFile(archiveToMerge);
            } catch (final IOException e) {
                throw new RuntimeException("Error while opening zip file "
                        + archiveToMerge.getPath(), e);
            }
            JkUtilsIO.mergeZip(zos, file, storedMethod());
        }
        JkUtilsIO.closeQuietly(zos);
        JkLog.done();
        return new JkCheckSumer(zipFile);
    }

    private boolean storedMethod() {
        return JkCompressionMethod.STORED.equals(jkCompressionMethod);
    }

    public JkZipper andEntryName(String entryName, File file) {
        final List<Object> list = new LinkedList<Object>(this.itemsToZip);
        list.add(new EntryFile(entryName, file));
        return new JkZipper(list, archivestoMerge, this.jkCompressionLevel,
                this.jkCompressionMethod);
    }

    public JkZipper andEntryPath(String entryPath, File file) {
        final List<Object> list = new LinkedList<Object>(this.itemsToZip);
        final String path = entryPath.endsWith("/") ? entryPath + file.getName() : entryPath + "/"
                + file.getName();
        list.add(new EntryFile(path, file));
        return new JkZipper(list, archivestoMerge, this.jkCompressionLevel,
                this.jkCompressionMethod);
    }

    private void addFileTree(ZipOutputStream zos, JkFileTree dirView) {
        if (!dirView.exists()) {
            return;
        }
        final File base = JkUtilsFile.canonicalFile(dirView.root());
        for (final File file : dirView) {
            JkUtilsIO.addZipEntry(zos, file, base,
                    JkCompressionMethod.STORED.equals(this.jkCompressionMethod));
        }
    }

    private static class EntryFile {
        final String path;
        final File file;

        public EntryFile(String path, File file) {
            super();
            this.path = path;
            this.file = file;
        }
    }

    public static void unzip(Iterable<File> zips, File folder) {
        for (final File zip : zips) {
            unzip(zip, folder);
        }
    }

    public static void unzip(File zip, File folder) {
        final byte[] buffer = new byte[1024];
        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }
            final ZipInputStream zis = new ZipInputStream(new FileInputStream(zip));
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                final String fileName = ze.getName();
                final File newFile = new File(folder, fileName);
                if (ze.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    newFile.createNewFile();
                    final FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (final IOException e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

    /**
     * Wrapper on <code>File</code> allowing to creates digests on it.
     * 
     * @author Jerome Angibaud
     */
    public static final class JkCheckSumer {

        /**
         * Creates an instance of {@link JkCheckSumer} wrapping the specified
         * file.
         */
        public static JkCheckSumer of(File file) {
            return new JkCheckSumer(file);
        }

        private final File file;

        private JkCheckSumer(File file) {
            JkUtilsAssert.isTrue(file.isFile(), file.getAbsolutePath()
                    + " is a directory, not a file.");
            this.file = file;
        }

        /**
         * Creates an MD5 digest for this wrapped file. The digest file is
         * written in the same directory as the digested file and has the same
         * name + '.md5' extension.
         */
        public JkCheckSumer md5() {
            JkLog.start("Creating MD5 file for : " + file);
            final File parent = file.getParentFile();
            final String md5 = JkUtilsFile.checksum(file, "MD5");
            final String fileName = file.getName() + ".md5";
            JkUtilsFile.writeString(new File(parent, fileName), md5, false);
            JkLog.done();
            return this;
        }

        /**
         * Creates an SHA-1 digest for this wrapped file. The digest file is
         * written in the same directory as the digested file and has the same
         * name + '.sha1' extension.
         */
        public JkCheckSumer sha1() {
            JkLog.start("Creating SHA-1 file for : " + file);
            final File parent = file.getParentFile();
            final String sha1 = JkUtilsFile.checksum(file, "SHA-1");
            final String fileName = file.getName() + ".sha1";
            JkUtilsFile.writeString(new File(parent, fileName), sha1, false);
            JkLog.done();
            return this;
        }

        /**
         * As {@link #md5()} but allow to pass a flag as parameter to actually
         * process or not the digesting.
         */
        public JkCheckSumer md5If(boolean process) {
            if (!process) {
                return this;
            }
            return md5();
        }

        /**
         * As {@link #sha1()} but allow to pass a flag as parameter to actually
         * process or not the digesting.
         */
        public JkCheckSumer sha1If(boolean process) {
            if (!process) {
                return this;
            }
            return sha1();
        }
    }

}
