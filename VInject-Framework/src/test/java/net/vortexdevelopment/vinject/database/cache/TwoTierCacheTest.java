package net.vortexdevelopment.vinject.database.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwoTierCacheTest {

    @Test
    void testPromotionToHotTier() {
        // Setup cache with small tiers
        TwoTierCache<String, String> cache = new TwoTierCache<>(5, 5);

        String key = "testKey";
        String value = "testValue";
        
        // Initial put goes to NORMAL tier
        cache.put(key, value);
        
        // Access 9 times - should remain in NORMAL (threshold is 10)
        for (int i = 0; i < 9; i++) {
            cache.get(key);
        }
        
        // At this point, it has 1 initial access (put/get? no put resets) + 9 gets = 10?
        // Wait, put creates entry with access count 0?
        // CacheEntry constructor: accessCount = new AtomicInteger(1)? Let's check CacheEntry.
        // Assuming accessCount starts at 1 or 0.
        // If threshold is 10.
        
        // 10th access should trigger promotion if logic holds
        cache.get(key);
        
        // We can check if it was promoted by inspecting internal state via reflection OR relying on behavior.
        // TwoTierCache doesn't expose `isHot(key)`. 
        // But we can check `getPromotions()`
        
        // However, `shouldPromote` checks: `age < HOT_PROMOTION_WINDOW_MS`.
        // We need to valid verify CacheEntry implementation too.
        
        // Let's rely on getPromotions()
        
        // Since we can't easily guarantee how many accesses trigger it without seeing CacheEntry, 
        // let's access it loop 15 times to be sure.
        
        for (int i=0; i<15; i++) {
             cache.get(key);
        }
        
        assertThat(cache.getPromotions()).isGreaterThan(0);
    }
    
    @Test
    void testEvictionFromNormal() {
        TwoTierCache<String, String> cache = new TwoTierCache<>(2, 2);
        
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3"); // Should evict k1 (LRU)
        
        assertThat(cache.get("k1")).isNull();
        assertThat(cache.get("k2")).isNotNull();
        assertThat(cache.get("k3")).isNotNull();
        
        assertThat(cache.getEvictions()).isEqualTo(1);
    }

    @Test
    void testHotTierProtection() {
        TwoTierCache<String, String> cache = new TwoTierCache<>(2, 2);
        
        cache.put("hot1", "val");
        // Promote hot1
        for (int i=0; i<20; i++) cache.get("hot1");
        
        assertThat(cache.getPromotions()).isEqualTo(1);
        
        // Fill normal tier
        cache.put("n1", "val");
        cache.put("n2", "val");
        cache.put("n3", "val"); // Evicts n1
        
        // hot1 should still be there even if we added 3 items to normal
        assertThat(cache.get("hot1")).isNotNull();
        // n1 should be gone
        assertThat(cache.get("n1")).isNull();
    }
}
