package cz.siret.prank

import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic
import org.junit.Assert
import org.junit.Test

import java.nio.file.Path
import java.nio.file.Paths

import static cz.siret.prank.utils.PathUtils.path
import static cz.siret.prank.utils.PathUtils.path
import static cz.siret.prank.utils.PathUtils.path
import static cz.siret.prank.utils.PathUtils.path
import static cz.siret.prank.utils.PathUtils.path
import static org.junit.Assert.assertEquals

/**
 *
 */
@CompileStatic
class StructureParsingTest {
    Path installDir = Paths.get("distro").toAbsolutePath()
    Path dataDir = path installDir, "test_data"

    Path pdb_1fbl = path dataDir, "1fbl.pdb.gz"
    Path cif_1fbl = path dataDir, "1fbl.cif"

    Path pdb_2W83 = path dataDir, "2W83.pdb"
    Path cif_2W83 = path dataDir, "2W83.cif"

    @Test
    void pdbVsCifEquality() throws Exception {
        doTestPdbVsCifEquality(pdb_1fbl, cif_1fbl)
        doTestPdbVsCifEquality(pdb_2W83, cif_2W83)
    }

    void doTestPdbVsCifEquality(Path pdbFile, Path cifFile) {
        Protein pdb = Protein.load(pdbFile.toString())
        Protein cif = Protein.load(cifFile.toString())

        assertEquals pdb.allAtoms.count, cif.allAtoms.count
        assertEquals pdb.ligandCount, cif.ligandCount
        assertEquals pdb.allLigandAtoms.count, cif.allLigandAtoms.count
        assertEquals pdb.residues.count, cif.residues.count
        assertEquals pdb.residueChains.size(), cif.residueChains.size()
    }

}
