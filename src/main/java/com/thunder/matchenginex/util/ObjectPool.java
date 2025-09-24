package com.thunder.matchenginex.util;

import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

import java.util.function.Supplier;

/**
 * High-performance object pool using Agrona for lock-free operations
 */
public class ObjectPool<T> {
    private final ManyToOneConcurrentLinkedQueue<T> pool;
    private final Supplier<T> factory;
    private final int maxPoolSize;

    public ObjectPool(Supplier<T> factory, int maxPoolSize) {
        this.factory = factory;
        this.maxPoolSize = maxPoolSize;
        this.pool = new ManyToOneConcurrentLinkedQueue<>();

        // Pre-populate the pool
        for (int i = 0; i < Math.min(maxPoolSize / 2, 100); i++) {
            pool.offer(factory.get());
        }
    }

    /**
     * Acquire an object from the pool, creating a new one if pool is empty
     */
    public T acquire() {
        T object = pool.poll();
        return object != null ? object : factory.get();
    }

    /**
     * Return an object to the pool
     */
    public void release(T object) {
        if (object != null && pool.size() < maxPoolSize) {
            // Reset object state if needed (implement Resetable interface)
            if (object instanceof Resetable) {
                ((Resetable) object).reset();
            }
            pool.offer(object);
        }
    }

    /**
     * Get current pool size
     */
    public int size() {
        return pool.size();
    }

    /**
     * Interface for objects that can be reset when returned to pool
     */
    public interface Resetable {
        void reset();
    }
}