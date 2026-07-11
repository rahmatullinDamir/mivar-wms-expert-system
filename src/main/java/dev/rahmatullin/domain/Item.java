package dev.rahmatullin.domain;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Item {
    private final String id;
    private final double weight;
    private String shelfId;
}
