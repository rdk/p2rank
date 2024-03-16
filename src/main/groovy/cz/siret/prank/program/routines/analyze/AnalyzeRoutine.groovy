
package cz.siret.prank.program.routines.analyze

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.*
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.export.FastaExporter
import cz.siret.prank.features.implementation.table.AtomTableFeature
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.rendering.PymolRenderer
import cz.siret.prank.program.rendering.RenderingModel
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.ResidueNumber

import javax.annotation.Nullable

import static cz.siret.prank.geom.SecondaryStructureUtils.assignSecondaryStructure
import static cz.siret.prank.utils.Cutils.newSynchronizedList
import static cz.siret.prank.utils.Formatter.format
import static cz.siret.prank.utils.Futils.writeFile
import static java.util.Collections.unmodifiableMap

/**
 * Various tools for analyzing datasets.
 * Routine with sub-commands.
 */
@Slf4j
@CompileStatic
class AnalyzeRoutine extends Routine {

    String subCommand
    String label
    @Nullable Dataset dataset

    AnalyzeRoutine(CmdLineArgs args, Main main) {
        super(null)

        subCommand = args.popFirstUnnamedArg() // next if present should be dataset
        if (!commandRegister.containsKey(subCommand)) {
            write "Invalid analyze sub-command '$subCommand'! Available commands: " + commandRegister.keySet()
            throw new PrankException("Invalid command.")
        }

        if (!args.unnamedArgs.empty || args.get('f') != null) {
            dataset = main.loadDatasetOrFile()
        }

        label = "analyze_" + subCommand + (dataset!=null ? "_"+dataset.label : "")
        outdir = main.findOutdir(label)
        main.configureLoggers(outdir)
    }

    void execute() {
        write "executing analyze $subCommand command"

        writeParams(outdir)
        commandRegister.get(subCommand).call()

        write "results saved to directory [${Futils.absPath(outdir)}]"
    }
    
 //===========================================================================================================//
 // Sub-Commands
 //===========================================================================================================//

    final Map<String, Closure> commandRegister = unmodifiableMap([
        "residues" : { cmdResidues() },
        "binding-residues" : { cmdBindingResidues() },
        "labeled-residues" : { cmdLabeledResidues() },
        "aa-propensities" : { cmdAaPropensities() },
        "aa-surf-seq-duplets" : { cmdAaSurfSeqDuplets() },
        "aa-surf-seq-triplets" : { cmdAaSurfSeqTriplets() },
        "conservation" : { cmdConservation() },
        "chains" : { cmdChains() },
        "chains-residues" : { cmdChainsResidues() },
        "fasta-raw" : { cmdFastaRaw() },
        "fasta-masked" : { cmdFastaMasked() },
        "peptides" : { cmdPeptides() },
        "convert-dataset-to-atomid" : { cmdConvertContactresDataset() },
        "print-volsite-table" : { print_volsite_table() }
    ])

//===========================================================================================================//

