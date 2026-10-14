package com.carrental.domain;

import java.util.EnumMap;
import java.util.Map;

/** Number of cars owned per car type. */
public final class Fleet {

    private final Map<CarType, Integer> capacity = new EnumMap<>(CarType.class);

    public Fleet(Map<CarType, Integer> counts) {
        for (CarType type : CarType.values()) {
            Integer count = counts == null ? null : counts.get(type);
            if (count == null || count < 0) {
                throw new IllegalArgumentException("Fleet count for " + type + " must be set and >= 0");
            }
            capacity.put(type, count);
        }
    }

    public int capacity(CarType type) {
        return capacity.get(type);
    }
}
