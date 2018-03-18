package cz.siret.prank.domain.labeling

import com.google.common.collect.Maps
import cz.siret.prank.domain.Residue
import groovy.transform.CompileStatic

import javax.annotation.Nullable

/**
 * Holds partiticular assignment of labels to a set of residues.
 */
@CompileStatic
class ResidueLabeling<L>  {

    private List<LabeledResidue> labeledResidues
    private Map<Residue.Key, LabeledResidue<L>> labeledMap

    ResidueLabeling(List<LabeledResidue> labeledResidues) {
        this.labeledResidues = labeledResidues
        this.labeledMap = Maps.uniqueIndex(labeledResidues, { it.residue.key })
    }

    ResidueLabeling() {
        this.labeledResidues = new ArrayList<>()
        this.labeledMap = new HashMap<>()
    }
    
    void add(Residue residue, L label) {
        LabeledResidue lres = new LabeledResidue(residue, label)
        labeledResidues.add(lres)
        labeledMap.put(residue.key, lres)
    }

    @Nullable
    L getLabel(Residue residue) {
        return get(residue)?.label
    }

    @Nullable
    LabeledResidue<L> get(Residue residue) {
        return labeledMap.get(residue.key)
    }

    List<LabeledResidue> getLabeledResidues() {
        return labeledResidues
    }
    
}
