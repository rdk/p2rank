package cz.siret.prank.domain

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.LigandBasedResidueLabeler
import cz.siret.prank.domain.labeling.ResidueLabeler
import cz.siret.prank.domain.loaders.pockets.ConcavityLoader
import cz.siret.prank.domain.loaders.pockets.DeepSiteLoader
import cz.siret.prank.domain.loaders.pockets.FPocketLoader
import cz.siret.prank.domain.loaders.pockets.LiseLoader
import cz.siret.prank.domain.loaders.pockets.MetaPocket2Loader
import cz.siret.prank.domain.loaders.pockets.P2RankLoader
import cz.siret.prank.domain.loaders.pockets.PredictionLoader
import cz.siret.prank.domain.loaders.pockets.SiteHoundLoader
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ThreadPoolFactory
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static cz.siret.prank.utils.Sutils.partBefore
import static cz.siret.prank.utils.Sutils.partBetween
import static cz.siret.prank.utils.Sutils.split
import static org.apache.commons.lang3.StringUtils.isBlank

/**
 * Dataset represents a list of items (usually proteins) to be processed by the program.
 * Multi-column format with declared variable header allows to specify complementary data.
 *
 * see distro/test_data/readme.txt for dataset format specification
 */
@Slf4j
class Dataset implements Parametrized {

    static final Splitter SPLITTER = Splitter.on(CharMatcher.whitespace()).trimResults().omitEmptyStrings()

    /*
     * dataset parameter names
     * Dataset parameters can be defined in dataset file as PARAM.<PARAM_NAME>=<value>
     */
    static final String PARAM_PREDICTION_METHOD = "PREDICTION_METHOD"
    static final String PARAM_LIGANDS_SEPARATED_BY_TER = "LIGANDS_SEPARATED_BY_TER"
    static final String PARAM_RESIDUE_LABELING_FORMAT = "RESIDUE_LABELING_FORMAT"
    static final String PARAM_RESIDUE_LABELING_FILE = "RESIDUE_LABELING_FILE"

    /*
     * dataset column names
     */
    static final String COLUMN_PROTEIN = "protein"
    static final String COLUMN_PREDICTION = "prediction"
    static final String COLUMN_LIGANDS = "ligands"
    static final String COLUMN_LIGAND_CODES = "ligand_codes"
    static final String COLUMN_CONSERVATION_FILES_PATTERN = "conservation_files_pattern"
    static final String COLUMN_CHAINS = "chains"

    static final List<String> DEFAULT_HEADER = [ COLUMN_PROTEIN ]

//===========================================================================================================//

    String name
    String dir
    Map<String, String> attributes = new HashMap<>()
    List<String> header = DEFAULT_HEADER
    List<Item> items = new ArrayList<>()
    boolean cached = false

    private ResidueLabeler residueLabeler

//===========================================================================================================//

    Dataset withCache(boolean c = true) {
        cached = c
        return this
    }

    String getLabel() {
        Futils.removeExtension(name)
    }

    int getSize() {
        items.size()
    }

    /**
     * clear cached properties of cached proteins
     * (clears generated surfaces and secondary data calculated by feature implementations)
     */
    void clearSecondaryCaches() {
        items.each {
            if (it.cachedPair!=null) {
                it.cachedPair.prediction.protein.clearSecondaryData()
                it.cachedPair.protein.clearSecondaryData()
            }
        }
    }

    /**
     * clear cached structures
     */
    void clearPrimaryCaches() {
        items.each { it.cachedPair = null }
    }

    boolean checkFilesExist() {
        boolean ok = true
        items.each {
            if (header.contains(COLUMN_PREDICTION)) {
                if (!Futils.exists(it.pocketPredictionFile)) {
                    log.error "prediction file doesn't exist: $it.pocketPredictionFile"
                    ok = false
                }
            }
            if (header.contains(COLUMN_PROTEIN)) {
                if (!Futils.exists(it.proteinFile)) {
                    log.error "protein file doesn't exist: $it.proteinFile"
                    ok = false
                }
            }

        }
        return ok
    }

    /**
     * Process all dataset items with provided processor.
     */
    Result processItems(final Processor processor) {
        return processItems(params.parallel, processor)
    }

    Result processItems(final Closure processor) {
        return processItems(new Processor() {
            @Override
            void processItem(Item item) {
                processor.call(item)
            }
        })
    }

