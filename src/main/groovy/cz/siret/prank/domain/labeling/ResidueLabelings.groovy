package cz.siret.prank.domain.labeling

import com.beust.jcommander.internal.Console
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.pockets.PrankPocket
import cz.siret.prank.prediction.transformation.ScoreTransformer
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.ConsoleWriter

/**
 * 
 */
class ResidueLabelings implements Parametrized {

    Residues residues
    List<NamedLabeling> labelings = new ArrayList<>()

    ResidueLabelings(Residues residues) {
        this.residues = residues
    }


    static ResidueLabelings calculate(Prediction prediction, Model model, Atoms sasPoints, List<LabeledPoint> labeledPoints, ProcessedItemContext context) {
        Protein protein = prediction.protein
        Residues residues = protein.residues

        ModelBasedResidueLabeler labeler = new ModelBasedResidueLabeler(model, sasPoints, context)
        labeler.calculateLabeling(residues, labeledPoints, protein)

        ResidueLabeling<Double> lab_score = labeler.doubleLabeling

        // TODO temporaryFix
        lab_score.labeledResidues.each {
            if (it.label == Double.NaN) {
                it.label = 0d
            }
        }


        // score transformers
        // TODO use different parametrizations for transformers, train them
        ScoreTransformer zscoreTpTransformer = ScoreTransformer.load(Params.inst.zscoretp_transformer)
        ScoreTransformer probaTpTransformer = ScoreTransformer.load(Params.inst.probatp_transformer)

        ResidueLabeling<Double> lab_zscore = transformLabeling(lab_score, zscoreTpTransformer)
        ResidueLabeling<Double> lab_probability = transformLabeling(lab_score, probaTpTransformer)

        ResidueLabeling<Integer> lap_pocketref = poctetReferenceLabeling(prediction, residues)

        ResidueLabelings res = new ResidueLabelings(residues)
        res.labelings.add(new NamedLabeling("score", lab_score))
        res.labelings.add(new NamedLabeling("zscore", lab_zscore))
        res.labelings.add(new NamedLabeling("probability", lab_probability))
        // decoys
        res.labelings.add(new NamedLabeling("pocket", lap_pocketref))   
//        res.labelings.add(new NamedLabeling("pocket_score", lab_score))
//        res.labelings.add(new NamedLabeling("pocket_zscore", lab_score))
//        res.labelings.add(new NamedLabeling("pocket_probability", lab_score))
        return res
    }


    static ResidueLabeling<Integer> poctetReferenceLabeling(Prediction prediction, Residues residues) {
        Map<Residue.Key, Integer> labels = new HashMap<>()

        residues.each { labels.put(it.key, 0) }

        for (Pocket p  : prediction.pockets.reverse()) {    // pockets on top of the list have priority in labeling residues
            PrankPocket pp = (PrankPocket) p
            pp.residues.each {
                labels.put(it.key, pp.rank) 
            }
        }

        ResidueLabeling<Integer> res = new ResidueLabeling<>(residues.count)
        residues.each {
            res.add(it, labels.get(it.key))
        }
        res
    }

    static ResidueLabeling<Double> transformLabeling(ResidueLabeling<Double> orig, ScoreTransformer transformer) {
        ResidueLabeling<Double> res = new ResidueLabeling<>(orig.size)
        for (LabeledResidue<Double> lr : orig.labeledResidues) {
            res.add(lr.residue, transformer.transformScore(lr.label))
        }
        return res
    }

    String toCSV() {
        StringBuilder s = new StringBuilder()
        s << "chain, residue_num, residue_name, " << (labelings*.name).join(", ") << "\n"
        for (Residue r : residues) {
            s << r.chainId << ", " << r.residueNumber.seqNum << ", " << r.code << ","
            s << labelings.collect { fmt it.labeling.get(r).label }.join(", ") << "\n"
        }
        s.toString()
    }

    // TODO refactor and centralize CSV formatting
    static String fmt(Object x) {
        if (x==null) return ""

        if (x instanceof Double) {
            return sprintf("%8.4f", x)
        } else {
            return x.toString()
        }
    }


    static class NamedLabeling {
        String name
        ResidueLabeling labeling

        NamedLabeling(String name, ResidueLabeling labeling) {
            this.name = name
            this.labeling = labeling
        }
    }

}
