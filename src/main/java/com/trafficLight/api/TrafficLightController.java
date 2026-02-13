package com.trafficLight.api;

import com.trafficLight.model.Intersection;
import com.trafficLight.service.IntersectionRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
}