    /**
     * Write out residue details
     *
     * Similar to cmdChainsResidues but add binding info and produces only one csv per protein.
     */
    void cmdResidues() {

        double residueCutoff = params.ligand_protein_contact_distance

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            p.assignSecondaryStructure()

            Atoms bindingAtoms = p.proteinAtoms.cutoutShell(p.allRelevantLigandAtoms, residueCutoff)
            Set<Residue> bindingResidues = p.residues.getDistinctForAtoms(bindingAtoms).toSet()

            StringBuffer csv = new StringBuffer("chain_name, seq_num, ins_code, key, chain_mmcif_id, atoms, sec_struct_type, is_binding\n")
            for (ResidueChain chain : p.residueChains) {
                for (Residue res : chain.residues) {
                    ResidueNumber rn = res.residueNumber
                    int binding = bindingResidues.contains(res) ? 1 : 0
                    String insCode = (rn.insCode != null) ? ""+rn.insCode : "-"
                    csv << "$rn.chainName, $rn.seqNum, $insCode, $res.key, $res.chainMmcifId, $res.atoms.count, $res.secStruct, $binding\n"
                }
            }

            String outf = "$outdir/${p.name}_residues.csv"
            writeFile outf, csv.toString()
        }

    }


    /**
     * Write out binding residue keys
     */
    void cmdBindingResidues() {

        double bindingCutoff = params.ligand_protein_contact_distance

        StringBuffer summary = new StringBuffer()

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            Atoms bindingAtoms = p.proteinAtoms.cutoutShell(p.allRelevantLigandAtoms, bindingCutoff)
            List<String> bindingResidueCodes = bindingAtoms.distinctGroups.collect { it.residueNumber.printFull() }.toSet().toSorted()

            String msg = "Protein [$p.name]  ligands: $p.ligandCount  bindingAtoms: $bindingAtoms.count  bindingResidues: ${bindingResidueCodes.size()}"
            log.info msg
            summary << msg + "\n"

            String outf = "$outdir/${p.name}_binding-residues.txt"
            writeFile outf, bindingResidueCodes.join("\n")
        }

        write "\n" + summary.toString()

    }

    void cmdPeptides() {
        LoaderParams.ignoreLigandsSwitch = true

        StringBuffer csv = new StringBuffer("protein, pept_count, peptides\n")
        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            String ps = p.peptides.collect { "($it.authorId,$it.length)" }.join(" ")
            csv << "$p.name, ${p.peptides.size()}, $ps\n"
        }
        writeFile "$outdir/peptides.csv", csv
        write csv.toString()
    }

    /**
     * Chain statistics
     */
    void cmdChains() {
        LoaderParams.ignoreLigandsSwitch = true
        
        StringBuffer csv = new StringBuffer("protein, n_chains, chain_id, mmcif_id, n_residues, residue_string\n")
        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            int nchains = p.residueChains.size()
            String rows = ""
            p.residueChains.each {
                String chainId = it.authorId
                String mmcifId = it.mmcifId
                int nres = it.length
                String chars = it.biojavaCodeCharString
                rows += "${item.label}, $nchains, $chainId, $mmcifId, $nres, $chars \n"
            }
            csv << rows
        }
        writeFile "$outdir/chains.csv", csv
    }

    /**
     * Chain statistics
     */
    void cmdChainsResidues() {
        cmdChains()

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            int idx = 1
            for (ResidueChain chain : p.residueChains) {

                StringBuffer csv = new StringBuffer("chain_name, seq_num, ins_code, key, chain_mmcif_id, atoms, sec_struct_type\n")
                for (Residue res : chain.residues) {
                    ResidueNumber rn = res.residueNumber
                    csv << "$rn.chainName, $rn.seqNum, $rn.insCode, $res.key, $res.chainMmcifId, $res.atoms.count, $res.secStruct \n"
                }

                String strIdx = String.format("%02d", idx++)
                writeFile "$outdir/${item.label}_${strIdx}_${chain.authorId}_${chain.mmcifId}_residues.csv", csv
            }
        }
    }

    /**
     * Export chains to fasta in raw chain format (as P2Rank sees it).
     * Considers only protein AA residue chains.
     */
    void cmdFastaRaw() {
        doCmdFasta(false)
    }

    /**
     * Export chains to fasta where some residue codes are transformed:
     * 
     * 1. non-letter characters -> X
     *
     * Considers only protein AA residue chains.
     */
    void cmdFastaMasked() {
        doCmdFasta(true)
    }

    private doCmdFasta(boolean masked) {
        FastaExporter exporter = FastaExporter.getInstance()

        write "exporting fasta (masked: $masked)"

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            for (ResidueChain chain : p.residueChains) {
                String chainCode = Struct.maskEmptyChainId(chain.authorId)
                String protFileBaseName = Futils.baseName(item.proteinFile)
                String fname = "${protFileBaseName}_${chainCode}.fasta"

                String header = exporter.makeFastaHeader(chain, p.structure)
                String codes = exporter.getFastaChain(chain, masked)
                String fasta = exporter.formatFastaFile(header, codes)

                fname = "$outdir/$fname"
                
                write "$p.name: exporting chain $chain.authorId to $fname"

                writeFile(fname, fasta)
            }
        }
    }

    /**
     * Statistics about binary residue labeling + visualizations
     */
    void cmdLabeledResidues() {
        assert dataset.hasExplicitResidueLabeling()
        LoaderParams.ignoreLigandsSwitch = true

        def labeler = dataset.binaryResidueLabeler
        StringBuffer csv = new StringBuffer("protein, n_chains, chain_ids, n_residues, n_residues_in_labeling, positives, negatives, unlabeled\n")

        if (labeler instanceof SprintLabelingLoader) {
            printSprintChains((SprintLabelingLoader)labeler)
        }

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            BinaryLabeling labeling = labeler.getBinaryLabeling(p.residues, p)
            def s = BinaryLabelings.getStats(labeling)

            int nchains = p.residueChains.size()
            String chainIds = p.residueChains.collect { it.authorId }.join(" ")
            int nres = p.residues.size()
            int nlabres = s.total
            csv << "${item.label}, $nchains, $chainIds, $nres, $nlabres, ${s.positives}, ${s.negatives}, ${s.unlabeled}\n"

            if (params.visualizations) {
                new PymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: item.protein,
                        observedLabeling: labeling
                )).render()
            }
        }

        writeFile "$outdir/residue_stats.csv", csv
    }

    /**
     * Analyze and visualize conservation scores
     */
    void cmdConservation() {
        LoaderParams.ignoreLigandsSwitch = true

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            ResidueLabeling<Double> labeling = p.getConservationLabeling()
            if (labeling!=null) {

                if (log.isDebugEnabled()) {
                    labeling.labeledResidues.each {
                        log.debug "conserv. for residue {}: {}", it.residue, it.label
                    }
                    log.debug "score map:" + p.getConservationScore().getScoreMap()
                }

                if (params.visualizations) {
                    new PymolRenderer("$outdir/visualizations", new RenderingModel(
                            proteinFile: item.proteinFile,
                            label: item.label,
                            protein: item.protein,
                            doubleLabeling: labeling
                    )).render()
                }

            } else {
                log.error "Failed to load score for [{}]", item.label
            }
        }
    }

    /**
     * Compare chain strings in structure with those defined in sprint labeling file
     */
    @CompileStatic
    private void printSprintChains(SprintLabelingLoader loader) {
        StringBuffer csv = new StringBuffer(
                "# status: 'MATCH' | '!:LEN' = labeling/structure chain lengths don't match | '!:RES' = labeling/structure chain residues don't match\n" +
                "chain_code, source, status, length, chain_data\n")

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            assignSecondaryStructure(p.structure)

            log.info("processing protein [$p.name] with residue chains ${p.residueChains*.authorId}")

            for (ResidueChain chain : p.residueChains) {
                String chainCode = loader.toElementCode(p, chain)
                if (loader.elementsByCode.containsKey(chainCode)) {
                    log.info "writing sprint chain [{}]", chainCode

                    def strStruct = chain.biojavaCodeCharString
                    def strLabeler = loader.elementsByCode.get(chainCode)?.chain
                    def strLabels = loader.elementsByCode.get(chainCode)?.labels

                    def secStruct = chain.secStructString

                    String status = "MATCH"
                    if (strStruct.length() != strLabeler.length()) {
                        status = "!:LEN"
                    } else if (strStruct != strLabeler) {
                        status = "!:RES"
                    }

                    StringBuilder sb = new StringBuilder()
                    sb << String.format("%s, structure, %-6s, %6s, %s \n", chainCode, status, strStruct .length(), strStruct )
                    sb << String.format("%s,   labeler, %-6s, %6s, %s \n", chainCode, "", strLabeler.length(), strLabeler)
                    sb << String.format("%s,    labels, %-6s, %6s, %s \n", chainCode, "", strLabels .length(), strLabels )
                    sb << String.format("%s, sec.struc, %-6s, %6s, %s \n", chainCode, "", secStruct .length(), secStruct )
                    csv << sb.toString()
                } else {
                    log.warn "labeling for chain [{}] not found", chainCode
                }
            }
        }

        writeFile "$outdir/labeled_chains.csv", csv
    }

    /**
     * calculate AA propensities of exposed residues
     * i.e. propensity of being labeled as 1 by binary labeling
     * which is either explicitly defined by dataset or derived from ligands
     */
    private void cmdAaPropensities() {
        List<BinCounter<AA>> counters = newSynchronizedList()

        dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)   // TODO not always only exposed!

            def counter = new BinCounter<AA>()

            labeling.labeledResidues.each { LabeledResidue<Boolean> lres ->
                AA aa = lres.residue.aa
                if (aa != null && lres.label != null) {
                    counter.add(aa, lres.label)
                }
            }

            counters.add(counter)
        }

        BinCounter<AA> counter = BinCounter.join(counters)
        savePropensities("$outdir/aa-propensities.csv", counter)
    }



    /**
     * ordering dependent sequence duplets (only starting from exposed residues)
     */
    private void cmdAaSurfSeqDuplets() {
        List<BinCounter<String>> counters = newSynchronizedList()

        dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)    // TODO not always only exposed!

            def counter = new BinCounter<String>()

            labeling.labeledResidues.each { LabeledResidue<Boolean> lres ->
                def res = lres.residue
                def prev = res.previousInChain
                def next = res.nextInChain

                // in each direction
                counter.add(Residue.safeOrderedCode2(res, prev), lres.label)
                counter.add(Residue.safeOrderedCode2(res, next), lres.label)
            }

            counters.add(counter)
        }

        savePropensities("$outdir/duplets.csv", BinCounter.join(counters))
    }

    /**
     * sequence triplets (only from exposed residues)
     */
    private void cmdAaSurfSeqTriplets() {
        List<BinCounter<String>> counters = newSynchronizedList()

        dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)       // TODO not always only exposed!

            def counter = new BinCounter<String>()

            labeling.labeledResidues.each { lres ->
                String code = Residue.safeSorted3CodeFor(lres.residue)
                counter.add(code, lres.label)
            }

            counters.add(counter)
        }

        savePropensities("$outdir/triplets.csv", BinCounter.join(counters))
    }

    private void savePropensities(String fname, BinCounter counter) {
        StringBuilder csv = new StringBuilder("key, propensity, propensity^2, count, pos, neg\n")
        counter.table.keySet().toSorted().each {
            def bin = counter.get(it)
            double r = bin.posRatio
            csv << String.format("%s, %-7s, %-7s, %8s, %8s, %8s\n", it, format(r, 5), format(r*r, 5), bin.count, bin.positives, bin.negatives)
        }
        writeFile fname, csv
        write "Calculated propensities saved to [$fname]"
    }

    /**
     * Convert dataset with ligand definitions based on contact residue ids to
     * one with definitions based on ligand atom_id.
     */
    private void cmdConvertContactresDataset() {
        String headerLine = "HEADER: " + dataset.header.join(" ")

        List<String> newItems = Cutils.newSynchronizedList(dataset.size)
        List<String> nonMatchingItems = Cutils.newSynchronizedList(dataset.size)


        dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein

            List<String> ligDefs = new ArrayList<>()
            for (Ligand lig : prot.relevantLigands) {
                String name = lig.groups[0].PDBName
                int atomId = lig.atoms[0].PDBserial
                String ligDef = name + "[atom_id:" + atomId + "]"
                ligDefs.add(ligDef)
            }

            String newLigDefsStr = ligDefs.toSorted().join(",")
            Map<String, String> newColVals = new HashMap<>(item.columnValues)
            newColVals.put(Dataset.COLUMN_LIGANDS, newLigDefsStr)
            String newLine = dataset.header.collect {newColVals.get(it) }.join("  ")

            if (item.ligandDefinitions.size() == ligDefs.size()) {
                newItems.add(newLine)
            } else {
                String oldLine = dataset.header.collect {item.columnValues.get(it) }.join("  ")
                String ne = "${item.ligandDefinitions.size()} != ${ligDefs.size()}"
                nonMatchingItems.add(ne + "  |OLD:|  " + oldLine + "  |NEW:|  " + newLigDefsStr)
            }
        }

        newItems = newItems.toSorted()

        String newDsText = headerLine + "\n" + newItems.join("\n") + "\n"
        String nonMatchingText = nonMatchingItems.join("\n") + "\n"

        log.info("Matching items: {}", newItems.size())
        log.info("Non matching items: {}", nonMatchingItems.size())
        log.info("Non matching items were ignored.")

        writeFile "$outdir/${dataset.label}_converted.ds", newDsText
        writeFile "$outdir/non_matching_items.txt", nonMatchingText
    }

    
    void print_volsite_table() {
        List<String> atomTypes = AtomTableFeature.atomPropertyTable.itemNames.toSorted()

        StringBuilder sb = new StringBuilder()
        sb << "atomName, vsAromatic, vsCation, vsAnion, vsHydrophobic, vsAcceptor, vsDonor\n"
        for (String atomType : atomTypes) {
            def ss = Sutils.split(atomType, ".")
            String resName = ss[0]
            String atomName = ss[1]

            VolSitePharmacophore.AtomProps props = VolSitePharmacophore.getAtomProperties(atomName, resName)

            sb << atomType + ", "
            sb << (props.aromatic?"1":"0"   ) + ", "
            sb << (props.cation?"1":"0"     ) + ", "
            sb << (props.anion?"1":"0"      ) + ", "
            sb << (props.hydrophobic?"1":"0") + ", "
            sb << (props.acceptor?"1":"0"   ) + ", "
            sb << (props.donor?"1":"0"      )
            sb << "\n"
        }

        String ss = sb.toString()
        write ss
        writeFile "$outdir/volsite_atom_table.csv", ss

    }

}
