package cz.siret.prank.domain.loaders

import groovy.transform.CompileStatic
import org.apache.commons.csv.CSVRecord
import org.apache.commons.lang3.StringUtils

/**
 * Pocket-level metadata from the full ahoj_ubs CSV format.
 *
 * Contains pocket classification, density/overlap metrics, apo/holo probabilities,
 * and AlphaFold pLDDT score. All numeric fields use {@code Double.NaN} for missing values.
 *
 * Parsed by {@link AhojUbsSiteParser} when the CSV header contains these columns.
 */
@CompileStatic
class AhojSiteInfo {

    // CSV column names
    static final String COL_POCKET_CLASS             = "pocket_class"
    static final String COL_POCKET_DENSITY_COMBINED  = "pocket_density_combined"
    static final String COL_POCKET_DENSITY_PAIR      = "pocket_density_pair"
    static final String COL_POCKET_DENSITY_STRONGEST = "pocket_density_strongest"
    static final String COL_POCKET_OVERLAP_MODE      = "pocket_overlap_mode"
    static final String COL_POCKET_OVERLAP_OVERALL   = "pocket_overlap_overall"
    static final String COL_POCKET_OVERLAP_PAIR      = "pocket_overlap_pair"
    static final String COL_POCKET_SEPARATION_PAIR   = "pocket_separation_pair"
    static final String COL_POCKET_P_APO             = "pocket_p_apo"
    static final String COL_POCKET_P_HOLO            = "pocket_p_holo"
    static final String COL_POCKET_SCORE             = "pocket_score"
    static final String COL_MODEL_POCKET_PLDDT       = "model_pocket_plddt"
    static final String COL_N_APO_AVG                = "n_apo_avg"
    static final String COL_N_HOLO_AVG               = "n_holo_avg"

    /** Column used to detect whether the full format is present */
    static final String MARKER_COLUMN = COL_POCKET_CLASS

    // Ordered list of all export column headers
    static final List<String> EXPORT_COLUMNS = [
        COL_POCKET_CLASS,
        COL_POCKET_DENSITY_COMBINED,
        COL_POCKET_DENSITY_PAIR,
        COL_POCKET_DENSITY_STRONGEST,
        COL_POCKET_OVERLAP_MODE,
        COL_POCKET_OVERLAP_OVERALL,
        COL_POCKET_OVERLAP_PAIR,
        COL_POCKET_SEPARATION_PAIR,
        COL_POCKET_P_APO,
        COL_POCKET_P_HOLO,
        COL_POCKET_SCORE,
        COL_MODEL_POCKET_PLDDT,
        COL_N_APO_AVG,
        COL_N_HOLO_AVG,
    ].asImmutable()

    // Fields

    String pocketClass             // "apo" or "holo"
    double pocketDensityCombined
    double pocketDensityPair
    double pocketDensityStrongest
    double pocketOverlapMode
    double pocketOverlapOverall
    double pocketOverlapPair
    double pocketSeparationPair
    double pocketPApo
    double pocketPHolo
    double pocketScore             // NaN when empty in CSV
    double modelPocketPlddt
    double nApoAvg
    double nHoloAvg

    /**
     * Creates an AhojSiteInfo from a CSV record.
     * Assumes the record's header contains all required columns.
     */
    static AhojSiteInfo fromCsvRecord(CSVRecord record) {
        AhojSiteInfo info = new AhojSiteInfo()
        info.pocketClass            = record.get(COL_POCKET_CLASS)
        info.pocketDensityCombined  = parseDouble(record.get(COL_POCKET_DENSITY_COMBINED))
        info.pocketDensityPair      = parseDouble(record.get(COL_POCKET_DENSITY_PAIR))
        info.pocketDensityStrongest = parseDouble(record.get(COL_POCKET_DENSITY_STRONGEST))
        info.pocketOverlapMode      = parseDouble(record.get(COL_POCKET_OVERLAP_MODE))
        info.pocketOverlapOverall   = parseDouble(record.get(COL_POCKET_OVERLAP_OVERALL))
        info.pocketOverlapPair      = parseDouble(record.get(COL_POCKET_OVERLAP_PAIR))
        info.pocketSeparationPair   = parseDouble(record.get(COL_POCKET_SEPARATION_PAIR))
        info.pocketPApo             = parseDouble(record.get(COL_POCKET_P_APO))
        info.pocketPHolo            = parseDouble(record.get(COL_POCKET_P_HOLO))
        info.pocketScore            = parseDouble(record.get(COL_POCKET_SCORE))
        info.modelPocketPlddt       = parseDouble(record.get(COL_MODEL_POCKET_PLDDT))
        info.nApoAvg                = parseDouble(record.get(COL_N_APO_AVG))
        info.nHoloAvg               = parseDouble(record.get(COL_N_HOLO_AVG))
        return info
    }

    /**
     * Returns values in the same order as {@link #EXPORT_COLUMNS}.
     */
    List<String> toExportValues() {
        return [
            pocketClass ?: "",
            fmtDouble(pocketDensityCombined),
            fmtDouble(pocketDensityPair),
            fmtDouble(pocketDensityStrongest),
            fmtDouble(pocketOverlapMode),
            fmtDouble(pocketOverlapOverall),
            fmtDouble(pocketOverlapPair),
            fmtDouble(pocketSeparationPair),
            fmtDouble(pocketPApo),
            fmtDouble(pocketPHolo),
            fmtDouble(pocketScore),
            fmtDouble(modelPocketPlddt),
            fmtDouble(nApoAvg),
            fmtDouble(nHoloAvg),
        ]
    }

    /**
     * Returns a list of empty strings matching the column count,
     * for rows that have no AhojSiteInfo.
     */
    static List<String> emptyExportValues() {
        return Collections.nCopies(EXPORT_COLUMNS.size(), "")
    }

    private static double parseDouble(String s) {
        if (StringUtils.isBlank(s)) {
            return Double.NaN
        }
        return Double.parseDouble(s)
    }

    private static String fmtDouble(double v) {
        if (Double.isNaN(v)) {
            return ""
        }
        return String.valueOf(v)
    }

}
