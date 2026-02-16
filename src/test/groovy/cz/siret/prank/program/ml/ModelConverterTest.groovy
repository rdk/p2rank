package cz.siret.prank.program.ml

import cz.siret.prank.fforest.api.FasterForestConverter
import cz.siret.prank.fforest.api.TrainableFasterForest
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

        TrainableFasterForest trainableForest = new ModelConverter().frfToTrainableBinaryForest((FastRandomForest)model.classifier)

        FasterForestConverter.convertFasterForest(trainableForest, FasterForestConverter.ForestType.FlatBinaryForest)
        FasterForestConverter.convertFasterForest(trainableForest, FasterForestConverter.ForestType.LegacyFlatBinaryForest)
        FasterForestConverter.convertFasterForest(trainableForest, FasterForestConverter.ForestType.InterleavedBfsForest)
    }

}
