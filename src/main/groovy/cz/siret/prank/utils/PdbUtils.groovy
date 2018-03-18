package cz.siret.prank.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.Structure
import org.biojava.nbio.structure.io.FileParsingParameters
import org.biojava.nbio.structure.io.PDBFileParser
import org.biojava.nbio.structure.io.PDBFileReader
import org.biojava.nbio.structure.io.mmcif.ChemCompGroupFactory
import org.biojava.nbio.structure.io.mmcif.ReducedChemCompProvider


/**
 * BioJava PDB utility methods.
 */
@Slf4j
@CompileStatic
class PdbUtils {

    private static final int BUFFER_SIZE = 5*1024*1024;

    static final FileParsingParameters PARSING_PARAMS = new FileParsingParameters();
    static {
        ChemCompGroupFactory.setChemCompProvider(new ReducedChemCompProvider()); // does not download any chem comp definitions (by default BioJava does)

        PARSING_PARAMS.setAlignSeqRes(false);  // should the ATOM and SEQRES residues be aligned when creating the internal data model?
        PARSING_PARAMS.setParseSecStruc(false);  // should secondary structure getByID parsed from the file

        PARSING_PARAMS.setCreateAtomBonds(false)
        PARSING_PARAMS.setCreateAtomCharges(false)
        PARSING_PARAMS.setParseBioAssembly(false)

        //PARSING_PARAMS.setLoadChemCompInfo(Params.inst.biojava_load_chem_info); // info about modified amino acid residues, not available in BioJava4
    }

    static Structure loadFromFile(String file) {

        log.info "loading file [$file]"

        if (file==null) throw new IllegalArgumentException("file name not provided")

        Structure struct
        if (file.endsWith(".pdb")) {
            // load with buffer
            PDBFileParser pdbpars = new PDBFileParser();
            pdbpars.setFileParsingParameters(PARSING_PARAMS);
            InputStream inStream = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)
            struct = pdbpars.parsePDBFile(inStream)
        } else {
            // for tar.gz files
            PDBFileReader pdbReader = new PDBFileReader()
            pdbReader.setFileParsingParameters(PARSING_PARAMS)
            struct = pdbReader.getStructure(file)
        }

        return struct
    }

    static Structure loadFromString(String pdbText)  {
        PDBFileParser pdbpars = new PDBFileParser();
        pdbpars.setFileParsingParameters(PARSING_PARAMS);

        BufferedReader br = new BufferedReader(new StringReader(pdbText))

        Structure struc = pdbpars.parsePDBFile(br)
        return struc
    }

    static String correctResidueCode(String residueCode) {
        //MSE is only found as a molecular replacement for MET
        //'non-standard', genetically encoded
        if (residueCode=="MSE")
            residueCode = "MET"
        else if (residueCode=="MEN")  // N-METHYL ASPARAGINE
            residueCode = "ASP"

        return residueCode
    }

    /**
     * @return three letter residue code (e.g. "ASP")
     */
    static String getAtomResidueCode(Atom a) {
        a.group.PDBName
    }
    
    /**
     * @return three letter residue code (e.g. "ASP"), some corrections are applied
     */
    static String getCorrectedAtomResidueCode(Atom a) {
        //assert a.group instanceof AminoAcid

        correctResidueCode(getAtomResidueCode(a))
    }

    /**
     * @return three letter residue code (e.g. "ASP")
     */
    static String getResidueCode(Group group) {
        if (group==null || group.size()==0) return null;

        return getAtomResidueCode(group.getAtom(0))
    }

    /**
     * @return three letter residue code (e.g. "ASP"), some corrections are applied
     */
    static String getCorrectedResidueCode(Group group) {
        return correctResidueCode(getResidueCode(group))
    }

    /**
     *
     * @return  THR -> Thr
     */
    static String normAAcode(String aa) {
        if (aa.isEmpty()) {
            return aa;
        }
        if (aa.length()!=3) {
            log.warn " Suspicious AA code: " + aa
        }

        String a = aa.substring(0, 1).toUpperCase()
        String b = aa.substring(1, aa.length()).toLowerCase()
        return a + b
    }

    static boolean trySetElement(Atom a, String code) {
        Element ele
        try {
            ele = Element.valueOf(code)
        } catch (IllegalArgumentException e) {
            // nothing, biojava just doesnt know the element
        }

        if (ele!=null) {
            //log.warn "correcting atom {} to element {}", a.name, ele
            a.setElement(ele)
        }
    }

    static void correctBioJavaElement(Atom a) {
        if (a.element.equals(Element.R)) {   //  R=unknown

            if (a.name.length()>1) {
                if (trySetElement(a, a.name.substring(0,2)) ) {
                    return
                }
            }
            if (a.name.length()>0) {
                trySetElement(a, a.name.substring(0,1))
            }
        }
    }

}
