package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.database.cache.TTLCache;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TTLCacheTest {

    @Test
    void testExpiration() throws InterruptedException {
        // Create TTL cache with 1 second TTL
        TTLCache<String, String> cache = new TTLCache<>(1);
        
        cache.put("key1", "value1");
        
        // Immediate get should work
        assertThat(cache.get("key1")).isEqualTo("value1");
        
        // Wait 1.1s to ensure expiration
        Thread.sleep(1100);
        
        // Should be expired (returns null)
        assertThat(cache.get("key1")).isNull();
        
        // Check stats
        assertThat(cache.getEvictions()).isEqualTo(1);
        assertThat(cache.getMisses()).isEqualTo(1);
    }

    @Test
    void testCleanup() throws InterruptedException {
        // TTL 1s
        TTLCache<String, String> cache = new TTLCache<>(1);
        
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        
        Thread.sleep(1100);
        
        // Cleanup explicitly
        cache.cleanup();
        
        // Size should be 0
        assertThat(cache.size()).isEqualTo(0);
        assertThat(cache.getEvictions()).isEqualTo(2);
    }
}
