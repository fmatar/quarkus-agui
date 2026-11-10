package org.agui.quarkus.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Registers AG-UI polymorphic Jackson MixIns and enum codecs on Quarkus's shared {@link ObjectMapper}.
 */
@Singleton
public class AgUiObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        AgUiJacksonMixins.configure(objectMapper);
    }
}
