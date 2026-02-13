package com.trafficLight.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a state change event for a traffic light.
 * Used for tracking timing history.
 */
public record StateChangeEvent(
        Direction direction,
        LightState previousState,
        LightState newState,
        Instant timestamp
) {
    public StateChangeEvent {
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(newState, "newState must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        // previousState can be null for initial state
    }

    /**
     * Creates a state change event with the current timestamp.
     */
    public static StateChangeEvent now(Direction direction, LightState previousState, LightState newState) {
        return new StateChangeEvent(direction, previousState, newState, Instant.now());
    }

    /**
     * Creates an initial state event (no previous state).
     */
    public static StateChangeEvent initial(Direction direction, LightState state) {
        return new StateChangeEvent(direction, null, state, Instant.now());
    }

    /**
     * Checks if this is an initial state event (no previous state).
     */
    public boolean isInitialState() {
        return previousState == null;
    }
}
