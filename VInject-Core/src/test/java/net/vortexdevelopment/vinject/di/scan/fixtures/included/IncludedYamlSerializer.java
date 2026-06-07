package net.vortexdevelopment.vinject.di.scan.fixtures.included;

import net.vortexdevelopment.vinject.annotation.yaml.YamlSerializer;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;

import java.util.Map;

@YamlSerializer
public class IncludedYamlSerializer implements YamlSerializerBase<IncludedSerializedType> {

    @Override
    public Class<IncludedSerializedType> getTargetType() {
        return IncludedSerializedType.class;
    }

    @Override
    public Map<String, Object> serialize(IncludedSerializedType instance) {
        return Map.of();
    }

    @Override
    public IncludedSerializedType deserialize(Map<String, Object> map) {
        return new IncludedSerializedType();
    }
}
