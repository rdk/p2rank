package cz.siret.prank.domain

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.LigandBasedResidueLabeler
import cz.siret.prank.domain.labeling.ResidueLabeler
import cz.siret.prank.domain.loaders.DatasetItemLoader
import cz.siret.prank.domain.loaders.ExtendedResidueId
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.domain.loaders.pockets.*
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.Failable
import cz.siret.prank.program.P2Rank
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ThreadPoolFactory
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import static cz.siret.prank.utils.Cutils.newSynchronizedList
import static cz.siret.prank.utils.Sutils.*
import static org.apache.commons.lang3.StringUtils.isBlank

/**
 * Dataset represents a list of items (usually proteins) to be processed by the program.
 * Multi-column format with declared variable header allows to specify complementary data.
 *
 * see distro/test_data/readme.txt for dataset format specification
 */
@Slf4j
@CompileStatic
class Dataset implements Parametrized, Writable, Failable {

    private static final Splitter SPLITTER = Splitter.on(CharMatcher.whitespace()).trimResults().omitEmptyStrings()

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
    static final String COLUMN_CHAINS = "chains"
    static final String COLUMN_PREDICTION = "prediction"
    static final String COLUMN_LIGANDS = "ligands"
    static final String COLUMN_LIGAND_CODES = "ligand_codes"
    static final String COLUMN_CONSERVATION_FILES_PATTERN = "conservation_files_pattern"
    static final String COLUMN_APO_PROTEIN = "apo_protein"
    static final String COLUMN_APO_CHAINS = "apo_chains"

    static final List<String> DEFAULT_HEADER = [ COLUMN_PROTEIN ]

//===========================================================================================================//

    String name
    String dir
    Map<String, String> attributes = new HashMap<>()
    List<String> header = DEFAULT_HEADER
    List<Item> items = new ArrayList<>()
    boolean cached = false
    boolean apoholo = false
    boolean forTraining = false

    private ResidueLabeler residueLabeler

//===========================================================================================================//

    Dataset withCache(boolean c = true) {
        cached = c
        return this
    }

    Dataset forTraining(boolean t = true) {
        forTraining = t
        return this
    }

    String getLabel() {
        Futils.removeLastExtension(name)
    }

    int getSize() {
        items.size()
    }


