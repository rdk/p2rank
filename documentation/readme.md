# Documentation

This directory contains documentation and tutorials for P2Rank.

> [!IMPORTANT]
> **Start here:** the [User Guide](user-guide.md) is a comprehensive introduction covering
> installation, prediction, rescoring, configuration, performance tuning, and more.

## Usage

| File | Description |
|------|-------------|
| [rescoring.md](rescoring.md) | Rescoring predictions from other pocket prediction methods (Fpocket, Pocketeer, etc.) |
| [docking.md](docking.md) | Deriving docking search boxes (center + size) from predicted pockets |
| [export-points.md](export-points.md) | Exporting SAS points with feature vectors and predicted ligandability scores |
| [export-pocket-grid.md](export-pocket-grid.md) | Exporting per-pocket 3D grid points (PyMOL/ChimeraX-ready) |
| [export-pocket-descriptors.md](export-pocket-descriptors.md) | Exporting per-pocket scalar descriptors (volume, sphericity, principal moments, ...) |
| [cofactors.md](cofactors.md) | Treating selected HETATM groups as part of the protein surface |
| [aa-mapping.md](aa-mapping.md) | Non-canonical amino acid residue mapping to standard residues |
| [conservation.md](conservation.md) | Conservation-aware prediction (HMM-based) |
| [utility-commands.md](utility-commands.md) | Reference for `analyze`, `transform`, and `print` subcommands |

## Training

| File | Description |
|------|-------------|
| [training-tutorial.md](training-tutorial.md) | Training and evaluating custom models, crossvalidation, grid optimization |
| [feature-setup.md](feature-setup.md) | Feature vector configuration and introduction to adding new features |
| [new-feature-evaluation-tutorial.md](new-feature-evaluation-tutorial.md) | Implementing a new feature and evaluating its contribution to prediction |
| [hyperparameter-optimization-tutorial.md](hyperparameter-optimization-tutorial.md) | Grid and Bayesian optimization of algorithm parameters |
| [training-score-transformers.md](training-score-transformers.md) | Training probability and z-score transformers for pocket and residue scores |

