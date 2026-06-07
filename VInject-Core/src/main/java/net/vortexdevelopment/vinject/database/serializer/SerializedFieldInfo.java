package net.vortexdevelopment.vinject.database.serializer;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.FieldValue;
import net.vortexdevelopment.vinject.annotation.database.MethodValue;

import java.lang.reflect.Field;

/**
 * Internal class to hold information about a serialized field.
 */
public record SerializedFieldInfo(Field field,
                                  Column columnAnnotation,
                                  FieldValue fieldAnnotation,
                                  MethodValue methodAnnotation,
                                  Object defaultValue) {
}
