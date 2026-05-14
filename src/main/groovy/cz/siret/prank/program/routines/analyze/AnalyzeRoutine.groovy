
package cz.siret.prank.program.routines.analyze

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.*
import cz.siret.prank.domain.loaders.ExplicitSitesIndex
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.export.FastaExporter
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.features.implementation.table.AtomTableFeature
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.results.Evaluation
import cz.siret.prank.program.visualization.RenderingModel
import cz.siret.prank.program.visualization.renderers.NewPymolRenderer
import cz.siret.prank.utils.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.ResidueNumber

import cz.siret.prank.program.params.Params

import static cz.siret.prank.domain.Dataset.LigandDefinition
import static cz.siret.prank.geom.Struct.getAuthorId

import javax.annotation.Nullable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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
        "binding-sites" : { cmdBindingSites() },
        "binding-site-centers" : { cmdBindingSiteCenters() },
        "labeled-residues" : { cmdLabeledResidues() },
        "aa-propensities" : { cmdAaPropensities() },
        "atomtype-propensities" : { cmdAtomTypePropensities() },
        "aa-surf-seq-duplets" : { cmdAaSurfSeqDuplets() },
        "aa-surf-seq-triplets" : { cmdAaSurfSeqTriplets() },
        "all-propensities" : { cmdAllPropensities() },
        "conservation" : { cmdConservation() },
        "proteins" : { cmdProteins() },
        "parse-proteins" : { cmdParseProteins() },
        "chains" : { cmdChains() },
        "chains-residues" : { cmdChainsResidues() },
        "fasta-raw" : { cmdFastaRaw() },
        "fasta-masked" : { cmdFastaMasked() },
        "peptides" : { cmdPeptides() },
        "convert-dataset-to-atomid" : { cmdConvertContactresDataset() },
        "print-volsite-table" : { print_volsite_table() },
        "cofactors" : { cmdCofactors() }
    ])

