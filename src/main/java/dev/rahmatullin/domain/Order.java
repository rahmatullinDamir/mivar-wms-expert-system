package dev.rahmatullin.domain;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Order {
    private final String id;
    private final String parentOrderId;
    private final String itemId;
    private String destinationZoneId;
    private boolean isUrgent;
    private OrderStatus status;
}


