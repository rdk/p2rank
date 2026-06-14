package cz.siret.prank.features.implementation.propensity

import cz.siret.prank.features.implementation.table.PropertyTable
import cz.siret.prank.program.params.Params
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression: the default feat_propensity_tables must point at resources that
 * actually exist. The peptide propensity tables were moved under
 * /tables/propensities/peptides/..., but the default value was left as
 * "SprintT1070", so the duplets/triplets/aa-propensity/atomtype-propensity
 * features crash with a PrankException (PropertyTable.parseResource on a
 * missing resource) when enabled with the default.
 *
 * Mirrors the exact path the *PropensityFeature classes build:
 *   /tables/propensities/$feat_propensity_tables/<file>.csv
 */
class PropensityTablesDefaultTest {

    @Test
    void defaultPropensityTablesResolveAndLoad() {
        String tables = new Params().feat_propensity_tables   // the shipped default
        for (String file : ["duplets.csv", "triplets.csv", "aa-propensity.csv", "atomtype-propensity.csv"]) {
            String path = "/tables/propensities/$tables/$file"
            PropertyTable t = PropertyTable.parseResource(path)   // throws PrankException if missing
            assertNotNull(t, "default propensity table failed to load: $path")
        }
    }
}
