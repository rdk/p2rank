package cz.siret.prank.features.generic

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class GenericHeader {

    public static final GenericHeader EMPTY = new GenericHeader(Collections.<String>emptyList())

    private final ImmutableList<String> colNames
    private final ImmutableMap<String, Integer> indexMap

    private int size

    GenericHeader(List<String> cols) {
        colNames = ImmutableList.copyOf(cols)

        size = cols.size()

        int i = 0
        indexMap = ImmutableMap.copyOf(  colNames.collectEntries { [it,i++] } )
    }

    GenericHeader(String... cols) {
        this(cols.toList())
    }

    int getColIndex(String colName) {
        return indexMap.get(colName)
    }

    int getSize() {
        return size
    }

    ImmutableList<String> getColNames() {
        return colNames
    }

}
