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
        long positives
        long negatives

        void add(Bin bin) {
            positives += bin.positives
            negatives += bin.negatives
        }

        long getCount() {
            positives + negatives
        }

        double getPosRatio() {
            if (count == 0) return 0

            return positives / count
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

        for (BinCounter c : counters) {
            res.count += c.count
            for (Map.Entry<T, Bin> it : c.table.entrySet()) {
                Bin rbin = res.table.get(it.key)
                if (rbin == null) {
                    res.table.put(it.key, it.value)
                } else {
                    rbin.add(it.value)
                }
            }
        }

        return res
    }

}
