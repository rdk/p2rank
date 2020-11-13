package cz.siret.prank.program.routines

import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.writeFile

@Slf4j
@CompileStatic
class Routine implements Parametrized, Writable  {

    String outdir

    Routine(String outdir) {
        this.outdir = outdir
    }

    void setOutdir(String outdir) {
        if (outdir==null) {
            throw new PrankException('No outdir specified!')
        }
        this.outdir = outdir
    }

    private Routine() {}

    void logTime(String timeMsg) {
        write timeMsg
        Futils.append "$outdir/time.log", timeMsg + "\n"
    }

    void writeParams(String outdir) {
        String v = "version: " + Main.version + "\n"

        String gitHead = getGitHeadId()
        if (gitHead != null) {
            v += "git head: " + getGitHeadId() + "\n"
        }

        writeFile("$outdir/params.txt", v + params.toString())
    }

    String getGitHeadId() {
        String res = null
        try {
            res = 'git rev-parse --short HEAD'.execute().text
        } catch (Exception e) {
            log.trace 'failed to get git commit version', e
        }
        return res
    }

}
