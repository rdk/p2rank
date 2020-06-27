package cz.siret.prank.features.implementation.external

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord;
import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.ResidueNumber

import java.nio.charset.StandardCharsets;

/**
 * We expect in each directory file with same name as protein but with added
 * ".csv" extension.
 */
@Slf4j
@CompileStatic
public class ExternalAtomFeature {

    private static String PDB_SERIAL_COLUMN = "pdb serial";

    private static String CHAIN_COLUMN = "chain";

    private static String INS_CODE_COLUMN = "ins. code";

    private static String SEQ_CODE_COLUMN = "seq. code";

    private static String IGNORE_COLUMN_PREFIX = "#";

    private static List<String> RESIDUE_HEADER =
            Arrays.asList(CHAIN_COLUMN, INS_CODE_COLUMN, SEQ_CODE_COLUMN);

    private static enum CsvFileType {
        AtomCsv,
        ResidueCsv
    }

    private static class FileMetadata {

        public CsvFileType type;

        public List<Integer> valueColumns = new ArrayList<>();

    }

    private Map<String, List<Double>> atomFeatures = new HashMap<>();

    private Map<ResidueNumber, List<Double>> residueFeatures = new HashMap<>();

    private int totalAtomFeatureSize = 0;

    private int totalResidueFeatureSize = 0;

    private Map<String, FileMetadata> directoryMetadata = new HashMap<>();

    public void load(List<String> directories, String fileName) {
        ensureMetadataAreLoaded(directories, fileName);
        for (String dir in directories) {
            String path = dir + File.separator + fileName + ".csv";
            loadCsv(new File(path), directoryMetadata.get(dir));
        }
        validate(directories, fileName);
    }

    private void ensureMetadataAreLoaded(
            List<String> directories, String fileName) {
        directoryMetadata = new HashMap<>();
        for (String dir in directories) {
            if (directoryMetadata.containsKey(dir)) {
                continue;
            }
            File path = new File(dir, fileName + ".csv");
            FileMetadata metadata = loadCsvMetadata(path);
            directoryMetadata.put(dir, metadata);
            switch (metadata.type) {
                case CsvFileType.AtomCsv:
                    totalAtomFeatureSize += metadata.valueColumns.size();
                    break;
                case CsvFileType.ResidueCsv:
                    totalResidueFeatureSize += metadata.valueColumns.size();
                    break;
                default:
                    throw new PrankException("Unknown file type.");
            }
        }
    }

    private static FileMetadata loadCsvMetadata(File file) {
        FileInputStream stream = new FileInputStream(file);
        List<String> headers;
        try {
            InputStreamReader input = new InputStreamReader(
                    stream, StandardCharsets.UTF_8);
            CSVParser csvParser =
                    CSVFormat.RFC4180.withFirstRecordAsHeader().parse(input);
            headers = csvParser.getHeaderNames();
        } finally {
            stream.close();
        }
        if (headers.contains(PDB_SERIAL_COLUMN)) {
            FileMetadata result = new FileMetadata();
            result.type = CsvFileType.AtomCsv;
            for (int index = 0; index < headers.size(); ++index) {
                String colName = headers.get(index);
                if (colName.startsWith(IGNORE_COLUMN_PREFIX)) {
                    continue;
                }
                if (PDB_SERIAL_COLUMN == colName) {
                    continue;
                }
                result.valueColumns.add(index);
            }
            return result;
        } else if (headers.containsAll(RESIDUE_HEADER)) {
            FileMetadata result = new FileMetadata();
            result.type = CsvFileType.ResidueCsv;
            for (int index = 0; index < headers.size(); ++index) {
                String colName = headers.get(index);
                if (colName.startsWith(IGNORE_COLUMN_PREFIX)) {
                    continue;
                }
                if (RESIDUE_HEADER.contains(colName)) {
                    continue;
                }
                result.valueColumns.add(index);
            }
            return result;
        } else {
            throw new PrankException(
                    "Can't recognize CSV header for: " + file)
        }
    }

