package cz.siret.prank.features.implementation.csv

import cz.siret.prank.domain.Protein
import cz.siret.prank.program.params.Params
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 *
 */
class CsvFileFeatureTest {

    static String dir = 'src/test/resources/data/csv_feature'

    @Test
    void testCsvFeatureLoading() {
        Params.inst.feat_csv_ignore_missing = false
        List<String> columns = ["pdbekb_conservation"]

        CsvFileFeatureValues feature = new CsvFileFeatureValues(false)
        feature.load(["$dir/pdbekb_conservation"], "1bviC.pdb", columns)

        Protein p1bviC = Protein.load("$dir/1bviC.pdb")

        Atom atom = p1bviC.proteinAtoms.withIndex().getByID(1664) // residue: C_16
        assertEquals(1, feature.getValues(atom, columns)[0] as int)

        for (Atom surfAtom : p1bviC.exposedAtoms) {
            assertEquals(1, feature.getValues(surfAtom, columns).size())  // fails if any value is missing
        }
    }

}