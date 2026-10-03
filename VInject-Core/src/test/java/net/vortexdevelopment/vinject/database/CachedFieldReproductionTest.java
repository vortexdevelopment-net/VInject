package net.vortexdevelopment.vinject.database;

import net.vortexdevelopment.vinject.annotation.database.CachedField;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class CachedFieldReproductionTest {

    @Test
    void testCachedFieldDirtyTracking() throws Exception {
        TestEntity entity = new TestEntity();
        entity.setId(UUID.randomUUID());
        
        // Test addAmount which is NOT a standard setter name
        entity.addAmount(BigInteger.ONE);

        // Check if __vinject_dirty_fields exists and contains "amount"
        try {
            Field dirtyFieldsField = TestEntity.class.getDeclaredField("__vinject_dirty_fields");
            dirtyFieldsField.setAccessible(true);
            Set<String> dirtyFields = (Set<String>) dirtyFieldsField.get(entity);
            
            assertThat(dirtyFields).as("Dirty fields should contain 'amount' after addAmount").contains("amount");
        } catch (NoSuchFieldException e) {
            fail("Class was not transformed: __vinject_dirty_fields field missing.");
        }
    }

    @Entity(table = "test_entities")
    public static class TestEntity {
        @Id
        private UUID id;

        @Column
        private BigInteger amount = BigInteger.ZERO;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public BigInteger getAmount() {
            return amount;
        }

        @CachedField("amount")
        public void setAmount(BigInteger amount) {
            this.amount = amount;
        }

        @CachedField("amount")
        public void addAmount(BigInteger toAdd) {
            this.amount = this.amount.add(toAdd);
        }
    }
}
