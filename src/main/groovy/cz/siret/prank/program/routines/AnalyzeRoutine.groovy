package cz.siret.prank.program.routines

import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.DatasetCachedLoader
import cz.siret.prank.domain.LoaderParams
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.domain.labeling.BinaryLabelings
import cz.siret.prank.domain.labeling.BinaryResidueLabeling
import cz.siret.prank.domain.labeling.ResidueLabeler
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.domain.labeling.SprintLabelingLoader
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.rendering.PymolRenderer
import cz.siret.prank.program.rendering.RenderingModel
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
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

            Atoms bindingAtoms = p.proteinAtoms.cutoffAtoms(p.allLigandAtoms, residueCutoff)
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
     * Chain statistics`
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

            BinaryResidueLabeling labeling = labeler.getBinaryLabeling(p.proteinResidues, p)
            def s = BinaryLabelings.getStats(labeling)

            int nchains = p.residueChains.size()
            String chainIds = p.residueChains.collect { it.id }.join(" ")
            int nres = p.proteinResidues.size()
            int nlabres = s.total
            csv << "${item.label}, $nchains, $chainIds, $nres, $nlabres, ${s.positives}, ${s.negatives}, ${s.unlabeled}\n"

            if (params.visualizations) {
                new PymolRenderer("$outdir/visualizations", new RenderingModel(
                        generateProteinFile: params.vis_generate_proteins,
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: item.protein,
                        binaryLabeling: labeling
                )).render()
            }
        }

        writeFile "$outdir/residue_stats.csv", csv
    }

    /**
     * Compare chain strings in strcture with those defined in sprint labeling file
     */
    private printSprintChains(SprintLabelingLoader loader) {
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

}
