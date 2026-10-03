package net.vortexdevelopment.vinject.database.mapper;

import net.vortexdevelopment.vinject.annotation.database.Column;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class MapperLengthTest {

    private static class TestEntity {
        @Column(length = 20)
        public BigDecimal fieldWithLength;

        @Column(precision = 15)
        public BigDecimal fieldWithPrecision;

        @Column(length = 20, precision = 5)
        public BigDecimal fieldWithBoth;
    }

    @Test
    void testH2MapperBigDecimalWithLength() throws Exception {
        H2Mapper mapper = new H2Mapper();
        Field field = TestEntity.class.getField("fieldWithLength");
        Column column = field.getAnnotation(Column.class);

        String sqlType = mapper.getSQLType(BigDecimal.class, column, null, null);
        assertThat(sqlType).contains("NUMERIC(20,2)");
    }

    @Test
    void testMySQLMapperBigDecimalWithLength() throws Exception {
        MySQLMapper mapper = new MySQLMapper();
        Field field = TestEntity.class.getField("fieldWithLength");
        Column column = field.getAnnotation(Column.class);

        String sqlType = mapper.getSQLType(BigDecimal.class, column, null, null);
        assertThat(sqlType).contains("DECIMAL(20,2)");
    }
    
    @Test
    void testH2MapperBigDecimalWithPrecision() throws Exception {
        H2Mapper mapper = new H2Mapper();
        Field field = TestEntity.class.getField("fieldWithBoth");
        Column column = field.getAnnotation(Column.class);

        // Precision should take precedence over length
        String sqlType = mapper.getSQLType(BigDecimal.class, column, null, null);
        assertThat(sqlType).contains("NUMERIC(5,2)");
    }

    @Test
    void testH2MapperBigDecimalWithDefault() throws Exception {
        H2Mapper mapper = new H2Mapper();
        Field field = TestEntity.class.getField("fieldWithPrecision");
        Column column = field.getAnnotation(Column.class);

        String sqlType = mapper.getSQLType(BigDecimal.class, column, null, null);
        assertThat(sqlType).contains("NUMERIC(15,2)");
    }
}
