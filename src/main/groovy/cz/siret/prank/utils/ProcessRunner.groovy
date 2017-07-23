package cz.siret.prank.utils

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
@CompileStatic
class ProcessRunner {

    String command
    String dir

    int exitcode = -1

    Process process
    ProcessBuilder processBuilder

    ProcessRunner(String command, String dir) {
        this.command = command
        this.dir = dir

        List<String> cmdList = Splitter.on(CharMatcher.whitespace()).splitToList(command)

        processBuilder = new ProcessBuilder(cmdList)
        processBuilder.directory(new File(dir))
    }

    ProcessRunner execute() {
        Futils.mkdirs(dir)

        process = processBuilder.start()

        return this
    }

    ProcessRunner redirectErrorStream() {
        processBuilder.redirectErrorStream()
        return this
    }

    ProcessRunner redirectOutput(File file) {
        processBuilder.redirectOutput(file)
        return this
    }


    int waitFor() {
        process.waitFor()

        exitcode = process.exitValue()

        return exitcode
    }

    void kill() {
        try {
            log.info "killing process $process"
            process.destroy()
        } catch (Exception e) {
            log.warn("failed to kill proecss", e)
            try {
                log.info("killing process forciblly", e)
                process.destroyForcibly().waitFor()
            } catch (Exception e2) {
                log.warn("failed to kill proecss forcibly", e)
            }
        }
    }

}
