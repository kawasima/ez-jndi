package net.unit8.ezjndi;

import java.util.*;

public class ContextClassLoaderSeparatedMap<K, V> implements Map<K, V> {
    private final Map<ClassLoader, Map<K, V>> map;
    private final ClassLoader root;

    public ContextClassLoaderSeparatedMap(ClassLoader root) {
        map = new HashMap<>();
        this.root = root;
    }

    @Override
    public int size() {
        return map.get(Thread.currentThread().getContextClassLoader())
                .size();
    }

    @Override
    public boolean isEmpty() {
        return map.get(Thread.currentThread().getContextClassLoader())
                .isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while(cl != null) {
            Map<K, V> m = map.get(cl);
            if (m != null && m.containsKey(key)) {
                return true;
            }
            cl = cl.getParent();
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while(cl != null) {
            Map<K, V> m = map.get(cl);
            if (m != null && m.containsValue(value)) {
                return true;
            }
            cl = cl.getParent();
        }
        return false;
    }

    @Override
    public V get(Object key) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while(cl != null) {
            Map<K, V> m = map.get(cl);
            if (m != null && m.containsKey(key)) {
                return m.get(key);
            }
            cl = cl.getParent();
        }
        return null;
    }

    @Override
    public V put(K k, V v) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        map.putIfAbsent(cl, new HashMap<>());

        Map<K, V> m = map.get(cl);
        return m.put(k, v);
    }

    @Override
    public V remove(Object key) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return Optional.ofNullable(map.get(cl))
                .map(m -> m.remove(key))
                .orElse(null);

    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        map.forEach(this::put);
    }

    @Override
    public void clear() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Optional.ofNullable(map.get(cl))
                .ifPresent(Map::clear);
    }

    @Override
    public Set<K> keySet() {
        return null;
    }

    @Override
    public Collection<V> values() {
        return null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return null;
    }
}
