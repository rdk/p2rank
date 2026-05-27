package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.*

/**
 * GetCleft style output generator
 */
@Slf4j
@CompileStatic
class GetcleftOutputCalculator implements Parametrized, Writable {



    void generateGetcleftSasPdbFiles(Prediction prediction, String outdir) {

        log.info "Generating GetCleft PDB files"

        int pdbSerial = 1000

        String subdirName = prediction.protein.shortFileName + "_getcleft"
        String out = "$outdir/$subdirName"
        String proteinBaseName = Futils.baseName(prediction.protein.shortFileName)

        Futils.mkdirs out

        Chain virtChain = new ChainImpl()
        virtChain.setName("Z")

        for (Pocket pocket : prediction.outputPockets) {
            Atoms cleftPoints = new Atoms(pocket.sasPoints.count)

            HetatomImpl virtualPocketGroup = new HetatomImpl() ///  "SPH"
            virtualPocketGroup.setPDBName("SPH")
            virtualPocketGroup.setId(pocket.rank)
            virtualPocketGroup.setResidueNumber(new ResidueNumber("Z", 1, null))
            virtChain.addGroup(virtualPocketGroup)


            for (Atom sasPoint : pocket.sasPoints) {

                Atom closestProtAtom = pocket.surfaceAtoms.findNearest(sasPoint)
                double radius = Struct.dist(sasPoint, closestProtAtom) - closestProtAtom.getElement().getVDWRadius();


                AtomImpl point = new AtomImpl()
                point.setOccupancy(1f)
                point.setTempFactor((float) radius)
                point.setAltLoc(null)
                point.setCharge(0 as short)
                point.setX(sasPoint.getX())
                point.setY(sasPoint.getY())
                point.setZ(sasPoint.getZ())
                point.setPDBserial(pdbSerial++)
                point.setName("C")
                point.setElement(Element.C)

                cleftPoints.add(point)
                virtualPocketGroup.addAtom(point)
            }


            String fname = "${proteinBaseName}_sph_${pocket.rank}.pdb"
            Futils.writeFile "$out/$fname", atomsToPdb(cleftPoints)
        }

        log.info "GetCleft PDB files saved to directory [$out]"
    }


    static StringBuilder atomsToPdb(Atoms atoms) {
        StringBuilder sb = new StringBuilder()
        for (Atom a : atoms) {
            sb.append(a.toPDB().replace("HETATM", "ATOM  "))
        }
        return sb
    }

}
