package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

/**
 * Loader for predictions produced by PUResNet server
 */
@Slf4j
@CompileStatic
class PUResNetLoader extends PredictionLoader implements Parametrized {

    /**
     *
     * @param predictionOutputFile  actually a pocket output dir
     * @param queryProtein
     * @return
     */
    @Override
    Prediction loadPrediction(String predictionOutputFile, @Nullable Protein queryProtein) {
        return new Prediction(queryProtein, loadPockets(predictionOutputFile, queryProtein))
    }


    List<PUResNetPocket> loadPockets(String pocketDir, Protein liganatedProtein) {

        List<File> pocketFiles = Futils.listFiles(pocketDir, {
            it.name.endsWith('pkt.pdb') && it.name != 'without_clus_pkt.pdb'
        })

        log.info('Found {} pocket files: {}', pocketFiles.size(), pocketFiles*.name)

        List<PUResNetPocket> res = new ArrayList<>()
        int i = 1
        for (File pocketFile : pocketFiles) {
            String absName = pocketFile.absolutePath
            log.info('Loading pocket from file {}', absName)

            Protein pocketStructure = Protein.load(absName)
            Atoms pocketAtoms = pocketStructure.allAtoms

            PUResNetPocket pocket = new PUResNetPocket(pocketAtoms)
            pocket.rank = i++
            pocket.name = Sutils.removeSuffix(Futils.baseName(pocketFile.name), '.pdb')
            pocket.surfaceAtoms = pocketAtoms  // not all of them are necessarily on the surface but it s

            res.add(pocket)
        }

        return res
    }



    static class PUResNetPocket extends Pocket {
        Atoms pocketAtoms  // as defined by PUResNet
        PUResNetPocket(Atoms pocketAtoms) {
            this.pocketAtoms = pocketAtoms
        }
    }

}
