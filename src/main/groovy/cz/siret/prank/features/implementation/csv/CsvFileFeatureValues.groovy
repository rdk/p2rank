package cz.siret.prank.features.implementation.csv

import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.ResidueNumber

import javax.annotation.Nullable
import java.nio.charset.StandardCharsets

/**
 * Represents values loaded from csv files for single protein.
 *
 * We expect in each directory file with same name as protein but with added
 * ".csv" extension.
 */
@Slf4j
@CompileStatic
class CsvFileFeatureValues {

    private static enum CsvFileType {
        ATOM_CSV,
        RESIDUE_CSV
    }

    private static class CsvColumn {
        int index
        String name

        CsvColumn(int index, String name) {
            this.index = index
            this.name = name
        }
    }

    private static class FileMetadata {
        CsvFileType type
        List<CsvColumn> valueColumns = new ArrayList<>()

        FileMetadata(CsvFileType type) {
            this.type = type
        }
    }

    /** Holds values from ATOM_CSV value column */
    private static class AtomFeature {
        Map<Integer, Double> values = new HashMap<>() //key: PDBSerial
    }

    /** Holds values from RESIDUE_CSV value column */
    private static class ResidueFeature {
        Map<ResidueNumber, Double> values = new HashMap<>()
    }

//===========================================================================================================//

    private static final CSVFormat CSV_FORMAT = CSVFormat.RFC4180.withFirstRecordAsHeader()

    private static final String PDB_SERIAL_COLUMN = "pdb serial"
    private static final String CHAIN_COLUMN = "chain"
    private static final String INS_CODE_COLUMN = "ins. code"
    private static final String SEQ_CODE_COLUMN = "seq. code"
    private static final String IGNORE_COLUMN_PREFIX = "#"

    private static final List<String> RESIDUE_HEADER = [CHAIN_COLUMN, INS_CODE_COLUMN, SEQ_CODE_COLUMN]

    
    private final boolean ignoreMissing
    
    private Map<String, FileMetadata> directoryMetadata = new HashMap<>()  // key: directory path
    private Map<String, AtomFeature> atomFeatures = new HashMap<>()        // key: value column name
    private Map<String, ResidueFeature> residueFeatures = new HashMap<>()  // key: value column name

//===========================================================================================================//

    CsvFileFeatureValues(boolean ignoreMissing) {
        this.ignoreMissing = ignoreMissing
    }

//===========================================================================================================//

    private void missingError(String msg) throws PrankException {
        if (ignoreMissing) {
            log.debug "MISSING: " + msg
        } else {
            throw new PrankException(msg)
        }
    }

    @Nullable
    private File getCsvFileForProtein(String proteinName, String dir) {
        return new File(dir, proteinName + ".csv")
    }

    void load(List<String> directories, String proteinName, List<String> enabledColumns) {
        ensureMetadataAreLoaded(directories, proteinName)
        validateColumns(proteinName, enabledColumns)

        for (String dir : directories) {
            File file = getCsvFileForProtein(proteinName, dir)
            if (Futils.exists(file)) {
                loadCsv(file, directoryMetadata.get(dir))
            }
            // file missing error was processed when loading metadata
        }
    }

    private void validateColumns(String proteinName, List<String> enabledColumns) {

        Map<String, String> columnsToDirs = new HashMap<>()

        for (String dir : directoryMetadata.keySet()) {
            FileMetadata metadata = directoryMetadata.get(dir)

            for (String column : metadata.valueColumns*.name) {
                // check for duplicates
                if (columnsToDirs.containsKey(column)) {
                    throw new PrankException("Duplicate column '$column' for protein '$proteinName' found in directories: [$dir AND ${columnsToDirs[column]}]")
                }

                columnsToDirs.put(column, dir)
            }

        }

        for (String column : enabledColumns) {
            if (!columnsToDirs.containsKey(column)) {
                missingError "Column '$column' is not defined for protein '$proteinName'"
            }
        }

    }

    private void ensureMetadataAreLoaded(List<String> directories, String proteinName) {
        directoryMetadata = new HashMap<>()
        for (String dir in directories) {
            if (directoryMetadata.containsKey(dir)) {
                continue
            }

            File file = getCsvFileForProtein(proteinName, dir)
            if (Futils.exists(file)) {
                FileMetadata metadata = loadCsvMetadata(file)
                directoryMetadata.put(dir, metadata)
            } else {
                missingError "CSV file for protein '$proteinName' doesn't exist in directory '$dir'"
            }

        }
    }

    private static boolean ignoreColumn(String colName) {
        return colName.startsWith(IGNORE_COLUMN_PREFIX)
    }

    private static CSVParser parseCsv(InputStream stream) {
        return CSV_FORMAT.parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
    }

