package dev.rahmatullin.domain;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Robot {
    private final String id;
    private final int liftingCapacity;
    private String currentZoneId;
    private RobotStatus status;
    private Point position;
}
