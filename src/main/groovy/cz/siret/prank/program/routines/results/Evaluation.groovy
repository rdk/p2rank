package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.labeling.ResidueLabelings
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.pockets.criteria.*
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.MathUtils
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import javax.annotation.concurrent.ThreadSafe

import static cz.siret.prank.geom.Atoms.intersection
import static cz.siret.prank.geom.Atoms.union
import static cz.siret.prank.utils.Cutils.head
import static cz.siret.prank.utils.Cutils.newSynchronizedList
import static cz.siret.prank.utils.Formatter.*
import static java.util.Collections.emptyList

/**
 * Represents evaluation of pocket prediction on a dataset of proteins
 *
 * Allows to collect results for a set of different pocket identification success criteria simultaneously.
 */
@ThreadSafe
@Slf4j
class Evaluation implements Parametrized {

    /** cutoff distance in A around ligand atoms that determines which SAS points cover the ligand */
    final double LIG_SAS_CUTOFF = params.ligand_induced_volume_cutoff  

    PocketCriterium standardCriterium = new DCA(4.0d)
    List<PocketCriterium> criteria
    List<ProteinRow> proteinRows = newSynchronizedList()
    List<LigRow> ligandRows = newSynchronizedList()
    List<PocketRow> pocketRows = newSynchronizedList()
    List<ResidueRow> residueRows = newSynchronizedList()

    List<Double> bindingScores = newSynchronizedList()
    List<Double> nonBindingScores = newSynchronizedList()

    long proteinCount
    long pocketCount

    long ligandCount
    long ignoredLigandCount
    long smallLigandCount
    long distantLigandCount

    long ligSASPointsCount
    long ligSASPointsCoveredCount
    double ligSASPointsScoreSum

