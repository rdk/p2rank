package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.labeling.ResidueLabelings
import cz.siret.prank.domain.loaders.AhojSiteInfo
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import cz.siret.prank.prediction.pockets.criteria.*
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.MathUtils
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.biojava.nbio.structure.Group

import javax.annotation.concurrent.ThreadSafe
import java.util.function.Function

import static cz.siret.prank.geom.Atoms.intersection
import static cz.siret.prank.geom.Atoms.union
import static cz.siret.prank.utils.Cutils.head
import static cz.siret.prank.utils.Cutils.newSynchronizedList
import static cz.siret.prank.utils.Formatter.*
import static cz.siret.prank.utils.Futils.writeFile
import static java.util.Collections.emptyList

/**
 * Represents evaluation of pocket prediction on a dataset of proteins
 *
 * Allows to collect results for a set of different pocket identification success criteria simultaneously.
 */
@ThreadSafe
@Slf4j
@CompileStatic
class Evaluation implements Parametrized {

    /** cutoff distance in A around ligand atoms that determines which SAS points cover the ligand */
    final double LIG_SAS_CUTOFF = params.ligand_induced_volume_cutoff  

    PocketCriterion canonicalCriterion = new DCA("DCA_4", 4.0d)
    PocketCriteria criteria

    List<ProteinRow> proteinRows = newSynchronizedList(1024)
    List<LigRow> ligandRows = newSynchronizedList(4 * 1024)
    List<PocketRow> pocketRows = newSynchronizedList(16 * 1024)
    List<ResidueRow> residueRows = newSynchronizedList(16 * 1024)

    List<Double> bindingConservationScores = newSynchronizedList()
    List<Double> nonBindingConservationScores = newSynchronizedList()

    long proteinCount
    long pocketCount

    long ligandCount
    long ignoredLigandCount
    long smallLigandCount
    long distantLigandCount

    long ligSASPointsCount
    long ligSASPointsCoveredCount
    double ligSASPointsScoreSum

    Evaluation(List<PocketCriterion> criteria) {
        this.criteria = new PocketCriteria(criteria)
    }

    Evaluation() {
        this( getDefaultEvalCriteria() )
    }

    void sort() {
        proteinRows = proteinRows.sort { it.name }
        ligandRows = ligandRows.sort { it.protName + "_" + it.ligName + "_" + it.ligCode }
        pocketRows = pocketRows.sort { it.protName + "_" + it.pocketName }
    }

    /**
     * Pocket closest to {@code site} under DCA semantics. Honors
     * {@code site_eval_sas_pts_as_atoms} the same way {@link DCA} does so the
     * reported {@code closestPocketDist} matches the criterion that other
     * eval columns are computed under.
     *
     * <p>Package-visible for the regression test in {@code EvaluationClosestPocketTest}.
     */
    static Pocket closestPocket(BindingSite site, List<Pocket> pockets) {
        if (pockets.empty) return null

        Atoms sitePoints = sitePointsForEval(site)
        Pocket res = null
        double minDist = Double.MAX_VALUE

        for (Pocket p : pockets) {
            if (p.centroid == null) continue
            double dist = sitePoints.dist(p.centroid)
            if (dist < minDist) {
                minDist = dist
                res = p
            }
        }

        return res
    }

    /**
     * Mirrors {@link DCA#getSitePoints}. {@code site_eval_sas_pts_as_atoms}
     * selects whether DCA-family distances measure against the site's atom
     * set or its SAS-point set.
     */
    private static Atoms sitePointsForEval(BindingSite site) {
        return Params.inst.site_eval_sas_pts_as_atoms ? site.sasPoints : site.atoms
    }

    private static double getAvgConservationForAtoms(Atoms atoms, ConservationScore score) {
        def distinctGroups = atoms.distinctGroups

        if (distinctGroups.empty) {
            return 0.0
        }

        return avg(distinctGroups, { Group group -> score.getScoreForResidueSafe(group.residueNumber) })
    }

    private static Pocket findPocketForSite(BindingSite site, List<Pocket> pockets,
                                     PocketCriterion criterion, EvalContext context) {
        for (Pocket pocket : pockets) {
            if (criterion.isIdentified(site, pocket, context)) {
                return pocket
            }
        }
        return null
    }

    private void assignPocketsToSites(List<? extends BindingSite> sites, List<Pocket> pockets, EvalContext context) {
        for (BindingSite site : sites) {
            site.predictedPocket = findPocketForSite(site, pockets, canonicalCriterion, context)
        }
    }

    void addPrediction(PredictionPair pair, List<Pocket> pockets) {
        EvalContext context = new EvalContext()
        List<BindingSite> sites = pair.holoProtein.sites
        boolean isLigandMode = !sites.isEmpty() && sites[0] instanceof Ligand

        // Reset per-site cached SAS points so they're recomputed against this prediction's
        // accessibleSurface (the cache is lazy on each BindingSite, not on EvalContext).
        for (BindingSite site : sites) {
            site.sasPoints = null
        }

        assignPocketsToSites(sites, pockets, context)
        addBindingSitePrediction(pair, pockets, sites, isLigandMode, context)
    }

