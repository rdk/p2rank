# Evaluation metric fixes during the 2.6 dev cycle

Four commits between `2.5.2-dev.5` and the in-development 2.6 line change reported
success-rate values for the same dataset/model. They are listed here in expected-impact
order so users comparing numbers across releases know which metrics moved and why.

## 1. `e7fc457f`: Fix ligand detection for BioJava GroupType misclassifications
*Between `2.6.0-dev.5` and `2.6.0-dev.6`.*

Before this commit only `GroupType.HETATM` groups qualified as ligand candidates.
BioJava classifies groups by Chemical Component Dictionary, not structural role, so
ligands in non-polymer chains can carry any GroupType:

- GDP, GTP, ATP → `GroupType.NUCLEOTIDE`
- SHR and similar → `GroupType.AMINOACID`
- most others → `GroupType.HETATM`

After the fix, any non-water group in a NONPOLYMER chain is a ligand candidate
regardless of GroupType. This changes the relevant-ligand set on any dataset that
contains nucleotide or amino-acid-derivative ligands, which moves DCA/DCC numerator
and denominator simultaneously. **This is the only commit in the list that can shift
DCA, the canonical p2rank success metric.**

## 2. `838b0a69`: Fix integer division bug in DSO criterion
*Between `2.6.0-dev.5` and `2.6.0-dev.6`.*

```groovy
// before
double ratio = inter / union          // int / int under @CompileStatic → 0 in real cases
// after
double ratio = (double) inter / union
```

`inter` and `union` are both `int` and the class is `@CompileStatic`, so the old code
performed Java integer division. Since `inter < union` in any realistic case, `ratio`
was 0 ⇒ `ratio >= threshold` always false ⇒ all `DSO_*_SUCC` and `DSO_02_T*` metrics
were floored at 0 (or hit only on the rare `inter == union` edge). **Any DSO metric
reported in releases prior to this commit is meaningless.**

## 3. `48cb681a`: Refactor DSO/DSWO (fixes the same bug class for DSWO)
*After `2.6.0-dev.6`, before `2.6.0-dev.7`.*

Despite the "refactor" commit message, this is where DSWO finally gets the cast
`838b0a69` missed:

```groovy
// at tag 2.6.0-dev.6 (still broken)
double ligCov = inter / nlig
double pocCov = inter / npoc
// after 48cb681a
double ligCov = (double) counts.intersectionCount / site.sasPoints.count
double pocCov = (double) counts.intersectionCount / pocket.sasPoints.count
```

DSWO was still broken at `2.6.0-dev.6`. Combined with `838b0a69`, the picture is:

| Metric  | 2.5.2-dev.5     | 2.6.0-dev.6           | HEAD (post 48cb681a) |
|---------|-----------------|-----------------------|----------------------|
| `DSO_*` | broken (int div)| **fixed** (838b0a69)  | fixed                |
| `DSWO_*`| broken (int div)| **still broken**      | **fixed** (48cb681a) |

## 4. `22ac1e51`: Fix DCC criterion to use predefined site centroid (ResidueSites)
*Between `2.6.0-dev.5` and `2.6.0-dev.6`.*

```groovy
// before
Struct.dist(site.atoms.centroid, pocket.centroid)
// after
Struct.dist(site.centroid, pocket.centroid)
```

The site center used by DCC for **ligand-defined** ground truth is unchanged in
practice; it was effectively the ligand center of mass before this commit and
remains so after. **Ligand-mode DCC numbers do not shift across this commit.**

The real fix is for `ResidueSite` (explicit residue-defined) ground truth: DCC now
uses the predefined centroid from the input site definition, which is the
authoritative binding-site location, instead of recomputing a mean over the
resolved residue atoms. Only datasets using explicit-site evaluation are affected;
the typical fpocket-rescore-of-ligand-set workflow is not.
