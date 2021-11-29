package cz.siret.prank.features.generic


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

@Slf4j
@CompileStatic
class GenericHeader {

    public static final GenericHeader EMPTY = new GenericHeader(Collections.<String>emptyList())

    private final List<String> colNames
    private final Map<String, Integer> indexMap

    private int size

    GenericHeader(List<String> cols) {
        colNames = Collections.unmodifiableList(cols)

        size = cols.size()

        int i = 0
        indexMap = Collections.unmodifiableMap( colNames.collectEntries { [it,i++] } )
    }

    GenericHeader(String... cols) {
        this(cols.toList())
    }

    @Nullable
    Integer getColIndex(String colName) {
        return indexMap.get(colName)
    }

    int getSize() {
        return size
    }

    List<String> getColNames() {
        return colNames
    }

}