    /**
     * Find which site (if any) identifies the given pocket using the standard criterion.
     */
    private String findSiteForPocket(List<? extends BindingSite> sites, Pocket pocket, EvalContext context) {
        for (BindingSite site : sites) {
            if (canonicalCriterion.isIdentified(site, pocket, context)) {
                return site.label
            }
        }
        return ""
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    private void addBindingSitePrediction(PredictionPair pair, List<Pocket> pockets,
                                          List<? extends BindingSite> sites, boolean isLigandMode,
                                          EvalContext context) {
        List<LigRow> tmpLigRows = new ArrayList<>()
        List<PocketRow> tmpPockets = new ArrayList<>()

        Protein protein = pair.protein
        Atoms sasPoints = pair.prediction.protein.accessibleSurface.points

        // === ProteinRow ===

        ProteinRow protRow = new ProteinRow()
        protRow.name = pair.name
        protRow.atoms = protein.allAtoms.count
        protRow.protAtoms = protein.proteinAtoms.count
        protRow.exposedAtoms = pair.prediction.protein.exposedAtoms.count
        protRow.chains = protein.residueChains.size()
        protRow.chainNames = protein.residueChains.collect { it.authorId }.join(" ")
        protRow.ligands = sites.size()
        protRow.pockets = pair.prediction.pocketCount
        protRow.sasPoints = sasPoints.count

        if (isLigandMode) {
            Ligands ligands = pair.ligands
            protRow.ligNames = ligands.relevantLigands.collect { "$it.name($it.size)" }.join(" ")
            protRow.ignoredLigands = ligands.ignoredLigandCount
            protRow.ignoredLigNames = ligands.ignoredLigands.collect { "$it.name($it.size)" }.join(" ")
            protRow.smallLigands = ligands.smallLigandCount
            protRow.smallLigNames = ligands.smallLigands.collect { "$it.name($it.size)" }.join(" ")
            protRow.distantLigands = ligands.distantLigandCount
            protRow.distantLigNames = ligands.distantLigands.collect { "$it.name($it.size|${format(it.contactDistance,1)}|${format(it.centerToProteinDist,1)})" }.join(" ")
        } else {
            protRow.ligNames = sites.collect { it.label }.join(" ")
            protRow.ignoredLigands = 0
            protRow.ignoredLigNames = ""
            protRow.smallLigands = 0
            protRow.smallLigNames = ""
            protRow.distantLigands = 0
            protRow.distantLigNames = ""
        }

        // === Pre-computation ===

        List lp = pair.prediction.labeledPoints
        if (lp == null && !sites.isEmpty()) {
            log.debug "No labeledPoints for [{}] — site reachability and point score stats will be zero", pair.name
        }
        Atoms labeledPoints = new Atoms(lp ?: emptyList())

        int n_ligSasPoints = 0
        int n_ligSasPointsCovered = 0
        double _ligSasPointsScoreSum = 0d
        ConservationScore score = null
        List<Double> bindingScrs = emptyList()
        List<Double> nonBindingScrs = emptyList()
        Atoms allLigLabeledPoints = null

        if (isLigandMode) {
            Ligands ligands = pair.ligands

            // overlaps and coverages
            n_ligSasPoints = calcCoveragesProt(protRow, pair, sites, sasPoints, pockets)
            // ligand coverage by positively predicted points (note: not by pockets!)
            allLigLabeledPoints = labeledPoints.cutoutShell(ligands.allRelevantLigandAtoms, LIG_SAS_CUTOFF)
            n_ligSasPointsCovered = allLigLabeledPoints.findAll { ((LabeledPoint) it).predicted }.size()  // only for P2Rank
            _ligSasPointsScoreSum = allLigLabeledPoints.collect { ((LabeledPoint) it).score }.sum(0) as double

            // Conservation stats
            ConservationResult conservationResult = calcConservationStats(protein, protRow)
            score = conservationResult.score
            bindingScrs = conservationResult.bindingScores
            nonBindingScrs = conservationResult.nonBindingScores
        } else {
            score = protein.conservationScore
        }

        // === Per-site LigRows ===

        for (BindingSite site : sites) {
            LigRow row = new LigRow()

            row.protName = pair.name
            row.ligName = site.label
            row.ligCount = sites.size()
            row.atoms = site.atoms.count

            row.ranks = criteria.list.collect { criterion -> PredictionPair.rankOfIdentifiedPocket(site, pockets, criterion, context) }
            row.dca4rank = PredictionPair.rankOfIdentifiedPocket(site, pockets, canonicalCriterion, context)

            Atom centroid = site.centroid
            if (centroid != null) {
                row.centerX = centroid.x
                row.centerY = centroid.y
                row.centerZ = centroid.z
                row.siteRadius = siteRadius(centroid, site.atoms)
            } else {
                row.centerX = Double.NaN
                row.centerY = Double.NaN
                row.centerZ = Double.NaN
                row.siteRadius = Double.NaN
            }

            Pocket closest = closestPocket(site, pockets)
            if (closest != null) {
                row.closestPocketDist = sitePointsForEval(site).dist(closest.centroid)
            } else {
                row.closestPocketDist = Double.NaN
            }

            if (isLigandMode) {
                Ligand lig = (Ligand) site
                row.siteType = "ligand"
                row.ligCode = lig.code
                row.chainCode = lig.chain
                row.centerToProtDist = lig.centerToProteinDist
                row.proteinDist = lig.contactDistance
                row.sasDist = protein.accessibleSurface.points.dist(lig.atoms)
                Atoms contactAtomSet = protein.proteinAtoms.cutoutShell(lig.atoms, params.ligand_protein_contact_distance)
                row.contactAtoms = contactAtomSet.count
                row.residues = protein.residues.getDistinctForAtoms(contactAtomSet).size()
                row.atomIds = (lig.atoms*.PDBserial).toSorted() as List<Integer>
            } else {
                ResidueSite rs = (ResidueSite) site
                row.siteType = "explicit"
                row.ligCode = ""
                row.chainCode = rs.residues.collect { it.chainAuthorId }.unique().join(" ")
                row.residues = rs.residues.size()
                row.contactAtoms = 0
                row.centerToProtDist = Double.NaN
                row.proteinDist = Double.NaN
                row.sasDist = Double.NaN
                row.atomIds = emptyList()
                row.ahojSiteInfo = (AhojSiteInfo) rs.secondaryData.get(ResidueSite.KEY_AHOJ_SITE_INFO)
            }

            // Site reachability and point score stats (unified for both ligand and explicit sites)
            List<LabeledPoint> siteNearPoints = labeledPoints.cutoutShell(site.atoms, LIG_SAS_CUTOFF).toList() as List<LabeledPoint>
            int hotCount = (int) siteNearPoints.count { it.score >= params.pred_point_threshold }
            row.hotPointCount = hotCount
            row.siteReachabilityScore = Math.min((double) hotCount / params.pred_min_cluster_size, 1.0d)

            siteNearPoints.sort { -it.score }
            List<Double> ptScores = siteNearPoints.collect { it.score }
            row.avgPointScore = avg ptScores
            row.maxPointScore = ptScores.empty ? 0d : ptScores[0]
            row.avgMax3PointScore = avg head(3, ptScores)
            row.avgMaxHalfPointScore = avg head(MathUtils.ceilDiv(ptScores.size(), 2), ptScores)

            tmpLigRows.add(row)
        }

        // === PocketRows ===

        int pocketIdx = 0
        for (Pocket pocket in pockets) {
            pocketIdx++
            PocketRow prow = new PocketRow()
            prow.protName = pair.name
            prow.pocketName = pocket.name
            prow.pocketVolume = pocket.stats.realVolumeApprox
            prow.surfaceAtomCount = pocket.surfaceAtoms.count
            prow.ligCount = sites.size()
            prow.pocketCount = pair.prediction.pocketCount
            prow.ligName = findSiteForPocket(sites, pocket, context)
            prow.oldScore = pocket.stats.pocketScore
            prow.score = pocket.newScore
            prow.rank = pocket.rank
            prow.newRank = pocketIdx
            prow.auxInfo = pocket.auxInfo

            if (score != null) {
                prow.avgConservation = getAvgConservationForAtoms(pocket.surfaceAtoms, score)
            }

            tmpPockets.add(prow)
        }

        // Pocket conservation ranking
        List<PocketRow> conservationSorted = tmpPockets.toSorted { it.avgConservation }.reverse(true)
        List<PocketRow> combiSorted = tmpPockets.toSorted { (Math.pow(it.avgConservation, protein.params.conservation_exponent) * it.score) }.reverse(true)
        for (PocketRow prow : tmpPockets) {
            prow.conservationRank = conservationSorted.indexOf(prow) + 1
            prow.combinedRank = combiSorted.indexOf(prow) + 1
        }

        // === ResidueRows (ligand mode only) ===

        List<ResidueRow> tmpResidueRows = emptyList()
        if (isLigandMode) {
            ResidueLabelings rlabs = pair.prediction.residueLabelings
            if (rlabs != null) {
                tmpResidueRows = new ArrayList<>(protein.residues.size())
                for (Residue res : protein.residues) {
                    double resScore = rlabs.scoreLabeling.getLabel(res)
                    Boolean resLabel = rlabs.observed?.getLabel(res)
                    tmpResidueRows.add(new ResidueRow(resScore, resLabel))
                }
            }
        }

        // === Synchronized updates ===

        proteinRows.add(protRow)
        ligandRows.addAll(tmpLigRows)
        pocketRows.addAll(tmpPockets)
        residueRows.addAll(tmpResidueRows)
        if (isLigandMode && !protein.params.log_scores_to_file.isEmpty()) {
            bindingConservationScores.addAll(bindingScrs)
            nonBindingConservationScores.addAll(nonBindingScrs)
        }

        synchronized (this) {
            ligandCount += sites.size()
            if (isLigandMode) {
                Ligands ligands = pair.ligands
                ignoredLigandCount += ligands.ignoredLigandCount
                smallLigandCount += ligands.smallLigandCount
                distantLigandCount += ligands.distantLigandCount
                ligSASPointsCount += n_ligSasPoints
                ligSASPointsCoveredCount += n_ligSasPointsCovered
                ligSASPointsScoreSum += _ligSasPointsScoreSum
            }
            pocketCount += tmpPockets.size()
            proteinCount += 1
        }
    }

    private static ConservationResult calcConservationStats(Protein protein, ProteinRow protRow) {
        ConservationScore score = protein.conservationScore
        List<Double> bindingScrs = new ArrayList<>()
        List<Double> nonBindingScrs = new ArrayList<>()
        if (score != null) {
            Atoms bindingAtoms = protein.proteinAtoms.cutoutShell(protein.allRelevantLigandAtoms, protein.params.ligand_protein_contact_distance)
            Atoms nonBindingAtoms = new Atoms(protein.proteinAtoms - bindingAtoms)

            protRow.avgConservation = getAvgConservationForAtoms(protein.proteinAtoms, score)
            protRow.avgBindingConservation = getAvgConservationForAtoms(bindingAtoms, score)
            protRow.avgNonBindingConservation = getAvgConservationForAtoms(nonBindingAtoms, score)

            if (!protein.params.log_scores_to_file.isEmpty()) {
                bindingScrs = bindingAtoms.distinctGroupsSorted.collect { it ->
                    score.getScoreForResidueSafe(it.getResidueNumber())
                }
                nonBindingScrs = nonBindingAtoms.distinctGroupsSorted.collect { it ->
                    score.getScoreForResidueSafe(it.getResidueNumber())
                }
            }
        }
        new ConservationResult(score, bindingScrs, nonBindingScrs)
    }

    private static OverlapStats calcOverlapStatsForPockets(List<Pocket> topPockets, Atoms ligSasPoints) {
        Atoms pocSasp = union((topPockets*.sasPoints).toList())
        int intersect = intersection(ligSasPoints, pocSasp).count
        int union     = union(ligSasPoints, pocSasp).count
        double ligCov = div intersect, ligSasPoints.count
        double surfOverlap = div intersect, union
        new OverlapStats(ligCov, surfOverlap)
    }

    private int calcCoveragesProt(ProteinRow protRow, PredictionPair pair, List<? extends BindingSite> sites, Atoms sasPoints, List<Pocket> pockets) {
        Protein prot = pair.protein
        Atoms ligSasp = sasPoints.cutoutShell(prot.allRelevantLigandAtoms, LIG_SAS_CUTOFF)
        int n_ligSasPoints = ligSasp.count

        // ligand coverage by pockets
        List<Pocket> topn0Pockets = head(sites.size(), pockets)
        List<Pocket> topn2Pockets = head(sites.size() + 2, pockets)
        OverlapStats overlapN0 = calcOverlapStatsForPockets(topn0Pockets, ligSasp)
        OverlapStats overlapN2 = calcOverlapStatsForPockets(topn2Pockets, ligSasp)
        protRow.ligandCoverageN0 = overlapN0.ligandCoverage
        protRow.ligandCoverageN2 = overlapN2.ligandCoverage
        protRow.surfOverlapN0 = overlapN0.surfaceOverlap
        protRow.surfOverlapN2 = overlapN2.surfaceOverlap

        List<BindingSite> succSites = sites.findAll { it.predictedPocket != null }
        List<Pocket> succPockets = succSites.collect { it.predictedPocket }
        Atoms succLigSasp = union( (succSites*.sasPoints).toList() )
        Atoms succPocSasp = union( (succPockets*.sasPoints).toList() )
        int succUnion = union(succLigSasp, succPocSasp).count
        int succIntersect = intersection(succLigSasp, succPocSasp).count

        protRow.ligandCoverageSucc = div succIntersect, succLigSasp.count
        protRow.surfOverlapSucc    = div succIntersect, succUnion

        return n_ligSasPoints
    }

    void addAll(Evaluation eval) {
        proteinCount += eval.proteinCount
        pocketCount += eval.pocketCount
        ligandCount += eval.ligandCount
        ignoredLigandCount += eval.ignoredLigandCount
        smallLigandCount += eval.smallLigandCount
        distantLigandCount += eval.distantLigandCount
        ligSASPointsCount += eval.ligSASPointsCount
        ligSASPointsCoveredCount += eval.ligSASPointsCoveredCount
        ligSASPointsScoreSum += eval.ligSASPointsScoreSum

        proteinRows.addAll(eval.proteinRows)
        ligandRows.addAll(eval.ligandRows)
        pocketRows.addAll(eval.pocketRows)
        residueRows.addAll(eval.residueRows)
        bindingConservationScores.addAll(eval.bindingConservationScores)
        nonBindingConservationScores.addAll(eval.nonBindingConservationScores)
    }

//===========================================================================================================//

    /**
     * Top-(n+T) mode: Top-(n+0), Top-(n+2) ...
     *   n ... number of ligands in given protein
     *   T ... supplied tolerance
     */
    double calcSuccessRate(int criterionIndex, int tolerance) {
        return _calcSuccessRate(criterionIndex, tolerance, true)
    }

    /**
     * Top-N mode: Top-1, Top-3, ...
     * without considering number of ligands in the protein
     */
    double calcSuccessRateTopN(int criterionIndex, int topN) {
        return _calcSuccessRate(criterionIndex, topN, false)
    }

    /**
     *
     * @param criterionIndex
     * @param tolerance
     * @param topKplusNmode if true, then number of ligands is added to tolerance for each protein
     * @return
     */
    private double _calcSuccessRate(int criterionIndex, int tolerance, boolean topNplusKmode) {
        int identified = 0

        for (LigRow ligRow in ligandRows) {
            int rankForCriterium = ligRow.ranks[criterionIndex]  // 1-based; -1 means not identified (PredictionPair.rankOfIdentifiedPocket)

            int rowTolerance = tolerance
            if (topNplusKmode) {
                rowTolerance += ligRow.ligCount  // Top-(n+K) mode where n in number of ligands in given protein and K is supplied tolerance
            }

            if ((rankForCriterium > 0) && (rankForCriterium <= rowTolerance)) {  // pocket is found and is within tolerance
                identified += 1
            }
        }

        double res = 0
        if (ligandCount != 0) {
            res = ((double) identified) / ligandCount
        }

        return res
    }

//===========================================================================================================//

    /**
     *
     * @param criterionIndex
     * @param tolerance
     * @return
     */
    double calcSuccessRateProteinCentric(int criterionIndex, int tolerance) {
        double identified = 0

        for (LigRow ligRow in ligandRows) {
            int rankForCriterion = ligRow.ranks[criterionIndex]
            if ((rankForCriterion > 0) && (rankForCriterion <= ligRow.ligCount + tolerance)) {
                identified += 1.0 / ligRow.ligCount
            }
        }

        double res = 0
        if (ligandCount != 0) {
            res = ((double) identified) / proteinCount
        }

        return res
    }

    double calcSuccessRate(String criterionName, int tolerance) {
        return calcSuccessRate(criteria.getCriterionIndexForName(criterionName), tolerance)
    }

    double calcSuccessRateTopN(String criterionName, int topN) {
        return calcSuccessRateTopN(criteria.getCriterionIndexForName(criterionName), topN)
    }

    double calcSuccessRateProteinCentric(String criterionName, int tolerance) {
        return calcSuccessRateProteinCentric(criteria.getCriterionIndexForName(criterionName), tolerance)
    }

    double calcDefaultCriterionSuccessRate(int tolerance) {
        return calcSuccessRate("DCA_4", tolerance)
    }

    /**
     * n tolerance -> site considered successfully identified if pocket is predicted with rank within (#ligands + n)
     *
     * @return by [accessor, tolerance]
     */
    List<List<Double>> calcSuccessRates(List<Integer> tolerances) {
        assert tolerances !=null && !tolerances.isEmpty()

        int n = criteria.list.size()

        List<List<Double>> res = new ArrayList<>(n)

        if (ligandCount == 0) {
            log.warn "no ligands loaded for calculating success rates!"
        }

        for (int i=0; i!=n; i++) {
            List<Double> resRow = new ArrayList<>(tolerances.size())

            for (int tolerance : tolerances) {
                double resCell = calcSuccessRate(i, tolerance)
                resRow.add(resCell)
            }

            res.add(resRow)
        }

        return res
    }

    /**
     * @param a
     * @param b
     * @return a-b... modifies a
     */
    static List<List<Double>> diffSuccRates(List<List<Double>> a, List<List<Double>> b) {
        for (int i=0; i!=a.size(); ++i) {
            for (int j=0; j!=a.get(i).size(); ++j) {
                a.get(i).set(j, a.get(i).get(j) - b.get(i).get(j))
            }
        }
        return a
    }

//===========================================================================================================//

    /**
     * Average of non-NaN values. Divides sum by total list size (not just non-NaN count).
     */
    static double avg(List<Double> list) {
        if (list.isEmpty()) return Double.NaN
        double sum = 0d
        for (Double v : list) {
            if (v != null && !v.isNaN()) {
                sum += v
            }
        }
        return sum / list.size()
    }

    /**
     * Apply closure to each element, then average non-null, non-NaN results. Divides by total list size.
     */
    static <T> double avg(List<T> list, Function<T, Double> function) {
        if (list.isEmpty()) return Double.NaN
        double sum = 0d
        for (T item : list) {
            Double v = function(item)
            if (v!=null && !v.isNaN()) {
                sum += v
            }
        }
        return sum / list.size()
    }

    /**
     * Average only on proteins that have relevant ligands.
     *
     * Note: this divides by the total number of proteins with ligands
     */
    static double avgLigProt(List<ProteinRow> list, Function<ProteinRow, Double> function) {

        return avg(list.findAll { it.ligands > 0 }, function)
    }

    static double div(double a, double b) {
        if (b == 0d) return Double.NaN
        return a / b
    }

//===========================================================================================================//

    double getAvgPockets() {
        div pocketCount, proteinCount
    }

    double getAvgLigandAtoms() {
        div ligandRows.collect {it.atoms}.sum(0) as double, ligandCount
    }

    double getAvgPocketVolume() {
        div pocketRows.collect { it.pocketVolume }.sum(0) as double, pocketCount
    }

    double getAvgPocketVolumeTruePockets() {
        avg pocketRows.findAll { it.truePocket }, { PocketRow it -> it.pocketVolume }
    }

    double getAvgPocketSurfAtoms() {
        div pocketRows.collect { it.surfaceAtomCount }.sum(0) as double, pocketCount
    }

    double getAvgPocketSurfAtomsTruePockets() {
        avg pocketRows.findAll { it.truePocket }, { PocketRow it -> (double) it.surfaceAtomCount }
    }

    double getAvgPocketInnerPoints() {
        div pocketRows.collect { it.auxInfo.samplePoints }.sum(0) as double, pocketCount
    }

    double getAvgPocketInnerPointsTruePockets() {
        avg pocketRows.findAll { it.truePocket }, { PocketRow it -> (double) it.auxInfo.samplePoints }
    }

    double getAvgProteinAtoms() {
        div proteinRows.collect { it.protAtoms }.sum(0) as double, proteinCount
    }

    double getAvgExposedAtoms() {
        div proteinRows.collect { it.exposedAtoms }.sum(0) as double, proteinCount
    }

    double getAvgProteinSasPoints() {
        avg proteinRows, { ProteinRow it -> (double) it.sasPoints }
    }

    double getAvgLigCenterToProtDist() {
        avg ligandRows, { LigRow it -> it.centerToProtDist }
    }

    double getLigandCoverage() {
        div ligSASPointsCoveredCount, ligSASPointsCount
    }

    double getAvgLigandPointScore() {
        div ligSASPointsScoreSum, ligSASPointsCount
    }

    double getAvgClosestPocketDist() {
        avg ligandRows, { LigRow row -> row.closestPocketDist }
    }

    /**
     * Todo optimize closures
     */
    Map getStats() {
        // Note: insertion order here is informational only — EvalResults.getStats()
        // copies into a TreeMap for the user-facing stats CSV, so the on-disk
        // column order is alphabetical regardless of the order we build below.
        def m = new LinkedHashMap()

        m.PROTEINS = proteinCount
        m.POCKETS = pocketCount
        m.LIGANDS = ligandCount
        m.LIGANDS_IGNORED = ignoredLigandCount
        m.LIGANDS_SMALL = smallLigandCount
        m.LIGANDS_DISTANT = distantLigandCount

        m.AVG_LIGAND_ATOMS = avgLigandAtoms
        m.AVG_PROT_ATOMS =  avgProteinAtoms
        m.AVG_PROT_EXPOSED_ATOMS = avgExposedAtoms
        m.AVG_PROT_SAS_POINTS =  avgProteinSasPoints
        m.AVG_PROT_CONSERVATION = avg(proteinRows, {it -> it.avgConservation})
        m.AVG_PROT_BINDING_CONSERVATION = avg(proteinRows, {it -> it.avgBindingConservation})
        m.AVG_PROT_NON_BINDING_CONSERVATION = avg(proteinRows, {it -> it.avgNonBindingConservation})

        m.AVG_LIG_CENTER_TO_PROT_DIST = avgLigCenterToProtDist
        m.AVG_LIG_CLOSEST_POCKET_DIST = avgClosestPocketDist
        m.LIGAND_COVERAGE = ligandCoverage

        m.AVG_DSO_TOPN0    = avgLigProt proteinRows, { it.surfOverlapN0      }  // avg by proteins (unlike DCA and others)
        m.AVG_DSO_TOPN2    = avgLigProt proteinRows, { it.surfOverlapN2      }  // avg by proteins (unlike DCA and others)
        m.AVG_DSO_SUCC     = avgLigProt proteinRows, { it.surfOverlapSucc    }  // avg by proteins (unlike DCA and others)
        m.AVG_LIGCOV_TOPN0 = avgLigProt proteinRows, { it.ligandCoverageN0   }  // avg by proteins (unlike DCA and others)
        m.AVG_LIGCOV_TOPN2 = avgLigProt proteinRows, { it.ligandCoverageN2   }  // avg by proteins (unlike DCA and others)
        m.AVG_LIGCOV_SUCC  = avgLigProt proteinRows, { it.ligandCoverageSucc }  // avg by proteins (unlike DCA and others)

        m.AVG_LIG_POINT_SCORE = avgLigandPointScore // average of all ligand adjacent points
        m.AVG_LIG_AVG_POINT_SCORE = avg ligandRows, { it.avgPointScore } // average of ligand averages
        m.AVG_LIG_MAX_POINT_SCORE = avg ligandRows, { it.maxPointScore }
        m.AVG_LIG_AVG_MAX3_POINT_SCORE = avg ligandRows, { it.avgMax3PointScore }
        m.AVG_LIG_AVG_MAXHALF_POINT_SCORE = avg ligandRows, { it.avgMaxHalfPointScore }

        m.SITE_REACHABILITY     = avg ligandRows, { it.siteReachabilityScore }
        m.SITE_REACHABLE_RATE   = div((long) ligandRows.count { it.hotPointCount >= 1 }, ligandCount)
        m.SITE_CLUSTERABLE_RATE = div((long) ligandRows.count { it.hotPointCount >= params.pred_min_cluster_size }, ligandCount)
        m.SITE_UNREACHABLE      = (long) ligandRows.count { it.hotPointCount == 0 }

        m.AVG_POCKETS = avgPockets
        m.AVG_POCKET_SURF_ATOMS = avgPocketSurfAtoms
        m.AVG_POCKET_SURF_ATOMS_TRUE_POCKETS = avgPocketSurfAtomsTruePockets
        m.AVG_POCKET_SAS_POINTS = avgPocketInnerPoints
        m.AVG_POCKET_SAS_POINTS_TRUE_POCKETS = avgPocketInnerPointsTruePockets
        m.AVG_POCKET_VOLUME =  avgPocketVolume
        m.AVG_POCKET_VOLUME_TRUE_POCKETS =  avgPocketVolumeTruePockets

        def truePockets = pocketRows.findAll { it.truePocket }
        def falsePockets = pocketRows.findAll { !it.truePocket }

        m.AVG_POCKET_CONSERVATION = avg pocketRows, { it.avgConservation }
        m.AVG_TRUE_POCKET_CONSERVATION = avg truePockets, { it.avgConservation }
        m.AVG_FALSE_POCKET_CONSERVATION = avg falsePockets, { it.avgConservation }

        m.AVG_TRUE_POCKET_PRANK_RANK = avg truePockets, { it.newRank }
        m.AVG_FALSE_POCKET_PRANK_RANK = avg falsePockets, { it.newRank }
        m.AVG_TRUE_POCKET_CONSERVATION_RANK = avg truePockets, { it.conservationRank as double }
        m.AVG_FALSE_POCKET_CONSERVATION_RANK = avg falsePockets, { it.conservationRank as double }
        m.AVG_TRUE_POCKET_COMBINED_RANK = avg truePockets, { it.combinedRank as double }
        m.AVG_FALSE_POCKET_COMBINED_RANK = avg falsePockets, { it.combinedRank as double }

        m.DCA_4_0 = calcSuccessRate("DCA_4", 0)
        m.DCA_4_1 = calcSuccessRate("DCA_4", 1)
        m.DCA_4_2 = calcSuccessRate("DCA_4", 2)
        m.DCA_4_4 = calcSuccessRate("DCA_4", 4)
        m.DCA_4_10 = calcSuccessRate("DCA_4", 10)
        m.DCA_4_99 = calcSuccessRate("DCA_4", 99)

        m.DCA_4_0_NOMINAL =  (long)((double)m.DCA_4_0  * (long)m.LIGANDS)
        m.DCA_4_1_NOMINAL =  (long)((double)m.DCA_4_1  * (long)m.LIGANDS)
        m.DCA_4_2_NOMINAL =  (long)((double)m.DCA_4_2  * (long)m.LIGANDS)
        m.DCA_4_4_NOMINAL =  (long)((double)m.DCA_4_4  * (long)m.LIGANDS)
        m.DCA_4_10_NOMINAL = (long)((double)m.DCA_4_10 * (long)m.LIGANDS)

        m.DCA_4_0_PC = calcSuccessRateProteinCentric("DCA_4", 0)
        m.DCA_4_2_PC = calcSuccessRateProteinCentric("DCA_4", 2)

        m.DCC_4_0 = calcSuccessRate("DCC_4",0)
        m.DCC_4_2 = calcSuccessRate("DCC_4",2)
        m.DCC_5_0 = calcSuccessRate("DCC_5",0)
        m.DCC_5_2 = calcSuccessRate("DCC_5",2)
        m.DCC_10_0 = calcSuccessRate("DCC_10",0)
        m.DCC_10_2 = calcSuccessRate("DCC_10",2)
        m.DCC_12_0 = calcSuccessRate("DCC_12",0)
        m.DCC_12_2 = calcSuccessRate("DCC_12",2)

        m.DCC_4_T1 = calcSuccessRateTopN("DCC_4",1)
        m.DCC_4_T3 = calcSuccessRateTopN("DCC_4",3)
        m.DCC_4_T5 = calcSuccessRateTopN("DCC_4",5)
        m.DCC_4_T7 = calcSuccessRateTopN("DCC_4",7)

        m.DCC_4_0_PC = calcSuccessRateProteinCentric("DCC_4", 0)
        m.DCC_4_2_PC = calcSuccessRateProteinCentric("DCC_4", 2)
        m.DCC_10_0_PC = calcSuccessRateProteinCentric("DCC_10", 0)
        m.DCC_10_2_PC = calcSuccessRateProteinCentric("DCC_10", 2)

        m.DSO_005_0 = calcSuccessRate("DSO_0.05",0)
        m.DSO_005_2 = calcSuccessRate("DSO_0.05",2)
        m.DSO_005_4 = calcSuccessRate("DSO_0.05",4)
        m.DSO_005_6 = calcSuccessRate("DSO_0.05",6)

        m.DSO_01_0 = calcSuccessRate("DSO_0.1",0)
        m.DSO_01_2 = calcSuccessRate("DSO_0.1",2)
        m.DSO_01_4 = calcSuccessRate("DSO_0.1",4)
        m.DSO_01_6 = calcSuccessRate("DSO_0.1",6)

        m.DSO_02_0 = calcSuccessRate("DSO_0.2",0)
        m.DSO_02_2 = calcSuccessRate("DSO_0.2",2)
        m.DSO_02_4 = calcSuccessRate("DSO_0.2",4)
        m.DSO_02_6 = calcSuccessRate("DSO_0.2",6)

        m.DPA_1_0 = calcSuccessRate("DPA_1",0)
        m.DPA_1_2 = calcSuccessRate("DPA_1",2)
        m.DPA_1_4 = calcSuccessRate("DPA_1",4)

        m.DSWO_05_0 = calcSuccessRate("DSWO_0.5",0)
        m.DSWO_05_2 = calcSuccessRate("DSWO_0.5",2)

        m.DSO_02_T1 = calcSuccessRateTopN("DSO_0.2",1)
        m.DSO_02_T3 = calcSuccessRateTopN("DSO_0.2",3)
        m.DSO_02_T5 = calcSuccessRateTopN("DSO_0.2",5)
        m.DSO_02_T7 = calcSuccessRateTopN("DSO_0.2",7)

        m.OPT1 = 100*(double)m.DCA_4_0 + 100*(double)m.DCA_4_2 + 50*(double)m.DCA_4_4 + 10*(double)m.AVG_LIGCOV_SUCC + 5*(double)m.AVG_DSO_SUCC
        m.OPT2 = 100*(double)m.DCA_4_0_PC + 50*(double)m.DCA_4_2_PC + 5*(double)m.AVG_LIGCOV_SUCC + 3*(double)m.AVG_DSO_SUCC

        writeScoresToFileIfRequested()

        return m
    }

    /**
     * Append binding/non-binding scores to the file specified by log_scores_to_file param.
     */
    private void writeScoresToFileIfRequested() {
        if (StringUtils.isNotBlank(params.log_scores_to_file)) {
            PrintWriter w = new PrintWriter(new BufferedWriter(
                    new FileWriter(params.log_scores_to_file, false)))
            try {
                w.println("First line of the file")
                nonBindingConservationScores.forEach({ it -> w.print(it); w.print(' ') })
                w.println()
                bindingConservationScores.forEach({ it -> w.print(it); w.print(' ') })
                w.println()
            } finally {
                w.close()
            }
        }
    }

    /**
     * get list of evaluation criteria used during eval routines
     */
    static List<PocketCriterion> getDefaultEvalCriteria() {
        double REQUIRED_POCKET_COVERAGE = 0.2d  //  like in fpocket MOc criterion
        return [
                new DCA("DCA_2",   2),
                new DCA("DCA_3",   3),
                new DCA("DCA_4",   4),
                new DCA("DCA_5",   5),
                new DCA("DCA_6",   6),
                new DCA("DCA_7",   7),
                new DCA("DCA_8",   8),
                new DCA("DCA_9",   9),
                new DCA("DCA_10", 10),
                new DCA("DCA_11", 11),
                new DCA("DCA_12", 12),

                new DCC("DCC_4",   4),
                new DCC("DCC_5",   5),
                new DCC("DCC_6",   6),
                new DCC("DCC_7",   7),
                new DCC("DCC_8",   8),
                new DCC("DCC_9",   9),
                new DCC("DCC_10", 10),
                new DCC("DCC_11", 11),
                new DCC("DCC_12", 12),
                new DCC("DCC_13", 13),
                new DCC("DCC_14", 14),

                new DSO("DSO_0.5",  0.5d),
                new DSO("DSO_0.4",  0.4d),
                new DSO("DSO_0.3",  0.3d),
                new DSO("DSO_0.2",  0.2d),
                new DSO("DSO_0.1",  0.1d),
                new DSO("DSO_0.05", 0.05d),

                new DPA("DPA_1", 1),
                new DPA("DPA_2", 2),
                new DPA("DPA_3", 3),

                new DSWO("DSWO_1.0", 1.0d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.9", 0.9d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.8", 0.8d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.7", 0.7d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.6", 0.6d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.5", 0.5d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.4", 0.4d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.3", 0.3d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.2", 0.2d, REQUIRED_POCKET_COVERAGE),
                new DSWO("DSWO_0.1", 0.1d, REQUIRED_POCKET_COVERAGE),

        ] as List<PocketCriterion>

        //        ((1..6).collect { new DPA(it) }) +
        //        ((1..6).collect { new DSA(it) }) +
    }

//===========================================================================================================//

    String toSuccessRatesCSV(List<Integer> tolerances) {
        return formatSuccessRatesCSV(tolerances, calcSuccessRates(tolerances))
    }

    String getMiscStatsCSV() {

        stats.collect { "$it.key, ${fmtCsv it.value}" }.join("\n")
    }

    String diffSuccessRatesCSV(List<Integer> tolerances, Evaluation diffWith) {
        List<List<Double>> ours = calcSuccessRates(tolerances)
        List<List<Double>> theirs = diffWith.calcSuccessRates(tolerances)
        return formatSuccessRatesCSV(tolerances, diffSuccRates(ours, theirs))
    }

    String formatSuccessRatesCSV(List<Integer> tolerances, List<List<Double>> succRates) {

        StringBuilder str = new StringBuilder()
        str << "tolerances:," + tolerances.collect{"[$it]"}.join(",") + "\n"
        int i = 0
        criteria.list.each {
            str << criteria.list[i].toString() + "," + succRates[i].collect{ formatPercent(it) }.join(",")
            str << "\n"
            i++
        }

        return str.toString()
    }

    /**
     * chain - pdb chain code(s) which ligand belongs to
     * proteinDist - distance to the closest protein atom
     * sasDist - distance to the closest SAS point (can be used as a proxy for how deep is the ligand buried) 
     * #contactProteinAtoms - number of protein atoms within a close distance around ligand atoms (threshold is given by a parameter ligand_protein_contact_distance) 
     * atomIds - list of PDBSerial numbers of all ligand atoms (sorted and separated by a space)
     *
     * @return
     */
    String toLigandsCSV() {
        StringBuilder csv = new StringBuilder()
        csv <<  "file, #ligands, ligand, chain, ligCode, #atoms, dca4rank, closestPocketDist, proteinDist, centerToProteinDist, sasDist, #contactProteinAtoms, hotPointCount, siteReachability, atomIds\n"
        for (LigRow r : ligandRows) {
            List rec = new ArrayList()

            rec.add r.protName
            rec.add r.ligCount
            rec.add r.ligName
            rec.add r.chainCode
            rec.add r.ligCode
            rec.add r.atoms
            rec.add r.dca4rank

            rec.add fmtCsv(r.closestPocketDist)
            rec.add fmtCsv(r.proteinDist)
            rec.add fmtCsv(r.centerToProtDist)
            rec.add fmtCsv(r.sasDist)
            rec.add r.contactAtoms
            rec.add r.hotPointCount
            rec.add fmtCsv(r.siteReachabilityScore)
            rec.add r.atomIds.join(" ")

            csv << rec.join(", ") << "\n"
        }
        return csv.toString()
    }

    /**
     * Unified CSV with all binding sites (ligand-defined and explicit).
     */
    String toSitesCSV() {
        // Include AhojSiteInfo columns only when at least one row has the data
        boolean hasAhojInfo = ligandRows.any { it.ahojSiteInfo != null }

        StringBuilder csv = new StringBuilder()
        String header = "file, site_type, #sites, site, chain, ligCode, #atoms, #residues, center_x, center_y, center_z, site_radius, dca4rank, closestPocketDist, proteinDist, centerToProteinDist, sasDist, #contactProteinAtoms, hotPointCount, siteReachability"
        if (hasAhojInfo) {
            header += ", " + AhojSiteInfo.EXPORT_COLUMNS.join(", ")
        }
        csv << header << "\n"

        for (LigRow r : ligandRows) {
            List rec = new ArrayList()
            rec.add r.protName
            rec.add r.siteType
            rec.add r.ligCount
            rec.add r.ligName
            rec.add r.chainCode
            rec.add r.ligCode
            rec.add r.atoms
            rec.add r.residues
            rec.add fmtCsv(r.centerX)
            rec.add fmtCsv(r.centerY)
            rec.add fmtCsv(r.centerZ)
            rec.add fmtCsv(r.siteRadius)
            rec.add r.dca4rank
            rec.add fmtCsv(r.closestPocketDist)
            rec.add fmtCsv(r.proteinDist)
            rec.add fmtCsv(r.centerToProtDist)
            rec.add fmtCsv(r.sasDist)
            rec.add r.contactAtoms
            rec.add r.hotPointCount
            rec.add fmtCsv(r.siteReachabilityScore)
            if (hasAhojInfo) {
                rec.addAll(r.ahojSiteInfo != null ? r.ahojSiteInfo.toExportValues() : AhojSiteInfo.emptyExportValues())
            }
            csv << rec.join(", ") << "\n"
        }
        return csv.toString()
    }

    static double siteRadius(Atom centroid, Atoms atoms) {
        double maxDist = 0
        for (Atom a : atoms) {
            double d = Struct.dist(centroid, a)
            if (d > maxDist) maxDist = d
        }
        return maxDist
    }

    /**
     * @return print ranks for all criteria
     */
    String toRanksCSV() {
        StringBuilder csv = new StringBuilder()
        csv << "file,#ligands,ligand," + criteria.list.join(",") + "\n"
        for (LigRow row : ligandRows) {
            csv << "$row.protName,$row.ligCount,$row.ligName," + row.ranks.join(",") + "\n"
        }
        return csv.toString()
    }

    String toProteinsCSV() {
        StringBuilder csv = new StringBuilder()

        csv << "name,#atoms,#proteinAtoms,#chains,chainNames,#ligands,#pockets,ligandNames,#ignoredLigands,ignoredLigNames,#smallLigands,smallLigNames,#distantLigands,distantLigNames\n"

        for (ProteinRow p in proteinRows) {
            csv << "$p.name,$p.atoms,$p.protAtoms,$p.chains,$p.chainNames,$p.ligands,$p.pockets,$p.ligNames,$p.ignoredLigands,$p.ignoredLigNames,$p.smallLigands,$p.smallLigNames,$p.distantLigands,$p.distantLigNames\n"
        }

        return csv.toString()
    }

    String toPocketsCSV() {
        StringBuilder csv = new StringBuilder()

        csv <<  "file,#ligands,#pockets,pocket,ligand,rank,score,newRank,oldScore,zScoreTP,probaTP,samplePoints,rawNewScore,pocketVolume,surfaceAtoms\n"

        for (PocketRow p in pocketRows) {
            csv << "$p.protName,$p.ligCount,$p.pocketCount,$p.pocketName,$p.ligName,"
            csv << "$p.rank,$p.score,$p.newRank,${fmtCsv(p.oldScore)},${fmtCsv(p.auxInfo.zScoreTP)},${fmtCsv(p.auxInfo.probaTP)},$p.auxInfo.samplePoints,${fmtCsv(p.auxInfo.rawNewScore)},$p.pocketVolume,$p.surfaceAtomCount"
            csv << "\n"
        }

        return csv.toString()
    }

//===========================================================================================================//

    void writeCases(String dir) {
        writeFile "$dir/proteins.csv",          this.toProteinsCSV()
        writeFile "$dir/ligands.csv",           this.toLigandsCSV()
        writeFile "$dir/observed_sites.csv",    this.toSitesCSV()
        writeFile "$dir/predicted_pockets.csv", this.toPocketsCSV()
        writeFile "$dir/ranks.csv",             this.toRanksCSV()
    }

//===========================================================================================================//

    static class ProteinRow {
        String name
        int atoms
        int protAtoms
        int exposedAtoms
        int chains
        String chainNames
        int ligands
        int pockets
        String ligNames

        int ignoredLigands
        String ignoredLigNames

        int smallLigands
        String smallLigNames

        int distantLigands
        String distantLigNames

        double avgConservation
        double avgBindingConservation
        double avgNonBindingConservation

        double ligandCoverageN0    // conered by top-n pockets
        double ligandCoverageN2    // covered by top-(n+2) pockets
        double surfOverlapN0       // discretized surface overlap considering top-n pockets
        double surfOverlapN2       // discretized surface overlap considering top-(n+2) pockets
        double ligandCoverageSucc  // coverage only considering those ligands that were successfully predicted according to DCA(4)
        double surfOverlapSucc     // overlap only considering those ligands that were successfully predicted according to DCA(4)

        int sasPoints
    }

    static class LigRow {
        String protName
        String ligName
        String ligCode
        String chainCode
        String siteType          // "ligand" or "explicit"
        int ligCount
        int atoms = 0
        int contactAtoms = 0
        int residues = 0
        double closestPocketDist
        double centerToProtDist
        double proteinDist
        double sasDist
        double centerX
        double centerY
        double centerZ
        double siteRadius
        int dca4rank = -1

        double avgPointScore
        double maxPointScore
        double avgMax3PointScore
        double avgMaxHalfPointScore

        int hotPointCount = 0
        double siteReachabilityScore = 0d

        List<Integer> atomIds
        List<Integer> ranks // of identified pocket for given criterion starting with 1 (-1 = not identified)

        AhojSiteInfo ahojSiteInfo
    }

    static class PocketRow {
        String protName
        int ligCount
        int pocketCount
        String pocketName
        double pocketVolume
        int surfaceAtomCount
        String ligName

        int rank
        double score
        double newRank
        double oldScore

        int conservationRank
        int combinedRank

        Pocket.AuxInfo auxInfo

        double avgConservation

        boolean isTruePocket() {
            StringUtils.isNotEmpty(ligName)
        }
    }

    static class ResidueRow {
        double score
        Boolean observed

        ResidueRow(double score, Boolean observed) {
            this.score = score
            this.observed = observed
        }
    }

    static class ConservationResult {
        final ConservationScore score
        final List<Double> bindingScores
        final List<Double> nonBindingScores

        ConservationResult(ConservationScore score, List<Double> bindingScores, List<Double> nonBindingScores) {
            this.score = score
            this.bindingScores = bindingScores
            this.nonBindingScores = nonBindingScores
        }
    }

    static class OverlapStats {
        final double ligandCoverage
        final double surfaceOverlap

        OverlapStats(double ligandCoverage, double surfaceOverlap) {
            this.ligandCoverage = ligandCoverage
            this.surfaceOverlap = surfaceOverlap
        }
    }

}
