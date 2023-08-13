package cz.siret.prank.program.ml

import groovy.transform.CompileStatic
import hr.irb.fastRandomForest.FastRandomForest
import org.junit.jupiter.api.Test

/**
 *
 */
@CompileStatic
class ModelConverterTest {


    @Test
    void testToFlatForest() {
        Model model = Model.loadFromFile("distro/models/default.model")
        assert model.classifier instanceof FastRandomForest

        new ModelConverter().frfToFlatForest((FastRandomForest)model.classifier)
    }

}
