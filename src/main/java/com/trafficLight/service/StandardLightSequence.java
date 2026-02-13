package com.trafficLight.service;

import com.trafficLight.api.TrafficLightController.LightSequence;
import com.trafficLight.model.Direction;
import com.trafficLight.model.Intersection;
import com.trafficLight.model.LightState;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standard light sequence implementations for common traffic patterns.
 */
public final class StandardLightSequence {

    private StandardLightSequence() {
        // Utility class
    }

    /**
     * Creates a simple two-phase sequence (NS green, then EW green).
     * Each phase includes:
     * 1. Set opposite direction to yellow
     * 2. Set opposite direction to red
     * 3. Set current direction to green
     */
    public static LightSequence twoPhase() {
        return new TwoPhaseSequence();
    }

    /**
     * Creates a four-phase sequence with protected left turns.
     * Phases:
     * 1. NS straight
     * 2. NS left turn
     * 3. EW straight
     * 4. EW left turn
     */
    public static LightSequence fourPhaseWithLeftTurn() {
        return new FourPhaseWithLeftTurnSequence();
    }

    /**
     * Creates a custom sequence from a list of phases.
     * Each phase specifies which directions should be green.
     */
    public static LightSequence custom(List<Set<Direction>> phases) {
        return new CustomSequence(phases);
    }

    /**
     * Simple two-phase sequence: NS green <-> EW green
     */
    private static class TwoPhaseSequence implements LightSequence {
        private final AtomicInteger currentPhase = new AtomicInteger(0);

        // Phases: 0 = NS green, 1 = NS yellow, 2 = EW green, 3 = EW yellow
        private static final int PHASE_COUNT = 4;

        @Override
        public void advancePhase(Intersection intersection) {
            int phase = currentPhase.getAndUpdate(p -> (p + 1) % PHASE_COUNT);

            Set<Direction> directions = intersection.getDirections();
            boolean hasNorthSouth = directions.contains(Direction.NORTH) || directions.contains(Direction.SOUTH);
            boolean hasEastWest = directions.contains(Direction.EAST) || directions.contains(Direction.WEST);

            Map<Direction, LightState> states = new EnumMap<>(Direction.class);

            switch (phase) {
                case 0 -> { // NS green
                    if (hasNorthSouth) {
                        setIfPresent(states, directions, Direction.NORTH, LightState.GREEN);
                        setIfPresent(states, directions, Direction.SOUTH, LightState.GREEN);
                    }
                    if (hasEastWest) {
                        setIfPresent(states, directions, Direction.EAST, LightState.RED);
                        setIfPresent(states, directions, Direction.WEST, LightState.RED);
                    }
                }
                case 1 -> { // NS yellow
                    if (hasNorthSouth) {
                        setIfPresent(states, directions, Direction.NORTH, LightState.YELLOW);
                        setIfPresent(states, directions, Direction.SOUTH, LightState.YELLOW);
                    }
                }
                case 2 -> { // EW green
                    if (hasNorthSouth) {
                        setIfPresent(states, directions, Direction.NORTH, LightState.RED);
                        setIfPresent(states, directions, Direction.SOUTH, LightState.RED);
                    }
                    if (hasEastWest) {
                        setIfPresent(states, directions, Direction.EAST, LightState.GREEN);
                        setIfPresent(states, directions, Direction.WEST, LightState.GREEN);
                    }
                }
                case 3 -> { // EW yellow
                    if (hasEastWest) {
                        setIfPresent(states, directions, Direction.EAST, LightState.YELLOW);
                        setIfPresent(states, directions, Direction.WEST, LightState.YELLOW);
                    }
                }
            }

            if (!states.isEmpty()) {
                intersection.setLightStates(states);
            }
        }

        private void setIfPresent(Map<Direction, LightState> states, Set<Direction> available,
                                  Direction direction, LightState state) {
            if (available.contains(direction)) {
                states.put(direction, state);
            }
        }
    }

    /**
     * Four-phase sequence with protected left turns.
     */
    private static class FourPhaseWithLeftTurnSequence implements LightSequence {
        private final AtomicInteger currentPhase = new AtomicInteger(0);
        private static final int PHASE_COUNT = 8; // 4 phases × 2 (green + yellow)

