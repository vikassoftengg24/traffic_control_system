package com.trafficLight.model;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a single traffic light for a specific direction.
 * Thread-safe implementation using read-write locks.
 */
public class TrafficLight {

    private final Direction direction;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile LightState currentState;
    private volatile Instant lastStateChange;

    public TrafficLight(Direction direction) {
        this(direction, LightState.RED);
    }

    public TrafficLight(Direction direction, LightState initialState) {
        this.direction = Objects.requireNonNull(direction, "direction must not be null");
        this.currentState = Objects.requireNonNull(initialState, "initialState must not be null");
        this.lastStateChange = Instant.now();
    }

    public Direction getDirection() {
        return direction;
    }

    public LightState getCurrentState() {
        lock.readLock().lock();
        try {
            return currentState;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Instant getLastStateChange() {
        lock.readLock().lock();
        try {
            return lastStateChange;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Changes the state of this traffic light.
     *
     * @param newState the new state to set
     * @return a StateChangeEvent recording this transition
     */
    public StateChangeEvent setState(LightState newState) {
        Objects.requireNonNull(newState, "newState must not be null");

        lock.writeLock().lock();
        try {
            LightState previousState = this.currentState;
            this.currentState = newState;
            this.lastStateChange = Instant.now();

            return new StateChangeEvent(direction, previousState, newState, lastStateChange);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Advances to the next state in the standard cycle.
     *
     * @return a StateChangeEvent recording this transition
     */
    public StateChangeEvent advanceState() {
        lock.writeLock().lock();
        try {
            return setState(currentState.nextState());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if this light is currently allowing traffic.
     */
    public boolean isAllowingTraffic() {
        return getCurrentState().allowsTraffic();
    }

    /**
     * Creates a snapshot of the current state.
     */
    public TrafficLightSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return new TrafficLightSnapshot(direction, currentState, lastStateChange);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("TrafficLight{direction=%s, state=%s}", direction, getCurrentState());
    }

    /**
     * Immutable snapshot of a traffic light's state at a point in time.
     */
    public record TrafficLightSnapshot(
            Direction direction,
            LightState state,
            Instant stateChangedAt
    ) {}
}
