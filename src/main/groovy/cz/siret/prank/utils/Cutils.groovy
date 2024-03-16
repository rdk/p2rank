package cz.siret.prank.utils

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import groovy.transform.CompileStatic

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/**
 * Collection utils
 */
@CompileStatic
class Cutils {

    static boolean empty(Collection<?> col) {
        return col == null || col.isEmpty()
    }

    static <T> List<T> head(int n, List<T> list) {
        if (n>=list.size()) return list
        return list.subList(0, n)
    }

    static <T> List<T> tail(int n, List<T> list) {
        return head(n, list.reverse())
    }

    static <T> T previousInList(int i, List<T> list) {
        if (i > 0) {
            return list[i-1]
        }
        return null
    }

    static <T> T nextInList(int i, List<T> list) {
        if (i < list.size()-1) {
            return list[i+1]
        }
        return null
    }

    @Nonnull
    static <T, E> List<T> mapList(@Nullable List<E> list, @Nonnull Function<E, T> mapper) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList()
        }
        List<T> res = new ArrayList<>(list.size())
        for (E ent : list) {
            T val = mapper.apply(ent)
            res.add(val)
        }
        return res;
    }

    static <K, E> Map<K, E> transformKeys(Map<K, E> map, Closure<K> closure) {
        Map<K, E> res = new HashMap<>(map.size())
        map.each {
            res.put( closure.call(it.key), it.value )
        }
        return res
    }

    static <E> Map<String, E> prefixMapKeys(Map<String, E> map, String prefix) {
        transformKeys(map, { (String) prefix + it})
    }

    static double sum(List<Double> list) {
        if (list == null || list.empty) return 0

        double sum = 0
        for (Double d : list) {
            if (d != null) {
                sum += d
            }
        }

        return sum
    }

    static <K, V> Map<K, V> mapWithIndex(
            Iterable<V> values, Function<? super V, K> keyFunction) {
        return mapWithIndex(values.iterator(), keyFunction);
    }

    static <K, V> Map<K, V> mapWithIndex(
            Iterator<V> values, Function<? super V, K> keyFunction) {

        values.collectEntries {
            [(keyFunction.apply(it)): it]
        }
    }

    static <E> List<E> findDuplicates(Iterable<E> values) {
        values.groupBy{ it }.values().findAll { it.size() > 1}.collect { it[0] }.toList()
    }

    static <K, V> ImmutableMap<K, V> mapWithUniqueIndex(
            @Nonnull Iterable<V> values,
            @Nonnull Function<? super V, K> keyFunction,
            @Nullable Consumer<List<K>> duplicateKeysFoundCallback) {

        List<K> duplicates = findDuplicates( values.collect { keyFunction(it)} )

        if (duplicates.empty) {
            return Maps.uniqueIndex(values, keyFunction as com.google.common.base.Function<? super V, K>)
        } else {
            if (duplicateKeysFoundCallback != null) {
                duplicateKeysFoundCallback(duplicates)
                return null
            } else {
                throw new IllegalArgumentException("Duplicate keys are not allowed")
            }
        }
    }

    static <K, V> ImmutableMap<K, V> mapWithUniqueIndex(
            @Nonnull Iterable<V> values,
            @Nonnull Function<? super V, K> keyFunction) {
        return mapWithUniqueIndex(values, keyFunction, null)
    }

    /**
     * get element or null
     */
    @Nullable
    static <E> E listElement(int idx, List<E> list) {
        if (list == null) return null
        if (idx < 0 || idx >= list.size()) return null
        return list.get(idx)
    }

    @Nonnull
    static <E> List<E> newSynchronizedList() {
        return Collections.synchronizedList(new ArrayList<E>());
    }

    @Nonnull
    static <E> List<E> newSynchronizedList(int initialSize) {
        return Collections.synchronizedList(new ArrayList<E>(initialSize));
    }

    @Nonnull
    static <E> List<E> synchronizedCopy(Collection<E> collection) {
        return Collections.synchronizedList(new ArrayList<E>(collection));
    }

//===========================================================================================================//

    @Nonnull
    static <E> PredicateSplit<E> splitByPredicate(Collection<E> collection, Predicate<E> predicate) {
        List<E> positives = new ArrayList<>()
        List<E> negatives = new ArrayList<>()
        for(E e : collection) {
            if (predicate.test(e)) {
                positives.add(e)
            } else {
                negatives.add(e)
            }
        }
        return new PredicateSplit<E>(positives, negatives)
    }

    static class PredicateSplit<E>  {
        private final List<E> positives
        private final List<E> negatives

        PredicateSplit(List<E> positives, List<E> negatives) {
            this.positives = positives
            this.negatives = negatives
        }

        @Nonnull
        List<E> getPositives() {
            return positives
        }

        @Nonnull
        List<E> getNegatives() {
            return negatives
        }
    }

}
