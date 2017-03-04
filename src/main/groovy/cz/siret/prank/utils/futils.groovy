package cz.siret.prank.utils

import com.google.common.io.Files
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.zeroturnaround.zip.ZipUtil


/**
 * Safe file utility methods
 *
 * needed also to avoid problems with File(String/URI) ambiguous constructor
 *
 */
@Slf4j
@CompileStatic
class futils {

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


    static String shortName(String path) {
        if (path==null) return null

        new File(path).name
    }

    static String removeExtention(String path) {
        path?.replaceFirst(~/\.[^\.]+$/, '')
    }

    /**
     * reads text file from classpath
     */
    static String readResource(String path) {
        return futils.class.getResourceAsStream(path).newReader().getText()
    }

    /**
     * loads properties from classpath
     */
    static Properties loadProperties(String path) {
        Properties res = new Properties()
        res.load(futils.class.getResourceAsStream(path))
        return res
    }

    static PrintWriter overwrite(String fname) {
        File file = new File(fname)
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()

        return new PrintWriter(new BufferedWriter(new FileWriter(file), 10000))
    }

    static void overwrite(String fname, Object text) {

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

    static List<File> listFiles(String dir, String ext) {
        return new File(dir).listFiles().findAll { it.name ==~ /.*\.$ext/ }.toList()
    }

    static boolean exists(String name) {
        if (name==null) return false
        return new File((String)name).exists()
    }

    /**
     * delete file or directory recursively if exists
     */
    static boolean delete(String fname) {
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

    static void mkdirs(String s) {
        if (s==null) return

        new File(s).mkdirs()
    }

    static void copy(String from, String to) {
        Files.copy(new File(from), new File(to))
    }


    static void zipAndDelete(String fileOrDirectory) {
        ZipUtil.pack(new File(fileOrDirectory), new File(fileOrDirectory + '.zip'))
        delete(fileOrDirectory)
    }

}
