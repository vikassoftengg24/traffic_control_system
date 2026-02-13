package com.trafficLight.model;

/**
 * Represents the possible states of a traffic light.
 */
public enum LightState {
    RED("Stop"),
    YELLOW("Caution"),
    GREEN("Go");

    private final String description;

    LightState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns the next state in a standard traffic light cycle.
     * GREEN -> YELLOW -> RED -> GREEN
     */
    public LightState nextState() {
        return switch (this) {
            case GREEN -> YELLOW;
            case YELLOW -> RED;
            case RED -> GREEN;
        };
    }

    /**
     * Checks if this state allows traffic to proceed.
     */
    public boolean allowsTraffic() {
        return this == GREEN;
    }
}
