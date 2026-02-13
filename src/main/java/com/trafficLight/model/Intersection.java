package com.trafficLight.model;

import com.trafficLight.exception.ConflictingStateException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Represents a traffic intersection with multiple traffic lights.
 * Manages state changes while ensuring no conflicting directions are green simultaneously.
 * Thread-safe implementation.
 */
public class Intersection {

    private final String id;
    private final String name;
    private final Map<Direction, TrafficLight> lights;
    private final List<StateChangeEvent> history;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile boolean paused = false;

    private Intersection(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.lights = new EnumMap<>(Direction.class);
        this.history = new CopyOnWriteArrayList<>();

        for (Direction direction : builder.directions) {
            TrafficLight light = new TrafficLight(direction, LightState.RED);
            lights.put(direction, light);
            history.add(StateChangeEvent.initial(direction, LightState.RED));
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isPaused() {
        return paused;
    }

    /**
     * Gets the current state of a specific direction's light.
     */
    public LightState getLightState(Direction direction) {
        TrafficLight light = lights.get(direction);
        if (light == null) {
            throw new IllegalArgumentException("Direction not configured: " + direction);
        }
        return light.getCurrentState();
    }

    /**
     * Gets all configured directions at this intersection.
     */
    public Set<Direction> getDirections() {
        return Collections.unmodifiableSet(lights.keySet());
    }

    /**
     * Gets all directions currently showing green.
     */
    public Set<Direction> getGreenDirections() {
        lock.readLock().lock();
        try {
            return lights.entrySet().stream()
                    .filter(e -> e.getValue().getCurrentState() == LightState.GREEN)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableSet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets the state of a specific direction's light.
     * Validates that the new state doesn't create a conflict.
     *
     * @throws ConflictingStateException if the state change would create a conflict
     * @throws IllegalStateException if the intersection is paused
     */
    public StateChangeEvent setLightState(Direction direction, LightState newState) {
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(newState, "newState must not be null");

        lock.writeLock().lock();
        try {
            if (paused) {
                throw new IllegalStateException("Intersection is paused");
            }

            TrafficLight light = lights.get(direction);
            if (light == null) {
                throw new IllegalArgumentException("Direction not configured: " + direction);
            }

            // Validate that this state change doesn't create a conflict
            if (newState == LightState.GREEN) {
                validateNoConflicts(direction);
            }

            StateChangeEvent event = light.setState(newState);
            history.add(event);
            return event;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets multiple light states atomically.
     * Either all changes succeed, or none do.
     *
     * @throws ConflictingStateException if the state changes would create a conflict
     */
    public List<StateChangeEvent> setLightStates(Map<Direction, LightState> stateChanges) {
        Objects.requireNonNull(stateChanges, "stateChanges must not be null");

        lock.writeLock().lock();
        try {
            if (paused) {
                throw new IllegalStateException("Intersection is paused");
            }

            // Validate all directions exist
            for (Direction direction : stateChanges.keySet()) {
                if (!lights.containsKey(direction)) {
                    throw new IllegalArgumentException("Direction not configured: " + direction);
                }
            }

            // Validate the resulting state would not have conflicts
            validateNoConflictsAfterChanges(stateChanges);

            // Apply all changes
            List<StateChangeEvent> events = new ArrayList<>();
            for (Map.Entry<Direction, LightState> entry : stateChanges.entrySet()) {
                StateChangeEvent event = lights.get(entry.getKey()).setState(entry.getValue());
                events.add(event);
                history.add(event);
            }

            return Collections.unmodifiableList(events);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Pauses the intersection, preventing state changes.
     * All lights transition to RED for safety.
     */
    public List<StateChangeEvent> pause() {
        lock.writeLock().lock();
        try {
            if (paused) {
                return Collections.emptyList();
            }

            paused = true;

            // Set all lights to RED for safety
            List<StateChangeEvent> events = new ArrayList<>();
            for (TrafficLight light : lights.values()) {
                if (light.getCurrentState() != LightState.RED) {
                    StateChangeEvent event = light.setState(LightState.RED);
                    events.add(event);
                    history.add(event);
                }
            }

            return Collections.unmodifiableList(events);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Resumes operation after being paused.
     */
    public void resume() {
        lock.writeLock().lock();
        try {
            paused = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the timing history of all state changes.
     */
    public List<StateChangeEvent> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * Gets the timing history for a specific direction.
     */
    public List<StateChangeEvent> getHistory(Direction direction) {
        return history.stream()
                .filter(e -> e.direction() == direction)
                .toList();
    }

    /**
     * Gets state changes since a specific time.
     */
    public List<StateChangeEvent> getHistorySince(Instant since) {
        return history.stream()
                .filter(e -> e.timestamp().isAfter(since))
                .toList();
    }

    /**
     * Gets a snapshot of all traffic lights' current states.
     */
    public IntersectionSnapshot snapshot() {
        lock.readLock().lock();
        try {
            Map<Direction, TrafficLight.TrafficLightSnapshot> snapshots = new EnumMap<>(Direction.class);
            for (Map.Entry<Direction, TrafficLight> entry : lights.entrySet()) {
                snapshots.put(entry.getKey(), entry.getValue().snapshot());
            }
            return new IntersectionSnapshot(id, name, snapshots, paused, Instant.now());
        } finally {
            lock.readLock().unlock();
        }
    }

    private void validateNoConflicts(Direction greenDirection) {
        Set<Direction> conflicting = new HashSet<>();
        conflicting.add(greenDirection);

        for (Map.Entry<Direction, TrafficLight> entry : lights.entrySet()) {
            Direction otherDirection = entry.getKey();
            if (otherDirection != greenDirection
                    && entry.getValue().getCurrentState() == LightState.GREEN
                    && greenDirection.conflictsWith(otherDirection)) {
                conflicting.add(otherDirection);
            }
        }

        if (conflicting.size() > 1) {
            throw new ConflictingStateException(conflicting);
        }
    }

    private void validateNoConflictsAfterChanges(Map<Direction, LightState> stateChanges) {
        // Build the proposed state
        Set<Direction> wouldBeGreen = new HashSet<>();

        for (Map.Entry<Direction, TrafficLight> entry : lights.entrySet()) {
            Direction direction = entry.getKey();
            LightState proposedState = stateChanges.getOrDefault(direction, entry.getValue().getCurrentState());
            if (proposedState == LightState.GREEN) {
                wouldBeGreen.add(direction);
            }
        }

        // Check for conflicts
        Set<Direction> conflicting = new HashSet<>();
        List<Direction> greenList = new ArrayList<>(wouldBeGreen);
        for (int i = 0; i < greenList.size(); i++) {
            for (int j = i + 1; j < greenList.size(); j++) {
                if (greenList.get(i).conflictsWith(greenList.get(j))) {
                    conflicting.add(greenList.get(i));
                    conflicting.add(greenList.get(j));
                }
            }
        }

        if (!conflicting.isEmpty()) {
            throw new ConflictingStateException(conflicting);
        }
    }

    /**
     * Immutable snapshot of an intersection's state.
     */
    public record IntersectionSnapshot(
            String id,
            String name,
            Map<Direction, TrafficLight.TrafficLightSnapshot> lights,
            boolean paused,
            Instant capturedAt
    ) {
        public IntersectionSnapshot {
            lights = Collections.unmodifiableMap(new EnumMap<>(lights));
        }
    }

    /**
     * Builder for creating Intersection instances.
     */
    public static class Builder {
        private String id;
        private String name;
        private final Set<Direction> directions = EnumSet.noneOf(Direction.class);

        public Builder id(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            return this;
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            return this;
        }

        public Builder withDirection(Direction direction) {
            this.directions.add(Objects.requireNonNull(direction, "direction must not be null"));
            return this;
        }

        public Builder withDirections(Direction... directions) {
            for (Direction d : directions) {
                withDirection(d);
            }
            return this;
        }

        public Builder withStandardFourWay() {
            return withDirections(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
        }

        public Intersection build() {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("id is required");
            }
            if (name == null || name.isBlank()) {
                name = "Intersection " + id;
            }
            if (directions.isEmpty()) {
                throw new IllegalStateException("At least one direction must be configured");
            }
            return new Intersection(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
