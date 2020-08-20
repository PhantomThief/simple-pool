package com.github.phantomthief.pool.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import com.github.phantomthief.util.SimpleRateLimiter;

/**
 * @author w.vela
 * Created on 2020-08-19.
 */
public class DynamicCapacityLinkedBlockingQueue<E> implements BlockingQueue<E> {

    private final VariableLinkedBlockingQueue<E> queue;
    private final IntSupplier capacity;
    private final SimpleRateLimiter rateLimiter;

    public DynamicCapacityLinkedBlockingQueue(IntSupplier capacity) {
        this.capacity = capacity;
        int thisCapacity = capacity.getAsInt();
        this.queue = new VariableLinkedBlockingQueue<>(thisCapacity <= 0 ? Integer.MAX_VALUE : thisCapacity);
        this.rateLimiter = SimpleRateLimiter.create(1);
    }

    public static <T> BlockingQueue<T> lazyDynamicCapacityLinkedBlockingQueue(IntSupplier capacity) {
        return new LazyBlockingQueue<>(() -> new DynamicCapacityLinkedBlockingQueue<>(capacity));
    }

    private void tryCheckCapacity() {
        if (rateLimiter.tryAcquire()) {
            int thisCapacity = capacity.getAsInt();
            if (thisCapacity <= 0) {
                thisCapacity = Integer.MAX_VALUE;
            }
            if (thisCapacity != queue.getCapacity()) {
                queue.setCapacity(thisCapacity);
            }
        }
    }

    @Override
    public boolean add(E e) {
        return queue.add(e);
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public E remove() {
        return queue.remove();
    }

    @Override
    public E element() {
        return queue.element();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return queue.addAll(c);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return queue.removeAll(c);
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return queue.retainAll(c);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    @Override
    public void put(E o) throws InterruptedException {
        tryCheckCapacity();
        queue.put(o);
    }

    @Override
    public boolean offer(E o, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(o, timeout, unit);
    }

    @Override
    public boolean offer(E o) {
        tryCheckCapacity();
        return queue.offer(o);
    }

    @Override
    public E take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public E poll() {
        return queue.poll();
    }

    @Override
    public E peek() {
        return queue.peek();
    }

    @Override
    public boolean remove(Object o) {
        return queue.remove(o);
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return queue.toArray(a);
    }

    @Override
    public String toString() {
        return queue.toString();
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return queue.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        return queue.drainTo(c, maxElements);
    }

    @Override
    public Iterator<E> iterator() {
        return queue.iterator();
    }
}
