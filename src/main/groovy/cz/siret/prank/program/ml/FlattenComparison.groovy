package cz.siret.prank.program.ml

import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.program.routines.traineval.EvalPocketsRoutine
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j

/**
 * G0 — a pocket-level acceptance gate for flattened random-forest variants.
 *
 * <p>Runs de-novo pocket prediction + evaluation with a base model (the <i>baseline</i>) and with the
 * base model re-flattened to each target forest type, on the SAME dataset, then compares the
 * pocket-level ranking (DCA top-N success rate) and the per-point AUC. This is the
 * production-representative check the FasterForest ranking probes pointed at: it certifies an
 * (approximate) flatten target against p2rank's ACTUAL objective — binding-site detection — rather than
 * just point-score ranking-equivalence to the full-precision forest.
 *
 * <p>Reusable from the CLI (<code>prank transform compare-flatten-eval -f &lt;dataset.ds&gt;</code>) and
 * from tests. The CALLER must configure p2rank exactly as a normal {@code eval-predict -m <model>} run
 * (set {@code installDir} and load the model's config, e.g. {@code distro/config/default.groovy}) so
 * feature extraction matches the model.
 *
 * <p><b>Compare only WITHIN the faithful family</b> (LegacyFlat / SoaLegacy / Int16LeafSoa): they share
 * the default point-score operating point ({@code pred_point_threshold}). Score-family targets shift the
 * operating point and would drop DCA for non-semantic reasons (see {@code Params.rf_flatten_target}).
 *
 * <p><b>This is the authoritative acceptance gate</b> for an approximate flatten target — the
 * pocket-level objective, not the instance-level point-ranking that FasterForest's
 * {@code RankingEquivalenceTest} pre-screens with. The point estimate this prints (dDCA vs baseline)
 * should be paired with a <b>per-protein bootstrap CI</b> ({@code misc/dca_bootstrap_ci.py}, run on this
 * command's output dir) so a small delta can be told from sampling noise; accept iff the dDCA CI
 * includes 0 on coach420 AND holo4k. Calibration finding (real default model): {@code Int16LeafSoa} and
 * even {@code Int8LeafSoa} — which FAILS FasterForest's instance-level tau-b >= 0.999 pre-screen — are
 * pocket-indistinguishable from the faithful baseline here (dDCA CI includes 0), i.e. the instance-level
 * tau-b bar is stricter than this objective requires. See FasterForest PREDICTION-SEMANTICS.md
 * "Calibrating the acceptance thresholds against p2rank's objective".
 */
@Slf4j
@CompileStatic
class FlattenComparison implements Parametrized, Writable {

    /** Recommended faithful targets to certify against the (faithful) default model. */
    static final List<String> DEFAULT_FAITHFUL_TARGETS =
            ["SoaLegacyFlatBinaryForest", "Int16LeafSoaLegacyFlatBinaryForest"]

    @TupleConstructor
    static class VariantEval {
        String variant
        boolean baseline
        String forestClass
        double dca_4_0
        double dca_4_2
        double point_AUC
        double evalSeconds   // wall-time of this variant's predict+eval pass (indicative — see caveat)
    }

    /**
     * @param baseModel  the unflattened base model (e.g. the shipped faithful default)
     * @param dataset    a liganated dataset (so DCA is computable)
     * @param targets    flatten target forest-type names to certify against the baseline
     * @return per-variant pocket-level metrics (baseline first)
     */
    List<VariantEval> compare(Model baseModel, Dataset dataset, List<String> targets, String outdir) {
        List<VariantEval> results = new ArrayList<>()
        results.add(evalVariant("baseline", baseModel, dataset, "$outdir/baseline", true))
        for (String target : targets) {
            Model flat = new ModelConverter().flattenRandomForest(baseModel, target)
            results.add(evalVariant(target, flat, dataset, "$outdir/$target", false))
        }
        write "\n" + formatTable(results)
        return results
    }

    private VariantEval evalVariant(String name, Model model, Dataset dataset, String outdir, boolean baseline) {
        write "G0: evaluating variant '$name' (${model.classifier.class.simpleName}) on dataset [$dataset.name]"
        long t0 = System.currentTimeMillis()
        EvalResults res = new EvalPocketsRoutine(dataset, model, outdir).execute()
        double evalSeconds = (System.currentTimeMillis() - t0) / 1000.0d
        Map<String, Double> s = res.stats
        return new VariantEval(name, baseline, model.classifier.class.simpleName,
                nz(s.get("DCA_4_0")), nz(s.get("DCA_4_2")), nz(s.get("point_AUC")), evalSeconds)
    }

    private static double nz(Double d) { return d == null ? Double.NaN : d.doubleValue() }

    static String formatTable(List<VariantEval> rs) {
        VariantEval base = rs.find { VariantEval v -> v.baseline }
        StringBuilder sb = new StringBuilder()
        sb.append("=== G0 flatten comparison (pocket-level, within faithful family) ===\n")
        sb.append(String.format("%-40s %9s %9s %10s %9s   %s%n",
                "variant", "DCA_4_0", "DCA_4_2", "point_AUC", "eval_s", "delta vs baseline"))
        for (VariantEval v : rs) {
            String delta = (base != null && !v.baseline) ?
                    String.format("dDCA_4_0=%+.4f  dDCA_4_2=%+.4f  dAUC=%+.6f  time=%+.1f%%",
                            v.dca_4_0 - base.dca_4_0, v.dca_4_2 - base.dca_4_2, v.point_AUC - base.point_AUC,
                            base.evalSeconds > 0 ? (v.evalSeconds - base.evalSeconds) / base.evalSeconds * 100.0d : 0.0d) : ""
            sb.append(String.format("%-40s %9.4f %9.4f %10.6f %9.1f   %s%n",
                    v.variant, v.dca_4_0, v.dca_4_2, v.point_AUC, v.evalSeconds, delta))
        }
        sb.append("(eval_s = wall-time of each variant's predict+eval pass; INDICATIVE only — dominated by\n")
        sb.append(" shared SAS-surface + feature extraction, and confounded by JIT warmup of the first pass.\n")
        sb.append(" The clean isolated forest-inference speed is the FasterForest JMH result.)\n")
        return sb.toString()
    }
}
