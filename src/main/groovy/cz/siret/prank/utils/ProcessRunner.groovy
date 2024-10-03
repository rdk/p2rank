package cz.siret.prank.utils

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Encapsulates system (command line) process
 */
@Slf4j
@CompileStatic
class ProcessRunner {

    static Splitter CMD_SPLITTER = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings()

    String command
    String dir

    int exitcode = -1

    Process process
    ProcessBuilder processBuilder

    ProcessRunner(String command, String dir) {
        this.command = command
        this.dir = dir
        if (this.dir == null) {
            this.dir = System.getProperty('java.io.tmpdir')
        }

        List<String> cmdList = CMD_SPLITTER.splitToList(command)

        processBuilder = new ProcessBuilder(cmdList)
        processBuilder.directory(new File(this.dir))
    }

    static ProcessRunner process(String cmd) {
        process(cmd, null)
    }

    static ProcessRunner process(String cmd, String dir) {
        new ProcessRunner(cmd, dir)
    }

    ProcessRunner execute() {
        Futils.mkdirs(dir)

        process = processBuilder.start()

        return this
    }

    /**
     * Redirects error stream to output stream
     */
    ProcessRunner redirectErrorStream() {
        processBuilder.redirectErrorStream()
        return this
    }

    ProcessRunner redirectOutput(String file) {
        processBuilder.redirectOutput(new File(file))
        return this
    }

    ProcessRunner inheritIO() {
        processBuilder.inheritIO()
        return this
    }


    int waitFor() {
        process.waitFor()

        exitcode = process.exitValue()

        return exitcode
    }

    int executeAndWait() {
        execute()
        return waitFor()
    }

    void kill() {
        try {
            log.info "killing process $process"
            process.destroy()
        } catch (Exception e) {
            log.warn("failed to kill process", e)
            try {
                log.info("killing process forcibly", e)
                process.destroyForcibly().waitFor()
            } catch (Exception e2) {
                log.warn("failed to kill process forcibly", e2)
            }
        }
    }

}