//===========================================================================================================//

    /**
     * Write out residue details
     *
     * Similar to cmdChainsResidues but add binding info and produces only one csv per protein.
     */
    void cmdResidues() {

        double residueCutoff = params.ligand_protein_contact_distance

        def res = dataset.processItems { Dataset.Item item ->
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

        write res.writeErrorsAndGetSummary(outdir)
    }


    /**
     * Write out binding residue keys
     */
    void cmdBindingResidues() {

        double bindingCutoff = params.ligand_protein_contact_distance

        StringBuffer summary = new StringBuffer()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            Atoms bindingAtoms = p.proteinAtoms.cutoutShell(p.allRelevantLigandAtoms, bindingCutoff)
            List<String> bindingResidueCodes = bindingAtoms.distinctGroups.collect { it.residueNumber.printFull() }.toSet().toSorted()

            String msg = "Protein [$p.name]  ligands: $p.ligandCount  bindingAtoms: $bindingAtoms.count  bindingResidues: ${bindingResidueCodes.size()}"
            log.info msg
            summary << msg + "\n"

            String outf = "$outdir/${p.name}_binding-residues.txt"
            writeFile outf, bindingResidueCodes.join("\n")
        }

        write res.writeErrorsAndGetSummary(outdir)
        write "\n" + summary.toString()

    }

    /**
     * Binding site statistics - works for both ligand-based and explicit site datasets.
     * Produces a unified CSV with the same header regardless of site source.
     */
    void cmdBindingSites() {
        DataTable dt = new DataTable("protein",
                "site_label", "site_type",
                "n_atoms", "n_residues", "site_radius", "residue_ids",
                "center_x", "center_y", "center_z",
                "lig_name", "lig_code", "lig_chain",
                "contact_dist", "center_to_prot_dist"
        )

        boolean hasExplicitSites = dataset.hasExplicitSites()

        // Ligand-specific counters
        AtomicInteger totalIgnored = new AtomicInteger()
        AtomicInteger totalSmall = new AtomicInteger()
        AtomicInteger totalDistant = new AtomicInteger()

        // Explicit-site-specific counters
        AtomicInteger totalSkippedSites = new AtomicInteger()
        AtomicInteger totalUnresolvedResidues = new AtomicInteger()

        Queue<String> itemsWithoutSites = new ConcurrentLinkedQueue<>()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            if (hasExplicitSites) {
                ExplicitSitesIndex index = dataset.explicitSitesIndex
                List<ExplicitSitesIndex.SiteDef> defs = index.getDefsForProtein(item.proteinFile)
                List<ResidueSite> sites = (p.sites ?: []) as List<ResidueSite>

                if (sites.isEmpty()) {
                    itemsWithoutSites.add(item.row)
                }

                // Track unresolved: compare defs vs resolved sites
                Map<String, ResidueSite> resolvedByName = new HashMap<>()
                for (ResidueSite site : sites) {
                    resolvedByName.put(site.name, site)
                }
                for (ExplicitSitesIndex.SiteDef sd : defs) {
                    ResidueSite resolved = resolvedByName.get(sd.siteId)
                    if (resolved == null) {
                        totalSkippedSites.incrementAndGet()
                        totalUnresolvedResidues.addAndGet(sd.residueIds.size())
                    } else {
                        int unresolved = sd.residueIds.size() - resolved.residues.size()
                        if (unresolved > 0) {
                            totalUnresolvedResidues.addAndGet(unresolved)
                        }
                    }
                }

                for (ResidueSite site : sites) {
                    Atom c = site.centroid

                    dt.newRow(item.label)
                            .put("site_label", site.label)
                            .put("site_type", "explicit")
                            .put("n_atoms", site.atoms.count)
                            .put("n_residues", site.residues.size())
                            .put("site_radius", siteRadius(c, site.atoms))
                            .put("residue_ids", formatResidueIds(site.residues))
                            .put("center_x", c.x)
                            .put("center_y", c.y)
                            .put("center_z", c.z)
                }
            } else {
                if (p.relevantLigands.isEmpty()) {
                    itemsWithoutSites.add(item.row)
                }
                double cutoff = params.ligand_protein_contact_distance
                for (Ligand lig : p.relevantLigands) {
                    Atom c = lig.centroid
                    Atoms contactAtoms = p.proteinAtoms.cutoutShell(lig.atoms, cutoff)
                    List<Residue> contactResidues = p.residues.getDistinctForAtoms(contactAtoms)

                    dt.newRow(item.label)
                            .put("site_label", lig.label)
                            .put("site_type", "ligand")
                            .put("n_atoms", lig.size)
                            .put("n_residues", contactResidues.size())
                            .put("site_radius", siteRadius(c, lig.atoms))
                            .put("residue_ids", formatResidueIds(contactResidues))
                            .put("center_x", c.x)
                            .put("center_y", c.y)
                            .put("center_z", c.z)
                            .put("lig_name", lig.name)
                            .put("lig_code", lig.code as String)
                            .put("lig_chain", lig.chain)
                            .put("contact_dist", lig.contactDistance)
                            .put("center_to_prot_dist", lig.centerToProteinDist)
                }

                totalIgnored.addAndGet(p.ligands.ignoredLigandCount)
                totalSmall.addAndGet(p.ligands.smallLigandCount)
                totalDistant.addAndGet(p.ligands.distantLigandCount)
            }

            if (params.visualizations) {
                BinaryLabeling labeling
                List<Atom> centroids = new ArrayList<>()
                if (hasExplicitSites) {
                    // Build labeling from resolved site residues
                    Set<Residue> siteResidues = new HashSet<>()
                    for (ResidueSite site : ((p.sites ?: []) as List<ResidueSite>)) {
                        siteResidues.addAll(site.residues)
                        centroids.add(site.centroid)
                    }
                    labeling = new BinaryLabeling(p.residues.count)
                    for (Residue r : p.residues) {
                        labeling.add(r, siteResidues.contains(r))
                    }
                } else {
                    labeling = item.binaryLabeling
                    for (Ligand lig : p.relevantLigands) {
                        centroids.add(lig.centroid)
                    }
                }
                if (labeling != null) {
                    new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                            proteinFile: item.proteinFile,
                            label: item.label,
                            protein: p,
                            observedLabeling: labeling,
                            siteCentroids: centroids,
                            cofactorResult: p.cofactorExtractionResult
                    )).render()
                }
            }
        }

        writeFile "$outdir/binding_sites.csv", dt.toCsv()

        Map<String, Object> extraInfo = new LinkedHashMap<>()
        int noSiteCount = itemsWithoutSites.size()
        if (hasExplicitSites) {
            extraInfo.put("Site source:", "explicit")
            extraInfo.put("Sites format:", dataset.attributes.get(Dataset.PARAM_EXPLICIT_SITES_FORMAT))
            extraInfo.put("Sites file:", dataset.attributes.get(Dataset.PARAM_EXPLICIT_SITES_FILE))
            extraInfo.put("Proteins with sites:", dataset.size - noSiteCount - res.errorCount)
            extraInfo.put("Proteins without sites:", noSiteCount)
            extraInfo.put("Sites skipped (no residues):", totalSkippedSites.get())
            extraInfo.put("Unresolved residues:", totalUnresolvedResidues.get())
        } else {
            extraInfo.put("Site source:", "ligands")
            extraInfo.put("Proteins without ligands:", noSiteCount)
            extraInfo.put("Ignored ligands:", totalIgnored.get())
            extraInfo.put("Small ligands:", totalSmall.get())
            extraInfo.put("Distant ligands:", totalDistant.get())
        }
        extraInfo.put("Errors:", res.errorCount)

        Set<String> noSummary = ["center_x", "center_y", "center_z"] as Set
        String summary = dt.formatSummaryTable("Binding Sites Summary", extraInfo, noSummary)
        write summary
        writeFile "$outdir/binding_sites_summary.txt", summary

        if (!itemsWithoutSites.isEmpty()) {
            String noSitesFile = "$outdir/items_without_sites.txt"
            writeFile noSitesFile, itemsWithoutSites.toSorted().join("\n") + "\n"
            write "NOTE: $noSiteCount of ${dataset.size} items have no binding sites. List written to [$noSitesFile]"
        }

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    private static String formatResidueIds(List<Residue> residues) {
        residues.collect { Residue r ->
            r.chain.authorId + "_" + r.residueNumber.seqNum + (r.residueNumber.insCode ?: "")
        }.join(" ")
    }

    private static double siteRadius(Atom centroid, Atoms atoms) {
        return Evaluation.siteRadius(centroid, atoms)
    }

    /**
     * Analyzes binding site centers by computing each valid SiteCenterMethod for every site
     * and reporting distances between methods, to SAS surface, and to protein atoms.
     *
     * Produces:
     *  - binding_site_centers.csv - all results in one table
     *  - binding_site_centers_{method}.csv - per-method tables
     *  - binding_site_centers_summary.txt - overall + per-method distance statistics
     */
    void cmdBindingSiteCenters() {
        List<String> distColumns = ["dist_to_atom_com", "dist_to_sas", "dist_to_protein"]

        DataTable dt = new DataTable("protein",
                "site_label", "site_type", "method",
                "center_x", "center_y", "center_z",
                "dist_to_atom_com", "dist_to_sas", "dist_to_protein"
        )

        boolean hasExplicitSites = dataset.hasExplicitSites()

        AtomicInteger totalIgnored = new AtomicInteger()
        AtomicInteger totalSmall = new AtomicInteger()
        AtomicInteger totalDistant = new AtomicInteger()
        AtomicInteger totalSkippedSites = new AtomicInteger()

        Queue<String> itemsWithoutSites = new ConcurrentLinkedQueue<>()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            p.calcuateSurfaceAndExposedAtoms()

            List<BindingSite> sites = p.sites

            if (sites.isEmpty()) {
                itemsWithoutSites.add(item.row)
            }
            if (!hasExplicitSites) {
                totalIgnored.addAndGet(p.ligands.ignoredLigandCount)
                totalSmall.addAndGet(p.ligands.smallLigandCount)
                totalDistant.addAndGet(p.ligands.distantLigandCount)
            }

            for (BindingSite site : sites) {
                boolean isLigand = site instanceof Ligand
                String siteType = isLigand ? "ligand" : "explicit"

                Atom baselineCenter = site.getCenterForMethod(SiteCenterMethod.atoms_center_of_mass)

                for (SiteCenterMethod method : SiteCenterMethod.values()) {
                    if (isLigand && !method.supportedForLigandSites) continue
                    if (!isLigand && !method.supportedForExplicitSites) continue

                    Atom center = site.getCenterForMethod(method)
                    if (center == null) continue

                    double distToAtomCom = baselineCenter != null ? Struct.dist(center, baselineCenter) : Double.NaN
                    double distToSas = p.accessibleSurface.points.dist(center)
                    double distToProtein = p.proteinAtoms.dist(center)

                    dt.newRow(item.label)
                            .put("site_label", site.label)
                            .put("site_type", siteType)
                            .put("method", method.name())
                            .put("center_x", center.x)
                            .put("center_y", center.y)
                            .put("center_z", center.z)
                            .put("dist_to_atom_com", distToAtomCom)
                            .put("dist_to_sas", distToSas)
                            .put("dist_to_protein", distToProtein)
                }
            }
        }

        writeFile "$outdir/binding_site_centers.csv", dt.toCsv()

        // Write per-method CSVs
        for (String method : dt.distinctValues("method")) {
            DataTable methodDt = dt.filter("method", method)
            writeFile "$outdir/binding_site_centers_${method}.csv", methodDt.toCsv()
        }

        // Build text summary
        Map<String, Object> extraInfo = new LinkedHashMap<>()
        int noSiteCount = itemsWithoutSites.size()
        if (hasExplicitSites) {
            extraInfo.put("Site source:", "explicit")
            extraInfo.put("Proteins without sites:", noSiteCount)
            extraInfo.put("Sites skipped:", totalSkippedSites.get())
        } else {
            extraInfo.put("Site source:", "ligands")
            extraInfo.put("Proteins without ligands:", noSiteCount)
            extraInfo.put("Ignored ligands:", totalIgnored.get())
            extraInfo.put("Small ligands:", totalSmall.get())
            extraInfo.put("Distant ligands:", totalDistant.get())
        }
        extraInfo.put("Errors:", res.errorCount)

        Set<String> noSummary = ["center_x", "center_y", "center_z"] as Set
        String summary = dt.formatSummaryTable("Binding Site Centers Summary", extraInfo, noSummary)
        summary += dt.formatGroupedSummaryTable("method", distColumns, "Distance Statistics by Center Method")

        write summary
        writeFile "$outdir/binding_site_centers_summary.txt", summary

        if (!itemsWithoutSites.isEmpty()) {
            String noSitesFile = "$outdir/items_without_sites.txt"
            writeFile noSitesFile, itemsWithoutSites.toSorted().join("\n") + "\n"
            write "NOTE: $noSiteCount of ${dataset.size} items have no binding sites. List written to [$noSitesFile]"
        }

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    void cmdPeptides() {
        LoaderParams.ignoreLigandsSwitch = true

        StringBuffer csv = new StringBuffer("protein, pept_count, peptides\n")
        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein
            String ps = p.peptides.collect { "($it.authorId,$it.length)" }.join(" ")
            csv << "$p.name, ${p.peptides.size()}, $ps\n"
        }
        writeFile "$outdir/peptides.csv", csv
        write res.writeErrorsAndGetSummary(outdir)
        write csv.toString()
    }

    /**
     * Protein-level statistics
     */
    void cmdProteins() {
        DataTable dt = new DataTable("protein",
                "n_chains_total", "n_poly_chains", "n_protein_chains",
                "n_residues", "n_protein_atoms", "n_all_atoms",
                "n_relevant_ligands", "n_other_ligands", "n_peptides",
                "protein_chain_ids"
        )

        Queue<String> withProteinChains = new ConcurrentLinkedQueue<>()
        Queue<String> withoutProteinChains = new ConcurrentLinkedQueue<>()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            dt.newRow(item.label)
                .put("n_chains_total",     p.structure.chains.size())
                .put("n_poly_chains",      p.structure.polyChains.size())
                .put("n_protein_chains",   p.residueChains.size())
                .put("n_residues",         p.residues.size())
                .put("n_protein_atoms",    p.proteinAtoms.count)
                .put("n_all_atoms",        p.allAtoms.count)
                .put("n_relevant_ligands", p.relevantLigands.size())
                .put("n_other_ligands",    p.allIgnoredLigands.size())
                .put("n_peptides",         p.peptides.size())
                .put("protein_chain_ids",  p.residueChains.collect { it.authorId }.join(" "))

            if (p.residueChains.empty) {
                withoutProteinChains.add(item.row)
            } else {
                withProteinChains.add(item.row)
            }
        }

        writeFile "$outdir/proteins.csv", dt.toCsv()

        // Write split dataset files if some structures have no protein chains
        if (!withoutProteinChains.empty) {
            String headerLine = dataset.header.size() > 1 ? "HEADER: " + dataset.header.join(" ") + "\n\n" : ""

            String withFile = "$outdir/${dataset.label}_with_protein_chains.ds"
            writeFile withFile,
                    "# Structures from ${dataset.name} that contain protein chains\n\n" +
                    headerLine +
                    withProteinChains.toSorted().join("\n") + "\n"

            String withoutFile = "$outdir/${dataset.label}_without_protein_chains.ds"
            writeFile withoutFile,
                    "# Structures from ${dataset.name} that have no protein chains\n\n" +
                    headerLine +
                    withoutProteinChains.toSorted().join("\n") + "\n"

            write ""
            write "NOTE: ${withoutProteinChains.size()} of ${dataset.size} structures have no protein chains. Split dataset files have been written to:\n"
            write "  Structures with protein chains:    $withFile"
            write "  Structures without protein chains: $withoutFile"
        }

        String summary = dt.formatSummaryTable("Protein Dataset Summary",
                ["No protein chains:": dt.countWhere("n_protein_chains", 0),
                 "Errors:": res.errorCount] as Map<String, Object>)
        write summary
        writeFile "$outdir/proteins_summary.txt", summary

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Parse all proteins in the dataset and report errors.
     */
    void cmdParseProteins() {
        LoaderParams.ignoreLigandsSwitch = true  // no need to load ligands for this

        def res = dataset.processItems { Dataset.Item item ->
            item.protein
        }

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Chain statistics
     */
    void cmdChains() {
        LoaderParams.ignoreLigandsSwitch = true

        DataTable dt = new DataTable("protein",
                "n_chains", "chain_id", "mmcif_id", "n_residues",
                "residue_string"
        )

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            int nchains = p.residueChains.size()
            for (ResidueChain chain : p.residueChains) {
                dt.newRow(item.label)
                        .put("n_chains", nchains)
                        .put("chain_id", chain.authorId)
                        .put("mmcif_id", chain.mmcifId)
                        .put("n_residues", chain.length)
                        .put("residue_string", chain.biojavaCodeCharString)
            }
        }

        writeFile "$outdir/chains.csv", dt.toCsv()

        Set<String> noSummary = ["residue_string"] as Set
        String summary = dt.formatSummaryTable("Chains Summary",
                ["Errors:": res.errorCount] as Map<String, Object>, noSummary)
        write summary
        writeFile "$outdir/chains_summary.txt", summary

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Chain statistics
     */
    void cmdChainsResidues() {
        cmdChains()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            int idx = 1
            for (ResidueChain chain : p.residueChains) {

                List<String> csvRows = []
                for (Residue res : chain.residues) {
                    ResidueNumber rn = res.residueNumber
                    csvRows.add("$rn.chainName, $rn.seqNum, $rn.insCode, $res.key, $res.chainMmcifId, $res.atoms.count, $res.secStruct " as String)
                }

                String csv = "chain_name, seq_num, ins_code, key, chain_mmcif_id, atoms, sec_struct_type\n" +
                        csvRows.toSorted().collect { it + "\n" }.join("")
                String strIdx = String.format("%02d", idx++)
                writeFile "$outdir/${item.label}_${strIdx}_${chain.authorId}_${chain.mmcifId}_residues.csv", csv
            }
        }

        write res.writeErrorsAndGetSummary(outdir)
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
        LoaderParams.ignoreLigandsSwitch = true
        FastaExporter exporter = FastaExporter.getInstance()

        write "exporting fasta (masked: $masked)"

        def res = dataset.processItems { Dataset.Item item ->
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

        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Statistics about binary residue labeling + visualizations
     */
    void cmdLabeledResidues() {
        // assert dataset.hasExplicitResidueLabeling()
        LoaderParams.ignoreLigandsSwitch = true

        def labeler = dataset.binaryResidueLabeler

        if (labeler instanceof SprintLabelingLoader) {
            printSprintChains((SprintLabelingLoader)labeler)
        }

        List<String> csvRows = newSynchronizedList()
        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            BinaryLabeling labeling = labeler.getBinaryLabeling(p.residues, p)
            def s = BinaryLabelings.getStats(labeling)

            int nchains = p.residueChains.size()
            String chainIds = p.residueChains.collect { it.authorId }.join(" ")
            int nres = p.residues.size()
            int nlabres = s.total
            csvRows.add("${item.label}, $nchains, $chainIds, $nres, $nlabres, ${s.positives}, ${s.negatives}, ${s.unlabeled}" as String)

            if (params.visualizations) {
                new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: item.protein,
                        observedLabeling: labeling,
                        cofactorResult: item.protein.cofactorExtractionResult
                )).render()
            }
        }

        String csv = "protein, n_chains, chain_ids, n_residues, n_residues_in_labeling, positives, negatives, unlabeled\n" +
                csvRows.toSorted().collect { it + "\n" }.join("")
        writeFile "$outdir/residue_stats.csv", csv
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Analyze conservation scores per chain.
     * Produces a CSV with per-chain conservation loading info: whether conservation was loaded,
     * the conservation file path, and how many residues were matched.
     */
    void cmdConservation() {
        LoaderParams.ignoreLigandsSwitch = true

        DataTable dt = new DataTable("protein",
                "chain_id", "mmcif_id", "n_residues",
                "conserv_loaded", "conserv_file", "conserv_matched_residues",
                "residue_string"
        )

        AtomicInteger fullyMatchedChains = new AtomicInteger()
        AtomicInteger fullyMatchedChainItems = new AtomicInteger()
        AtomicInteger failedChains = new AtomicInteger()
        AtomicInteger failedChainItems = new AtomicInteger()
        AtomicInteger partialChains = new AtomicInteger()
        AtomicInteger partialChainItems = new AtomicInteger()

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            // Load conservation scores (graceful - null on failure)
            ConservationScore conservScore = null
            try {
                conservScore = p.loadConservationScores(item.context)
            } catch (Exception e) {
                log.warn "Failed to load conservation for [{}]: {}", item.label, e.message
            }

            boolean itemHasFullyMatchedChain = false
            boolean itemHasFailedChain = false
            boolean itemHasPartialChain = false

            for (ResidueChain chain : p.residueChains) {
                def row = dt.newRow(item.label)
                        .put("chain_id", chain.authorId)
                        .put("mmcif_id", chain.mmcifId)
                        .put("n_residues", chain.length)
                        .put("residue_string", chain.biojavaCodeCharString)

                ConservationScore.ChainConservationInfo ci = conservScore?.chainInfoMap?.get(chain.authorId)
                if (ci != null) {
                    row.put("conserv_loaded", ci.loaded ? 1 : 0)
                            .put("conserv_file", ci.scoreFile?.absolutePath ?: "")
                            .put("conserv_matched_residues", ci.matchedResidues)

                    if (!ci.loaded) {
                        failedChains.incrementAndGet()
                        itemHasFailedChain = true
                    } else if (ci.matchedResidues < ci.chainResidues) {
                        partialChains.incrementAndGet()
                        itemHasPartialChain = true
                    } else {
                        fullyMatchedChains.incrementAndGet()
                        itemHasFullyMatchedChain = true
                    }
                } else {
                    row.put("conserv_loaded", 0)
                            .put("conserv_file", "")
                            .put("conserv_matched_residues", 0)
                    failedChains.incrementAndGet()
                    itemHasFailedChain = true
                }
            }

            if (itemHasFullyMatchedChain) fullyMatchedChainItems.incrementAndGet()
            if (itemHasFailedChain) failedChainItems.incrementAndGet()
            if (itemHasPartialChain) partialChainItems.incrementAndGet()

            if (params.visualizations && conservScore != null) {
                ResidueLabeling<Double> labeling = conservScore.toDoubleLabeling(p)
                new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: p,
                        doubleLabeling: labeling,
                        cofactorResult: p.cofactorExtractionResult
                )).render()
            }
        }

        writeFile "$outdir/conservation.csv", dt.toCsv()

        Map<String, Object> extraInfo = new LinkedHashMap<>()
        extraInfo.put("Fully matched chains:", "${fullyMatchedChains.get()} in ${fullyMatchedChainItems.get()} dataset items")
        extraInfo.put("Partially matched chains:", "${partialChains.get()} in ${partialChainItems.get()} dataset items")
        extraInfo.put("Failed chains:", "${failedChains.get()} in ${failedChainItems.get()} dataset items")
        extraInfo.put("Failed to load dataset items:", res.errorCount)

        Set<String> noSummary = ["residue_string", "conserv_file"] as Set
        String summary = dt.formatSummaryTable("Conservation Summary", extraInfo, noSummary, "Total chains:")
        write summary
        writeFile "$outdir/conservation_summary.txt", summary

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
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

        def res = dataset.processItems { Dataset.Item item ->
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

        write res.writeErrorsAndGetSummary(outdir)
        BinCounter<AA> counter = BinCounter.join(counters)
        savePropensities("$outdir/aa-propensity.csv", counter)
    }

    private void cmdAtomTypePropensities() {
        List<BinCounter<String>> counters = newSynchronizedList()

        boolean exposedOnly = true // TODO make a configurable param for ions

        def res = dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler

            Atoms atoms = exposedOnly ? prot.exposedAtoms : prot.proteinAtoms
            Atoms ligandAtoms = prot.allRelevantLigandAtoms.withKdTree()

            def counter = new BinCounter<String>()

            atoms.each { Atom atom ->
                String atomCode = PdbUtils.getAtomTypeInResidueCode(atom)
                boolean isBinding = ligandAtoms.areWithinDistance(atom, params.ligand_protein_contact_distance)
                counter.add(atomCode, isBinding)
            }

            counters.add(counter)
        }

        write res.writeErrorsAndGetSummary(outdir)
        BinCounter<String> counter = BinCounter.join(counters)
        savePropensities("$outdir/atomtype-propensity.csv", counter)
    }


    /**
     * ordering dependent sequence duplets (only starting from exposed residues)
     */
    private void cmdAaSurfSeqDuplets() {
        List<BinCounter<String>> counters = newSynchronizedList()

        def res = dataset.processItems { Dataset.Item item ->
            Protein prot = item.protein
            ResidueLabeler<Boolean> labeler = dataset.binaryResidueLabeler
            BinaryLabeling labeling = labeler.getBinaryLabeling(prot.exposedResidues, prot)    // TODO not always only exposed!

            def counter = new BinCounter<String>()

            labeling.labeledResidues.each { LabeledResidue<Boolean> lres ->
                def r = lres.residue
                def prev = r.previousInChain
                def next = r.nextInChain

                // in each direction
                counter.add(Residue.safeOrderedCode2(r, prev), lres.label)
                counter.add(Residue.safeOrderedCode2(r, next), lres.label)
            }

            counters.add(counter)
        }

        write res.writeErrorsAndGetSummary(outdir)
        savePropensities("$outdir/duplets.csv", BinCounter.join(counters))
    }

    /**
     * sequence triplets (only from exposed residues)
     */
    private void cmdAaSurfSeqTriplets() {
        List<BinCounter<String>> counters = newSynchronizedList()

        def res = dataset.processItems { Dataset.Item item ->
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

        write res.writeErrorsAndGetSummary(outdir)
        savePropensities("$outdir/triplets.csv", BinCounter.join(counters))
    }

    /**
     * Runs all propensity calculations
     *   - aa-propensities
     *   - atomtype-propensities
     *   - aa-surf-seq-duplets
     *   - aa-surf-seq-triplets
     */
    private void cmdAllPropensities() {
        cmdAaPropensities()
        cmdAtomTypePropensities()
        cmdAaSurfSeqDuplets()
        cmdAaSurfSeqTriplets()
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

        def res = dataset.processItems { Dataset.Item item ->
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
        write res.writeErrorsAndGetSummary(outdir)
    }

    /**
     * Survey HETATM groups and dry-run cofactor specifiers.
     *
     * <p>Without {@code -cofactors}: lists every distinct HETATM group instance with
     * chain/residue/atoms for discovery (which names exist? which to use as cofactor
     * specifiers?).
     *
     * <p>With {@code -cofactors} (or a {@code cofactors} column in the dataset): additionally
     * reports which specifiers matched which groups, using the same per-item resolution as
     * pocket prediction would (column overrides global). Lets users verify precise specifiers
     * before committing to a long-running run.
     */
    void cmdCofactors() {
        DataTable dt = new DataTable("protein",
                "het_name", "chain", "res_num", "group_id",
                "n_heavy_atoms", "dist_to_protein",
                "currently_classified_as", "would_be_cofactor"
        )

        // Aggregates across the dataset
        Map<String, Set<String>> nameToStructures = new java.util.concurrent.ConcurrentHashMap<>()
        Map<String, java.util.concurrent.atomic.AtomicInteger> nameToGroupCount = new java.util.concurrent.ConcurrentHashMap<>()
        Map<String, Set<String>> specToStructures = new java.util.concurrent.ConcurrentHashMap<>()
        Map<String, java.util.concurrent.atomic.AtomicInteger> specToGroupCount = new java.util.concurrent.ConcurrentHashMap<>()
        java.util.concurrent.atomic.AtomicInteger itemsWithSpecifiers = new java.util.concurrent.atomic.AtomicInteger()

        DataTable mt = new DataTable("protein",
                "specifier", "matched_count", "matched_group_ids", "unmatched_reason")

        def res = dataset.processItems { Dataset.Item item ->
            Protein p = item.protein

            // Per-item resolved specifiers - same resolution as protein loading does.
            // Either the dataset's `cofactors` column (if present) or the global Params.inst.cofactors.
            List<LigandDefinition> itemSpecifiers = dataset.resolveCofactorDefinitions(item)
            if (!itemSpecifiers.isEmpty()) itemsWithSpecifiers.incrementAndGet()

            // Index cofactor-matched groups for fast classification
            Set<Group> cofactorMatched = Collections.newSetFromMap(new IdentityHashMap<>())
            if (p.cofactorExtractionResult != null) {
                for (List<Group> gs : p.cofactorExtractionResult.foundGroups.values()) {
                    cofactorMatched.addAll(gs)
                }
            }

            // Index relevant + ignored ligand groups
            Set<Group> relevantLigGroups = Collections.newSetFromMap(new IdentityHashMap<>())
            Set<Group> ignoredLigGroups = Collections.newSetFromMap(new IdentityHashMap<>())
            for (Ligand lig : p.relevantLigands ?: []) {
                relevantLigGroups.addAll(lig.atoms.distinctGroups)
            }
            for (Ligand lig : p.allIgnoredLigands ?: []) {
                ignoredLigGroups.addAll(lig.atoms.distinctGroups)
            }

            // Per-specifier per-structure match tracking
            Map<String, List<String>> matchedGroupIdsBySpec = new LinkedHashMap<>()
            for (LigandDefinition d : itemSpecifiers) matchedGroupIdsBySpec.put(d.originalString, new ArrayList<>())

            for (Group g : Struct.getHetGroups(p.structure)) {
                String name = g.PDBName?.toUpperCase()
                if (name == null) continue

                nameToGroupCount.computeIfAbsent(name, { new java.util.concurrent.atomic.AtomicInteger() } as java.util.function.Function).incrementAndGet()
                nameToStructures.computeIfAbsent(name, { (Set<String>) (java.util.concurrent.ConcurrentHashMap.newKeySet()) } as java.util.function.Function).add(item.label)

                String chain = getAuthorId(g.chain)
                String resNum = g.residueNumber?.printFull() ?: "?"
                String groupId = "${chain}_${resNum}"
                Atoms ga = Atoms.allFromGroup(g).withoutHydrogens()

                String cls
                if (cofactorMatched.contains(g)) cls = "cofactor"
                else if (relevantLigGroups.contains(g)) cls = "relevant_ligand"
                else if (ignoredLigGroups.contains(g)) cls = "ignored"
                else cls = "other"

                int wouldBeCofactor = 0
                for (LigandDefinition d : itemSpecifiers) {
                    if (d.matchesGroup(g, p)) {
                        wouldBeCofactor = 1
                        specToGroupCount.computeIfAbsent(d.originalString, { new java.util.concurrent.atomic.AtomicInteger() } as java.util.function.Function).incrementAndGet()
                        specToStructures.computeIfAbsent(d.originalString, { (Set<String>) (java.util.concurrent.ConcurrentHashMap.newKeySet()) } as java.util.function.Function).add(item.label)
                        matchedGroupIdsBySpec.get(d.originalString).add(groupId)
                    }
                }

                DataTable.Row r = dt.newRow(item.label)
                r.put("het_name", name)
                r.put("chain", chain)
                r.put("res_num", resNum)
                r.put("group_id", groupId)
                r.put("n_heavy_atoms", ga.count)
                if (ga.empty) {
                    r.put("dist_to_protein", "")
                } else {
                    r.put("dist_to_protein", p.proteinAtoms.dist(ga))
                }
                r.put("currently_classified_as", cls)
                r.put("would_be_cofactor", itemSpecifiers.isEmpty() ? "" : String.valueOf(wouldBeCofactor))
            }

            // cofactor_matches.csv row(s) for this structure
            for (LigandDefinition d : itemSpecifiers) {
                List<String> matched = matchedGroupIdsBySpec.get(d.originalString)
                String reason = ""
                if (matched.isEmpty()) {
                    boolean nameInStructure = Struct.getHetGroups(p.structure)
                            .any { ((Group) it).PDBName?.toUpperCase() == d.groupName?.toUpperCase() }
                    reason = nameInStructure ? "name present but specifier filter excluded all instances"
                                             : "name not in structure"
                }
                DataTable.Row mr = mt.newRow(item.label)
                mr.put("specifier", d.originalString)
                mr.put("matched_count", matched.size())
                mr.put("matched_group_ids", matched.join(" "))
                mr.put("unmatched_reason", reason)
            }

            // Visualizations - renderer reads cofactorResult and emits per-name selections.
            if (params.visualizations) {
                new NewPymolRenderer("$outdir/visualizations", new RenderingModel(
                        proteinFile: item.proteinFile,
                        label: item.label,
                        protein: p,
                        cofactorResult: p.cofactorExtractionResult
                )).render()
            }
        }

        writeFile "$outdir/het_groups.csv", dt.toCsv()

        boolean anySpecifiers = itemsWithSpecifiers.get() > 0

        StringBuilder summary = new StringBuilder()
        summary << "HETATM Survey for ${dataset.name} (${dataset.size} structures)\n\n"
        summary << "Most frequent HETATM groups:\n"
        nameToStructures.entrySet()
                .toSorted { -it.value.size() }
                .each { e ->
                    int nStruct = e.value.size()
                    int nGroups = nameToGroupCount.get(e.key).get()
                    double pct = (100.0d * nStruct) / Math.max(1, dataset.size)
                    summary << String.format("  %-8s %4d structures (%5.1f%%) - %d groups total\n",
                            e.key, nStruct, pct, nGroups)
                }

        if (anySpecifiers) {
            summary << "\nCofactor specifier match (per-item resolution, column overrides global):\n"
            specToStructures.entrySet()
                    .toSorted { -it.value.size() }
                    .each { e ->
                        int nStruct = e.value.size()
                        int nGroups = specToGroupCount.get(e.key).get()
                        String marker = nStruct == 0 ? "   ← matched no structures" : ""
                        summary << String.format("  %-30s %d/%d structures, %d groups total%s\n",
                                e.key, nStruct, dataset.size, nGroups, marker)
                    }
            writeFile "$outdir/cofactor_matches.csv", mt.toCsv()
        }

        String summaryStr = summary.toString()
        writeFile "$outdir/het_groups_summary.txt", summaryStr
        write summaryStr

        write "Processed ${dataset.size} items"
        write res.writeErrorsAndGetSummary(outdir)
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
