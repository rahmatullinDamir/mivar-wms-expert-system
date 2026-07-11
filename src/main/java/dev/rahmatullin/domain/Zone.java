package dev.rahmatullin.domain;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Zone {
    private final String id;
    private final Point center;
}
