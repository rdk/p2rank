package cz.siret.prank.program.routines.predict.external

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.ProcessRunner
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
@CompileStatic
class FpocketRunner implements Parametrized {

    String fpocketCommand = params.fpocket_command

    /**
     * fpocket output directory name for a given structure file name
     *
     * Examples:
     *  - 1abc.pdb -> 1abc_out
     */
    static String fpocketOutDir(String structureFileName) {
        String baseName = Futils.baseName(structureFileName)

        return "${baseName}_out"
    }

    /**
     * Prepare structure file for running Fpocket.
     * Copy to tmpDir if necessary.
     * Unzip if necessary.
     *
     * For now always creates file in tmpDir.
     * In the future it may not be necessary (if 'fpocket -o' is implemented)
     *
     * @param structFile
     * @param tmpDir
     * @return
     */
    private String prepareStructureFile(String structFile, String tmpDir) {
        String structFname = Futils.shortName(structFile)
        String tmpStructFile

        if (Futils.isCompressed(structFile)) {
            tmpStructFile = "$tmpDir/${Futils.removeLastExtension(structFname)}"
            Futils.decompressFile(structFile, tmpStructFile)
        } else {
            // just copy
            tmpStructFile = "$tmpDir/$structFname"
            Futils.copy(structFile, tmpStructFile)
        }

        return Futils.absPath(tmpStructFile)
    }

    /**
     * Run Fpocket on a given structure file.
     * @param structFile Structure file in PDB format or CIF format
     * @param outDir output directory (fpocket output directory will be moved to _be_ outDir, not _in_ outDir)
     * @param tmpDir top level temp directory, a subdirectory will be created and deleted for each run
     * @return true if the run was successful
     */
    boolean execute(String structFile, String outDir, String tmpBaseDir) {
        structFile = Futils.absPath(structFile)

        if (!Futils.exists(structFile)) {
            throw new RuntimeException("Structure file does not exist: $structFile")
        }

        String structFname = Futils.shortName(structFile)
        String tmpDir = Futils.mkdirs("$tmpBaseDir/fpocket_${structFname}_tmp")
        String logFile = "$tmpDir/fpocket.log"
        String tmpStructFile = prepareStructureFile(structFile, tmpDir)
        String tmpStructFname = Futils.shortName(tmpStructFile)
        //boolean tmpStructFileCreated = !tmpStructFile.equals(structFile)

        String command = "$fpocketCommand -f $tmpStructFname"

        log.info("Running fpocket with command: [$command] in directory: [$tmpDir]")

        ProcessRunner fpocketProcess = ProcessRunner.process(command, tmpDir)
                .redirectErrorStream()
                .redirectOutput(logFile)
        int exitcode = fpocketProcess.executeAndWait()

        if (exitcode != 0) {
            throw new RuntimeException("Fpocket failed with exit code $exitcode. See fpocket output in [$logFile].")
        }
        String fpocketOutDir = "$tmpDir/${fpocketOutDir(structFname)}"
        log.info("Fpocket finished successfully on structure file: [$structFile] (output in [$fpocketOutDir])")

        // move fpocket output dir fromm tmpDir to outDir


        if (!Futils.exists(fpocketOutDir)) {
            throw new RuntimeException("Expected fpocket output directory doesn't exist: $fpocketOutDir (perhaps naming convention in fpocket has changed?)")
        }
        if (Futils.exists(outDir)) {
            log.info("Target output directory already exists, deleting it: $outDir")
            Futils.delete(outDir)
        }
        log.info("Moving fpocket output directory from $fpocketOutDir to: $outDir")
        Futils.moveDir(fpocketOutDir, outDir)

        // delete tmpDir (with log file)
        log.debug("Deleting pocket temp directory: $tmpDir")
        Futils.delete(tmpDir)

        return true
    }

    public static runFpocket(String structFile, String outDir, String tmpBaseDir) {
        new FpocketRunner().execute(structFile, outDir, tmpBaseDir)
    }

}
