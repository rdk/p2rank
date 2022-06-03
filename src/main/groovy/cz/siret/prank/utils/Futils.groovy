package cz.siret.prank.utils


import com.google.common.io.Files
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.apache.commons.io.FileUtils
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.LZMAOutputStream
import org.tukaani.xz.XZIOException
import org.zeroturnaround.zip.ZipUtil

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.function.Predicate
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Safe file utility methods
 *
 * needed also to avoid problems with File(String/URI) ambiguous constructor
 */
@Slf4j
@CompileStatic
class Futils {

    public static final int GZIP_DEFAULT_LEVEL = Deflater.DEFAULT_COMPRESSION

    public static final int ZIP_BEST_COMPRESSION = 9

    private static final int BUFFER_SIZE = 128 * 1024

    private static final Set<String> COMPRESSED_EXTENSIONS = ["gz", "zst", "zip", "bz2"].toSet()

//===========================================================================================================//

    static String normalize(String path) {
        if (path==null) return null

        new File(path).toPath().normalize()
    }

    static boolean isAbsolute(String path) {
        new File(path).isAbsolute()
    }

    static String absPath(String path) {
        if (path==null) return null

        normalize(new File(path).absolutePath)
    }

    static String prependIfNotAbsolute(String path, String dir) {
        if (isAbsolute(path)) {
            return path
        } else {
            return safe(dir + "/" + path)
        }
    }

    static String absSafePath(String path) {
        if (path==null) return null

        safe(absPath(path))
    }

    static String safe(String path) {
        if (path==null) return null

        path.replace("\\","/").replace("//","/")
    }

    static String  sanitizeFilename(String inputName) {
        return inputName.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    }

    /**
     * parent dir of a file
     */
    static String dir(String path) {
        new File(path).parent
    }

    static String relativize(String what, String relativeToDir) {
        what = absPath(what).replace("\\","/")
        relativeToDir = absPath(relativeToDir).replace("\\","/")

        relativeToDir.toURI().relativize( what.toURI() ).toString()
    }

    static long size(String fname) {
        return new File(fname).length()
    }

    static String sizeMBFormatted(String fname) {
        double size = size(fname) / (double)(1024*1024)
        return Formatter.format(size, 1)
    }

    /**
     * file name stripped of path 
     */
    static String shortName(String path) {
        if (path==null) return null

        new File(path).name
    }

    static String removeCompressExt(String path) {
        if (path==null) return null

        if (isCompressed(path)) {
            path = removeLastExtension(path)
        }

        return path
    }

    /**
     *
     * Real file extension (ignoring last compressed extension).
     *
     * file.pdb -> pdb
     * file.pdb.gz -> pdb
     * file -> NULL
     */
    @Nullable
    static String realExtension(String path) {
        return lastExt(removeCompressExt(shortName(path)))
    }

    /**
     * @return true is file has extension that P2Rank can work with ("gz","zstd",...)
     */
    static boolean isCompressed(String fname) {
        return COMPRESSED_EXTENSIONS.contains(lastExt(fname))
    }

    /**
     * @return non-empty last file extension or null
     */
    @Nullable
    static String lastExt(String fname) {
        if (fname == null) return null

        fname = shortName(fname)

        int idx = fname.lastIndexOf('.')
        if (idx >= 0) {
            if (idx+1 == fname.length()) {
                return null
            }
            return fname.substring(idx+1)
        } else {
            return null
        }
    }

    /**
     * Base short file name without extension.
     * I.e. removes one extension including potentially one additional compression ext.
     * Other extensions / dots in filename ale preserved.
     *
     * "/dir/ABCD.pdb" -> "ABCD"
     * "/dir/ABCD.pdb.gz" -> "ABCD"
     * "/dir/ABCD.pdb.cube.gz" -> "ABCD.pdb"
     *
     * @param path
     * @return
     */
    static String baseName(String path) {
        if (path==null) return null

        return removeLastExtension(removeCompressExt(shortName(path)))
    }

    /**
     * Removes last file extension suffix from path.
     * If file has no extension keeps path intact.
     */
    static String removeLastExtension(String path) {
        if (path == null) return null

        String lastExt = lastExt(path)

        if (lastExt == null) {
            return path
        } else {
            return Sutils.removeSuffix(path, '.'+lastExt)
        }
    }

    /**
     * reads text file from classpath
     */
    static String readResource(String path) {
        return Futils.class.getResourceAsStream(path).newReader().getText()
    }

