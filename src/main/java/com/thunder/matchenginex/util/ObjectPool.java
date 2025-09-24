package com.thunder.matchenginex.util;

import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

import java.util.function.Supplier;

/**
 * High-performance object pool using Agrona for lock-free operations - 使用Agrona实现无锁操作的高性能对象池
 */
public class ObjectPool<T> {
    private final ManyToOneConcurrentLinkedQueue<T> pool;
    private final Supplier<T> factory;
    private final int maxPoolSize;

    public ObjectPool(Supplier<T> factory, int maxPoolSize) {
        this.factory = factory;
        this.maxPoolSize = maxPoolSize;
        this.pool = new ManyToOneConcurrentLinkedQueue<>();

        // Pre-populate the pool - 预填充对象池
        for (int i = 0; i < Math.min(maxPoolSize / 2, 100); i++) {
            pool.offer(factory.get());
        }
    }

    /**
     * Acquire an object from the pool, creating a new one if pool is empty - 从对象池获取对象，如果池为空则创建新对象
     */
    public T acquire() {
        T object = pool.poll();
        return object != null ? object : factory.get();
    }

    /**
     * Return an object to the pool - 将对象返回到对象池
     */
    public void release(T object) {
        if (object != null && pool.size() < maxPoolSize) {
            // Reset object state if needed (implement Resetable interface) - 如需要重置对象状态 (实现Resetable接口)
            if (object instanceof Resetable) {
                ((Resetable) object).reset();
            }
            pool.offer(object);
        }
    }

    /**
     * Get current pool size - 获取当前对象池大小
     */
    public int size() {
        return pool.size();
    }

    /**
     * Interface for objects that can be reset when returned to pool - 返回对象池时可以被重置的对象接口
     */
    public interface Resetable {
        void reset();
    }
}