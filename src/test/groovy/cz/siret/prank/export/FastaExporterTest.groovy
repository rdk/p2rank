package cz.siret.prank.export

import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 *
 *
 */
@CompileStatic
class FastaExporterTest {

    static String data_dir = "distro/test_data"
    static Protein protein_2W83
    static Protein protein_1fbl
    //static Protein protein_1fbl_cif


    FastaExporter fastaExporter = new FastaExporter()

    @BeforeAll
    static void setUp() throws Exception {
        protein_2W83 = Protein.load("$data_dir/2W83.pdb")
        protein_1fbl = Protein.load("$data_dir/1fbl.pdb.gz")
        protein_1fbl = Protein.load("$data_dir/1fbl.cif")
    }

    @Test
    void getFastaChainRaw() {
        String exported_2W83_A = fastaExporter.getFastaChainRaw(protein_2W83.getResidueChain("A"))
        String answer_2W83_A = "MEMRILMLGLDAAGKTTILYKLKLGQSVTTIPTVGFNVETVTYKNVKFNVWDVGGLDKIRPLWRHYYTGTQGLIFVVDCADRDRIDEARQELHRIINDREMRDAIILIFANKQDLPDAMKPHEIQEKLGLTRIRDRNWYVQPSCATSGDGLYEGLTWLTSN"
        assertEquals answer_2W83_A, exported_2W83_A

        String exported_1fbl_A = fastaExporter.getFastaChainRaw(protein_1fbl.getResidueChain("A"))
        String answer_1fbl_A = "FVLTPGNPRWENTHLTYRIENYTPDLSREDVDRAIEKAFQLWSNVSPLTFTKVSEGQADIMISFVRGDHRDNSPFDGPGGNLAHAFQPGPGIGGDAHFDEDERWTKNFRDYNLYRVAAHELGHSLGLSHSTDIGALMYPNYIYTGDVQLSQDDIDGIQAIYGPSENPVQPSGPQTPQVCDSKLTFDAITTLRGELMFFKDRFYMRTNSFYPEVELNFISVFWPQVPNGLQAAYEIADRDEVRFFKGNKYWAVRGQDVLYGYPKDIHRSFGFPSTVKNIDAAVFEEDTGKTYFFVAHECWRYDEYKQSMDTGYPKMIAEEFPGIGNKVDAVFQKDGFLYFFHGTRQYQFDFKTKRILTLQKANSWFNC"
        assertEquals answer_1fbl_A, exported_1fbl_A

        String exported_1fbl_cif_A = fastaExporter.getFastaChainRaw(protein_1fbl.getResidueChain("A"))
        assertEquals answer_1fbl_A, exported_1fbl_cif_A
    }

    @Test
    void getFastaChainMasked() {
        String exported_2W83_A = fastaExporter.getFastaChainMasked(protein_2W83.getResidueChain("A"))
        String answer_2W83_A = "MEMRILMLGLDAAGKTTILYKLKLGQSVTTIPTVGFNVETVTYKNVKFNVWDVGGLDKIRPLWRHYYTGTQGLIFVVDCADRDRIDEARQELHRIINDREMRDAIILIFANKQDLPDAMKPHEIQEKLGLTRIRDRNWYVQPSCATSGDGLYEGLTWLTSN"
        assertEquals answer_2W83_A, exported_2W83_A

        String exported_1fbl_A = fastaExporter.getFastaChainMasked(protein_1fbl.getResidueChain("A"))
        String answer_1fbl_A = "FVLTPGNPRWENTHLTYRIENYTPDLSREDVDRAIEKAFQLWSNVSPLTFTKVSEGQADIMISFVRGDHRDNSPFDGPGGNLAHAFQPGPGIGGDAHFDEDERWTKNFRDYNLYRVAAHELGHSLGLSHSTDIGALMYPNYIYTGDVQLSQDDIDGIQAIYGPSENPVQPSGPQTPQVCDSKLTFDAITTLRGELMFFKDRFYMRTNSFYPEVELNFISVFWPQVPNGLQAAYEIADRDEVRFFKGNKYWAVRGQDVLYGYPKDIHRSFGFPSTVKNIDAAVFEEDTGKTYFFVAHECWRYDEYKQSMDTGYPKMIAEEFPGIGNKVDAVFQKDGFLYFFHGTRQYQFDFKTKRILTLQKANSWFNC"
        assertEquals answer_1fbl_A, exported_1fbl_A

        String exported_1fbl_cif_A = fastaExporter.getFastaChainMasked(protein_1fbl.getResidueChain("A"))
        assertEquals answer_1fbl_A, exported_1fbl_cif_A

        // TODO add examples where residue are actually masked by X
    }
    
}