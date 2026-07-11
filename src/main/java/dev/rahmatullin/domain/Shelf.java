package dev.rahmatullin.domain;


import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Shelf {
    private final String id;
    private int fullness;
    private final String currentZoneId;
    private final Point position;
}
