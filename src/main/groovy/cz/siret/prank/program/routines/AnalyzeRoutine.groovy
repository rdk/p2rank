package cz.siret.prank.program.routines

import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.AA
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.labeling.LabeledResidue
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
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.BinCounter
import groovy.util.logging.Slf4j

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
        "chains" : this.&cmdChains
    ])

    /**
     * Write out binding residue ids
     */
    void cmdBindingResidues() {

        double residueCutoff = params.ligand_protein_contact_distance

        StringBuffer summary = new StringBuffer()

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            Atoms bindingAtoms = p.proteinAtoms.cutoutShell(p.allLigandAtoms, residueCutoff)
            List<Integer> bindingResidueIds = bindingAtoms.distinctGroups.collect { it.residueNumber.seqNum }.toSet().toSorted()

            String msg = "Protein [$p.name]  ligands: $p.ligandCount  bindingAtoms: $bindingAtoms.count  bindingResidues: ${bindingResidueIds.size()}"
            log.info msg
            summary << msg + "\n"

            String outf = "$outdir/${p.name}_binding-residues.txt"
            writeFile outf, bindingResidueIds.join("\n")
        }

        write "\n" + summary.toString()

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
                String chainId = it.id
                int nres = it.size
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
        assert dataset.hasResidueLabeling()
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
            String chainIds = p.residueChains.collect { it.id }.join(" ")
            int nres = p.residues.size()
            int nlabres = s.total
            csv << "${item.label}, $nchains, $chainIds, $nres, $nlabres, ${s.positives}, ${s.negatives}, ${s.unlabeled}\n"

            if (params.visualizations) {
                new PymolRenderer("$outdir/visualizations", new RenderingModel(
                        generateProteinFile: params.vis_generate_proteins,
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
     * Compare chain strings in strcture with those defined in sprint labeling file
     */
    private void printSprintChains(SprintLabelingLoader loader) {
        StringBuffer csv = new StringBuffer("chain_code, source, length, residue_string\n")

        dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            for (ResidueChain chain : p.residueChains) {
                String chainCode = loader.toElementCode(p, chain)
                if (loader.elementsByCode.containsKey(chainCode)) {
                    log.info "writing sprint chain [{}]", chainCode

                    def strStruct = chain.codeCharString
                    def strLabeler = loader.elementsByCode.get(chainCode).chain
                    def strLabels = loader.elementsByCode.get(chainCode).labels

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
                    csv << sb.toString()
                } else {
                    log.warn "labeling for chain [{}] not found", chainCode
                }
            }
        }

        writeFile "$outdir/sprint_chains.csv", csv
    }

    private void cmdAaPropensities() {
        assert dataset.hasResidueLabeling()
        LoaderParams.ignoreLigandsSwitch = true

        def labeler = dataset.binaryResidueLabeler

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

        StringBuilder csv = new StringBuilder("AA, pos_ratio, count, pos, neg\n")
        AA.values().each {
            def bin = counter.get(it)
            csv << String.format("%s, %-7s, %8s, %8s, %8s\n", it, Formatter.format(bin.posRatio, 5), bin.count, bin.positives, bin.negatives)
        }
        writeFile "$outdir/aa_propensities.csv", csv
    }


}