    /**
     * Process all dataset items with provided processor.
     */
    Result processItems(boolean parallel, final Processor processor) {

        log.trace "ITEMS: " + Sutils.toStr(items)

        Result result = new Result()

        if (parallel) {
            int nt = ThreadPoolFactory.pool.poolSize
            log.info "processing dataset [$name] using $nt threads"

            ExecutorService executor = Executors.newFixedThreadPool(params.threads)
            List<Callable> tasks = new ArrayList<>()
            items.eachWithIndex { Item item, int idx ->
                int num = idx + 1
                tasks.add(new Callable() {
                    @Override
                    Object call() throws Exception {
                        processItem(item, num, processor, result)
                    }
                })
            }
            executor.invokeAll(tasks)
            executor.shutdownNow()

        } else {
            log.info "processing dataset [$name] using 1 thread"

            int counter = 1
            items.each { Item item ->
                processItem(item, counter++, processor, result)
            }
        }

        return result
    }

    private void processItem(Item item, int num, Processor processor, Result result) {

        String msg = "processing [$item.label] ($num/$size)"
        log.info (
           "\n------------------------------------------------------------------------------------------------------------------------"
         + "\n$msg"
         + "\n------------------------------------------------------------------------------------------------------------------------"
        )
        System.out.println(msg)

        try {

            processor.processItem(item)

        } catch (Exception e) {
            String emsg = "error processing dataset item [$item.label]"
            log.error(emsg, e)
            result.errorItems.add(item)

            if (params.fail_fast) {
                throw new PrankException(emsg, e)
            }
        }

    }

    private PredictionLoader getLoader(Item item) {
        return getLoader(attributes.get(PARAM_PREDICTION_METHOD), item)
    }

    /**
     * Get configured instance of prediction loader.
     * @param method LBS prediction method name
     */
    private PredictionLoader getLoader(String method, Item item) {
        PredictionLoader res
        switch (method) {
            case "fpocket":
                res = new FPocketLoader()
                break
            case "concavity":
                res = new ConcavityLoader()
                break
            case "sitehound":
                res = new SiteHoundLoader()
                break
            case "lise":
                res = new LiseLoader()
                break
            case "deepsite":
                res = new DeepSiteLoader()
                break
            case "metapocket2":
                res = new MetaPocket2Loader()
                break
            case "p2rank":
                res = new P2RankLoader()
                break
            default:
                res = new FPocketLoader() // TODO: throw exception here, should not be run on prank predict
                //throw new Exception("Unknown prediction method defined in dataset: $method")
        }

        if (res!=null) {
            res.loaderParams.ligandsSeparatedByTER = (attributes.get(PARAM_LIGANDS_SEPARATED_BY_TER) == "true")  // for bench11 dataset
            res.loaderParams.relevantLigandsDefined = hasExplicitlyDefinedLigands()
            res.loaderParams.relevantLigandDefinitions = item.getLigandDefinitions()
            res.loaderParams.load_conservation_paths = (params.extra_features.any{s->s.contains("conservation")} || params.load_conservation)
            res.loaderParams.load_conservation = params.load_conservation
            res.loaderParams.conservation_origin = params.conservation_origin
        }

        return res
    }

    public Dataset randomSubset(int subsetSize, long seed) {
        if (subsetSize >= this.size) {
            return this
        }

        List<Item> shuffledItems = new ArrayList<>(items)
        Collections.shuffle(shuffledItems, new Random(seed))

        return createSubset( shuffledItems.subList(0, subsetSize), this.name + " (random subset of size $subsetSize)" )
    }

    List<Fold> sampleFolds(int k, long seed) {
        if (size < k)
            throw new PrankException("There is less dataset items than folds! ($k < $size)")

        List<Item> shuffledItems = new ArrayList<>(items)
        Collections.shuffle(shuffledItems, new Random(seed))

        // split to subsets
        List<Item>[] subsets = new List<Item>[k]
        for (int i=0; i<k; i++)
            subsets[i] = new ArrayList<Item>()
        for (int i=0; i!=items.size(); ++i)
            subsets[i % k].add( shuffledItems[i] )

        // create folds
        List<Fold> folds = new ArrayList<>(k)
        for (int i=0; i!=k; ++i) {
            Dataset evalset = createSubset(subsets[i], "${name}_fold.${k}.${i}_eval")
            Dataset trainset = createSubset( shuffledItems - subsets[i], "${name}_fold.${k}.${i}_train" )
            folds.add(new Fold(i+1, trainset, evalset))
        }

        return folds
    }

