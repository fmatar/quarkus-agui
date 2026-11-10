package org.agui.quarkus.adapters;

import com.agui.community.core.serialization.SerializationException;
import com.agui.community.core.serialization.Serializer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Quarkus CDI {@link Serializer} implementation backed by Jackson and {@link AgUiJacksonMixins}.
 */
@ApplicationScoped
public class AgUiJacksonSerializer implements Serializer {

    private final ObjectMapper mapper;

    public AgUiJacksonSerializer() {
        this(new ObjectMapper());
    }

    @Inject
    public AgUiJacksonSerializer(ObjectMapper quarkusMapper) {
        Objects.requireNonNull(quarkusMapper, "quarkusMapper must not be null");
        this.mapper = AgUiJacksonMixins.configure(quarkusMapper.copy());
    }

    @Override
    public String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize " + value, e);
        }
    }

    @Override
    public <T> T deserialize(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize to " + type.getName(), e);
        }
    }

    @Override
    public <T> List<T> deserializeList(String json, Class<T> elementType) {
        try {
            JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return mapper.readValue(json, listType);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize list of " + elementType.getName(), e);
        }
    }
}
