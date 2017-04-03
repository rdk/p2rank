package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.ConcavityLoader
import cz.siret.prank.domain.loaders.FPockeLoader
import cz.siret.prank.domain.loaders.PredictionLoader
import cz.siret.prank.domain.loaders.SiteHoundLoader
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ThreadPoolFactory
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.StrUtils
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool

import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents a dataset of protein files (or file pairs for PRANK rescoring: prediction file and liganated protein)
 */
@Slf4j
class Dataset implements Parametrized {

    /**
     * Contains file names, (optionally) ligand codes and cached structures.
     */
    class Item {
        String proteinFile  // liganated/unliganated protein for predictions
        String pocketPredictionFile //may be null
        Set<String> ligandNames

        String label

        PredictionPair cachedPair

        Item(String proteinFile, String pocketPredictionFile, Set<String> ligandNames) {
            this.proteinFile = proteinFile
            this.pocketPredictionFile = pocketPredictionFile
            this.ligandNames = ligandNames

            this.label = Futils.shortName( pocketPredictionFile ?: proteinFile )
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
            getPredictionPair().liganatedProtein
        }

        PredictionPair loadPredictionPair() {
            PredictionPair pair = getLoader(this).loadPredictionPair(proteinFile, pocketPredictionFile)
            Path parentDir = Paths.get(Futils.absPath(proteinFile)).parent
            String pdbBaseName = Futils.removeExtention(Futils.shortName(proteinFile))
            // TODO: Rewrite when better parsing of dataset file is finished.

            if (params.extra_features.any{s->s.contains("conservation")}) {
                pair.liganatedProtein.setConservationPathForChain({ String chainId ->
                    parentDir.resolve(pdbBaseName + chainId + ".scores").toFile()
                })
            }
            return pair
        }

    }


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

    interface Processor {

        abstract void processItem(Dataset.Item item)
    }

    /**
     * summary of a dataset processing run
     */
    static class Result {
        List<Dataset.Item> errorItems = Collections.synchronizedList(new ArrayList<Item>())

        boolean hasErrors() {
            errorItems.size() > 0
        }

        int getErrorCount() {
            errorItems.size()
        }
    }


//===========================================================================================================//

    String name
    String dir
    Map<String, String> attributes = new HashMap<>()
    List<Item> items = new ArrayList<>()
    boolean cached = false

    /** if dataset contains pairs of liganated protein files and pocket prediction files */
    boolean hasPairs = false

    /** dataset contains only list of pocket prediction files to rescore */
    boolean isForRescoring() {
        return !hasPairs
    }

    void setHasPairs(boolean hasPairs) {
        this.hasPairs = hasPairs
    }
//===========================================================================================================//

    Dataset withCache(boolean c = true) {
        cached = c
        return this
    }

    String getLabel() {
        Futils.removeExtention(name)
    }

    int getSize() {
        items.size()
    }

