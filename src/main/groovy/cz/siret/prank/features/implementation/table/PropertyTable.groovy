package cz.siret.prank.features.implementation.table

import com.google.common.base.Splitter
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static java.util.Collections.unmodifiableMap
import static java.util.Collections.unmodifiableSet

@Slf4j
@CompileStatic
class PropertyTable {

    Set<String> itemNames = new HashSet<>()
    Set<String> propertyNames = new HashSet<>()

    private Map<PropertyTableKey, Double> values = new HashMap<>()

//===========================================================================================================//

    /**
     * @return new table with items and properties reversed
     */
    PropertyTable reverse() {
        PropertyTable res = new PropertyTable()
        res.itemNames = propertyNames
        res.propertyNames = itemNames

        for (def it : values) {
            res.values.put(it.key.reversed(), it.value)
        }
        
        return res
    }

    /**
     * lines starting with # are comments
     * first non-comment line is header
     *
     * Expects csv file with items in lines and properties in columns.
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
                        log.warn "can't parse number [$s]: " + e.message
                    }

                    if (value != null) {
                        if (value.isNaN()) {
                            log.debug "NaN value in property table for item '$itemName' property '$propName'"
                            value = null
                        } else if (value.isInfinite()) {
                            throw new PrankException("Infinite value in property table for item '$itemName' property '$propName'")
                        }
                    }

                    res.addValue(itemName, propName, value)

                    i++
                }
            }
        }

        //log.info res.toCSV()

        res.immutabilize()
        res.checkNulls()
        return res
    }

    static PropertyTable parseResource(String resourcePath) {
        try {
            log.debug "loading property table from resource: $resourcePath"
            return parse(Futils.readResource(resourcePath))
        } catch (Exception e) {
            throw new PrankException("Failed to load property table from resource: $resourcePath", e)
        }
    }

    private checkNulls() {
        if (log.isDebugEnabled()) {
            // collect number of null values for each propertyName
            Map<String, Integer> nullCounts = new TreeMap<>()
            for (String propName : propertyNames) {
                for (String itemName : itemNames) {
                    if (getValue(itemName, propName) == null) {
                        nullCounts.put(propName, nullCounts.getOrDefault(propName, 0) + 1)
                    }
                }
            }

            int n = itemNames.size()
            log.debug "property table null value counts by property:"
            for (String propName : nullCounts.keySet()) {
                int count = nullCounts.get(propName)
                if (count > 0) {
                    log.debug "  $propName: $count / $n (${((double)count*100)/n}%) null values"
                }
            }
        }
    }

    private PropertyTable immutabilize() {
        itemNames = unmodifiableSet(itemNames)
        propertyNames = unmodifiableSet(propertyNames)
        values = unmodifiableMap(values)
        return this
    }

    private void addValue(String itemName, String propertyName, Double value) {
        itemNames.add(itemName)
        propertyNames.add(propertyName)
        values.put(new PropertyTableKey(itemName, propertyName), value);
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

        res.values = this.values.collectEntries { PropertyTableKey k, Double v ->
            [k.reversed(), v]
        } as Map<PropertyTableKey, Double>


        return res.immutabilize()
    }

//===========================================================================================================//

    @Nullable
    Double getValue(String itemName, String propertyName) {
        return values.get(new PropertyTableKey(itemName, propertyName))
    }

    @Nonnull
    double getValueOrDefault(String itemName, String propertyName, double defaultVal) {
        // explicit null-check, not Elvis: '?:' on a boxed Double routes through Groovy asBoolean
        // (dynamic dispatch) AND treats a legitimate 0.0 as falsy, so it would silently return
        // defaultVal for a real stored 0.0. Benign today (every caller passes 0d, so 0.0 -> 0.0),
        // but a latent correctness trap for any non-zero default. Explicit form is statically dispatched.
        Double v = getValue(itemName, propertyName)
        return v == null ? defaultVal : v.doubleValue()
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

}
