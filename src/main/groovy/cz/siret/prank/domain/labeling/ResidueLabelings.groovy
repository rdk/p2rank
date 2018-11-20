package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.transformation.ScoreTransformer
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params

/**
 * 
 */
class ResidueLabelings implements Parametrized {

    Residues residues
    List<NamedLabeling> labelings = new ArrayList<>()

    ResidueLabelings(Residues residues) {
        this.residues = residues
    }


    static ResidueLabelings calculate(Protein protein, Model model, Atoms sasPoints, List<LabeledPoint> labeledPoints, ProcessedItemContext context) {

        ModelBasedResidueLabeler labeler = new ModelBasedResidueLabeler(model, sasPoints, context)
        labeler.calculateLabeling(protein.residues, labeledPoints, protein)

        ResidueLabeling<Double> lab_score = labeler.doubleLabeling

        // score transformers
        // TODO use different parametrizations for transformers, train them
        ScoreTransformer zscoreTpTransformer = ScoreTransformer.load(Params.inst.zscoretp_transformer)
        ScoreTransformer probaTpTransformer = ScoreTransformer.load(Params.inst.probatp_transformer)

        ResidueLabeling<Double> lab_zscore = transformLabeling(lab_score, zscoreTpTransformer)
        ResidueLabeling<Double> lab_probability = transformLabeling(lab_score, probaTpTransformer)


        ResidueLabelings res = new ResidueLabelings(protein.residues)
        res.labelings.add(new NamedLabeling("score", lab_score))
        res.labelings.add(new NamedLabeling("zscore", lab_zscore))
        res.labelings.add(new NamedLabeling("probability", lab_probability))
        // decoys
        res.labelings.add(new NamedLabeling("pocket", lab_score))
        res.labelings.add(new NamedLabeling("pocket_score", lab_score))
        res.labelings.add(new NamedLabeling("pocket_zscore", lab_score))
        res.labelings.add(new NamedLabeling("pocket_probability", lab_score))
        return res
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
        s << "residue, " << (labelings*.name).join(", ") << "\n"
        for (Residue r : residues) {
            s << r.key << ", " << labelings.collect { fmt it.labeling.get(r) }.join(", ") << "\n"
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
