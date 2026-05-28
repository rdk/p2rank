# Conservation Scores in P2Rank

## Background

Sequence conservation is a strong signal for predicting binding sites.
Conservation-aware models have been available in [PrankWeb](https://prankweb.cz) for several years,
but were previously only usable through PrankWeb (the conservation pipeline was bundled with
PrankWeb's infrastructure rather than runnable on its own).

Since **P2Rank 2.6** (available in alpha builds), users can obtain conservation scores
on the fly from an external conservation server and use conservation-aware prediction models
directly from the command line.

## Conservation-Aware Models

P2Rank ships with pre-trained models that include conservation features:

| Config | Description | Usage |
|--------|-------------|-------|
| `conservation_hmm` | For standard experimental structures (X-ray) | `-c conservation_hmm` |
| `alphafold_conservation_hmm` | For AlphaFold, cryo-EM, and NMR models (no b-factor feature) | `-c alphafold_conservation_hmm` |

> [!IMPORTANT]
> The pre-trained conservation-aware models shipped with P2Rank were trained
> using HMM-based conservation scores (`.hom` format, a file extension inherited
> from the legacy JSD scoring method).
> Using conservation scores from a different method (e.g. with different score distribution or format)
> is not expected to produce good results and may not work at all.
> If you want to use a different conservation method, you would need to retrain the model.

## Quick Start

```bash
# Predict with conservation (fetching scores from a server)
prank predict -f protein.pdb \
  -c conservation_hmm \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030

# Preload conservation cache for a dataset, then predict
prank preload-conservation dataset.ds \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030

prank predict dataset.ds \
  -c conservation_hmm \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030
```

## Obtaining Conservation Scores

There are two ways to provide conservation scores to P2Rank:

### 1. Pre-computed Score Files

Place `.hom` score files next to the protein files (or in directories specified by `-conservation_dirs`).
Files are matched by naming convention: `{proteinBaseName}_{chainId}.hom` (e.g., `2W83_A.hom`).

```bash
prank predict -f protein.pdb -c conservation_hmm -conservation_dirs ./my_scores/
```

> [!NOTE]
> `-conservation_type` is only needed when using a provider or the cache mechanism.
> With pre-computed files and `-conservation_dirs`, files are matched by name convention alone.

### 2. External Conservation Server (since 2.6)

Configure P2Rank to fetch scores from an HTTP server on demand.
Fetched scores are cached locally so subsequent runs reuse them.

```bash
prank predict -f protein.pdb \
  -c conservation_hmm \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030
```

The server receives a POST request to `{url}/conservation` with a JSON body:
```json
{"fasta_content": ">proteinName_chainId\nSEQUENCE"}
```
and returns the raw `.hom` TSV content.

## Running the Conservation Server with Docker

The HMM conservation pipeline is available as a Docker image
(not published on Docker Hub yet, but can be built from `prankweb` repo).

> [!NOTE]
> These instructions will be simplified in the near future
> when the conservation server becomes part of the official P2Rank docker image
> or is published as a separate dedicated docker image.

See also https://github.com/rdk/prankweb/tree/conservation-server/executor-p2rank/conservation.

```bash
# for now, you need to build the image from the `conservation-server` branch of the prankweb repo fork
git clone -b conservation-server https://github.com/rdk/prankweb.git
cd prankweb

# to avoid running docker as root, add current user to docker group (you may need to log out and back in for this to take effect)
sudo usermod -aG docker $USER

# prepare data/cache directory on the host
mkdir -p /ssd/p2rank-conservation-docker-data/hmm-based

# download latest uniref database
cd /ssd/p2rank-conservation-docker-data/hmm-based
wget https://ftp.expasy.org/databases/uniprot/current_release/uniref/uniref50/uniref50.fasta.gz && gunzip uniref50.fasta.gz

# return to the prankweb repo (compose file lives there)
cd -

# build/rebuild docker image (only necessary after changes in this repo)
docker compose build conservation-server

# run server with mounted data directory
docker compose run --rm -p 8030:8030 \
    --user "$(id -u):$(id -g)" \
    -v /ssd/p2rank-conservation-docker-data:/data/conservation \
    conservation-server
```

Once the server is running, you can verify it is available:
```bash
curl http://localhost:8030/health
```

### Server-side Cache

The conservation server maintains a sequence-indexed result cache
in `/ssd/p2rank-conservation-docker-data/hmm-based-cache` (inside the mounted data directory).
Repeated queries for the same sequence are served immediately from this cache.

There is also an option to download a cache of conservation scores pre-computed by the PrankWeb server
for most PDB entries and some AlphaFold DB subsets (available upon request).
However, these were calculated with older versions of the UniRef database (approximately 2023 or older),
so results may differ from scores computed with a current UniRef release.

## Local Cache of Conservation Scores

By default, cache files are stored next to each protein file in `.p2rank-cache` directory.
For example, for protein `{protein_dir}/{baseName}.pdb` conservation files would be cached in:
```
{protein_dir}/.p2rank-cache/conservation/{conservation_type}/{baseName}_{chainId}.hom
```

To use a custom cache directory instead use the `-conservation_cache_dir` parameter:
```bash
prank predict -f protein.pdb \
  -c conservation_hmm \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030 \
  -conservation_cache_dir /path/to/custom/cache
```

Cache layout: `{conservation_cache_dir}/{conservation_type}/{baseName}_{chainId}.hom`

### Disabling Cache

To disable caching entirely (always fetch from server), add `-conservation_disable_cache true`
to your command.

### Score Resolution Order

When a conservation provider is configured, scores are resolved in this order:
1. Pre-computed files (from `conservation_dirs` or the protein file directory)
2. Local cache
3. Fetch from the conservation server (result is cached)

## Preloading Conservation Cache (Pre-calculating Conservation Scores)

> [!IMPORTANT]
> Conservation calculation is computationally intensive
> and typically takes several minutes per chain (depending on sequence length and hardware).

The `preload-conservation` command fetches and caches conservation scores for all chains in a dataset
without running predictions. This is useful on large datasets for catching any errors related to conservation
retrieval/calculation before starting a full prediction run.

```bash
prank preload-conservation dataset.ds \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030
```

The command reports progress and a summary of cached/fetched/failed chains.

Some long sequences may exceed the default timeout.
If some chains fail with timeout errors, you can safely re-run the same command.
Already cached chains will be skipped, and only the failed ones will be retried.
Use `-conservation_provider_timeout` to increase the per-request timeout:

```bash
prank preload-conservation dataset.ds \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030 \
  -conservation_provider_timeout 1200           # 20 minutes, default is 600 seconds (10 minutes)
```

You can also control the number of concurrent requests to avoid overloading the server:

```bash
prank preload-conservation dataset.ds \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030 \
  -conservation_provider_threads 4
```

### Preloading to a Dedicated Directory for Reproducible Experiments

For reproducible experiments, it is better to preload conservation scores into a dedicated directory
next to the dataset file and then use `-conservation_dirs` to point to it during prediction.
This way the scores are stored in a known location, are easy to version or share,
and you don't rely on the implicit per-protein cache mechanism.

```bash
# 1. Preload scores into a dedicated directory next to the dataset file
prank preload-conservation dataset.ds \
  -conservation_type hmm \
  -conservation_provider hmm_server \
  -conservation_provider_url http://localhost:8030 \
  -conservation_cache_dir ./conservation

# 2. Run prediction using -conservation_dirs, with cache disabled
#    (scores are loaded directly from the directory, no cache is read or written)
prank predict dataset.ds \
  -c conservation_hmm \
  -conservation_dirs ./conservation/hmm \
  -conservation_disable_cache true
```

> [!NOTE]
> `-conservation_dirs` points to `./conservation/hmm` because preloading
> creates the layout `{conservation_cache_dir}/{conservation_type}/`, so score files
> end up in `./conservation/hmm/*.hom`.

> [!NOTE]
> `-conservation_disable_cache true` only has an effect when a conservation provider
> is also configured for the predict step. With `-conservation_dirs` alone (no provider),
> the cache path is never consulted, so the flag is a no-op but harmless. It is included
> here so the same command remains correct if you later add a provider to the predict step.

## Parameters Reference

### Core

| Parameter | Default | Description |
|-----------|---------|-------------|
| `conservation_type` | `null` | Type of conservation scores. Determines cache subdirectory. Currently: `hmm` |
| `conservation_dirs` | `[]` | Directories to search for pre-computed score files. Absolute or relative to dataset dir. |

### Provider

| Parameter | Default | Description |
|-----------|---------|-------------|
| `conservation_provider` | `null` | External provider type. Currently: `hmm_server`. Null = use pre-computed files only. |
| `conservation_provider_url` | `null` | Base URL of the conservation server. Required when provider is set. |
| `conservation_provider_timeout` | `600` | Per-request timeout in seconds. |
| `conservation_provider_threads` | `0` | Max concurrent requests to the server. 0 = use `-threads` value. |

### Cache

| Parameter | Default | Description |
|-----------|---------|-------------|
| `conservation_cache_dir` | `null` | Centralized cache directory. Null = cache next to each protein file. |
| `conservation_disable_cache` | `false` | When true, cache is neither read nor written. |

### Other

| Parameter | Default | Description |
|-----------|---------|-------------|
| `fail_on_conserv_seq_mismatch` | `false` | Fail when sequences in the structure and score file do not match exactly. Only has effect when `fail_fast` is also `true`. |

## Non-Standard Residue Mapping

PDB structures often contain modified (non-canonical) amino acid residues (e.g., selenomethionine MSE, phosphoserine SEP).
Before sending sequences to the conservation server or matching them against score files,
P2Rank maps these residues to standard amino acids according to the `-aa_mapping` parameter.

For example, with `-aa_mapping pdbfixer`, MSE is mapped to MET, SEP to SER, etc.
This affects the FASTA sequence that P2Rank sends to the conservation provider
and the sequence used for alignment between structure residues and conservation scores.

See [aa-mapping.md](aa-mapping.md) for details on available mapping modes and custom mapping files.

## Debugging Sequence and Conservation Mapping

P2Rank provides several `analyze` subcommands useful for inspecting how sequences are processed:

```bash
# Export raw FASTA sequences as P2Rank sees them (after aa_mapping, before masking)
prank analyze fasta-raw dataset.ds

# Export masked FASTA sequences (non-standard residue codes replaced with X)
prank analyze fasta-masked dataset.ds

# Analyze conservation scores mapped to residues and optionally produce PyMOL visualizations
prank analyze conservation dataset.ds -threads 1 -visualizations true
```

`fasta-raw` shows the sequence after applying `-aa_mapping` but without any further masking.
`fasta-masked` additionally replaces non-standard one-letter codes with `X`.
This is the version sent to the conservation server and used for mapping scores to residues.
Comparing the two outputs helps identify residues that may cause mismatches with conservation score files.

The `conservation` subcommand loads and prints per-residue conservation scores for each chain.
With `-visualizations true`, it also generates PyMOL visualization scripts.