    private void loadCsv(File file, FileMetadata metadata) {
        FileInputStream stream = new FileInputStream(file);
        try {
            InputStreamReader input = new InputStreamReader(
                    stream, StandardCharsets.UTF_8);
            CSVParser csvParser =
                    CSVFormat.RFC4180.withFirstRecordAsHeader().parse(input);
            // Determine type of CSV file using information about header.
            switch (metadata.type) {
                case CsvFileType.AtomCsv:
                    loadAtomFeatureCsv(csvParser, metadata.valueColumns);
                    break;
                case CsvFileType.ResidueCsv:
                    loadResidueFeatureCsv(csvParser, metadata.valueColumns);
                    break;
                default:
                    throw new PrankException("Unknown file type for: " + file);
            }

        } finally {
            stream.close();
        }
    }

    private void loadAtomFeatureCsv(
            CSVParser csvParser, List<Integer> valueColumns) {
        for (CSVRecord record : csvParser) {
            String key = record.get(PDB_SERIAL_COLUMN);
            if (!atomFeatures.containsKey(key)) {
                atomFeatures.put(key, new ArrayList<>());
            }
            readToList(record, valueColumns, atomFeatures.get(key));
        }
    }

    private static void readToList(
            CSVRecord record, List<Integer> columns, List<Double> target) {
        for (int index : columns) {
            target.add(Double.parseDouble(record.get(index)));
        }
    }

    private void loadResidueFeatureCsv(
            CSVParser csvParser, List<Integer> valueColumns) {
        for (CSVRecord record : csvParser) {
            String chain = record.get(CHAIN_COLUMN);
            Integer seqCode = Integer.parseInt(record.get(SEQ_CODE_COLUMN));
            String insCodeStr = record.get(INS_CODE_COLUMN);
            Character insCode = null;
            if (!insCodeStr.isAllWhitespace()) {
                char[] chars = record.get(INS_CODE_COLUMN).getChars();
                if (chars.length > 0) {
                    insCode = Character.valueOf(chars[0]);
                }
            }
            ResidueNumber key = new ResidueNumber(chain, seqCode, insCode);
            if (!residueFeatures.containsKey(key)) {
                residueFeatures.put(key, new ArrayList<>());
            }
            readToList(record, valueColumns, residueFeatures.get(key));
        }
    }

    private void validate(List<String> directories, String fileName) {
        for (Map.Entry<String, List<Double>> entry : atomFeatures.entrySet()) {
            if (entry.value.size() != totalAtomFeatureSize) {
                throw new PrankException(
                        "Invalid atom feature size for ${entry.key}" +
                                " of ${entry.value.size()} " +
                                "expected ${totalAtomFeatureSize}")
            }
        }
        for (Map.Entry<ResidueNumber, List<Double>> entry :
                residueFeatures.entrySet()) {
            if (entry.value.size() != totalResidueFeatureSize) {
                throw new PrankException(
                        "Invalid residue feature size for ${entry.key}" +
                                " of ${entry.value.size()} " +
                                "expected ${totalResidueFeatureSize}")
            }
        }
        if (totalResidueFeatureSize + totalAtomFeatureSize == 0) {
            throw new PrankException(
                    "No features loaded for: ${fileName} from ${directories}");
        }
    }

    public double[] getValue(Atom atom) {
        double[] result =
                new double[totalAtomFeatureSize + totalResidueFeatureSize];
        int pdbSerial = atom.getPDBserial();
        if (atomFeatures.size() > 0) {
            double[] atomFeature = atomFeatures.get(pdbSerial);
            if (atomFeature == null) {
                throw new PrankException(
                        "Missing atom feature for: ${pdbSerial}");
            }
            copyToArray(atomFeature, result, 0);
        }
        ResidueNumber residueNumber = atom.getGroup().getResidueNumber();
        if (residueFeatures.size() > 0) {
            double[] residueFeature = residueFeatures.get(residueNumber);
            if (residueFeature == null) {
                throw new PrankException(
                        "Missing residue for: ${residueNumber}")
            }
            copyToArray(residueFeature, result, totalAtomFeatureSize);
        }
        return result;
    }

    private static void copyToArray(double[] from, double[] to, int offset) {
        for (int index = 0; index < from.length; ++index) {
            to[index + offset] = from[index];
        }
    }

}
