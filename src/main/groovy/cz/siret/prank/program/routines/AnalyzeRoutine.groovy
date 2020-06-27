package cz.siret.prank.program.routines

import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.AA
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.labeling.LabeledResidue
import cz.siret.prank.domain.labeling.ResidueLabeler
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.domain.loaders.DatasetCachedLoader
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.domain.labeling.BinaryLabelings
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.SprintLabelingLoader
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.rendering.PymolRenderer
import cz.siret.prank.program.rendering.RenderingModel
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.BinCounter
import groovy.util.logging.Slf4j

import static cz.siret.prank.geom.SecondaryStructureUtils.assignSecondaryStructure
import static cz.siret.prank.utils.Formatter.format
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Various tools for analyzing datasets
 */
@Slf4j
class AnalyzeRoutine extends Routine {

    String subCommand
    String label
    String datasetFile
    Dataset dataset

    AnalyzeRoutine(CmdLineArgs args, Main main) {
        super(null)

        subCommand = args.unnamedArgs[0]
        if (!commandRegister.containsKey(subCommand)) {
            write "Invalid analyze command '$subCommand'! Available commands: "+commandRegister.keySet()
            throw new PrankException("Invalid command.")
        }

        String datasetParam = args.unnamedArgs[1]

        datasetFile = Main.findDataset(datasetParam)
        dataset = DatasetCachedLoader.loadDataset(datasetFile)

        label = "analyze_" + subCommand + "_" + dataset.label
        outdir = main.findOutdir(label)
    }

    void execute() {
        write "executing analyze $subCommand command"

        writeParams(outdir)
        commandRegister.get(subCommand).call()

        write "results saved to directory [${Futils.absPath(outdir)}]"
    }
    
 //===========================================================================================================//
 // Commands
 //===========================================================================================================//

    Map<String, Closure> commandRegister = ImmutableMap.copyOf([
        "binding-residues" : this.&cmdBindingResidues,
        "labeled-residues" : this.&cmdLabeledResidues,
        "aa-propensities" : this.&cmdAaPropensities,
        "aa-surf-seq-duplets" : this.&cmdAaSurfSeqDuplets,
        "aa-surf-seq-triplets" : this.&cmdAaSurfSeqTriplets,
        "conservation" : this.&cmdConservation,
        "chains" : this.&cmdChains,
        "peptides" : this.&cmdPeptides
    ])

//===========================================================================================================//

    /**
     * Write out binding residue ids
     */
    void cmdBindingResidues() {

        double residueCutoff = params.ligand_protein_contact_distance

        StringBuffer summary = new StringBuffer()

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            Atoms bindingAtoms = p.proteinAtoms.cutoutShell(p.allLigandAtoms, residueCutoff)
            List<Integer> bindingResidueIds = bindingAtoms.distinctGroupsSorted.collect { it.residueNumber.seqNum }.toSet().toSorted()

            String msg = "Protein [$p.name]  ligands: $p.ligandCount  bindingAtoms: $bindingAtoms.count  bindingResidues: ${bindingResidueIds.size()}"
            log.info msg
            summary << msg + "\n"

            String outf = "$outdir/${p.name}_binding-residues.txt"
            writeFile outf, bindingResidueIds.join("\n")
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
        
        StringBuffer csv = new StringBuffer("protein, n_chains, chain_id, n_residues, residue_string\n")
        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            int nchains = p.residueChains.size()
            String rows = ""
            p.residueChains.each {
                String chainId = it.authorId
                int nres = it.length
                String chars = it.codeCharString
                rows += "${item.label}, $nchains, $chainId, $nres, $chars \n"
            }
            csv << rows
        }
        writeFile "$outdir/chains.csv", csv
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
    private void printSprintChains(SprintLabelingLoader loader) {
        StringBuffer csv = new StringBuffer("chain_code, source, length, residue_string\n")

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            assignSecondaryStructure(p.structure)

            for (ResidueChain chain : p.residueChains) {
                String chainCode = loader.toElementCode(p, chain)
                if (loader.elementsByCode.containsKey(chainCode)) {
                    log.info "writing sprint chain [{}]", chainCode

                    def strStruct = chain.codeCharString
                    def strLabeler = loader.elementsByCode.get(chainCode).chain
                    def strLabels = loader.elementsByCode.get(chainCode).labels

                    def secStruct = chain.secStructString

                    String status = "OK"
                    if (strStruct.length() != strLabeler.length()) {
                        status = "!:LEN"
                    } else if (strStruct != strLabeler) {
                        status = "!:RES"
                    }

                    StringBuilder sb = new StringBuilder()
                    sb << String.format("%s, structure, %-6s, %6s, %s \n", chainCode, status, strStruct .length(), strStruct )
                    sb << String.format("%s,   labeler, %-6s, %6s, %s \n", chainCode, status, strLabeler.length(), strLabeler)
                    sb << String.format("%s,    labels, %-6s, %6s, %s \n", chainCode, status, strLabels .length(), strLabels )
                    sb << String.format("%s, sec.struc, %-6s, %6s, %s \n", chainCode, status, secStruct .length(), secStruct )
                    csv << sb.toString()
                } else {
                    log.warn "labeling for chain [{}] not found", chainCode
                }
            }
        }

        writeFile "$outdir/sprint_chains.csv", csv
    }

    /**
     * calculate AA propensities of exposed residues
     * i.e. propensity of being labeled as 1 by binary labeling
     * which is either explicitly defined by dataset or derived from ligands
     */
    private void cmdAaPropensities() {
        ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler

        List<BinCounter<AA>> counters = Collections.synchronizedList(new ArrayList<>())

        dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)

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
        ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler

        List<BinCounter<String>> counters = Collections.synchronizedList(new ArrayList<>())

        dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)

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
        ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler

        List<BinCounter<String>> counters = Collections.synchronizedList(new ArrayList<>())

        dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)

            def counter = new BinCounter<String>()

            labeling.labeledResidues.each { lres ->
                String code = Residue.safeSorted3CodeFor(lres.residue)
                counter.add(code, lres.label)
            }

            counters.add(counter)
        }

        savePropensities("$outdir/triplets.csv", BinCounter.join(counters))
    }



    private static void savePropensities(String fname, BinCounter counter) {
        StringBuilder csv = new StringBuilder("key, propensity, propensity^2, count, pos, neg\n")
        counter.table.keySet().toSorted().each {
            def bin = counter.get(it)
            double r = bin.posRatio
            csv << String.format("%s, %-7s, %-7s, %8s, %8s, %8s\n", it, format(r, 5), format(r*r, 5), bin.count, bin.positives, bin.negatives)
        }
        writeFile fname, csv
        write "Calculated propensities saved to [$fname]"
    }


}
