package cz.siret.prank.program.routines

import cz.siret.prank.program.Main
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import cz.siret.prank.utils.futils


class Routine implements Parametrized, Writable  {

    String outdir

    void logTime(String timeMsg) {
        write timeMsg
        futils.append "$outdir/time.log", timeMsg + "\n"
    }

    void writeParams(String outdir) {
        String v = "version: " + Main.version + "\n"
        futils.overwrite("$outdir/params.txt", v + params.toString())
    }
    
}