    /**
     * Opens buffered decompressing (if needed) file stream.
     */
    static InputStream inputStream(File file) {
        String fname = file.getName()

        InputStream is = bufferedFileInputStream(file)

        if (fname.endsWith(".gz")) {
            is = new GZIPInputStream(is)
        } else if (fname.endsWith(".lzma")) {
            is = new LZMAInputStream(is)
        } else if (fname.endsWith(".zst") || fname.endsWith(".zstd")) {
            is = new ZstdCompressorInputStream(is)
        }

        return is
    }

    /**
     * Opens buffered decompressing (if needed) file stream.
     */
    static InputStream inputStream(String fname) {
        return inputStream(new File(fname))
    }

    public static BufferedInputStream bufferedFileInputStream(File file) {
        return new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)
    }

    static OutputStream outputStream(String fname) {
        return new FileOutputStream(fname)
    }

    static OutputStream bufferedOutputStream(String fname) {
        return new BufferedOutputStream(outputStream(fname), BUFFER_SIZE)
    }

    static String readFile(String fname) {
        new File(fname).text
    }

    static String readPossiblyCompressedFile(String fname) {
        return inputStream(new File(fname)).text
    }

    /**
     * loads properties from classpath
     */
    static Properties loadProperties(String path) {
        Properties res = new Properties()
        res.load(Futils.class.getResourceAsStream(path))
        return res
    }

    /**
     * Overwrites the file if exists and returns the writer
     */
    static PrintWriter getWriter(String fname) {
        File file = new File(fname)
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()

        return new PrintWriter(new BufferedWriter(new FileWriter(file), BUFFER_SIZE))
    }

//===========================================================================================================//

    /**
     * Overwrites the file if exists and returns the writer to gzipped output stream
     */
    static PrintWriter getGzipWriter(String fname, int compressionLevel = GZIP_DEFAULT_LEVEL) {
        File file = new File(fname)
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()

        GZIPOutputStream gos = getGzipOutputStream(fname, compressionLevel)

        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(gos), BUFFER_SIZE))
    }

    static GZIPOutputStream getGzipOutputStream(String fname, int compressionLevel = GZIP_DEFAULT_LEVEL) {
        return new GZIPOutputStream(bufferedOutputStream(fname), BUFFER_SIZE) {
            {
                this.def.level = compressionLevel
            }
        }
    }

    static OutputStream getLzmaOutputStream(String fname, int level = LZMA2Options.PRESET_DEFAULT) {
        return new LZMAOutputStream(bufferedOutputStream(fname), new LZMA2Options(level), -1)
    }

    static OutputStream getZstdOutputStream(String fname, int level = 6) {
        return new ZstdCompressorOutputStream(bufferedOutputStream(fname), level)
    }

    /**
     *
     * @param fname
     * @param format see constants in CompressorStreamFactory.GZIP
     * @return
     */
    private static OutputStream getCompressedOuts(String fname, String format) {
        return new CompressorStreamFactory().createCompressorOutputStream(format, bufferedOutputStream(fname))
    }

    static void serializeToGzip(String fname, Object object, int level = GZIP_DEFAULT_LEVEL) {
        serializeTo(fname, object, getGzipOutputStream(fname, level))
    }

    static void serializeToZstd(String fname, Object object, int level = 6) {
        serializeTo(fname, object, getZstdOutputStream(fname, level))
    }

    static void serializeToLzma(String fname, Object object, int level = LZMA2Options.PRESET_DEFAULT) {
        def lzmas = getLzmaOutputStream(fname, level)
        def oos = new ObjectOutputStream(lzmas)
        try {
            oos.writeObject(object)
            oos.close()
        } catch (XZIOException e) {
            // ignore expected exception that LZMAOutputStream does not support flushing
        } finally {
            lzmas.close()
        }
    }

    static void serializeToFile(String fname, Object object) {
        serializeTo(fname, object, bufferedOutputStream(fname))
    }

    private static void serializeTo(String fname, Object object, OutputStream stream) {
        def oos = new ObjectOutputStream(stream)
        try {
            oos.writeObject(object)
        } finally {
            oos.close()
        }
    }

    /**
     * @param file can be compressed (*.gz / *.bz2) or uncompressed
     * @return
     */
    static <T> T deserializeFromFile(String fname) {
        ObjectInputStream ois = new ObjectInputStream(inputStream(fname))
        try {
            return (T) ois.readObject()
        } finally {
            ois.close()
        }
    }

    static void writeGzip(String fname, Object text) {
        PrintWriter writer = getGzipWriter(fname)
        try {
            writer.print(text)
        } finally {
            writer.close()
        }
    }

