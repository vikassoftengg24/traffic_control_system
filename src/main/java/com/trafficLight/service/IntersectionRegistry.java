package com.trafficLight.service;

import com.trafficLight.model.Intersection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IntersectionRegistry {

    private final Map<String, Intersection> intersections = new ConcurrentHashMap<>();

    /**
     * Registers an intersection.
     *
     * @throws IllegalArgumentException if an intersection with the same ID already exists
     */
    public void register(Intersection intersection) {
        Objects.requireNonNull(intersection, "intersection must not be null");

        Intersection existing = intersections.putIfAbsent(intersection.getId(), intersection);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Intersection with ID '" + intersection.getId() + "' already exists");
        }
    }

    /**
     * Gets an intersection by ID.
     */
    public Optional<Intersection> get(String id) {
        return Optional.ofNullable(intersections.get(id));
    }

    /**
     * Gets all registered intersections.
     */
    public Collection<Intersection> getAll() {
        return Collections.unmodifiableCollection(intersections.values());
    }

    /**
     * Removes an intersection by ID.
     *
     * @return true if the intersection was removed
     */
    public boolean remove(String id) {
        return intersections.remove(id) != null;
    }

    /**
     * Checks if an intersection exists.
     */
    public boolean exists(String id) {
        return intersections.containsKey(id);
    }

    /**
     * Gets the count of registered intersections.
     */
    public int size() {
        return intersections.size();
    }

    /**
     * Clears all registered intersections.
     */
    public void clear() {
        intersections.clear();
    }
}
