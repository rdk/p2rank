package cz.siret.prank.features.tables

import com.google.common.base.Splitter
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

@Slf4j
@CompileStatic
class PropertyTable {

    Set<String> itemNames = new HashSet<>()
    Set<String> propertyNames = new HashSet<>()

    private Map<Key, Double> values = new HashMap<>()

//===========================================================================================================//

    /**
     * @return new table with items and properties reversed
     */
    PropertyTable reverse() {
        PropertyTable res = new PropertyTable()
        res.itemNames = propertyNames
        res.propertyNames = itemNames

        values.each { res.values.put(new Key(it.key.propertyName, it.key.itemName), it.value) }
        
        return res
    }

    /**
     * lines starting with # are comments
     * first non-comment line is header
     *
     * Expacts csv file with items in lines and properties in columns.
     *
     * @param csvText
     */
    static PropertyTable parse(String csvText) {
        PropertyTable res = new PropertyTable()

        List<String> lines = csvText.readLines().findAll { String s -> !s.startsWith("#") && StringUtils.isNotBlank(s) }.toList()

        // log.info "table lines: " + lines.size()

        List<String> propNames

        boolean header = true
        for (String line in lines) {
            List<String> fields = Splitter.on(',').trimResults().split(line).toList()

            // log.info "lines " + line

            if (header) {
                propNames = fields.subList(1, fields.size())
                header = false
            } else {

                String itemName = fields.get(0)

                int i = 0
                for (String s : fields.tail()) {
                    String propName = propNames.get(i)
                    Double value = null
                    try {
                        value = Double.valueOf(s)
                    } catch (NumberFormatException e) {
                        log.warn "can't parse number [$s]!"
                    }

                    res.addValue(itemName, propName, value)

                    i++
                }

            }
        }

        //log.info res.toCSV()

        return res.immutabilize()
    }

    private PropertyTable immutabilize() {
        itemNames = ImmutableSet.copyOf(itemNames)
        propertyNames = ImmutableSet.copyOf(propertyNames)
        values = ImmutableMap.copyOf(values)
        return this
    }

    private void addValue(String itemName, String propertyName, Double value) {
        itemNames.add(itemName)
        propertyNames.add(propertyName)
        values.put(new Key(itemName, propertyName), value);
    }

    PropertyTable join(PropertyTable with) {
        PropertyTable res = new PropertyTable()

        res.propertyNames.addAll(this.propertyNames)
        res.propertyNames.addAll(with.propertyNames)

        res.itemNames.addAll(this.itemNames)
        res.itemNames.addAll(with.itemNames)

        res.values.putAll(this.values)
        res.values.putAll(with.values)

        return res.immutabilize()
    }

    PropertyTable transposed() {
        PropertyTable res = new PropertyTable()

        res.propertyNames = this.itemNames
        res.itemNames = this.propertyNames

        res.values = this.values.collectEntries { Key k, Double v ->
            [k.reversed(), v]
        } as Map<Key, Double>


        return res.immutabilize()
    }

//===========================================================================================================//

    Double getValue(String itemName, String propertyName) {
        return values.get(new Key(itemName, propertyName))
    }

    double getValueOrDefault(String itemName, String propertyName, double defaultVal) {
        return values.get(new Key(itemName, propertyName)) ?: defaultVal
    }

    public String toCSV() {
        StringBuilder sb = new StringBuilder()

        List<String> propNames = propertyNames.asList()

        sb << "," + propNames.join(",") + "\n"
        itemNames.each { String atomName ->
            sb << atomName
            propNames.each {String propName ->
                sb << "," + getValue(atomName,propName)
            }
            sb << "\n"
        }
        return sb.toString()
    }

    private static class Key {
        String itemName
        String propertyName

        Key(String itemName, String propertyName) {
            this.itemName = itemName
            this.propertyName = propertyName
        }

        Key reversed() {
            return new Key(propertyName, itemName)
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Key key = (Key) o

            if (itemName != key.itemName) return false
            if (propertyName != key.propertyName) return false

            return true
        }

        int hashCode() {
            int result
            result = (itemName != null ? itemName.hashCode() : 0)
            result = 31 * result + (propertyName != null ? propertyName.hashCode() : 0)
            return result
        }
    }

}
