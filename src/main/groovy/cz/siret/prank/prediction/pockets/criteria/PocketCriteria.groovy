package cz.siret.prank.prediction.pockets.criteria

import groovy.transform.CompileStatic

/**
 * List of pocket criteria indexed by name
 */
@CompileStatic
class PocketCriteria {

    private final List<PocketCriterium> criteria

    private Map<String, Integer> nameToIndex

    PocketCriteria(List<PocketCriterium> criteria) {
        this.criteria = criteria
        this.nameToIndex = buildNameIndex(criteria)
    }

    private Map<String, Integer> buildNameIndex(List<PocketCriterium> criteria) {
        Map<String, Integer> index = new HashMap<>()
        int i = 0
        for (PocketCriterium criterium : criteria) {
            index.put(criterium.name, i)
            i++
        }
        return index
    }

    List<PocketCriterium> getList() {
        return criteria
    }

    int getCriteriumIndexForName(String name) {
        Integer index = nameToIndex.get(name)

        if (index == null) {
            throw new RuntimeException("Pocket identification criterium with name $name not found")
        }

        return index
    }

}
