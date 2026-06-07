package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;

import java.lang.reflect.Field;

/**
 * Information about a serialized field in an entity.
 */
public record SerializedFieldInfo(Field originalField,
                                 DatabaseSerializer<?> serializer,
                                 String baseColumnName,
                                 boolean usePrefix) {

    @SuppressWarnings("unchecked")
    public <T> DatabaseSerializer<T> getSerializer() {
        return (DatabaseSerializer<T>) serializer;
    }
}