    /**
     * create dataset view from subset of items
     */
    private Dataset createSubset(List<Item> items, String name) {
        Dataset res = new Dataset(name, this.dir)
        res.items = items
        res.attributes = this.attributes
        res.cached = this.cached
        res.header = this.header

        return res
    }

    /**
     * @param pdbFile corresponds to protein column in the dataset (but with absolute path)
     * @param itemContext allows to specify additional columns May be null.
     * @return
     */
    public static Dataset createSingleFileDataset(String pdbFile, ProcessedItemContext itemContext) {
        Dataset ds = new Dataset(Futils.shortName(pdbFile), Futils.dir(pdbFile))

        Map<String, String> columnValues = new HashMap<>()

        if (itemContext!=null) {
            columnValues.putAll(itemContext.datsetColumnValues)
        }
        
        ds.items.add(ds.createNewItem(pdbFile, null, columnValues))

        return ds
    }

    /**
     * @param pdbFile corresponds to protein column in the dataset (but with absolute path)
     * @return
     */
    public static Dataset createSingleFileDataset(String pdbFile) {
        createSingleFileDataset(pdbFile, null)
    }

    /**
     * @return true if valid ligands are defined explicitly in the dataset (i.e dataset has 'ligands' or 'ligand_codes' column)
     */
    public boolean hasExplicitlyDefinedLigands() {
        return header.contains(COLUMN_LIGANDS) || header.contains(COLUMN_LIGAND_CODES)
    }

    /**
     * @return true if explicit residue labeling is defined a as a part of th dataset
     */
    boolean hasExplicitResidueLabeling() {
        return attributes.containsKey(PARAM_RESIDUE_LABELING_FORMAT)
    }

    /**
     * @return true if predicted residue labeling is defined a as a part of the dataset
     */
    boolean hasPredictedResidueLabeling() {
        return attributes.containsKey(PARAM_PREDICTION_METHOD)
    }

    /**
     * Loads residue labeling based on PARAM_RESIDUE_LABELING_FORMAT and PARAM_RESIDUE_LABELING_FILE attributes defined in the dataset
     */
    @Nullable
    ResidueLabeler getResidueLabeler() {
        if (residueLabeler == null && hasExplicitResidueLabeling()) {
            String labelingFile = dir + "/" + attributes.get(PARAM_RESIDUE_LABELING_FILE)
            residueLabeler = ResidueLabeler.loadFromFile(attributes.get(PARAM_RESIDUE_LABELING_FORMAT), labelingFile)
        }
        return  residueLabeler
    }

    /**
     * Loads residue labeling based on PARAM_RESIDUE_LABELING_FORMAT and PARAM_RESIDUE_LABELING_FILE attributes defined in the dataset
     */
    @Nullable
    ResidueLabeler<Boolean> getExplicitBinaryResidueLabeler() {
        ResidueLabeler labeler = getResidueLabeler()
        if (!labeler.binary) {
            throw new PrankException("Dataset residue labeling is not binary!")
        }
        return (ResidueLabeler<Boolean>) labeler
    }

    /**
     * Returns either explicit labeler (if defined in dataset) or ligand based labeler
     */
    @Nullable
    ResidueLabeler<Boolean> getBinaryResidueLabeler() {
        if (hasExplicitResidueLabeling()) {
            return getExplicitBinaryResidueLabeler()
        } else {
            return new LigandBasedResidueLabeler()
        }
    }

    Dataset(String name, String dir) {
        this.name = name
        this.dir = dir  ?: "."  // safeguard for github#7
    }

     /**
     * file format:
     * <pre>
     * {@code
     *
     * # comment .. global dataset attributes start line with "PARAM."
     * PARAM.METHOD=fpocket/concavity
     *
     * # uncomment if ligands are separated with TER record:
     *  PARAM.LIGANDS_SEPARATED_BY_TER=true
     *
     * # relative paths
     * xxx/pocket-prediction-output1.pdb  liganateded-protein-file1.pdb
     * xxx/pocket-prediction-output1.pdb  liganateded-protein-file2.pdb
     *
     * }
     * </pre>
     * @param fname
     * @return
     */
    static Dataset loadFromFile(String fname) {
        File file = new File(fname)

        if (!file.exists()) {
            throw new PrankException("cannot find dataset file [$file.name]")
        }

        log.info "loading dataset [$file.absolutePath]"

        String dir = file.parent ?: "." // fix for bug github#7
        Dataset dataset = new Dataset(file.name, dir)

        for (String line in file.readLines()) {
            line = partBefore(line, "#") // ignore everything after comment
            line = line.trim()
            if (isBlank(line)) {
                // ignore comments and empty lines
            } else if (line.startsWith("PARAM.")) {
                String paramName = line.substring(line.indexOf('.') + 1, line.indexOf('=')).trim()
                String paramValue = line.substring(line.indexOf('=') + 1).trim()
                dataset.attributes.put(paramName, paramValue)
            } else if (line.startsWith("HEADER:")) {
                dataset.header = parseHeader(line)
            } else {
                dataset.items.add(dataset.parseItem(line))
            }
        }
        
        log.debug("dataset header: {}", dataset.header)

        if (!dataset.checkFilesExist()) {
            throw new PrankException("dataset contains invalid files")
        }

        return dataset
    }