    private static FileMetadata loadCsvMetadata(File file) {
        InputStream stream = Futils.bufferedFileInputStream(file)
        List<String> header = []
        try {
            CSVParser csvParser = parseCsv(stream)
            header = csvParser.getHeaderNames()
        } finally {
            stream.close()
        }
        if (header.contains(PDB_SERIAL_COLUMN)) {
            FileMetadata result = new FileMetadata(CsvFileType.ATOM_CSV)
            for (int i = 0; i < header.size(); ++i) {
                String colName = header[i]
                if (ignoreColumn(colName) || PDB_SERIAL_COLUMN == colName) {
                    continue
                }
                result.valueColumns.add(new CsvColumn(i, colName))
            }
            return result
        } else if (header.containsAll(RESIDUE_HEADER)) {
            FileMetadata result = new FileMetadata(CsvFileType.RESIDUE_CSV)
            for (int i = 0; i < header.size(); ++i) {
                String colName = header[i]
                if (ignoreColumn(colName) || RESIDUE_HEADER.contains(colName)) {
                    continue
                }
                result.valueColumns.add(new CsvColumn(i, colName))
            }
            return result
        } else {
            throw new PrankException("Can't recognize CSV header for: " + file)
        }
    }

    private void loadCsv(File file, FileMetadata metadata) {
        InputStream stream = Futils.bufferedFileInputStream(file)
        try {
            CSVParser csvParser = parseCsv(stream)
            // Determine type of CSV file using information about header.
            switch (metadata.type) {
                case CsvFileType.ATOM_CSV:
                    loadAtomFeatureCsv(csvParser, metadata.valueColumns)
                    break
                case CsvFileType.RESIDUE_CSV:
                    loadResidueFeatureCsv(csvParser, metadata.valueColumns)
                    break
                default:
                    throw new PrankException("Unknown file type for: " + file)
            }
        } finally {
            stream.close()
        }
    }

    private static Double readValue(CSVRecord record, int colIndex) {
        return Double.parseDouble(record.get(colIndex))
    }

    private ResidueNumber getResidueNumber(CSVRecord record) {
        String chain = record.get(CHAIN_COLUMN)
        Integer seqCode = Integer.parseInt(record.get(SEQ_CODE_COLUMN))
        String insCodeStr = record.get(INS_CODE_COLUMN)
        Character insCode = null
        if (!insCodeStr.isAllWhitespace()) {
            char[] chars = record.get(INS_CODE_COLUMN).getChars()
            if (chars.length > 0) {
                insCode = Character.valueOf(chars[0])
            }
        }
        return new ResidueNumber(chain, seqCode, insCode)
    }

    private void loadResidueFeatureCsv(CSVParser csvParser, List<CsvColumn> valueColumns) {
        for (CSVRecord record : csvParser) {

            ResidueNumber key = getResidueNumber(record)

            for (CsvColumn column : valueColumns) {
                ResidueFeature feat = residueFeatures.get(column.name)
                if (feat == null) {
                    feat = residueFeatures.get(column.name, new ResidueFeature())
                }
                feat.values.put(key, readValue(record, column.index))
            }
        }
    }

    private void loadAtomFeatureCsv(CSVParser csvParser, List<CsvColumn> valueColumns) {
        for (CSVRecord record : csvParser) {
            
            Integer key = Integer.valueOf(record.get(PDB_SERIAL_COLUMN))
            
            for (CsvColumn column : valueColumns) {
                AtomFeature feat = atomFeatures.get(column.name)
                if (feat == null) {
                    feat = atomFeatures.get(column.name, new AtomFeature())
                }
                feat.values.put(key, readValue(record, column.index))
            }
        }
    }

    @Nullable
    private Double getColumnValue(Atom atom, String columnName) {
        AtomFeature atomFeature = atomFeatures.get(columnName)
        if (atomFeature != null) {
            return atomFeature.values.get(atom.PDBserial)
        }

        ResidueFeature residueFeature = residueFeatures.get(columnName)
        if (residueFeature != null) {
            return residueFeature.values.get(atom.group.residueNumber)
        }

        // column not found
        return null
    }

    /**
     * @param atom
     * @return
     */
    double[] getValues(Atom atom, List<String> columns) {
        double[] result = new double[columns.size()]

        String pdbSerial = atom.PDBserial
        ResidueNumber residueNumber = atom.group.residueNumber

        for (int i = 0; i < columns.size(); ++i) {
            String column = columns[i]

            Double val = getColumnValue(atom, column)
            if (val == null) {
                missingError "CSV value of column [$column] for atom [$atom.PDBserial] is misisng"
                val = 0d
            }

            result[i] = val
        }
        return result
    }

}
