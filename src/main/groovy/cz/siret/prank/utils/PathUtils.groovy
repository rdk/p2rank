package cz.siret.prank.utils

import groovy.transform.CompileStatic

import java.nio.file.Path
import java.nio.file.Paths

/**
 *
 */
@CompileStatic
class PathUtils {

    static Path path(Path path, String... subpaths) {
        Paths.get(path.toString(), subpaths)
    }

}
