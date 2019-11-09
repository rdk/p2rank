package cz.siret.prank.domain.labeling

import com.google.common.collect.Maps
import cz.siret.prank.domain.Residue
import groovy.transform.CompileStatic

import javax.annotation.Nullable

/**
 * Holds particular assignment of labels to a set of residues.
 */
@CompileStatic
class ResidueLabeling<L>  {

    private List<LabeledResidue<L>> labeledResidues
    private Map<Residue.Key, LabeledResidue<L>> labeledMap

    ResidueLabeling(List<LabeledResidue<L>> labeledResidues) {
        this.labeledResidues = labeledResidues
        this.labeledMap = Maps.uniqueIndex(labeledResidues, { it.residue.key })
    }

    ResidueLabeling(int initSize = 128) {
        this.labeledResidues = new ArrayList<>(initSize)
        this.labeledMap = new HashMap<>(initSize)
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

    List<LabeledResidue<L>> getLabeledResidues() {
        return labeledResidues
    }

    int getSize() {
        labeledResidues.size()
    }
    
}
