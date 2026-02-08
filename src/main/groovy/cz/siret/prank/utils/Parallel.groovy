package cz.siret.prank.utils

import groovy.transform.CompileStatic

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.Future

/**
 * Simple parallel execution utility replacing GPars.
 * Uses ForkJoinPool + invokeAll for @CompileStatic compatibility,
 * guaranteed order preservation, and proper exception propagation.
 */
@CompileStatic
class Parallel {

    /**
     * Execute action on each item in parallel.
     * Exceptions from tasks propagate after all tasks complete.
     */
    static <T> void eachParallel(Collection<T> collection, int threads, Closure action) {
        if (threads <= 1 || collection.size() <= 1) {
            for (T item : collection) { action.call(item) }
            return
        }
        ForkJoinPool pool = new ForkJoinPool(threads)
        try {
            List<Callable<Object>> tasks = collection.collect { T item ->
                { -> action.call(item); return null } as Callable<Object>
            }
            List<Future<Object>> futures = pool.invokeAll(tasks)
            for (Future<Object> f : futures) {
                f.get()
            }
        } catch (ExecutionException e) {
            throw e.cause ?: e
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new RuntimeException("Parallel execution interrupted", e)
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Transform each item in parallel, returning results in original order.
     */
    static <T, R> List<R> collectParallel(Collection<T> collection, int threads, Closure<R> transform) {
        if (threads <= 1 || collection.size() <= 1) {
            return collection.collect(transform)
        }
        ForkJoinPool pool = new ForkJoinPool(threads)
        try {
            List<Callable<R>> tasks = collection.collect { T item ->
                { -> transform.call(item) } as Callable<R>
            }
            List<Future<R>> futures = pool.invokeAll(tasks)
            List<R> results = new ArrayList<>(futures.size())
            for (Future<R> f : futures) {
                results.add(f.get())
            }
            return results
        } catch (ExecutionException e) {
            throw e.cause ?: e
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new RuntimeException("Parallel execution interrupted", e)
        } finally {
            pool.shutdown()
        }
    }

}
