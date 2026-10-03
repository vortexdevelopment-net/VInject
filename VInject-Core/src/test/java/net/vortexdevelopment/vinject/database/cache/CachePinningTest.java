package net.vortexdevelopment.vinject.database.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachePinningTest {

    @Test
    void pinningUsesAReferenceCountAndPreventsLruEviction() {
        SimpleLRUCache<String, String> cache = new SimpleLRUCache<>(1);
        cache.put("island", "data");

        assertThat(cache.pin("island", "member-online")).isTrue();
        assertThat(cache.pin("island", "member-online")).isTrue();
        assertThat(cache.getPinCount("island")).isEqualTo(2);

        cache.put("visited", "other");
        assertThat(cache.get("island")).isEqualTo("data");
        assertThat(cache.get("visited")).isNull();

        assertThat(cache.unpin("island", "member-online")).isTrue();
        assertThat(cache.getPinCount("island")).isEqualTo(1);
        assertThat(cache.unpin("island", "member-online")).isTrue();
        assertThat(cache.getPinCount("island")).isZero();

        cache.put("new", "value");
        assertThat(cache.get("island")).isNull();
    }

    @Test
    void unpinningWithoutAReferenceFails() {
        CacheEntry<String> entry = new CacheEntry<>("data");

        assertThatThrownBy(() -> entry.unpin("member-online"))
                .isInstanceOf(IllegalStateException.class);
    }
}
