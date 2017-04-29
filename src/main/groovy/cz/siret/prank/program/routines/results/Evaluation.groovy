package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.rendering.LabeledPoint
import cz.siret.prank.score.criteria.*
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import static cz.siret.prank.utils.Formatter.*
import static java.util.Collections.emptyList

/**
 * Represents evaluation of pocket prediction on a dataset of proteins
 *
 * Allows to collect results for a set of different pocket identification success criteria simultaneously.
 *
 * Threadsafe.
 */
@Slf4j
class Evaluation {

    /** cutoff distance in A around ligand atoms that determins which SAS points cover the ligand */
    static final double LIG_SAS_CUTOFF = 2

    IdentificationCriterium standardCriterium = new DCA(4.0)
    List<IdentificationCriterium> criteria
    List<ProteinRow> proteinRows = Collections.synchronizedList(new ArrayList<>())
    List<LigRow> ligandRows = Collections.synchronizedList(new ArrayList<>())
    List<PocketRow> pocketRows = Collections.synchronizedList(new ArrayList<>())

    int proteinCount
    int pocketCount

    int ligandCount
    int ignoredLigandCount
    int smallLigandCount
    int distantLigandCount

    int ligSASPointsCount
    int ligSASPointsCoveredCount

    Evaluation(List<IdentificationCriterium> criteria) {
        this.criteria = criteria
    }

    Evaluation() {
        this( getDefaultEvalCrtieria() )
    }

    /**
     * get list of evaluation criteria used during eval routines
     */
    static List<IdentificationCriterium> getDefaultEvalCrtieria() {
        ((1..15).collect { new DCA(it) }) + ((1..10).collect { new DCC(it) }) + ((1..6).collect { new DPA(it) }) + ((1..6).collect { new DSA(it) })
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
        if (atoms.distinctGroups.size() == 0) {
            return 0.0
        }
        return atoms.distinctGroups.stream().mapToDouble( {
            group->score.getScoreForResidue(group.getResidueNumber())})
                .average().getAsDouble()
    }

