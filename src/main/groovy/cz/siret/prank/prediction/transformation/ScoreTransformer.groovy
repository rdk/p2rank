package cz.siret.prank.prediction.transformation


import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.results.Evaluation
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

/**
 *
 */
@Slf4j
@CompileStatic
abstract class ScoreTransformer {

    abstract double transformScore(double rawScore)

    abstract void trainForPockets(Evaluation evaluation)

    abstract JsonElement toJson()

    abstract ScoreTransformer loadFromJson(JsonElement json)


    static ScoreTransformer create(String name) {
        switch (name) {
            case "ZscoreTpTransformer":
                return new ZscoreTpTransformer()
            case "ProbabilityScoreTransformer":
                return new ProbabilityScoreTransformer()
            default:
                return null
        }
    }

    static ScoreTransformer loadFromJson(String json) {
        JsonObject obj = new JsonParser().parseString(json).getAsJsonObject()
        String name = obj.get("name").getAsString()

        ScoreTransformer transformer = create(name)
        if (transformer==null) {
            throw new PrankException("Invalid score transformer name: $name")
        }

        JsonObject params = obj.getAsJsonObject("params")
        return transformer.loadFromJson(params)
    }

    static String saveToJson(ScoreTransformer transformer) {
        JsonObject obj = new JsonObject()
        obj.addProperty("name", transformer.class.simpleName)
        obj.add("params", transformer.toJson())

        Sutils.GSON.toJson(obj)
    }

    /**
     * Load transformer from json in P2Rank install dir subdir (/models/score/)
     */
    static ScoreTransformer load(String paramVal) {
        try {
            if (StringUtils.isEmpty(paramVal)) {
                return null
            }
            String path = Params.inst.installDir + "/models/score/" + paramVal
            return ScoreTransformer.loadFromJson(Futils.readFile(path))

        } catch (Exception e) {
            log.error("Failed to load score transformer '$paramVal'", e)
        }
        return null
    }

}
