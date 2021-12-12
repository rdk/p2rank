package cz.siret.prank.program.routines.analyze

import com.google.common.base.Splitter
import com.google.common.collect.ImmutableMap
import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.*
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.export.FastaExporter
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.rendering.PymolRenderer
import cz.siret.prank.program.rendering.RenderingModel
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.BinCounter
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.ResidueNumber
import org.biojava.nbio.structure.Structure

import static cz.siret.prank.geom.SecondaryStructureUtils.assignSecondaryStructure
import static cz.siret.prank.utils.Cutils.newSynchronizedList
import static cz.siret.prank.utils.Formatter.format
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Various tools for analyzing datasets.
 * Routine with sub-commands.
 */
@Slf4j
@CompileStatic
class TransformRoutine extends Routine {

    String subCommand
    String label
    Dataset dataset
    CmdLineArgs args

    TransformRoutine(CmdLineArgs args, Main main) {
        super(null)

        this.args = args

        subCommand = args.popFirstUnnamedArg() // next if present should be dataset
        if (!commandRegister.containsKey(subCommand)) {
            write "Invalid transform sub-command '$subCommand'! Available commands: " + commandRegister.keySet()
            throw new PrankException("Invalid command.")
        }

        dataset = main.loadDatasetOrFile()

        label = "transform_" + subCommand + "_" + dataset.label
        outdir = main.findOutdir(label)
        main.configureLoggers(outdir)
    }

    void execute() {
        write "executing transform $subCommand command"

        commandRegister.get(subCommand).call()
    }

 //===========================================================================================================//
 // Sub-Commands
 //===========================================================================================================//

    final Map<String, Closure> commandRegister = ImmutableMap.copyOf([
        "reduce-to-chains" : { cmdReduceToChains() }
    ])

//===========================================================================================================//

    /**
     * chain label = "<author_id>(<mmcif_id>)"
     */
    private List<String> chainLabels(Structure structure) {
        return structure.chains.collect { Struct.getAuthorId(it) + "(" + Struct.getMmcifId(it) + ")" }
    }

    private void cmdReduceToChains() {
        String file = args.get("f")
        String outFormatParam = params.out_format
        String outFileParam = params.out_file
        String chainsParam = params.chains

        def validVals = ["keep", "pdb", "pdb.gz", "cif", "cif.gz"]
        if (!(outFormatParam in validVals)) {
            throw new PrankException("Invalid value of out_format param: '$outFormatParam'. Valid values: $validVals")
        }

        write "processing file [${Futils.absPath(file)}]"

        Structure structure = PdbUtils.loadFromFile(file)
        String baseFileName = Futils.baseName(file)
        String outFileBaseName // without extension

        List<String> schains = structure.chains.collect { Struct.getAuthorId(it) }.toUnique().toSorted()
        write "chains: " + chainLabels(structure)
        write "atoms: " + Atoms.allFromStructure(structure).count

        if (chainsParam == "keep") {
            write "keeping the structure as is / not reducing to chains"
            outFileBaseName = baseFileName
        } else {
            List<String> newChains
            if (chainsParam == "all") {
                write "selecting all the chains"
                newChains = schains
                outFileBaseName = baseFileName + "_all"
            } else {
                newChains = Sutils.split(chainsParam, ",")
                outFileBaseName = baseFileName + "_" + newChains.join(",")
            }

            write "reducing to chains: " + newChains

            structure = PdbUtils.reduceStructureToChains(structure, newChains)
            write "chains (after reduction): " + chainLabels(structure)
            write "atoms (after reduction): " + Atoms.allFromStructure(structure).count
        }

        boolean compress = false
        String outFormat = "pdb"
        String outExt
        if (outFormatParam == "keep") {
            compress = Futils.isCompressed(file)
            outFormat = Futils.realExtension(file)
            outExt = Futils.realExtension(file) + ((compress) ? ".gz" : "")
            if (outFormat == "ent") {
                outFormat = "pdb"
            }
        } else {
            compress = Futils.isCompressed(outFormatParam)
            outFormat = Sutils.removeSuffix(outFormatParam, ".gz")
            outExt = outFormat + ((compress) ? ".gz" : "")
        }

        String outFilePath
        if (outFileParam != null) {
            outFilePath = outFileParam
        } else {
            mkdirs(outdir)
            writeParams(outdir)
            String outFileName = outFileBaseName + "." + outExt
            outFilePath = outdir + "/" + outFileName
        }

        write "Output file: " + Futils.absPath(outFilePath)

        PdbUtils.saveToFile(structure, outFormat, outFilePath, compress)
    }

}