    private Item parseItem(String line) {
        List<String> cols = SPLITTER.splitToList(line)
        Map<String, String> colValues = new HashMap<>()
        header.eachWithIndex { String col, int i ->
            if (col == COLUMN_CONSERVATION_FILES_PATTERN && !cols[i].contains("%chainID%")) {
                throw new PrankException("invalid conservation files pattern." + cols[i] + "does " +
                        "not contain %chainID% substring.")
            }
            colValues.put(col, cols[i])
        }
        return createNewItem(colValues)
    }

    static List<String> parseHeader(String line) {
        SPLITTER.splitToList(line).tail()
    }


    static Dataset createJoined(List<Dataset> datasets) {
        assert datasets!=null && !datasets.empty
        
        if (datasets.size()==1) {
            return datasets[0]
        }

        String name = (datasets*.name).join('+')
        Dataset res = new Dataset(name, '--joined--')
        res.header = datasets[0].header

        for (Dataset d : datasets) {
            res.items.addAll(d.items)
        }

        return res
    }

//===========================================================================================================//

    /**
     * Definition of the ligand in the dataset file.
     * Examples:
     *
     * "MG"                    ... matches all ligand groups named MG
     * "MG[atom_id:1234]"      ... matches ligand group named MG that has atom with PDB id = 1234
     * "MG[group_id:C_120A]"   ... matches ligand group named MG that is in chain C and has PDB sequence number = 120A
     * "MG[C_120A]"            ... same as previous, group_id specifier is default
     */
    static class LigandDefinition {
        @Nonnull String groupName
        @Nullable String groupId
        @Nullable Integer atomId

        private LigandDefinition(String groupName, String groupId, Integer atomId) {
            this.groupName = groupName
            this.groupId = groupId
            this.atomId = atomId
        }

        boolean matchesGroup(@Nonnull Group group) {

            if (groupName == group.getPDBName()) {
                if (groupId==null && atomId==null) {
                    return true
                }
                if (groupId!=null) {
                    if (groupId == group.getResidueNumber()?.printFull()) {
                        return true
                    }
                }
                if (atomId!=null) {
                    for (Atom a : group.atoms) {
                        if (atomId == a.getPDBserial()) {
                            return true
                        }
                    }
                }
            }

            return false
        }

        static LigandDefinition parse(String str) {
            String groupName = null
            String groupId = null
            Integer atomId = null

            if (str.contains("[")) { // has specifier
                groupName = partBefore(str, "[").trim()
                String specifier = partBetween(str, "[", "]").trim()

                if (specifier.contains(":")) {
                    def (String stype, String svalue) = split(specifier, ":")
                    if (stype == "group_id") {
                        groupId = svalue
                        checkValidGroupId(groupId, str)
                    } else if (stype == "atom_id") {
                        try {
                            atomId = Integer.valueOf(svalue)
                        } catch (Exception e) {
                            throw new PrankException("Invalid ligand definition in the dataset file: '$str'. Invalid atom_id '$svalue'.")
                        }
                    } else {
                        throw new PrankException("Invalid ligand definition in the dataset file: '$str'. Invalid specifier type '$stype'. Valid options are: [atom_id, group_id]")
                    }
                } else {
                    groupId = specifier
                    checkValidGroupId(groupId, str)
                }
            } else {
                groupName = str
            }

            return new LigandDefinition(groupName, groupId, atomId)
        }

        private static checkValidGroupId(String groupId, String ligDef) {
            if (!groupId.contains("_")) {
                throw new PrankException("Invalid ligand definition in the dataset file: '$ligDef'. Invalid specifier '$groupId'.")
            }
        }

        @Override
        public String toString() {
            String res = groupName
            if (groupId != null) {
                res += "[group_id:$groupId]"
            } else if (atomId != null) {
                res += "[atom_id:$atomId]"
            }
            return res
        }

    }