    Evaluation(List<PocketCriterium> criteria) {
        this.criteria = criteria
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
     * considering DCA measure
     */
    private Pocket closestPocket(Ligand lig, List<Pocket> pockets) {
        if (pockets.empty) return null

        Pocket res = pockets.first()
        double minDist = lig.atoms.dist(res.centroid)

        for (Pocket p : pockets) {
            double dist = lig.atoms.dist(p.centroid)
            if (dist<minDist) {
                minDist = dist
                res = p
            }
        }

        return res
    }

    private double getAvgConservationForAtoms(Atoms atoms, ConservationScore score) {
        if (atoms.distinctGroupsSorted.size() == 0) {
            return 0.0
        }
        return atoms.distinctGroupsSorted.stream().mapToDouble( {
            group->score.getScoreForResidue(group.getResidueNumber())})
                .average().getAsDouble()
    }

    private Pocket findPocketForLigand(Ligand ligand, List<Pocket> pockets,
                                       PocketCriterium criterium, EvalContext context) {
        for (Pocket pocket in pockets) {
            if (criterium.isIdentified(ligand, pocket, context)) {
                return pocket
            }
        }
        return null
    }

    private void assignPocketsToLigands(List<Ligand> ligands, List<Pocket> pockets, EvalContext context) {
        for (Ligand ligand : ligands) {
            ligand.predictedPocket = findPocketForLigand(ligand, pockets, standardCriterium, context)
        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    void addPrediction(PredictionPair pair, List<Pocket> pockets) {
        EvalContext context = new EvalContext()

        Ligands ligands = pair.ligands

        ligands.relevantLigands.each { it.sasPoints = null } // clear sas points cache
        assignPocketsToLigands(ligands.relevantLigands, pockets, context)

        List<LigRow> tmpLigRows = new ArrayList<>()
        List<PocketRow> tmpPockets = new ArrayList<>()

        Protein protein = pair.protein
        Atoms sasPoints = pair.prediction.protein.accessibleSurface.points
        Atoms labeledPoints = new Atoms(pair.prediction.labeledPoints ?: emptyList())
        
        ProteinRow protRow = new ProteinRow()
        protRow.name = pair.name
        protRow.atoms = protein.allAtoms.count
        protRow.protAtoms = protein.proteinAtoms.count
        protRow.exposedAtoms = pair.prediction.protein.exposedAtoms.count
        protRow.chains = protein.residueChains.size()
        protRow.chainNames = protein.residueChains.collect {it.authorId}.join(" ")
        protRow.ligands = ligands.relevantLigandCount
        protRow.pockets = pair.prediction.pocketCount

        protRow.ligNames = ligands.relevantLigands.collect { "$it.name($it.size)" }.join(" ")
        protRow.ignoredLigands = ligands.ignoredLigandCount
        protRow.ignoredLigNames = ligands.ignoredLigands.collect { "$it.name($it.size)" }.join(" ")
        protRow.smallLigands = ligands.smallLigandCount
        protRow.smallLigNames = ligands.smallLigands.collect { "$it.name($it.size)" }.join(" ")
        protRow.distantLigands = ligands.distantLigandCount
        protRow.distantLigNames = ligands.distantLigands.collect { "$it.name($it.size|${format(it.contactDistance,1)}|${format(it.centerToProteinDist,1)})" }.join(" ")
        protRow.sasPoints = sasPoints.count

        // overlaps and coverages
        int n_ligSasPoints = calcCoveragesProt(protRow, pair, sasPoints, pockets)
        // ligand coverage by positively predicted points (note: not by pockets!)
        Atoms allLigLabeledPoints = labeledPoints.cutoutShell(ligands.allRelevantLigandAtoms, LIG_SAS_CUTOFF)
        int n_ligSasPointsCovered = allLigLabeledPoints.findAll { ((LabeledPoint) it).predicted }.size()  // only for P2Rank
        double _ligSasPointsScoreSum = allLigLabeledPoints.collect { LabeledPoint it -> it.score }.sum(0)
        //log.debug "XXXX n_ligSasPoints: $n_ligSasPoints covered: $n_ligSasPointsCovered"

        // Conservation stats
        def (ConservationScore score, List<Double> bindingScrs, List<Double> nonBindingScrs) = calcConservationStats(protein, protRow)

        for (Ligand lig : ligands.relevantLigands) {
            LigRow row = new LigRow()

            row.protName = pair.name
            row.ligName = lig.name
            row.ligCode = lig.code
            row.chainCode = lig.chain

            row.ligCount = ligands.relevantLigandCount
            row.ranks = criteria.collect { criterium -> pair.rankOfIdentifiedPocket(lig, pockets, criterium, context) }
            row.dca4rank = pair.rankOfIdentifiedPocket(lig, pockets, standardCriterium, context)
            row.atoms = lig.atoms.count
            row.centerToProtDist = lig.centerToProteinDist
            row.proteinDist = lig.contactDistance
            row.sasDist = protein.accessibleSurface.points.dist(lig.atoms)
            row.contactAtoms = protein.proteinAtoms.cutoutShell(lig.atoms, params.ligand_protein_contact_distance).count
            row.atomIds = (lig.atoms*.PDBserial).toSorted()


            Pocket closestPocket = closestPocket(lig, pockets)
            if (closestPocket!=null) {
                row.closestPocketDist = lig.atoms.dist(closestPocket.centroid)
            } else {
                row.closestPocketDist = Double.NaN
            }

            List<LabeledPoint> ligPoints = allLigLabeledPoints.cutoutShell(lig.atoms, LIG_SAS_CUTOFF).toList() as List<LabeledPoint>
            ligPoints.sort { -it.score }
            List<Double> ptScores = ligPoints.collect { it.score }
            row.avgPointScore = avg ptScores
            row.maxPointScore = ptScores.empty ? 0d : ptScores[0]
            row.avgMax3PointScore = avg Cutils.head(3, ptScores)
            row.avgMaxHalfPointScore = avg Cutils.head(MathUtils.ceilDiv(ptScores.size(), 2), ptScores)

            tmpLigRows.add(row)
        }

        for (Pocket pocket in pockets) {
            PocketRow prow = new PocketRow()
            prow.protName = pair.name
            prow.pocketName = pocket.name
            prow.pocketVolume = pocket.stats.realVolumeApprox
            prow.surfaceAtomCount = pocket.surfaceAtoms.count
            prow.ligCount = ligands.relevantLigandCount
            prow.pocketCount = pair.prediction.pocketCount

            Ligand ligand = pair.findLigandForPocket(pocket, standardCriterium, context)
            prow.ligName = (ligand==null) ? "" : ligand.name + "_" + ligand.code

            prow.oldScore = pocket.stats.pocketScore
            prow.score = pocket.newScore
            prow.rank = pocket.rank
            prow.newRank = pocket.newRank

            prow.auxInfo = pocket.auxInfo

            if (score != null) {
                prow.avgConservation = getAvgConservationForAtoms(pocket.surfaceAtoms, score)
            }

            tmpPockets.add(prow)
        }
        List<PocketRow> conservationSorted = tmpPockets.toSorted {it.avgConservation}.reverse(true)
        List<PocketRow> combiSorted = tmpPockets.toSorted { (Math.pow(it.avgConservation, protein.params.conservation_exponent) * it.score)}.reverse(true)
        for (PocketRow prow : tmpPockets) {
            prow.conservationRank = conservationSorted.indexOf(prow) + 1
            prow.combinedRank = combiSorted.indexOf(prow) + 1
        }

        ResidueLabelings rlabs = pair.prediction.residueLabelings
        if (rlabs != null) {
            for (Residue res : protein.residues) {
                double resScore = rlabs.scoreLabeling.getLabel(res)
                Boolean resLabel = rlabs.observed?.getLabel(res)

                ResidueRow rrow = new ResidueRow()
                rrow.score = resScore
                rrow.observed = resLabel

                residueRows.add(rrow)
            }
        }

        synchronized (this) {
            ligandCount += ligands.relevantLigandCount
            ignoredLigandCount += ligands.ignoredLigandCount
            smallLigandCount += ligands.smallLigandCount
            distantLigandCount += ligands.distantLigandCount
            pocketCount += tmpPockets.size()
            proteinCount += 1
            proteinRows.add(protRow)
            ligandRows.addAll(tmpLigRows)
            pocketRows.addAll(tmpPockets)
            ligSASPointsCount += n_ligSasPoints
            ligSASPointsCoveredCount += n_ligSasPointsCovered
            ligSASPointsScoreSum += _ligSasPointsScoreSum

            if (!protein.params.log_scores_to_file.isEmpty()) {
                bindingScores.addAll(bindingScrs)
                nonBindingScores.addAll(nonBindingScrs)
            }
        }
    }

    private List calcConservationStats(Protein protein, ProteinRow protRow) {
        ConservationScore score = protein.secondaryData.get(ConservationScore.CONSERV_SCORE_KEY) as ConservationScore
        List<Double> bindingScrs = new ArrayList<>()
        List<Double> nonBindingScrs = new ArrayList<>()
        if (score != null) {
            protRow.avgConservation = getAvgConservationForAtoms(protein.proteinAtoms, score)
            Atoms bindingAtoms = protein.proteinAtoms.cutoutShell(protein.allRelevantLigandAtoms, protein.params.ligand_protein_contact_distance)
            protRow.avgBindingConservation = getAvgConservationForAtoms(bindingAtoms, score)
            Atoms nonBindingAtoms = new Atoms(protein.proteinAtoms - bindingAtoms)
            protRow.avgNonBindingConservation = getAvgConservationForAtoms(nonBindingAtoms, score)

            if (!protein.params.log_scores_to_file.isEmpty()) {
                bindingScrs = bindingAtoms.distinctGroupsSorted.collect { it ->
                    score.getScoreForResidue(it.getResidueNumber())
                }
                nonBindingScrs = nonBindingAtoms.distinctGroupsSorted.collect { it ->
                    score.getScoreForResidue(it.getResidueNumber())
                }
            }
        }
        [score, bindingScrs, nonBindingScrs]
    }

    def calcOverlapStatsForPockets(List<Pocket> topPockets, Atoms ligSasPoints) {
        Atoms pocSasp = union((topPockets*.sasPoints).toList())
        int intersect = intersection(ligSasPoints, pocSasp).count
        int union     = union(ligSasPoints, pocSasp).count
        double ligCov = div intersect, ligSasPoints.count
        double surfOverlap = div intersect, union
        [ligCov, surfOverlap]
    }

    private int calcCoveragesProt(ProteinRow protRow, PredictionPair pair, Atoms sasPoints, List<Pocket> pockets) {
        Protein prot = pair.protein
        Atoms ligSasp = sasPoints.cutoutShell(prot.allRelevantLigandAtoms, LIG_SAS_CUTOFF)
        int n_ligSasPoints = ligSasp.count

        // ligand coverage by pockets
        List<Pocket> topn0Pockets = head(pair.ligands.relevantLigandCount, pockets)
        List<Pocket> topn2Pockets = head(pair.ligands.relevantLigandCount + 2, pockets)
        def (ligCovN0, surfOverlapN0) = calcOverlapStatsForPockets(topn0Pockets, ligSasp)
        def (ligCovN2, surfOverlapN2) = calcOverlapStatsForPockets(topn2Pockets, ligSasp)
        protRow.ligandCoverageN0 = ligCovN0
        protRow.ligandCoverageN2 = ligCovN2
        protRow.surfOverlapN0 = surfOverlapN0
        protRow.surfOverlapN2 = surfOverlapN2

        // TODO revisit: consider prot averaging vs ligand averaging etc...
        List<Ligand> succLigands = prot.relevantLigands.findAll { it.predictedPocket!=null }.toList() //.toList()
        List<Pocket> succPockets = succLigands.collect { it.predictedPocket }.toList()
        Atoms succLigSasp = union( (succLigands*.sasPoints).toList() )
        Atoms succPocSasp = union( (succPockets*.sasPoints).toList() )
        int succUnion = union(succLigSasp, succPocSasp).count
        int succIntersect = intersection(succLigSasp, succPocSasp).count

        protRow.ligandCoverageSucc = div succIntersect, succLigSasp.count
        protRow.surfOverlapSucc    = div succIntersect, succUnion

        n_ligSasPoints
    }

    void addAll(Evaluation eval) {
        proteinRows.addAll(eval.proteinRows)
        ligandRows.addAll(eval.ligandRows)
        pocketRows.addAll(eval.pocketRows)
        proteinCount += eval.proteinCount
        pocketCount += eval.pocketCount
        ligandCount += eval.ligandCount
        ignoredLigandCount += eval.ignoredLigandCount
        smallLigandCount += eval.smallLigandCount
        distantLigandCount += eval.distantLigandCount
        ligSASPointsCount += eval.ligSASPointsCount
        ligSASPointsCoveredCount += eval.ligSASPointsCoveredCount
        ligSASPointsScoreSum += eval.ligSASPointsScoreSum

        bindingScores.addAll(eval.bindingScores)
        nonBindingScores.addAll(eval.nonBindingScores)
    }

    double calcSuccRate(int assesorNum, int tolerance) {
        int identified = 0

        for (LigRow ligRow in ligandRows) {
            int rankForAssessor = ligRow.ranks[assesorNum]
            if ((rankForAssessor > 0) && (rankForAssessor <= ligRow.ligCount + tolerance)) {
                identified += 1
            }
        }

        double res = 0
        if (ligandCount != 0) {
            res = ((double) identified) / ligandCount
        }

        return res
    }

    double calcSuccRateProteinCentric(int assesorNum, int tolerance) {
        double identified = 0

        for (LigRow ligRow in ligandRows) {
            int rankForAssessor = ligRow.ranks[assesorNum]
            if ((rankForAssessor > 0) && (rankForAssessor <= ligRow.ligCount + tolerance)) {
                identified += 1.0 / ligRow.ligCount
            }
        }

        double res = 0
        if (ligandCount != 0) {
            res = ((double) identified) / proteinCount
        }

        return res
    }

    double calcDefaultCriteriumSuccessRate(int tolerance) {
        return calcSuccRate(3, tolerance)
    }

    /**
     * n tolerance -> site considered successfully identified if pocket is predicted with rank within (#ligands + n)
     *
     * @return by [accessor, tolerance]
     */
    List<List<Double>> calcSuccessRates(List<Integer> tolerances) {
        assert tolerances !=null && !tolerances.isEmpty()

        List<List<Double>> res = new ArrayList<>()

        if (ligandCount==0) {
            log.warn "no ligands loaded for calculating success rates!"
        }

        for (int assNum=0; assNum!=criteria.size(); assNum++) {
            List<Double> resRow = new ArrayList<>(tolerances.size())

            for (int tolerance in tolerances) {
                double resCell = calcSuccRate(assNum, tolerance)
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

    public double avg(List<Double> list) {
        if (list.size()==0) return Double.NaN
        list.findAll { it!=Double.NaN }.sum(0) / list.size()
    }
    
    public <T> double avg(List<T> list, Closure<T> closure) {
        if (list.size()==0) return Double.NaN
        list.collect { closure(it) }.findAll { it!=Double.NaN }.sum(0) / list.size()
    }

    public <T> double avgNanTo0(List<T> list, Closure<T> closure) {
        if (list.size()==0) return Double.NaN
        list.collect { closure(it) }.collect { nanNullTo0(it) }.sum(0) / list.size()
    }

    double nanNullTo0(Double d) {
        if (d == null || d.isNaN()) {
            return 0d
        } else {
            return d
        }
    }

    /**
     * average only on proteins that have relevant ligands
     */
    public double avgLigProt(List<ProteinRow> list, Closure<ProteinRow> closure) {
        List<ProteinRow> ligProts = list.findAll { it.ligands > 0 }.toList()
        return avg(ligProts, closure)
    }

    double div(double a, double b) {
        if (b==0d)
            return Double.NaN
        return a / b
    }

//===========================================================================================================//

    double getAvgPockets() {
        div pocketCount, proteinCount
    }

    double getAvgLigandAtoms() {
        div ligandRows.collect {it.atoms}.sum(0), ligandCount
    }

    double getAvgPocketVolume() {
        div pocketRows.collect { it.pocketVolume }.sum(0), pocketCount
    }
    double getAvgPocketVolumeTruePockets() {
        avg pocketRows.findAll { it.truePocket }, {PocketRow it -> it.pocketVolume }
    }

    double getAvgPocketSurfAtoms() {
        div pocketRows.collect { it.surfaceAtomCount }.sum(0), pocketCount
    }

    double getAvgPocketSurfAtomsTruePockets() {
        avg pocketRows.findAll { it.truePocket }, {PocketRow it -> it.surfaceAtomCount }
    }

    double getAvgPocketInnerPoints() {
        div pocketRows.collect { it.auxInfo.samplePoints }.sum(0), pocketCount
    }
    double getAvgPocketInnerPointsTruePockets() {
        avg pocketRows.findAll { it.truePocket }, {PocketRow it -> it.auxInfo.samplePoints }
    }

    double getAvgProteinAtoms() {
        div proteinRows.collect { it.protAtoms }.sum(0), proteinCount
    }

    double getAvgExposedAtoms() {
        div proteinRows.collect { it.exposedAtoms }.sum(0), proteinCount
    }

    double getAvgProteinConollyPoints() {
        avg proteinRows, {ProteinRow it -> it.sasPoints }
    }

    double getAvgLigCenterToProtDist() {
        avg ligandRows, {LigRow it -> it.centerToProtDist}
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
        def m = new LinkedHashMap() // keep insertion order

        m.PROTEINS = proteinCount
        m.POCKETS = pocketCount
        m.LIGANDS = ligandCount
        m.LIGANDS_IGNORED = ignoredLigandCount
        m.LIGANDS_SMALL = smallLigandCount
        m.LIGANDS_DISTANT = distantLigandCount

        if (pocketCount==0) pocketCount=1 // to avoid undefined division in calculations

        m.AVG_LIGAND_ATOMS = avgLigandAtoms
        m.AVG_PROT_ATOMS =  avgProteinAtoms
        m.AVG_PROT_EXPOSED_ATOMS = avgExposedAtoms
        m.AVG_PROT_SAS_POINTS =  avgProteinConollyPoints
        m.AVG_PROT_CONSERVATION = avg(proteinRows, {it -> it.avgConservation})
        m.AVG_PROT_BINDING_CONSERVATION = avg(proteinRows, {it -> it.avgBindingConservation})
        m.AVG_PROT_NON_BINDING_CONSERVATION = avg(proteinRows, {it -> it.avgNonBindingConservation})

        m.AVG_LIG_CENTER_TO_PROT_DIST = avgLigCenterToProtDist
        m.AVG_LIG_CLOSTES_POCKET_DIST = avgClosestPocketDist
        m.LIGAND_COVERAGE = ligandCoverage

        m.AVG_DSO_TOPN0    = avgLigProt proteinRows, { it.surfOverlapN0      }  // avg by proteins (unlike DCA and others)
        m.AVG_DSO_TOPN2    = avgLigProt proteinRows, { it.surfOverlapN2      }  // avg by proteins (unlike DCA and others)
        m.AVG_DSO_SUCC     = avgLigProt proteinRows, { it.surfOverlapSucc    }  // avg by proteins (unlike DCA and others)
        m.AVG_LIGCOV_TOPN0 = avgLigProt proteinRows, { it.ligandCoverageN0   }  // avg by proteins (unlike DCA and others)
        m.AVG_LIGCOV_TOPN2 = avgLigProt proteinRows, { it.ligandCoverageN2   }  // avg by proteins (unlike DCA and others)
        m.AVG_LIGCOV_SUCC  = avgLigProt proteinRows, { it.ligandCoverageSucc }  // avg by proteins (unlike DCA and others)

        m.AVG_LIG_POINT_SCORE = avgLigandPointScore // average of all ligand adjacent points
        m.AVG_LIG_AVG_POINT_SCORE = avgNanTo0 ligandRows, { it.avgPointScore } // average of ligand averages
        m.AVG_LIG_MAX_POINT_SCORE = avgNanTo0 ligandRows, { it.maxPointScore }
        m.AVG_LIG_AVG_MAX3_POINT_SCORE = avgNanTo0 ligandRows, { it.avgMax3PointScore }
        m.AVG_LIG_AVG_MAXHALF_POINT_SCORE = avgNanTo0 ligandRows, { it.avgMaxHalfPointScore }

        m.AVG_POCKETS = avgPockets
        m.AVG_POCKET_SURF_ATOMS = avgPocketSurfAtoms
        m.AVG_POCKET_SURF_ATOMS_TRUE_POCKETS = avgPocketSurfAtomsTruePockets
        m.AVG_POCKET_SAS_POINTS = avgPocketInnerPoints
        m.AVG_POCKET_SAS_POINTS_TRUE_POCKETS = avgPocketInnerPointsTruePockets
        m.AVG_POCKET_VOLUME =  avgPocketVolume
        m.AVG_POCKET_VOLUME_TRUE_POCKETS =  avgPocketVolumeTruePockets

        m.AVG_POCKET_CONSERVATION = avg pocketRows, { it.avgConservation }
        m.AVG_TRUE_POCKET_CONSERVATION = avg pocketRows.findAll { it.truePocket }, { it.avgConservation }
        m.AVG_FALSE_POCKET_CONSERVATION = avg pocketRows.findAll { !it.truePocket }, { it.avgConservation }

        m.AVG_TRUE_POCKET_PRANK_RANK = avg pocketRows.findAll { it.truePocket }, { it.newRank }
        m.AVG_FALSE_POCKET_PRANK_RANK = avg pocketRows.findAll { !it.truePocket }, { it.newRank }
        m.AVG_TRUE_POCKET_CONSERVATION_RANK = avg pocketRows.findAll { it.truePocket }, { it.conservationRank }
        m.AVG_FALSE_POCKET_CONSERVATION_RANK = avg pocketRows.findAll { !it.truePocket }, { it.conservationRank }
        m.AVG_TRUE_POCKET_COMBINED_RANK = avg pocketRows.findAll { it.truePocket }, { it.combinedRank }
        m.AVG_FALSE_POCKET_COMBINED_RANK = avg pocketRows.findAll { !it.truePocket }, { it.combinedRank }

        m.DCA_4_0 = calcDefaultCriteriumSuccessRate(0)
        m.DCA_4_1 = calcDefaultCriteriumSuccessRate(1)
        m.DCA_4_2 = calcDefaultCriteriumSuccessRate(2)
        m.DCA_4_4 = calcDefaultCriteriumSuccessRate(4)
        m.DCA_4_10 = calcDefaultCriteriumSuccessRate(10)
        m.DCA_4_99 = calcDefaultCriteriumSuccessRate(99)

        m.DCA_4_0_NOMINAL = m.DCA_4_0 * m.LIGANDS
        m.DCA_4_1_NOMINAL = m.DCA_4_1 * m.LIGANDS
        m.DCA_4_2_NOMINAL = m.DCA_4_2 * m.LIGANDS
        m.DCA_4_4_NOMINAL = m.DCA_4_4 * m.LIGANDS
        m.DCA_4_10_NOMINAL = m.DCA_4_10 * m.LIGANDS

        m.DCA_4_0_PC = calcSuccRateProteinCentric(3,0)
        m.DCA_4_2_PC = calcSuccRateProteinCentric(3,2)

        // compare to getDefaultEvalCriteria()
        m.DCC_4_0 = calcSuccRate(18,0)
        m.DCC_4_2 = calcSuccRate(18,2)
        m.DCC_5_0 = calcSuccRate(19,0)
        m.DCC_5_2 = calcSuccRate(19,2)

        m.DSOR_03_0 = calcSuccRate(29,0)
        m.DSOR_03_2 = calcSuccRate(29,2)
        m.DSOR_02_0 = calcSuccRate(29,0)
        m.DSOR_02_2 = calcSuccRate(29,2)
        m.DSWO_05_0 = calcSuccRate(37,0)
        m.DSWO_05_2 = calcSuccRate(37,2)

//        m.DPA_1_0 = calcSuccRate(25,0)
//        m.DPA_1_2 = calcSuccRate(25,2)
//        m.DSA_3_0 = calcSuccRate(33,0)
//        m.DSA_3_2 = calcSuccRate(33,2)
        
        m.OPT1 = 100*m.DCA_4_0 + 100*m.DCA_4_2 + 50*m.DCA_4_4 + 10*m.AVG_LIGCOV_SUCC + 5*m.AVG_DSO_SUCC
        m.OPT2 = 100*m.DCA_4_0_PC + 50*m.DCA_4_2_PC + 5*m.AVG_LIGCOV_SUCC + 3*m.AVG_DSO_SUCC



        // TODO: move this somewhere else (getStats() shouldn't write to disk)
        if (StringUtils.isNotBlank(params.log_scores_to_file)) {
            PrintWriter w = new PrintWriter(new BufferedWriter(
                    new FileWriter(params.log_scores_to_file, true)))
            w.println("First line of the file")
            nonBindingScores.forEach({ it -> w.print(it); w.print(' ') })
            w.println()
            bindingScores.forEach({ it -> w.print(it); w.print(' ') })
            w.println()
            w.close()
        }

        return m
    }

    /**
     * get list of evaluation criteria used during eval routines
     */
    static List<PocketCriterium> getDefaultEvalCriteria() {
        double REQUIRED_POCKET_COVERAGE = 0.2  //  like in fpocket MOc criterion
        ((1..15).collect { new DCA(it) }) +         // 0-14
        ((1..10).collect { new DCC(it) }) +         // 15-24
//        ((1..6).collect { new DPA(it) }) +
//        ((1..6).collect { new DSA(it) }) +
        ([0.7,0.6,0.5,0.4,0.3,0.2,0.1].collect { new DSO(it) }) + // 25-31
        ([1,0.9,0.8,0.7,0.6,0.5,0.4,0.3,0.2,0.1].collect { new DSWO((double)it, REQUIRED_POCKET_COVERAGE) }) // 32-41
    }

//===========================================================================================================//

    String toSuccRatesCSV(List<Integer> tolerances) {
        return formatSuccRatesCSV(tolerances, calcSuccessRates(tolerances))
    }

    String getMiscStatsCSV() {

        stats.collect { "$it.key, ${fmt it.value}" }.join("\n")
    }

    String diffSuccRatesCSV(List<Integer> tolerances, Evaluation diffWith) {
        List<List<Double>> ours = calcSuccessRates(tolerances)
        List<List<Double>> theirs = diffWith.calcSuccessRates(tolerances)
        return formatSuccRatesCSV(tolerances, diffSuccRates(ours, theirs))
    }

    String formatSuccRatesCSV(List<Integer> tolerances, List<List<Double>> succRates) {

        StringBuilder str = new StringBuilder()
        str << "tolerances:," + tolerances.collect{"[$it]"}.join(",") + "\n"
        int i = 0
        criteria.each {
            str << criteria[i].toString() + "," + succRates[i].collect{ formatPercent(it) }.join(",")
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
        csv <<  "file, #ligands, ligand, chain, ligCode, #atoms, dca4rank, closestPocketDist, proteinDist, centerToProteinDist, sasDist, #contactProteinAtoms, atomIds \n"
        ligandRows.each { r ->
            List<String> rec = new ArrayList()

            rec.add r.protName
            rec.add r.ligCount
            rec.add r.ligName
            rec.add r.chainCode
            rec.add r.ligCode
            rec.add r.atoms
            rec.add r.dca4rank

            rec.add fmt(r.closestPocketDist)
            rec.add fmt(r.proteinDist)
            rec.add fmt(r.centerToProtDist)
            rec.add fmt(r.sasDist)
            rec.add r.contactAtoms
            rec.add r.atomIds.join(" ")
            
            csv << rec.join(", ") << "\n"
        }
        return csv.toString()
    }

    /**
     * @return print ranks for all criteria
     */
    String toRanksCSV() {
        StringBuilder csv = new StringBuilder()
        csv <<  "file, #ligands, ligand," + criteria.join(",") + "\n"
        ligandRows.each { row ->
            csv << "$row.protName, $row.ligCount, $row.ligName, " + row.ranks.join(",") + "\n"
        }
        return csv.toString()
    }

    String toProteinsCSV() {
        StringBuilder csv = new StringBuilder()

        csv <<  "name, #atoms, #proteinAtoms, #chains, chainNames, #ligands, #pockets, ligandNames, #ignoredLigands, ignoredLigNames, #smallLigands, smallLigNames, #distantLigands, distantLigNames\n"

        for (ProteinRow p in proteinRows) {
            csv << "$p.name,$p.atoms,$p.protAtoms,$p.chains,$p.chainNames,$p.ligands,$p.pockets,$p.ligNames,$p.ignoredLigands,$p.ignoredLigNames,$p.smallLigands,$p.smallLigNames,$p.distantLigands,$p.distantLigNames\n"
        }

        return csv.toString()
    }

    String toPocketsCSV() {
        StringBuilder csv = new StringBuilder()

        csv <<  "file, #ligands, #pockets, pocket, ligand, rank, score, newRank, oldScore, zScoreTP, probaTP, samplePoints, rawNewScore, pocketVolume, surfaceAtoms  \n"

        for (PocketRow p in pocketRows) {
            csv << "$p.protName,$p.ligCount,$p.pocketCount,$p.pocketName,$p.ligName,"
            csv << "$p.rank,$p.score,$p.newRank,${fmt(p.oldScore)},${fmt(p.auxInfo.zScoreTP)},${fmt(p.auxInfo.probaTP)},$p.auxInfo.samplePoints,${fmt(p.auxInfo.rawNewScore)},$p.pocketVolume,$p.surfaceAtomCount"
            csv << "\n"
        }

        return csv.toString()
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

//        double protDCA_4_0

        int sasPoints
    }

    static class LigRow {
        String protName
        String ligName
        String ligCode
        String chainCode
        int ligCount
        int atoms = 0
        int contactAtoms = 0
        double closestPocketDist 
        double centerToProtDist
        double proteinDist
        double sasDist
        int dca4rank = -1

        double avgPointScore
        double maxPointScore
        double avgMax3PointScore
        double avgMaxHalfPointScore

        List<Integer> atomIds
        List<Integer> ranks // of identified pocket for given criterion (-1=not identified)
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
    }

}
