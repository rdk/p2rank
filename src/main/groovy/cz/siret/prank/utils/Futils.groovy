package cz.siret.prank.utils

import com.google.common.io.Files
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdUtils
import org.apache.commons.io.FileUtils
import org.apache.tools.bzip2.CBZip2InputStream
import org.apache.tools.bzip2.CBZip2OutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.LZMAOutputStream
import org.tukaani.xz.XZIOException
import org.zeroturnaround.zip.ZipUtil

import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


/**
 * Safe file utility methods
 *
 * needed also to avoid problems with File(String/URI) ambiguous constructor
 *
 */
@Slf4j
@CompileStatic
class Futils {

    public static final int ZIP_BEST_COMPRESSION = 9

    private static final int BUFFER_SIZE = 4 * 1024 * 1024

    static String normalize(String path) {
        if (path==null) return null

        new File(path).toPath().normalize()
    }

    static String absPath(String path) {
        if (path==null) return null

        normalize(new File(path).absolutePath)
    }

    static String absSafePath(String path) {
        if (path==null) return null

        safe(absPath(path))
    }

    static String safe(String path) {
        if (path==null) return null

        path.replace("\\","/")
    }

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

    static String removeExtension(String path) {
        path?.replaceFirst(~/\.[^\.]+$/, '')
    }

    /**
     * reads text file from classpath
     */
    static String readResource(String path) {
        return Futils.class.getResourceAsStream(path).newReader().getText()
    }

    /**
     * Opens and decompresses file (if it has gz or bz2 extension)
     */
    static InputStream inputStream(File file) {
        String fname = file.getName()

        InputStream is = bufferedInputStream(file)

        if (fname.endsWith(".gz")) {
            is = new GZIPInputStream(is)
        } else if (fname.endsWith(".bz2")) {
            is = new CBZip2InputStream(is)
        } else if (fname.endsWith(".lzma")) {
            is = new LZMAInputStream(is)
        } else if (fname.endsWith(".zstd")) {
            is = new ZstdCompressorInputStream(is)
        }

        return is
    }

    /**
     * Opens and decompresses file (if it has gzip extension)
     */
    static InputStream inputStream(String fname) throws IOException {
        return inputStream(new File(fname))
    }

    static BufferedInputStream bufferedInputStream(File file) {
        return new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)
    }

    static BufferedInputStream bufferedInputStream(String fname) {
        return new BufferedInputStream(new FileInputStream(fname), BUFFER_SIZE)
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
    static PrintWriter getGzipWriter(String fname) {
        File file = new File(fname)
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()

        GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(file), BUFFER_SIZE)

        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(gos), BUFFER_SIZE))
    }


    static GZIPOutputStream getGzipOutputStream(String fname, int compressionLevel = Deflater.DEFAULT_COMPRESSION) {
        return new GZIPOutputStream(bufferedOutputStream(fname), BUFFER_SIZE) {
            {
                this.@def.level = compressionLevel
            }
        }
    }


    static CBZip2OutputStream getBzip2OutputStream(String fname, int blockSize = CBZip2OutputStream.MAX_BLOCKSIZE) {
        return new CBZip2OutputStream(bufferedOutputStream(fname), blockSize)
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


    static void serializeToGzip(String fname, Serializable object, int level = Deflater.DEFAULT_COMPRESSION) {
        serializeTo(fname, object, getGzipOutputStream(fname, level))
    }

    static void serializeToZstd(String fname, Serializable object, int level = 6) {
        serializeTo(fname, object, getZstdOutputStream(fname, level))
    }

    static void serializeToLzma(String fname, Serializable object, int level = LZMA2Options.PRESET_DEFAULT) {
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

    static void compressToLzma(String fname, int compresLevel = LZMA2Options.PRESET_DEFAULT) {
        ZstdCompressorOutputStream
    }

    static void serializeToBzip2(String fname, Serializable object) {
        serializeTo(fname, object, getBzip2OutputStream(fname))
    }

    static void serializeToFile(String fname, Serializable object) {
        serializeTo(fname, object, bufferedOutputStream(fname))
    }

    private static void serializeTo(String fname, Serializable object, OutputStream stream) {
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
        T res = (T) ois.readObject()
        ois.close()
        return res
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

    static List<File> listFiles(String dir, String ext) {
        return new File(dir).listFiles().findAll { it.name ==~ /.*\.$ext/ }.toList()
    }

    static List<File> listFiles(String dir) {
        return new File(dir).listFiles().toList()
    }

    static boolean isDirEmpty(String dir) {
        listFiles(dir).isEmpty()    
    }


    static boolean exists(String name) {
        if (name==null) return false
        return new File((String)name).exists()
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

    static List<String> readLines(String fname) {
        File file = new File(fname)
        return file.readLines()
    }
}
