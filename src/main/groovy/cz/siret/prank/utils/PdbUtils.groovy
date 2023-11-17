package cz.siret.prank.utils

import cz.siret.prank.domain.AA
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.biojava.nbio.structure.*
import org.biojava.nbio.structure.io.FileParsingParameters
import org.biojava.nbio.structure.io.PDBFileParser
import org.biojava.nbio.structure.io.cif.CifStructureConverter

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static cz.siret.prank.geom.Struct.getAuthorId
import static cz.siret.prank.geom.Struct.getMmcifId

//import org.biojava.nbio.structure.io.mmcif.ChemCompGroupFactory
//import org.biojava.nbio.structure.io.mmcif.ReducedChemCompProvider

/**
 * BioJava PDB utility methods.
 */
@Slf4j
@CompileStatic
class PdbUtils {

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
        // TODO Biojava6: find out if there is a new way to disable fetching
        // ChemCompGroupFactory.setChemCompProvider(new ReducedChemCompProvider())
    }

    private static FileParsingParameters getParsingParams() {
        disableBiojavaFetching()
        return PARSING_PARAMS
    }

    static Structure loadFromFile(String file) {
        log.info "loading file [$file]"

        if (file==null) {
            throw new IllegalArgumentException("file name not provided")
        }

        String ext = Futils.realExtension(file)

        if (ext == "cif" || ext == "bcif" ) {
            return loadFromCifFile(file)
        } else { // pdb / ent
            return loadFromPdbFile(file)
        }
    }

    static Structure loadFromPdbFile(String file) {
        InputStream instream = Futils.inputStream(file)
        try {
            PDBFileParser pdbpars = new PDBFileParser()
            pdbpars.setFileParsingParameters(parsingParams)
            return pdbpars.parsePDBFile(instream)
        } catch (Exception e) {
            throw new PrankException("Failed to load structure from '$file'", e)
        } finally {
            instream.close()
        }
    }

    static Structure loadFromCifFile(String file) {
        InputStream instream = Futils.inputStream(file)
        try {
            return CifStructureConverter.fromInputStream(instream, parsingParams)
        } catch (Exception e) {
            throw new PrankException("Failed to load structure from '$file'", e)
        } finally {
            instream.close()
        }
    }

    static Structure loadFromString(String pdbText)  {
        PDBFileParser pdbpars = new PDBFileParser();
        pdbpars.setFileParsingParameters(parsingParams);

        BufferedReader br = new BufferedReader(new StringReader(pdbText))

        Structure struc = pdbpars.parsePDBFile(br)
        return struc
    }

    static Structure loadFromCifString(String cifText)  {
        InputStream inputStream = IOUtils.toInputStream(cifText)

        Structure struc = CifStructureConverter.fromInputStream(inputStream, parsingParams)
        return struc
    }

    /**
     * @param fileName
     * @param format "cif" or "pdb"
     * @param compressed - compress to gz
     * @return file name used
     */
    static String saveToFile(Structure structure, String format, String fileName, boolean compressed = false) {
        String content
        if (format == "cif") {
            content = structure.toMMCIF()
        } else {
            content = structure.toPDB()
        }

        if (compressed) {
            Futils.writeGzip fileName, content
        } else {
            Futils.writeFile fileName, content
        }
        return fileName
    }