        @Override
        public void advancePhase(Intersection intersection) {
            int phase = currentPhase.getAndUpdate(p -> (p + 1) % PHASE_COUNT);
            Set<Direction> directions = intersection.getDirections();
            Map<Direction, LightState> states = new EnumMap<>(Direction.class);

            // Initialize all to RED
            for (Direction d : directions) {
                states.put(d, LightState.RED);
            }

            switch (phase) {
                case 0 -> { // NS straight green
                    setIfPresent(states, directions, Direction.NORTH, LightState.GREEN);
                    setIfPresent(states, directions, Direction.SOUTH, LightState.GREEN);
                }
                case 1 -> { // NS straight yellow
                    setIfPresent(states, directions, Direction.NORTH, LightState.YELLOW);
                    setIfPresent(states, directions, Direction.SOUTH, LightState.YELLOW);
                }
                case 2 -> { // NS left turn green
                    setIfPresent(states, directions, Direction.NORTH_LEFT_TURN, LightState.GREEN);
                    setIfPresent(states, directions, Direction.SOUTH_LEFT_TURN, LightState.GREEN);
                }
                case 3 -> { // NS left turn yellow
                    setIfPresent(states, directions, Direction.NORTH_LEFT_TURN, LightState.YELLOW);
                    setIfPresent(states, directions, Direction.SOUTH_LEFT_TURN, LightState.YELLOW);
                }
                case 4 -> { // EW straight green
                    setIfPresent(states, directions, Direction.EAST, LightState.GREEN);
                    setIfPresent(states, directions, Direction.WEST, LightState.GREEN);
                }
                case 5 -> { // EW straight yellow
                    setIfPresent(states, directions, Direction.EAST, LightState.YELLOW);
                    setIfPresent(states, directions, Direction.WEST, LightState.YELLOW);
                }
                case 6 -> { // EW left turn green
                    setIfPresent(states, directions, Direction.EAST_LEFT_TURN, LightState.GREEN);
                    setIfPresent(states, directions, Direction.WEST_LEFT_TURN, LightState.GREEN);
                }
                case 7 -> { // EW left turn yellow
                    setIfPresent(states, directions, Direction.EAST_LEFT_TURN, LightState.YELLOW);
                    setIfPresent(states, directions, Direction.WEST_LEFT_TURN, LightState.YELLOW);
                }
            }

            intersection.setLightStates(states);
        }

        private void setIfPresent(Map<Direction, LightState> states, Set<Direction> available,
                                  Direction direction, LightState state) {
            if (available.contains(direction)) {
                states.put(direction, state);
            }
        }
    }

    /**
     * Custom sequence with user-defined phases.
     */
    private static class CustomSequence implements LightSequence {
        private final List<Set<Direction>> phases;
        private final AtomicInteger currentPhaseIndex = new AtomicInteger(0);

        CustomSequence(List<Set<Direction>> phases) {
            if (phases == null || phases.isEmpty()) {
                throw new IllegalArgumentException("phases must not be null or empty");
            }
            // Validate no conflicts within phases
            for (Set<Direction> phase : phases) {
                validatePhase(phase);
            }
            this.phases = new ArrayList<>(phases);
        }

        private void validatePhase(Set<Direction> greenDirections) {
            List<Direction> directionList = new ArrayList<>(greenDirections);
            for (int i = 0; i < directionList.size(); i++) {
                for (int j = i + 1; j < directionList.size(); j++) {
                    if (directionList.get(i).conflictsWith(directionList.get(j))) {
                        throw new IllegalArgumentException(
                                "Phase contains conflicting directions: " +
                                        directionList.get(i) + " and " + directionList.get(j));
                    }
                }
            }
        }

        @Override
        public void advancePhase(Intersection intersection) {
            int phaseIndex = currentPhaseIndex.getAndUpdate(p -> (p + 1) % phases.size());
            Set<Direction> greenDirections = phases.get(phaseIndex);
            Set<Direction> available = intersection.getDirections();

            Map<Direction, LightState> states = new EnumMap<>(Direction.class);

            // Set all to RED first
            for (Direction d : available) {
                states.put(d, LightState.RED);
            }

            // Set specified directions to GREEN
            for (Direction d : greenDirections) {
                if (available.contains(d)) {
                    states.put(d, LightState.GREEN);
                }
            }

            intersection.setLightStates(states);
        }
    }
}