//===========================================================================================================//

    /**
     * writeFile text file
     * @param fname
     * @param text
     */
    static void writeFile(String fname, Object text) {
        try {
            String dir = dir(fname)
            if (dir!=null && !exists(dir)) {
                mkdirs(dir)
            }

            File file = new File(fname)
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()

            file.write(text.toString())
        } catch (Exception e) {
            log.error "Error writing to file [$fname]:"+e.message, e
        }
    }

    static void append(String fname, Object text) {
        try {
            new File(fname) << text
        } catch (Exception e) {
            log.error "Error writing to file [$fname]:"+e.message, e
        }
    }

    static void appendl(String fname, Object text) {
        append(fname, text?.toString() + "\n")
    }

    static List<File> listFilesWithRealExtension(String dir, String realExtension) {
        return listFiles(dir, {
            removeLastExtension(it.name) == realExtension
        }).asList()
    }

    static List<File> listFiles(String dir, Predicate<File> filter) {
        return new File(dir).listFiles().findAll{ filter.test(it) }.asList()
    }

    @Nonnull
    static List<File> listFiles(String dir) {
        if (!Futils.exists(dir)) {
            throw new RuntimeException("Directory doesn't exist: " + dir)
        }
        return new File(dir).listFiles().toList()
    }

    static boolean isDirEmpty(String dir) {
        listFiles(dir).isEmpty()    
    }


    static boolean exists(@Nullable String name) {
        if (name==null) return false
        return new File((String)name).exists()
    }

    static boolean exists(@Nullable File file) {
        if (file==null) return false
        return file.exists()
    }

    /**
     * delete file or directory recursively if exists
     */
    static void delete(String fname) {
        if (fname==null) return

        log.info "deleting " + fname

        File f = new File(fname)
        if (f.exists()) {
            if (f.isDirectory()) {
                FileUtils.deleteDirectory(f)
            } else {
                f.delete()
            }
        }
    }

    static String mkdirs(String s) {
        if (s==null) return null

        new File(s).mkdirs()

        return s
    }

    static void copy(String from, String to) {
        log.debug "copying [{}] to [{}]", from, to
        Files.copy(new File(from), new File(to))
    }


//===========================================================================================================//

    static String getSystemTempDir() {
        System.getProperty('java.io.tmpdir')
    }

//===========================================================================================================//

    static void zip(String fileOrDirectory) {
        zip(fileOrDirectory, ZipUtil.DEFAULT_COMPRESSION_LEVEL)
    }

    /**
     *
     * @param fileOrDirectory
     * @param compressionLevel
     */
    static void zip(String fileOrDirectory, int compressionLevel) {
        File fileOrDir = new File(fileOrDirectory)
        File zipFile = new File(fileOrDirectory + '.zip')

        if (fileOrDir.isDirectory()) {
            ZipUtil.pack(fileOrDir, zipFile, compressionLevel)
        } else {
            ZipUtil.packEntries([fileOrDir] as File[], zipFile, compressionLevel)
        }
    }

    static void zipAndDelete(String fileOrDirectory) {
        zipAndDelete(fileOrDirectory, ZipUtil.DEFAULT_COMPRESSION_LEVEL)
    }

    static void zipAndDelete(String fileOrDirectory, int compressionLevel) {
        zip(fileOrDirectory, compressionLevel)
        delete(fileOrDirectory)
    }

//===========================================================================================================//

    static List<String> readLines(String fname) {
        File file = new File(fname)
        return file.readLines()
    }

//===========================================================================================================//

    /**
     * Find potentially compressed file.
     * @param fname
     * @return
     */
    @Nullable
    static String findCompressed(String fname) {
        if (exists(fname)) {
            return fname
        }
        for (String ext : COMPRESSED_EXTENSIONS) {
            String fc = fname + '.' + ext
            if (exists(fc)) {
                return fc
            }
        }
        return null
    }

    /**
     * Looks for file in dirs
     * @return null if not found
     */
    static String findFileInDirs(String fname, List<String> dirs) {
        if (fname == null || dirs == null) {
            return null
        }
        for (String dir : dirs) {
            String ff = "$dir/$fname"
            if (exists(ff)) {
                return ff
            }
        }
        return null
    }

    /**
     * Find potentially compressed file.
     */
    @Nullable
    static String findCompressedFileInDirs(String fname, List<String> dirs) {
        if (fname == null || dirs == null) {
            return null
        }
        for (String dir : dirs) {
            String ff = findCompressed("$dir/$fname")
            if (ff != null) {
                return ff
            }
        }
        return null
    }

    /**
     * Returns first file matching filter.
     * Directories don't have to exist.
     */
    @Nullable
    static File findFileInDirs(List<String> dirs, Predicate<File> filter) {
        if (dirs == null) {
            return null
        }
        dirs = dirs.findAll {exists(it)} //
        for (String dir : dirs) {
            for (File f : listFiles(dir)) {
                if (filter.test(f)) {
                    return f
                }
            }
        }
        return null
    }

}
