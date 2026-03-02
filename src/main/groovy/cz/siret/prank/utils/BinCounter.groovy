package cz.siret.prank.utils

import groovy.transform.CompileStatic


/**
 *
 */
@CompileStatic
class BinCounter<K> {

    int count = 0
    Map<K, Bin> table = new HashMap<>()

    static class Bin {
        long positives = 0
        long negatives = 0

        void add(Bin bin) {
            positives += bin.positives
            negatives += bin.negatives
        }

        long getCount() {
            positives + negatives
        }

        double getPosRatio() {
            if (count == 0) return 0

            return (double) positives / count
        }
    }

    Bin get(K key) {
        table.getOrDefault(key, new Bin())
    }

    void add(K key, boolean label) {

        Bin element = table.get(key)
        if (element == null) {
            element = new Bin()
            table.put(key, element)
        }

        if (label) {
            element.positives++
        } else {
            element.negatives++
        }
        count++
    }

    static <T> BinCounter<T> join(List<BinCounter<T>> counters) {
        BinCounter<T> res = new BinCounter<>()

        for (BinCounter<T> c : counters) {
            for (Map.Entry<T, Bin> e : c.table.entrySet()) {
                Bin bin = res.table.get(e.key)
                if (bin == null) {
                    bin = new Bin()
                    res.table.put(e.key, bin)
                }
                bin.add(e.value)
            }
            res.count += c.count
        }


        return res
    }

}
