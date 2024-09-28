package cz.siret.prank.program.ml

import groovy.transform.CompileStatic
import hr.irb.fastRandomForest.FastRandomForest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 *
 */
@CompileStatic
class ModelConverterTest {

    /**
     * TODO fix test after introducing v3 models that are already flat
     */
    @Test
    @Disabled
    void testToFlatForest() {
        Model model = Model.load("distro/models/default.model")
        assert model.classifier instanceof FastRandomForest

        new ModelConverter().frfToFlatForest((FastRandomForest)model.classifier)
    }

}