    /**
     * clear cached properties of cached proteins
     * (clears generated surfaces and secondary data calculated by feature implementations)
     */
    void clearSecondaryCaches() {
        items.each { Item it ->
            if (it.cachedPair != null) {
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

//===========================================================================================================//

    /**
     * Process all dataset items.
     * Runs in parallel or serially depending on configuration.
     */
    Result processItems(final Processor processor, boolean quiet = false) {
        return doProcessItems(params.parallel, processor, quiet)
    }

    /**
     * Process all dataset items.
     * Runs in parallel or serially depending on configuration.
     */
    Result processItems(final Closure processor, boolean quiet) {
        return processItems(new Processor() {
            @Override
            void processItem(Item item) {
                processor.call(item)
            }
        }, quiet)
    }

    Result processItems(final Closure processor) {
        return processItems(processor, false)
    }

    Result processItemsQuiet(final Closure processor) {
        return processItems(processor, true)
    }

    /**
     * Process all dataset items with provided processor.
     */
    private Result doProcessItems(boolean parallel, final Processor processor, boolean quiet = false) {

        //if (log.isTraceEnabled()) {
        //    log.trace "ITEMS: {}", Sutils.toStr(items) // doesn't work with java 17
        //}

        if (items.empty) {
            throw new PrankException("Trying to process dataset with no items [$name].")
        }

        Result result = new Result()

        if (parallel) {
            int nt = ThreadPoolFactory.pool.poolSize

            if (!quiet) {
                log.info "processing dataset [$name] using $nt threads"
            }

            ExecutorService executor = Executors.newFixedThreadPool(params.threads)
            List<Callable<Object>> tasks = new ArrayList<>()
            items.eachWithIndex { Item item, int idx ->
                int num = idx + 1
                tasks.add(new Callable() {
                    @Override
                    Object call() throws Exception {
                        if (P2Rank.isShuttingDown()) {
                            // stop processing other items in parallel if P2Rank already failed (see fail_fast)
                            return null
                        }

                        processssItem(item, num, processor, result, quiet)
                        return null
                    }
                })
            }
            executor.invokeAll((Collection<Callable<Object>>)tasks)
            executor.shutdownNow()

        } else {
            if (!quiet) {
                log.info "processing dataset [$name] using 1 thread"
            }

            int counter = 1
            for (Item item : items) {
                processssItem(item, counter++, processor, result, quiet)
            }
        }

        return result
    }

    private void processssItem(Item item, int num, Processor processor, Result result, boolean quiet) {

        if (!quiet) {
            String msg = "processing [$item.label] ($num/$size)"
            log.info (
                  "\n------------------------------------------------------------------------------------------------------------------------"
                + "\n$msg"
                + "\n------------------------------------------------------------------------------------------------------------------------\n"
            )
            write(msg)
        }

        try {

            processor.processItem(item)

        } catch (Exception e) {
            result.errorItems.add(item)

            fail("error processing dataset item [$item.label]", e, log)
        }

    }

//===========================================================================================================//

    boolean checkFilesExist() {
        AtomicBoolean allOk = new AtomicBoolean(true)

        boolean checkProtein = header.contains(COLUMN_PROTEIN)
        boolean checkApoProtein = header.contains(COLUMN_APO_PROTEIN)
        boolean checkPrediction = header.contains(COLUMN_PREDICTION)

        processItemsQuiet { Item it ->
            if (checkProtein) {
                if (!Futils.exists(it.proteinFile)) {
                    log.error "protein file doesn't exist: $it.proteinFile"
                    allOk.set(false)
                }
            }
            if (checkApoProtein) {
                if (!Futils.exists(it.apoProteinFile)) {
                    log.error "apo_protein file doesn't exist: $it.proteinFile"
                    allOk.set(false)
                }
            }
            if (checkPrediction) {
                if (!Futils.exists(it.pocketPredictionFile)) {
                    log.error "prediction file doesn't exist: $it.pocketPredictionFile"
                    allOk.set(false)
                }
            }
        }

        return allOk.get()
    }

//===========================================================================================================//

    private DatasetItemLoader getLoader(Item item) {
        new DatasetItemLoader(getLoaderParams(item), getPredictionLoader())
    }

    private LoaderParams getLoaderParams(Item item) {
        LoaderParams lp = new LoaderParams()
        lp.ligandsSeparatedByTER = (attributes.get(PARAM_LIGANDS_SEPARATED_BY_TER) == "true")  // for bench11 dataset
        lp.relevantLigandsDefined = hasExplicitlyDefinedLigands()
        lp.relevantLigandDefinitions = item.getLigandDefinitions()
        lp.load_conservation = (params.load_conservation || params.selectedFeatures.any{ s->s.contains("conserv")})
        return lp
    }

    /**
     * Get instance of prediction loader.
     * @return null if prediction method is not specified in the dataset
     */
    @Nullable
    private PredictionLoader getPredictionLoader() {
        String predictionMethod = attributes.get(PARAM_PREDICTION_METHOD)  // LBS prediction method name specified in the dataset

        if (predictionMethod == null) {
            return null
        }

        PredictionLoader res
        switch (predictionMethod) {
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
                throw new PrankException("Unknown prediction method specified in the dataset: $predictionMethod")
        }

        return res
    }

    Dataset randomSubset(int subsetSize, long seed) {
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
            int num = i+1
            Dataset evalset = createSubset(subsets[i], "${name}_fold.${k}.${num}_eval").forTraining(false)
            Dataset trainset = createSubset(shuffledItems - subsets[i], "${name}_fold.${k}.${num}_train").forTraining(true)
            folds.add(new Fold(num, trainset, evalset))
        }

        return folds
    }

    /**
     * create dataset view from subset of items
     */
    private Dataset createSubset(List<Item> items, String name) {

        items = items.collect { it.copy() }

        Dataset res = new Dataset(name, this.dir)
        res.items = items
        res.attributes = this.attributes
        res.cached = this.cached
        res.header = this.header
        res.apoholo = this.apoholo

        items.forEach { it.currentDataset = res }

        return res
    }

    /**
     * @param pdbFile corresponds to protein column in the dataset (but with absolute path)
     * @param itemContext allows to specify additional columns May be null.
     * @return
     */
    static Dataset createSingleFileDataset(String proteinFile, ProcessedItemContext itemContext) {
        Dataset ds = new Dataset(Futils.shortName(proteinFile), Futils.dir(proteinFile))

        Map<String, String> columnValues = new HashMap<>()

        if (itemContext!=null) {
            columnValues.putAll(itemContext.datsetColumnValues)
        }

        ds.items.add(ds.createNewItemForSingleFileDs(proteinFile, columnValues))

        return ds
    }

    /**
     * @param pdbFile corresponds to protein column in the dataset (but with absolute path)
     * @return
     */
    static Dataset createSingleFileDataset(String pdbFile) {
        createSingleFileDataset(pdbFile, null)
    }

    /**
     * @return true if valid ligands are defined explicitly in the dataset (i.e dataset has 'ligands' or 'ligand_codes' column)
     */
    boolean hasExplicitlyDefinedLigands() {
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

        if (dataset.header.contains(COLUMN_APO_PROTEIN)) {
            if (!dataset.header.contains(COLUMN_PROTEIN)) {
                throw new PrankException("Invalid dataset file. Dataset that contains '${COLUMN_APO_PROTEIN}' must also contain '${COLUMN_PROTEIN}' column.")
            }
            dataset.apoholo = true
        }

        if (dataset.size == 0) {
            throw new PrankException("Empty dataset [$dataset.name]")
        }

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
        return createNewItemFromColumns(colValues)
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
            res.items.addAll(d.items.collect { it.copy() })
        }

        res.items.forEach { it.currentDataset = res }

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
     * "MG[contact_res_ids:A_D246,A_D257,A_T259,A_E423]" ... specifies contact residues of given ligand
     */
    static class LigandDefinition {
        @Nonnull String originalString
        @Nonnull String groupName
        @Nullable String groupId
        @Nullable Integer atomId
        @Nullable List<String> contactResidueIds
        @Nonnull List<String> matchesGroupIds = new ArrayList<>()

        LigandDefinition(String originalString, String groupName, String groupId, Integer atomId, List<String> contactResidueIds) {
            this.originalString = originalString
            this.groupName = groupName
            this.groupId = groupId
            this.atomId = atomId
            this.contactResidueIds = contactResidueIds
        }

        boolean matchesGroup(@Nonnull Group group, @Nonnull Protein protein) {

            boolean matches = false

            if (groupName == group.getPDBName()) {
                if (groupId == null && atomId == null && contactResidueIds == null) {
                    matches = true
                }
                if (groupId != null) {
                    if (groupId == group.getResidueNumber()?.printFull()) {
                        matches = true
                    }
                }
                if (atomId != null) {
                    for (Atom a : group.atoms) {
                        if (atomId == a.getPDBserial()) {
                            matches = true
                        }
                    }
                }
                if (contactResidueIds != null) {
                    matches = matchesGroupWithContactResidues(group, protein, contactResidueIds)
                }
            }

            if (matches) {
                matchesGroupIds.add(group.getResidueNumber()?.printFull())
            }

            return matches
        }

        private matchesGroupWithContactResidues(@Nonnull Group group, @Nonnull Protein protein, @Nonnull List<String> contactResidueIds) {
            List<ExtendedResidueId> conactResIds = contactResidueIds.collect { ExtendedResidueId.parse(it) }
            Atoms ligAtoms = Atoms.allFromGroup(group)
            Atoms surroundingProtAtoms = protein.proteinAtoms.cutoutShell(ligAtoms, 7)
            Residues surroundingResidues = Residues.of(protein.residues.getDistinctForAtoms(surroundingProtAtoms))
            Residues closestResidues = Residues.of(surroundingResidues.findNNearestToAtoms(conactResIds.size(), ligAtoms))

            int matched = 0
            for (ExtendedResidueId resId : conactResIds) {
                Residue res = closestResidues.getResidue(Residue.Key.of(resId.toResidueNumber()))
                boolean resMatches = false
                if (res != null) {
                    // check if aa code matches
                    if (resId.aaCode == null) {
                        resMatches = true
                    } else {
                        AA aa = res.aa
                        if (aa == null) { // non standard AA -> assume match
                            resMatches = true
                        } else {
                            resMatches = aa.codeChar == resId.aaCode
                        }
                    }
                }
                if (resMatches) {
                    matched++
                }
            }

            return matched == contactResidueIds.size()
        }

        static LigandDefinition parse(String str) {
            String groupName = null
            String groupId = null
            Integer atomId = null
            List<String> contactResidueIds = null

            if (str.contains("[")) { // has specifier
                groupName = partBefore(str, "[").trim()
                String specifier = partBetween(str, "[", "]").trim()

                if (specifier.contains(":")) {
                    def tokens = split(specifier, ":")
                    String stype = tokens[0]
                    String svalue = tokens[1]
                    
                    if (stype == "group_id") {
                        groupId = svalue
                        checkValidGroupId(groupId, str)
                    } else if (stype == "atom_id") {
                        try {
                            atomId = Integer.valueOf(svalue)
                        } catch (Exception e) {
                            throw new PrankException("Invalid ligand definition in the dataset file: '$str'. Invalid atom_id '$svalue'.")
                        }
                    } else if (stype == "contact_res_ids") {
                        contactResidueIds = split(svalue, ",")
                        if (contactResidueIds.empty) {
                            throw new PrankException("No contact residue ids specified for ligand: '$str'. At least one is required.")
                        }
                        for (String cres : contactResidueIds) {
                            checkValidGroupId(cres, str)
                        }
                    } else {
                        throw new PrankException("Invalid ligand definition in the dataset file: '$str'. Invalid specifier type '$stype'. Valid options are: [atom_id, group_id, contact_res_ids]")
                    }
                } else { // specifier is ligand groupId by default
                    groupId = specifier
                    checkValidGroupId(groupId, str)
                }
            } else {
                groupName = str.trim()
            }

            return new LigandDefinition(str, groupName, groupId, atomId, contactResidueIds)
        }

        private static checkValidGroupId(String groupId, String ligDef) {
            if (!groupId.contains("_")) {
                throw new PrankException("Invalid ligand definition in the dataset file: '$ligDef'. Invalid specifier '$groupId'.")
            }
        }

        @Override
        String toString() {
            String res = groupName
            if (groupId != null) {
                res += "[group_id:$groupId]"
            } else if (atomId != null) {
                res += "[atom_id:$atomId]"
            } else if (contactResidueIds != null) {
                res += "[contact_res_ids:${contactResidueIds.join(',')}]"
            }
            return res
        }

    }

    /**
     * Contains file names, (optionally) ligand codes and cached structures.
     */
    class Item {

        /**
         * Origin dataset. Points to original loaded dataset (from file), before dataset is split to crossval folds or joined with other dataset.
         */
        Dataset originDataset

        /**
         * Current dataset. Only differs when datasets are joined or split to folds. In that case points to the joined dataset or (joined) fold(s).
         */
        Dataset currentDataset

        String label

        Map<String, String> columnValues

        /**
         * liganated or unliganated protein for predictions
         */
        String proteinFile
        @Nullable String apoProteinFile
        @Nullable String pocketPredictionFile

        @Nullable List<String> chains
        @Nullable List<String> apoChains


        /**
         * Loaded protein with prediction (if available)
         */
        PredictionPair cachedPair
        @Nullable List<LigandDefinition> ligandDefinitions

        private Item(Dataset dataset,
                     String label,
                     String proteinFile,
                     @Nullable String apoProteinFile,
                     @Nullable String predictionFile,
                     @Nullable List<String> chains,
                     @Nullable List<String> apoChains,
                     @Nullable List<LigandDefinition> ligandDefinitions,
                     Map<String, String> columnValues) {
            this.originDataset = dataset
            this.currentDataset = dataset
            this.label = label
            this.proteinFile = proteinFile
            this.apoProteinFile = apoProteinFile
            this.pocketPredictionFile = predictionFile
            this.chains = chains
            this.apoChains = apoChains
            this.ligandDefinitions = ligandDefinitions
            this.columnValues = columnValues
        }

        private Item(Item item) {
            this.originDataset        = item.originDataset
            this.currentDataset       = item.currentDataset
            this.label                = item.label
            this.proteinFile          = item.proteinFile
            this.apoProteinFile       = item.apoProteinFile
            this.pocketPredictionFile = item.pocketPredictionFile
            this.chains               = item.chains
            this.apoChains            = item.apoChains
            this.ligandDefinitions    = item.ligandDefinitions
            this.columnValues         = item.columnValues

            this.cachedPair           = item.cachedPair
        }

        Item copy() {
            return new Item(this)
        }


        PredictionPair getPredictionPair() {
            PredictionPair res = null
            if (cached) {
                if (cachedPair == null) {
                    cachedPair = loadPredictionPair()
                    log.info "caching structures in dataset item [$label]"
                }
                res = cachedPair
            } else {
                res = loadPredictionPair()
            }

            res.forTraining = currentDataset.forTraining

            return res
        }

        // for one column datasets
        Protein getProtein() {
            getPredictionPair().protein
        }

        Protein getApoProtein() {
            getPredictionPair().apoProtein
        }

        private PredictionPair loadPredictionPair() {
            return getLoader(this).loadPredictionPair(this)
        }

        /**
         * explicitly specified chain codes
         * @return null if column is not defined
         */
        @Nullable
        List<String> getChains() {
            chains
        }

        /**
         * explicitly specified chain codes
         * @return null if column is not defined
         */
        @Nullable
        List<String> getApoChains() {
            apoChains
        }

        /**
         * @return binary residue labeling that is defined in the dataset
         */
        @Nullable
        BinaryLabeling getExplicitBinaryLabeling() {
            if (originDataset.hasExplicitResidueLabeling()) {
                return originDataset.explicitBinaryResidueLabeler.getBinaryLabeling(protein.residues, protein)
            }
            return null
        }

        /**
         * @return explicit (if defined in the dataset) or ligand based labeling
         */
        @Nullable
        BinaryLabeling getBinaryLabeling() {
            return originDataset.binaryResidueLabeler.getBinaryLabeling(protein.residues, protein)
        }

        ProcessedItemContext getContext() {
            new ProcessedItemContext(this, columnValues)
        }
        
    }

    Item createNewItemForSingleFileDs(String proteinFile, Map<String, String> columnValues) {
        String label = Futils.shortName(proteinFile)
        return new Item(
                this,
                label,
                proteinFile,
                null,
                null,
                null,
                null,
                null,
                columnValues
        )
    }

    Item createNewItemFromColumns(Map<String, String> columnValues) {
        String proteinFile = absolutePathOrPrefixWithDir(columnValues.get(COLUMN_PROTEIN), dir)
        String apoProteinFile = absolutePathOrPrefixWithDir(columnValues.get(COLUMN_APO_PROTEIN), dir)
        String predictionFile = absolutePathOrPrefixWithDir(columnValues.get(COLUMN_PREDICTION), dir)

        String label = Futils.shortName(predictionFile ?: proteinFile)
        List<LigandDefinition> ligandDefinitions = parseLigandsColumn(getLigandsColumnValue(columnValues))
        List<String> chains = parseChainsColumn(columnValues.get(COLUMN_CHAINS))
        List<String> apoChains = parseChainsColumn(columnValues.get(COLUMN_APO_CHAINS))

        return new Item(
                this,
                label,
                proteinFile,
                apoProteinFile,
                predictionFile,
                chains,
                apoChains,
                ligandDefinitions,
                columnValues
        )
    }

    @Nullable
    String absolutePathOrPrefixWithDir(@Nullable String path, String dir) {
        if (path == null) return null
        return Futils.prependIfNotAbsolute(path, dir)
    }

    @Nullable
    private List<LigandDefinition> parseLigandsColumn(String columnValue) {
        if (columnValue == null) return null
        List<String> ligDefs = Sutils.splitRespectInnerParentheses(columnValue, ',' as char, '[' as char, ']' as char)
        return ligDefs.collect { LigandDefinition.parse(it) }
    }

    /**
     *
     * @param columnValue
     * @return null = all chains
     */
    @Nullable
    private List<String> parseChainsColumn(@Nullable String columnValue) {
        if (columnValue == null || columnValue.trim() == "*") {
            return null
        } else {
            return CHAIN_SPLITTER.split(columnValue).asList()
        }
    }

    private static final Splitter CHAIN_SPLITTER = Splitter.on(",")

    @Nullable
    private String getLigandsColumnValue(Map<String, String> columnValues) {
        return columnValues.getOrDefault(COLUMN_LIGANDS, columnValues.get(COLUMN_LIGAND_CODES))
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
    static interface Processor {

        abstract void processItem(Item item)

    }

    /**
     * summary of a dataset processing run
     */
    static class Result {
        List<Item> errorItems = newSynchronizedList()

        boolean hasErrors() {
            errorItems.size() > 0
        }

        int getErrorCount() {
            errorItems.size()
        }
    }

}
