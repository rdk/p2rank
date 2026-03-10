package cz.siret.prank.prediction.pockets.criteria

import groovy.transform.CompileStatic

/**
 * List of pocket criteria indexed by name
 */
@CompileStatic
class PocketCriteria {

    private final List<PocketCriterion> criteria

    private Map<String, Integer> nameToIndex

    PocketCriteria(List<PocketCriterion> criteria) {
        this.criteria = criteria
        this.nameToIndex = buildNameIndex(criteria)
    }

    private static Map<String, Integer> buildNameIndex(List<PocketCriterion> criteria) {
        Map<String, Integer> index = new HashMap<>()
        int i = 0
        for (PocketCriterion criterion : criteria) {
            index.put(criterion.name, i)
            i++
        }
        return index
    }

    List<PocketCriterion> getList() {
        return criteria
    }

    int getCriterionIndexForName(String name) {
        Integer index = nameToIndex.get(name)

        if (index == null) {
            throw new RuntimeException("Pocket identification criterium with name $name not found")
        }

        return index
    }

}
