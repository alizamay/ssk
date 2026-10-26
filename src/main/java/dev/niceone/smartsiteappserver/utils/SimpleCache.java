package dev.niceone.smartsiteappserver.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SimpleCache<K, V> {
    
    private final ConcurrentHashMap<K, CacheItem<V>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @Value("${app.myhome.cache.cleanup.interval.minutes:5}")
    private int cleanupIntervalMinutes;
    
    private static class CacheItem<V> {
        final V value;
        final long expirationTime;
        
        CacheItem(V value, long ttlMillis) {
            this.value = value;
            this.expirationTime = System.currentTimeMillis() + ttlMillis;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
    
    public SimpleCache() {
        // Schedule cleanup after initialization is complete
    }
    
    @javax.annotation.PostConstruct
    public void init() {
        // Clean expired entries based on configuration  
        int interval = cleanupIntervalMinutes > 0 ? cleanupIntervalMinutes : 5;
        scheduler.scheduleAtFixedRate(this::cleanExpiredEntries, interval, interval, TimeUnit.MINUTES);
    }
    
    public void put(K key, V value, long ttlMinutes) {
        cache.put(key, new CacheItem<>(value, ttlMinutes * 60 * 1000));
    }
    
    public V get(K key) {
        CacheItem<V> item = cache.get(key);
        if (item == null || item.isExpired()) {
            cache.remove(key);
            return null;
        }
        return item.value;
    }
    
    public void remove(K key) {
        cache.remove(key);
    }
    
    public void clear() {
        cache.clear();
    }
    
    private void cleanExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    public int size() {
        return cache.size();
    }
}