    void addPrediction(PredictionPair pair, List<Pocket> pockets) {

        List<LigRow> tmpLigRows = new ArrayList<>()
        List<PocketRow> tmpPockets = new ArrayList<>()

        Protein lp = pair.liganatedProtein
        Atoms sasPoints = pair.prediction.protein.connollySurface.points
        Atoms labeledPoints = new Atoms(pair.prediction.labeledPoints ?: emptyList())
        
        ProteinRow protRow = new ProteinRow()
        protRow.name = pair.name
        protRow.atoms = lp.allAtoms.count
        protRow.protAtoms = lp.proteinAtoms.count
        protRow.exposedAtoms = pair.prediction.protein.exposedAtoms.count
        protRow.chains = lp.structure.chains.size()
        protRow.chainNames = lp.structure.chains.collect {it.chainID}.join(" ")
        protRow.ligands = pair.ligandCount
        protRow.pockets = pair.prediction.pocketCount
        protRow.ligNames = lp.ligands.collect { "$it.name($it.size)" }.join(" ")
        protRow.ignoredLigands = lp.ignoredLigands.size()
        protRow.ignoredLigNames = lp.ignoredLigands.collect { "$it.name($it.size)" }.join(" ")
        protRow.smallLigands = lp.smallLigands.size()
        protRow.smallLigNames = lp.smallLigands.collect { "$it.name($it.size)" }.join(" ")
        protRow.distantLigands = lp.distantLigands.size()
        protRow.distantLigNames = lp.distantLigands.collect { "$it.name($it.size|${format(it.contactDistance,1)}|${format(it.centerToProteinDist,1)})" }.join(" ")
        protRow.sasPoints = sasPoints.count

        // ligand coverage
        Atoms ligSasPoints = labeledPoints.cutoffAtoms(lp.allLigandAtoms, LIG_SAS_CUTOFF)
        int n_ligSasPoints = ligSasPoints.count
        int n_ligSasPointsCovered = ligSasPoints.toList().findAll { ((LabeledPoint)it).predicted }.toList().size()
        log.debug "XXXX n_ligSasPoints: {} covered: {}", n_ligSasPoints, n_ligSasPointsCovered

        // Conservation stats
        ConservationScore score = lp.secondaryData.get(ConservationScore.conservationScoreKey)
        if (score != null) {
            protRow.avgConservation = getAvgConservationForAtoms(lp.proteinAtoms, score)
            Atoms bindingAtoms = lp.proteinAtoms.cutoffAtoms(lp.allLigandAtoms, lp.params.ligand_protein_contact_distance)
            protRow.avgBindingConservation = getAvgConservationForAtoms(bindingAtoms, score)
            Atoms nonBindingAtoms = lp.proteinAtoms - bindingAtoms
            protRow.avgNonBindingConservation = getAvgConservationForAtoms(nonBindingAtoms, score)
        }

        for (Ligand lig in pair.liganatedProtein.ligands) {
            LigRow row = new LigRow()

            row.protName = pair.name
            row.ligName = lig.name
            row.ligCode = lig.code

            row.ligCount = pair.ligandCount
            row.ranks = criteria.collect { criterium -> pair.rankOfIdentifiedPocket(lig, criterium, pockets) }
            row.dca4rank = pair.rankOfIdentifiedPocket(lig, standardCriterium, pockets)
            row.atoms = lig.atoms.count
            row.centerToProtDist = lig.centerToProteinDist

            Pocket closestPocket = closestPocket(lig, pockets)
            if (closestPocket!=null) {
                row.closestPocketDist = lig.atoms.dist(closestPocket.centroid)
            } else {
                row.closestPocketDist = Double.NaN
            }

            tmpLigRows.add(row)
        }

        for (Pocket pocket in pockets) {
            PocketRow prow = new PocketRow()
            prow.protName = pair.name
            prow.pocketName = pocket.name
            prow.pocketVolume = pocket.stats.realVolumeApprox
            prow.surfaceAtomCount = pocket.surfaceAtoms.count
            prow.ligCount = pair.ligandCount
            prow.pocketCount = pair.prediction.pocketCount

            Ligand ligand = pair.findLigandForPocket(pocket, standardCriterium)
            prow.ligName = (ligand==null) ? "" : ligand.name + "_" + ligand.code

            prow.score = pocket.stats.pocketScore
            prow.newScore = pocket.newScore
            prow.rank = pocket.rank
            prow.newRank = pocket.newRank

            prow.auxInfo = pocket.auxInfo

            if (score != null) {
                prow.avgConservation = getAvgConservationForAtoms(pocket.surfaceAtoms, score)
            }

            tmpPockets.add(prow)
        }
        List<PocketRow> conservationSorted = tmpPockets.toSorted {it.avgConservation}.reverse(true)
        List<PocketRow> combiSorted = tmpPockets.toSorted
            { Math.pow(it.avgConservation, lp.params.conservation_exponent) * it.newScore}.reverse
        (true)
        for (PocketRow prow : tmpPockets) {
            prow.conservationRank = conservationSorted.indexOf(prow) + 1
            prow.combinedRank = combiSorted.indexOf(prow) + 1
        }


        synchronized (this) {
            ligandCount += pair.ligandCount
            ignoredLigandCount += pair.ignoredLigandCount
            smallLigandCount += pair.smallLigandCount
            distantLigandCount += pair.distantLigandCount
            pocketCount +=tmpPockets.size()
            proteinCount += 1
            proteinRows.add(protRow)
            ligandRows.addAll(tmpLigRows)
            pocketRows.addAll(tmpPockets)
            ligSASPointsCount += n_ligSasPoints
            ligSASPointsCoveredCount += n_ligSasPointsCovered
        }
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

    double calcDefaultCriteriumSuccessRate(int tolerance) {
        return calcSuccRate(3, tolerance)
    }

    /**
     * n tolerance -> site considered sucesfully identified if pocket is predicted with rank within (#ligands + n)
     *
     * @return by [accessor, tolerance]
     */
    List<List<Double>> calcSuccessRates(List<Integer> tolerances) {
        assert tolerances !=null && !tolerances.isEmpty()

        List<List<Double>> res = new ArrayList<>()

        if (ligandCount==0) {
            log.error "no ligands!"
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

    public <T> double avg(List<T> list, Closure<T> closure) {
        if (list.size()==0) return Double.NaN
        list.collect { closure(it) }.findAll { it!=Double.NaN }.sum(0) / list.size()
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

    double getAvgClosestPocketDist() {
        avg ligandRows, { LigRow row -> row.closestPocketDist }
    }

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
        m.DCA_4_99 = calcDefaultCriteriumSuccessRate(99)

        // compare to getDefaultEvalCrtieria()
        m.DCC_4_0 = calcSuccRate(18,0)
        m.DCC_4_2 = calcSuccRate(18,2)
        m.DPA_1_0 = calcSuccRate(25,0)
        m.DPA_1_2 = calcSuccRate(25,2)
        m.DSA_3_0 = calcSuccRate(33,0)
        m.DSA_3_2 = calcSuccRate(33,2)

        m.DCA_4_0_NOMINAL = m.DCA_4_0 * m.LIGANDS

        return m
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
        int i = 0;
        criteria.each {
            str << criteria[i].toString() + "," + succRates[i].collect{ formatPercent(it) }.join(",")
            str << "\n"
            i++
        }

        return str.toString()
    }

    String toLigandsCSV() {
        StringBuilder csv = new StringBuilder()
        csv <<  "file, #ligands, ligand, ligCode, atoms, dca4rank, closestPocketDist, centerToProteinDist \n"
        ligandRows.each { r ->
            csv << "$r.protName, $r.ligCount, $r.ligName, $r.ligCode, $r.atoms, $r.dca4rank, ${fmt r.closestPocketDist}, ${fmt r.centerToProtDist},   \n"
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

        csv <<  "file, #ligands, #pockets, pocket, ligand, rank, score, newRank, newScore, samplePoints, rawNewScore, pocketVolume, surfaceAtoms  \n"

        for (PocketRow p in pocketRows) {
            csv << "$p.protName,$p.ligCount,$p.pocketCount,$p.pocketName,$p.ligName,"
            csv << "$p.rank,$p.score,$p.newRank,${fmt(p.newScore)},$p.auxInfo.samplePoints,${fmt(p.auxInfo.rawNewScore)},$p.pocketVolume,$p.surfaceAtomCount"
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

        int connollyPoints

        double avgConservation
        double avgBindingConservation
        double avgNonBindingConservation

        int sasPoints
    }

    static class LigRow {
        String protName
        String ligName
        String ligCode
        int ligCount
        int atoms = 0
        double closestPocketDist 
        double centerToProtDist
        int dca4rank = -1

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
        double newScore

        int conservationRank
        int combinedRank

        Pocket.AuxInfo auxInfo

        double avgConservation

        boolean isTruePocket() {
            StringUtils.isNotEmpty(ligName)
        }
   }

}
