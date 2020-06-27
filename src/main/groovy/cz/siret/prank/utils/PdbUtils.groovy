package cz.siret.prank.utils


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.*
import org.biojava.nbio.structure.io.FileParsingParameters
import org.biojava.nbio.structure.io.PDBFileParser
import org.biojava.nbio.structure.io.PDBFileReader
import org.biojava.nbio.structure.io.mmcif.ChemCompGroupFactory
import org.biojava.nbio.structure.io.mmcif.ReducedChemCompProvider

import static cz.siret.prank.geom.Struct.getAuthorId

/**
 * BioJava PDB utility methods.
 */
@Slf4j
@CompileStatic
class PdbUtils {

    private static final int BUFFER_SIZE = 5*1024*1024;

    static final FileParsingParameters PARSING_PARAMS = new FileParsingParameters()
    static {
        PARSING_PARAMS.setAlignSeqRes(false);  // should the ATOM and SEQRES residues be aligned when creating the internal data model?
        PARSING_PARAMS.setParseSecStruc(false);  // should secondary structure getByID parsed from the file

        PARSING_PARAMS.setCreateAtomBonds(false)
        PARSING_PARAMS.setCreateAtomCharges(false)
        PARSING_PARAMS.setParseBioAssembly(false)

        disableBiojavaFetching()
        //PARSING_PARAMS.setLoadChemCompInfo(Params.inst.biojava_load_chem_info); // info about modified amino acid residues, not available in BioJava4
    }

    /**
     * Tries to disable BioJava fetching external information since it leads to inconsistent protein parsing.
     */
    private static void disableBiojavaFetching() {
        ChemCompGroupFactory.setChemCompProvider(new ReducedChemCompProvider()) 
    }

    private static FileParsingParameters getParsingParams() {
        disableBiojavaFetching()
        return PARSING_PARAMS
    }

    static Structure loadFromFile(String file) {

        log.info "loading file [$file]"

        if (file==null) throw new IllegalArgumentException("file name not provided")

        Structure struct
        if (file.endsWith(".pdb")) {
            // load with buffer
            PDBFileParser pdbpars = new PDBFileParser();
            pdbpars.setFileParsingParameters(parsingParams);
            InputStream inStream = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)
            struct = pdbpars.parsePDBFile(inStream)
        } else {
            // for tar.gz files
            PDBFileReader pdbReader = new PDBFileReader()
            pdbReader.setFileParsingParameters(parsingParams)
            struct = pdbReader.getStructure(file)
        }

        return struct
    }

    static Structure loadFromString(String pdbText)  {
        PDBFileParser pdbpars = new PDBFileParser();
        pdbpars.setFileParsingParameters(parsingParams);

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



    /**
     * The code is based on StructureTools.getReducedStructure(String, String) from BioJava 5.3.0
     *
     * Note: has to be revised after upgrade to new BioJava versions!
     */
    static final Structure reduceStructureToModel(Structure s, int modelId) throws StructureException {

        Structure newS = new StructureImpl();
        newS.setPDBCode(s.getPDBCode());
        newS.setPDBHeader(s.getPDBHeader());
        newS.setName(s.getName());
        newS.setSSBonds(s.getSSBonds());
        newS.setDBRefs(s.getDBRefs());
        newS.setSites(s.getSites());
        newS.setBiologicalAssembly(s.isBiologicalAssembly());
        newS.setEntityInfos(s.getEntityInfos());
        newS.setSSBonds(s.getSSBonds());
        newS.setSites(s.getSites());

        // only get model modelId
        List<Chain> model = s.getModel(modelId);
        for (Chain c : model) {
            newS.addChain(c);
        }
        return newS;
    }


//    private static shouldCopyChain(Chain ch, List<String> chainIds) {
//        if (ch == null) return false
//        if (ch.entityType == EntityType.POLYMER && chainIds.contains(ch.id)) return true
//        if (ch.entityType == EntityType.NONPOLYMER) return true
//
//        ch.atomGroups[0].getChain().
//
//        return false
//    }

    private static shouldCopyChain(Chain ch, List<String> chainIds) {
        return chainIds.contains(getAuthorId(ch))
    }

    static List<Chain> getChanisForAuthorId(Structure s, String authorId) {
        List<Chain> res = new ArrayList<>()
        
        Chain polyChian = s.getPolyChainByPDB(authorId)
        if (polyChian != null) {
            res.add polyChian
        }
        res.addAll (s.getNonPolyChainsByPDB(authorId) ?: [])

        return res
    }

    /**
     * Reduces the structure to specified chains (and model 0 in case of multi-model structures).
     *
     * The code is based on StructureTools.getReducedStructure(String, String) from BioJava 5.3.0
     *
     * Note: has to be revised after upgrade to new BioJava versions!
     *
     * @param struct
     * @param chainIds
     * @return
     */
    static Structure getReducedStructure(Structure s, List<String> chainIds) {
        // since we deal here with structure alignments,
        // only use Model 1...

        ///// copied from StructureTools
        Structure newS = new StructureImpl();
        newS.setPDBCode(s.getPDBCode());
        newS.setPDBHeader(s.getPDBHeader());
        newS.setName(s.getName());
        newS.setSSBonds(s.getSSBonds());
        newS.setDBRefs(s.getDBRefs());
        newS.setSites(s.getSites());
        newS.setBiologicalAssembly(s.isBiologicalAssembly());
        newS.setEntityInfos(s.getEntityInfos());
        newS.setSSBonds(s.getSSBonds());
        newS.setSites(s.getSites());
        ///// end copied

        for (Chain ch : s.chains) {
            ///// end copied
            if (shouldCopyChain(ch, chainIds)) {
                newS.addChain(ch)
            }
        }

        //for (String authorId : chainIds) {
        //    getChanisForAuthorId(s, authorId).each {
        //        newS.addChain(it)
        //    }
        //}


        return newS;
    }

}
