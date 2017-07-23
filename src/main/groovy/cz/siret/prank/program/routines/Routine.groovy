package cz.siret.prank.program.routines

import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.Futils.writeFile

@CompileStatic
class Routine implements Parametrized, Writable  {

    String outdir

    Routine(String outdir) {
        this.outdir = outdir
    }

    void setOutdir(String outdir) {
        if (outdir==null) {
            throw new PrankException('fuck')
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
        writeFile("$outdir/variables.txt", v + params.toString())
    }

}
