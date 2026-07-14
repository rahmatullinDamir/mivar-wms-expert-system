package dev.rahmatullin.engine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rahmatullin.domain.WarehouseState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MockDataLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public WarehouseState loadState(Path jsonPath) {
        if (!Files.exists(jsonPath)) {
            throw new IllegalArgumentException("Path to json file does not exist: " + jsonPath);
        }

        try {
            return OBJECT_MAPPER.readValue(jsonPath.toFile(), WarehouseState.class);
        } catch (IOException e) {
            throw new RuntimeException("Exception while trying to read json file: " + e.getMessage(), e);
        }
    }
}
