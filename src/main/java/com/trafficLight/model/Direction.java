package com.trafficLight.model;

import java.util.Set;

/**
 * Represents traffic flow directions at an intersection.
 * Directions are grouped into lanes that can safely have green lights simultaneously.
 */
public enum Direction {
    NORTH(Lane.NORTH_SOUTH),
    SOUTH(Lane.NORTH_SOUTH),
    EAST(Lane.EAST_WEST),
    WEST(Lane.EAST_WEST),
    NORTH_LEFT_TURN(Lane.NORTH_SOUTH_LEFT),
    SOUTH_LEFT_TURN(Lane.NORTH_SOUTH_LEFT),
    EAST_LEFT_TURN(Lane.EAST_WEST_LEFT),
    WEST_LEFT_TURN(Lane.EAST_WEST_LEFT);

    private final Lane lane;

    Direction(Lane lane) {
        this.lane = lane;
    }

    public Lane getLane() {
        return lane;
    }

    /**
     * Checks if this direction conflicts with another direction.
     * Conflicting directions cannot both be green simultaneously.
     */
    public boolean conflictsWith(Direction other) {
        if (this == other) {
            return false;
        }
        return this.lane.getConflictingLanes().contains(other.lane);
    }

    /**
     * Represents a group of directions that can safely be green together.
     */
    public enum Lane {
        NORTH_SOUTH,
        EAST_WEST,
        NORTH_SOUTH_LEFT,
        EAST_WEST_LEFT;

        /**
         * Returns the set of lanes that conflict with this lane.
         */
        public Set<Lane> getConflictingLanes() {
            return switch (this) {
                case NORTH_SOUTH -> Set.of(EAST_WEST, EAST_WEST_LEFT);
                case EAST_WEST -> Set.of(NORTH_SOUTH, NORTH_SOUTH_LEFT);
                case NORTH_SOUTH_LEFT -> Set.of(EAST_WEST, EAST_WEST_LEFT);
                case EAST_WEST_LEFT -> Set.of(NORTH_SOUTH, NORTH_SOUTH_LEFT);
            };
        }
    }
}
