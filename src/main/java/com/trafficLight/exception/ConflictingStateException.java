package com.trafficLight.exception;

import com.trafficLight.model.Direction;

import java.util.Set;

/**
 * Exception thrown when an operation would result in conflicting
 * traffic light states (e.g., perpendicular directions both green).
 */
public class ConflictingStateException extends RuntimeException {

    private final Set<Direction> conflictingDirections;

    public ConflictingStateException(String message, Set<Direction> conflictingDirections) {
        super(message);
        this.conflictingDirections = Set.copyOf(conflictingDirections);
    }

    public ConflictingStateException(Set<Direction> conflictingDirections) {
        this("Conflicting directions cannot be green simultaneously: " + conflictingDirections,
                conflictingDirections);
    }

    public Set<Direction> getConflictingDirections() {
        return conflictingDirections;
    }
}
