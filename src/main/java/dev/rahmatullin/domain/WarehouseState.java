package dev.rahmatullin.domain;

import lombok.Data;

import java.util.List;

@Data
public class WarehouseState {
    private List<Robot> robots;
    private List<Order> orders;
    private List<Shelf> shelves;
    private List<Item> items;
    private List<Zone> zones;
}
