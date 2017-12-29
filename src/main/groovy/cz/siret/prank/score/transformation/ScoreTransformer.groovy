package cz.siret.prank.score.transformation

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.routines.results.Evaluation
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
abstract class ScoreTransformer {

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    abstract double transformScore(double rawScore)

    abstract void train(Evaluation evaluation)

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
        JsonObject obj = (JsonObject) GSON.toJsonTree(json)
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

        GSON.toJson(obj)
    }


    static loadTransformer(String paramStr) {
    }

}