    /**
     * clear cached properties of cached proteins
     */
    void clearSecondaryCaches() {
        items.each {
            if (it.cachedPair!=null) {
                it.cachedPair.prediction.protein.clearCachedSurfaces()
                it.cachedPair.liganatedProtein.clearCachedSurfaces()
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
            if (hasPairs) {
                if (!Futils.exists(it.pocketPredictionFile)) {
                    log.error "prediction file doesn't exist: $it.pocketPredictionFile"
                    ok = false
                }
            }

            if (!Futils.exists(it.proteinFile)) {
                log.error "protein file doesn't exist: $it.proteinFile"
                ok = false
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

    /**
     * Process all dataset items with provided processor.
     */
    Result processItems(boolean parallel, final Processor processor) {

        log.trace "ITEMS: " + StrUtils.toStr(items)

        Result result = new Result()

        if (parallel) {
            int nt = ThreadPoolFactory.pool.poolSize
            log.info "processing dataset [$name] using $nt threads"

            AtomicInteger counter = new AtomicInteger(1);

            GParsPool.withExistingPool(ThreadPoolFactory.pool) {
                items.eachParallel { Item item ->
                    int num = counter.getAndAdd(1)
                    processItem(item, num, processor, result)
                }
            }

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
            String emsg = "error processing dataset item [$item.label] \n ${e.toString()}"
            log.error(emsg, e)
            result.errorItems.add(item)

            if (params.fail_fast) {
                throw new PrankException(emsg, e)
            }
        }

    }

    private PredictionLoader getLoader(Item item) {
        return getLoader(attributes.get("METHOD"), item)
    }

    private PredictionLoader getLoader(String method, Item item) {
        PredictionLoader res
        switch (method) {
            case "fpocket":
                res = new FPockeLoader()
                break
            case "concavity":
                res = new ConcavityLoader()
                break
            case "sitehound":
                res = new SiteHoundLoader()
                break
            default:
                res = new FPockeLoader()
                //throw new Exception("invalid method: $method")
        }

        if (res!=null) {
            res.loaderParams.ligandsSeparatedByTER = (attributes.get("LIGANDS_SEPARATED_BY_TER") == "true")  // for bench11 dataset
            res.loaderParams.relevantLigandsDefined = (attributes.get("LIGAND_CODES") == "true")
            res.loaderParams.relevantLigandNames = item.ligandNames
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
        Dataset res = new Dataset()
        res.items = items
        res.name = name
        res.dir = this.dir
        res.attributes = this.attributes
        res.cached = this.cached
        res.hasPairs = this.hasPairs

        return res
    }

    public static Dataset createSingleFileDataset(String pdbFile) {
        Dataset res = new Dataset()
        res.hasPairs = false
        res.dir = Futils.dir(pdbFile)
        res.name = Futils.shortName(pdbFile)
        res.items.add(res.newItem(pdbFile, pdbFile, null))

        return res
    }

    public boolean hasLigandCodes() {
        return ("true" == attributes.get("LIGAND_CODES"))
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
    static Dataset loadFromFile(String fname, boolean pairDataset = false) {
        File file = new File(fname)

        if (!file.exists()) {
            throw new PrankException("cannot find dataset file [$file.name]")
        }

        log.info "loading dataset [$file.absolutePath]"

        Dataset dataSet = new Dataset()

        String dir = file.parent
        dataSet.dir = dir
        dataSet.name = file.name
        dataSet.hasPairs = pairDataset

        for (String line in file.readLines()) {
            line = line.trim()
            if (line.startsWith("#") || line.isEmpty()) {
                // ignore comments and empty lines
            } else if (line.startsWith("PARAM.")) {
                String paramName = line.substring(line.indexOf('.') + 1, line.indexOf('=')).trim()
                String paramValue = line.substring(line.indexOf('=') + 1).trim()
                dataSet.attributes.put(paramName, paramValue)
            } else {
                String protf = null
                String predf = null
                Set<String> ligandCodes = null

                def cols = line.split() // split on whitespace

                //TODO: refactor messy dataset loading

                int file_cols = cols.length
                if (dataSet.hasLigandCodes()) {    // column with ligand codes is always last
                    file_cols -= 1
                    def lcodes = cols[cols.length - 1]
                    ligandCodes = StrUtils.split(lcodes, ",").toSet()
                }

                if (file_cols >= 2) {
                    predf = dir + "/" + cols[0]
                    protf = dir + "/" + cols[1]

                    dataSet.hasPairs = true
                } else {
                    predf = null
                    protf = dir + "/" + cols[0]
                }

                dataSet.items.add(dataSet.newItem(protf, predf, ligandCodes))
            }
        }

        if (!dataSet.checkFilesExist()) {
            throw new PrankException("dataset contains invalid files")
        }

        return dataSet
    }

    Item newItem(String ligf, String predf, Set<String> ligandCodes) {
        return new Item(ligf, predf, ligandCodes)
    }

}
