package com.trafficLight.api;

import com.trafficLight.model.Direction;
import com.trafficLight.model.Intersection;
import com.trafficLight.model.LightState;
import com.trafficLight.model.StateChangeEvent;
import com.trafficLight.service.IntersectionRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class TrafficLightController {
    private final IntersectionRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> activeSequences;

    public TrafficLightController() {
        this(new IntersectionRegistry());
    }

    public TrafficLightController(IntersectionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.scheduler = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "traffic-light-scheduler");
                    t.setDaemon(true);
                    return t;
                }
        );
        this.activeSequences = new ConcurrentHashMap<>();
    }

    /**
     * Registers a new intersection with the controller.
     */
    public void registerIntersection(Intersection intersection) {
        registry.register(intersection);
    }

    /**
     * Creates and registers a standard four-way intersection.
     */
    public Intersection createStandardIntersection(String id, String name) {
        Intersection intersection = Intersection.builder()
                .id(id)
                .name(name)
                .withStandardFourWay()
                .build();
        registry.register(intersection);
        return intersection;
    }

    /**
     * Gets an intersection by ID.
     */
    public Optional<Intersection> getIntersection(String id) {
        return registry.get(id);
    }

    /**
     * Gets all registered intersections.
     */
    public Collection<Intersection> getAllIntersections() {
        return registry.getAll();
    }

    /**
     * Removes an intersection from the controller.
     */
    public boolean removeIntersection(String id) {
        stopSequence(id);
        return registry.remove(id);
    }

    /**
     * Stops the automated sequence at an intersection.
     */
    public void stopSequence(String intersectionId) {
        ScheduledFuture<?> future = activeSequences.remove(intersectionId);
        if (future != null) {
            future.cancel(false);
        }
    }

    // ==================== Light State Management ====================

    /**
     * Gets the current state of all lights at an intersection.
     */
    public Intersection.IntersectionSnapshot getState(String intersectionId) {
        return getIntersectionOrThrow(intersectionId).snapshot();
    }

    /**
     * Sets the state of a specific light.
     */
    public StateChangeEvent setLightState(String intersectionId, Direction direction, LightState state) {
        return getIntersectionOrThrow(intersectionId).setLightState(direction, state);
    }

    /**
     * Sets multiple light states atomically.
     */
    public List<StateChangeEvent> setLightStates(String intersectionId, Map<Direction, LightState> states) {
        return getIntersectionOrThrow(intersectionId).setLightStates(states);
    }

    // ==================== Helper Methods ====================

    private Intersection getIntersectionOrThrow(String id) {
        return registry.get(id)
                .orElseThrow(() -> new IllegalArgumentException("Intersection not found: " + id));
    }

    /**
     * Shuts down the controller gracefully.
     */
    public void shutdown() {
        activeSequences.values().forEach(f -> f.cancel(false));
        activeSequences.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Interface for automated light sequences.
     */
    @FunctionalInterface
    public interface LightSequence {
        /**
         * Advances to the next phase in the sequence.
         *
         * @param intersection the intersection to control
         */
        void advancePhase(Intersection intersection);
    }

    // ==================== Pause/Resume ====================

    /**
     * Pauses an intersection (all lights go red).
     */
    public List<StateChangeEvent> pause(String intersectionId) {
        return getIntersectionOrThrow(intersectionId).pause();
    }

    /**
     * Resumes an intersection after being paused.
     */
    public void resume(String intersectionId) {
        getIntersectionOrThrow(intersectionId).resume();
    }

    /**
     * Emergency stop - pauses all intersections.
     */
    public Map<String, List<StateChangeEvent>> emergencyStopAll() {
        Map<String, List<StateChangeEvent>> results = new HashMap<>();
        for (Intersection intersection : registry.getAll()) {
            results.put(intersection.getId(), intersection.pause());
        }
        return results;
    }

    // ==================== History ====================

    /**
     * Gets the complete timing history for an intersection.
     */
    public List<StateChangeEvent> getHistory(String intersectionId) {
        return getIntersectionOrThrow(intersectionId).getHistory();
    }

    /**
     * Gets history for a specific direction at an intersection.
     */
    public List<StateChangeEvent> getHistory(String intersectionId, Direction direction) {
        return getIntersectionOrThrow(intersectionId).getHistory(direction);
    }

    /**
     * Gets history since a specific time.
     */
    public List<StateChangeEvent> getHistorySince(String intersectionId, Instant since) {
        return getIntersectionOrThrow(intersectionId).getHistorySince(since);
    }
}
