package com.trafficLight.api;

import com.trafficLight.model.Intersection;
import com.trafficLight.service.IntersectionRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

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
