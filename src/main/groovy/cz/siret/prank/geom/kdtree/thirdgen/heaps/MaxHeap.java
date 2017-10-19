package cz.siret.prank.geom.kdtree.thirdgen.heaps;

/**
 *
 */
public interface MaxHeap<T> {
    public int size();
    public void offer(double key, T value);
    public void replaceMax(double key, T value);
    public void removeMax();
    public T getMax();
    public double getMaxKey();
}