    /**
     * Contains file names, (optionally) ligand codes and cached structures.
     */
    class Item {

        /**
         * origin dataset (points to original loaded dataset when dataset is split to folds)
         */
        Dataset dataset
        Map<String, String> columnValues
        String proteinFile  // liganated/unliganated protein for predictions
        @Nullable String pocketPredictionFile // nullable

        String label
        PredictionPair cachedPair
        @Nullable List<LigandDefinition> ligandDefinitions

        private Item(Dataset dataset, String proteinFile, String predictionFile, Map<String, String> columnValues) {
            this.dataset = dataset
            this.columnValues = columnValues
            this.proteinFile = proteinFile
            this.pocketPredictionFile = predictionFile
            this.label = Futils.shortName( pocketPredictionFile ?: proteinFile )

            ligandDefinitions = parseLigandsColumn(getLigandsColumnValue())
        }

        Prediction getPrediction() {
            // when running 'prank rescore' on a dataset with one column prediction is in proteinFile
            String file = pocketPredictionFile ?: proteinFile
            return getLoader(this).loadPredictionWithoutProtein(file)
        }

        PredictionPair getPredictionPair() {
            if (cached) {
                if (cachedPair==null) {
                    cachedPair = loadPredictionPair()
                    log.info "caching structures in dataset item [$label]"
                }
                return cachedPair
            } else {
                return loadPredictionPair()
            }
        }

        // for one column datasets
        Protein getProtein() {
            getPredictionPair().protein
        }

        PredictionPair loadPredictionPair() {
            getLoader(this).loadPredictionPair(proteinFile, pocketPredictionFile, getContext())
        }

        @Nullable
        private List<LigandDefinition> parseLigandsColumn(String columnValue) {
            if (columnValue==null) return null
            return split(columnValue, ",").collect { LigandDefinition.parse(it) }.toList()
        }

        @Nullable
        private String getLigandsColumnValue() {
            return columnValues.getOrDefault(COLUMN_LIGANDS, columnValues.get(COLUMN_LIGAND_CODES))
        }

        @Nullable
        private String getChainsColumnValue() {
            return columnValues.get(COLUMN_CHAINS)
        }

        boolean hasSpecifiedChaids() {
            String chains = getChainsColumnValue()
            
            return chains!=null && chains.trim()!="*"
        }

        /**
         * explicitly specified chain codes
         * @return null if column is not defined
         */
        @Nullable
        List<String> getChains() {
            if (!columnValues.containsKey(COLUMN_CHAINS)) {
                null
            } else {
                Splitter.on(",").split(columnValues[COLUMN_CHAINS]).asList()
            }
        }

        /**
         * @return binary residue labeling that is defined in the dataset
         */
        @Nullable
        BinaryLabeling getExplicitBinaryLabeling() {
            if (dataset.hasExplicitResidueLabeling()) {
                return dataset.explicitBinaryResidueLabeler.getBinaryLabeling(protein.residues, protein)
            }
            return null
        }

        /**
         * @return explicit (if defined in the dataset) or ligand based labeling
         */
        @Nullable
        BinaryLabeling getBinaryLabeling() {
            return dataset.binaryResidueLabeler.getBinaryLabeling(protein.residues, protein)
        }

        ProcessedItemContext getContext() {
            new ProcessedItemContext(this, columnValues)
        }
        
    }

    Item createNewItem(String proteinFile, String predictionFile, Map<String, String> columnValues) {
        return new Item(this, proteinFile, predictionFile, columnValues)
    }

    Item createNewItem(Map<String, String> columnValues) {
        String proteinFile = dir + "/" + columnValues.get(COLUMN_PROTEIN)
        String predictionFile = null
        if (header.contains(COLUMN_PREDICTION)) {
            predictionFile = dir + "/" + columnValues.get(COLUMN_PREDICTION)
        }

        return createNewItem(proteinFile, predictionFile, columnValues)
    }

//===========================================================================================================//

    static final class Fold {
        int num
        Dataset trainset
        Dataset evalset
        Fold(int num, Dataset trainset, Dataset evalset) {
            this.num = num
            this.trainset = trainset
            this.evalset = evalset
        }
    }


    @FunctionalInterface
    interface Processor {

        abstract void processItem(Item item)

    }

    /**
     * summary of a dataset processing run
     */
    static class Result {
        List<Item> errorItems = Collections.synchronizedList(new ArrayList<Item>())

        boolean hasErrors() {
            errorItems.size() > 0
        }

        int getErrorCount() {
            errorItems.size()
        }
    }

}