//===========================================================================================================//

    /**
     * Masking for internal P2Rank representation
     */
    @Nullable
    static String correctResidueCode(String residueCode) {
        //MSE is only found as a molecular replacement for MET
        //'non-standard', genetically encoded
        if ("MSE".equals(residueCode)) {
            residueCode = "MET"
        } else if ("MEN".equals(residueCode)) {  // N-METHYL ASPARAGINE present only in cca. 60 entries in the PDB but also in one in CHEN11 training dataset
            residueCode = "ASN"
        }

        return residueCode
    }

    /**
     * Masking for sequence / one-residue code mapping
     */
    @Nullable
    static String maskMseResidueCode(String residueCode) {
        //MSE is only found as a molecular replacement for MET
        if ("MSE".equals(residueCode)) {
            residueCode = "MET"
        }

        return residueCode
    }

    /**
     * One letter code via ChemComp. Result may depend on the online access.
     * @param group
     * @return
     */
    @Nullable
    static String getBiojavaOneLetterCodeString(Group group) {
        return group?.getChemComp()?.getOneLetterCode()
    }

    /**
     * Same as getBioJavaOneLetterCodeString() but null/empty is masked as '?'
     */
    @Nonnull
    static char getBiojavaOneLetterCode(Group group) {
        String code = getBiojavaOneLetterCodeString(group)
        if (code == null || code.size() == 0) {
            return '?' as char
        } else {
            return code.charAt(0)
        }
    }

    /**
     * Anything that is not one of 20 standard one letter codes is masked as '?'
     * Does not go via ChemComp as getBioJavaOneLetterCode but instead via residue three-letter code.
     * Should be more stable when online access sin not allowed.
     *
     * The only three letter code masking done is MSE->MET=M
     */
    @Nonnull
    static char getStandardOneLetterCode(Group group) {
        return getStandardOneLetterCode(getResidueCode(group))
    }

    /**
     * Maps standard 3-letter codes to 1-letter code.
     * Anything that is not one of 20 standard one letter codes is masked as '?'.
     * The only three letter code masking done is MSE->MET=M
     */
    @Nonnull
    static char getStandardOneLetterCode(String threeLetterCode) {
        String rawCode = maskMseResidueCode(threeLetterCode)

        AA aa = AA.forCode(rawCode)

        return (aa != null) ? aa.codeChar : '?' as char
    }

    /**
     * @return three letter residue code (e.g. "ASP")
     */
    @Nullable
    static String getAtomResidueCode(Atom a) {
        a?.group?.PDBName
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
    @Nullable
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

    static boolean trySetElement(Atom a, String code) {
        Element ele
        try {
            ele = Element.valueOf(code)
        } catch (IllegalArgumentException e) {
            // nothing, biojava just doesnt know the element
        }

        if (ele != null) {
            //log.warn "correcting atom {} to element {}", a.name, ele
            a.setElement(ele)
            return true
        }
        return false
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
     * TODO consider case insensitive matching
     */
    private static shouldCopyChain(Chain ch, List<String> chainIds) {
        return chainIds.contains(getAuthorId(ch))
    }

    private static Structure cleanCopyWithMetadata(Structure s) {
        Structure newS = new StructureImpl();
        newS.setPdbId(s.getPdbId());
        newS.setPDBHeader(s.getPDBHeader());
        newS.setName(s.getName());
        newS.setSSBonds(s.getSSBonds());
        newS.setDBRefs(s.getDBRefs());
        newS.setSites(s.getSites());
        newS.setBiologicalAssembly(s.isBiologicalAssembly());
        newS.setEntityInfos(s.getEntityInfos());
        newS.setSSBonds(s.getSSBonds());
        newS.setSites(s.getSites());

        newS.setStructureIdentifier(s.getStructureIdentifier()); // TODo maybe needs to reflect reduced chains
        // newS.setJournalArticle(s.getJournalArticle());  // set via header
        newS.setCrystallographicInfo(s.getCrystallographicInfo());

        return newS;
    }

    private static void copyChains(List<Chain> chains, Structure sourceStructure, Structure targetStructure) {
        for (Chain ch : chains) {
            targetStructure.addChain(ch)

            // TODO this is copied from BioJava 5.4 but seems just wrong
            for (EntityInfo comp : sourceStructure.getEntityInfos()) {
                if (comp.getChainIds().contains(getMmcifId(ch))) {  // matching by internal mmcif ID
                    // found matching entity info. set description...
                    targetStructure.getPDBHeader()
                            .setDescription("Chain ${getMmcifId(ch)}(authorId:${getAuthorId(ch)}) of ${sourceStructure.pdbId?.id} $comp.description")
                }
            }

            // TODO: need to copy anything else? EntityInfos?
        }
    }

    /**
     * The code is based on StructureTools.getReducedStructure(String, String) from BioJava 5.3.0
     *
     * Note: has to be revised after upgrade to new BioJava versions!
     */
    static final Structure reduceStructureToModel(Structure s, int modelId) throws StructureException {
        Structure newS = cleanCopyWithMetadata(s)

        // only get chains from model modelId
        List<Chain> modelChains = s.getModel(modelId);

        copyChains(modelChains, s, newS)

        return newS;
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
    static Structure reduceStructureToChains(Structure s, List<String> chainIds) {
        Structure newS = cleanCopyWithMetadata(s);

        // maybe s.getModel(0).chains?
        List<Chain> chainsToAdd = s.chains.findAll {shouldCopyChain(it, chainIds) }

        copyChains(chainsToAdd, s, newS)

        return newS;
    }



//    /**
//     * Original code from Biojava 5.4 for comparison
//     * see https://github.com/biojava/biojava/blob/9dcddeb0072ecc29aca8d8b1d43c3b18a430ac30/biojava-structure/src/main/java/org/biojava/nbio/structure/StructureTools.java#L1286
//     *
//     * Reduce a structure to provide a smaller representation . Only takes the
//     * first model of the structure. If chainName is provided only return a
//     * structure containing that Chain ID. Converts lower case chain IDs to
//     * upper case if structure does not contain a chain with that ID.
//     *
//     * @param s
//     * @param chainId
//     * @return Structure
//     * @since 3.0
//     * @deprecated Use {@link StructureIdentifier#reduce(Structure)} instead (v. 4.2.0)
//     */
//    @Deprecated
//    public static Structure getReducedStructure(Structure s,
//                                                String chainId) throws StructureException {
//        // since we deal here with structure alignments,
//        // only use Model 1...
//
//        Structure newS = new StructureImpl();
//        newS.setPDBCode(s.getPDBCode());
//        newS.setPDBHeader(s.getPDBHeader());
//        newS.setName(s.getName());
//        newS.setSSBonds(s.getSSBonds());
//        newS.setDBRefs(s.getDBRefs());
//        newS.setSites(s.getSites());
//        newS.setBiologicalAssembly(s.isBiologicalAssembly());
//        newS.setEntityInfos(s.getEntityInfos());
//        newS.setSSBonds(s.getSSBonds());
//        newS.setSites(s.getSites());
//
//        if (chainId != null)
//            chainId = chainId.trim();
//
//        if (chainId == null || chainId.equals("")) {
//            // only get model 0
//            List<Chain> model0 = s.getModel(0);
//            for (Chain c : model0) {
//                newS.addChain(c);
//            }
//            return newS;
//
//        }
//
//        Chain c = null;
//        try {
//            c = s.getChainByPDB(chainId);
//        } catch (StructureException e) {
//            logger.warn(e.getMessage() + ". Chain id " + chainId
//                    + " did not match, trying upper case Chain id.");
//            c = s.getChainByPDB(chainId.toUpperCase());
//
//        }
//        if (c != null) {
//            newS.addChain(c);
//            for (EntityInfo comp : s.getEntityInfos()) {
//                if (comp.getChainIds() != null
//                        && comp.getChainIds().contains(c.getChainID())) {
//                    // found matching entity info. set description...
//                    newS.getPDBHeader().setDescription(
//                            "Chain " + c.getChainID() + " of " + s.getPDBCode()
//                                    + " " + comp.getDescription());
//                }
//            }
//        }
//
//        return newS;
//    }


}
