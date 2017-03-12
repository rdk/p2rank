package cz.siret.prank.program.routines

import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.DatasetCachedLoader
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Writable
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
    String dtasetFile
    Dataset dataset

    AnalyzeRoutine(CmdLineArgs args, Main main) {

        subCommand = args.unnamedArgs[0]
        if (!commandRegister.containsKey(subCommand)) {
            write "Invalid analyze command '$subCommand'! Available commands: "+commandRegister.keySet()
            throw new PrankException("Invalid command.")
        }

        String datasetParam = args.unnamedArgs[1]

        dtasetFile = Main.findDataset(datasetParam)
        dataset = DatasetCachedLoader.loadDataset(dtasetFile)

        label = "analyze_" + subCommand + "_" + dataset.label
        outdir = main.findOutdir(label)
    }

    void execute() {
        write "executing analyze $subCommand command"

        commandRegister.get(subCommand).call()

        write "results saved to directory [${Futils.absPath(outdir)}]"
    }
    
 //===========================================================================================================//
 // Commands
 //===========================================================================================================//

    Map<String, Closure> commandRegister = ImmutableMap.copyOf([
            "binding-residues" : this.&cmdBindingResidues
    ])

    void cmdBindingResidues() {

        double residueCutoff = params.ligand_protein_contact_distance

        StringBuffer summary = new StringBuffer()

        dataset.processItems(new Dataset.Processor() {
            @Override
            void processItem(Dataset.Item item) {
                Protein p = item.protein


                Atoms bindingAtoms = p.proteinAtoms.cutoffAtoms(p.allLigandAtoms, residueCutoff)
                List<Integer> bindingResidueIds = bindingAtoms.distinctGroups.collect { it.residueNumber.seqNum }.toSet().toSorted()

                String msg = "Protein [$p.name]  ligands: $p.ligandCount  bindingAtoms: $bindingAtoms.count  bindingResidues: ${bindingResidueIds.size()}"
                log.info msg
                summary << msg + "\n"

                String outf = "$outdir/${p.name}_binding-residues.txt"
                writeFile outf, bindingResidueIds.join("\n")

            }
        })

        write "\n" + summary.toString()

    }

}
