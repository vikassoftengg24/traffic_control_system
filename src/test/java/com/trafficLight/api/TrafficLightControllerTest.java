package com.trafficLight.api;

import com.trafficLight.exception.ConflictingStateException;
import com.trafficLight.model.Direction;
import com.trafficLight.model.Intersection;
import com.trafficLight.model.LightState;
import com.trafficLight.model.StateChangeEvent;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

public class TrafficLightControllerTest {
    private TrafficLightController controller;

    @BeforeEach
    void setUp() {
        controller = new TrafficLightController();
    }

    @AfterEach
    void tearDown() {
        controller.shutdown();
    }


    @Nested
    @DisplayName("Intersection Management")
    class IntersectionManagement {

        @Test
        @DisplayName("creates and registers standard intersection")
        void createsAndRegistersStandardIntersection() {
            Intersection intersection = controller.createStandardIntersection("INT-001", "Main & 1st");

            assertThat(intersection).isNotNull();
            assertThat(intersection.getId()).isEqualTo("INT-001");
            assertThat(intersection.getName()).isEqualTo("Main & 1st");
            assertThat(intersection.getDirections()).hasSize(4);
        }

        @Test
        @DisplayName("registers custom intersection")
        void registersCustomIntersection() {
            Intersection custom = Intersection.builder()
                    .id("T-001")
                    .name("T-Junction")
                    .withDirections(Direction.NORTH, Direction.EAST, Direction.WEST)
                    .build();

            controller.registerIntersection(custom);

            assertThat(controller.getIntersection("T-001")).isPresent();
        }

        @Test
        @DisplayName("throws on duplicate intersection ID")
        void throwsOnDuplicateIntersectionId() {
            controller.createStandardIntersection("INT-001", "First");

            assertThatThrownBy(() -> controller.createStandardIntersection("INT-001", "Second"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("gets intersection by ID")
        void getsIntersectionById() {
            controller.createStandardIntersection("INT-001", "Test");

            Optional<Intersection> result = controller.getIntersection("INT-001");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Test");
        }

        @Test
        @DisplayName("returns empty for non-existent intersection")
        void returnsEmptyForNonExistentIntersection() {
            assertThat(controller.getIntersection("DOES-NOT-EXIST")).isEmpty();
        }

        @Test
        @DisplayName("gets all intersections")
        void getsAllIntersections() {
            controller.createStandardIntersection("INT-001", "First");
            controller.createStandardIntersection("INT-002", "Second");

            assertThat(controller.getAllIntersections()).hasSize(2);
        }

        @Test
        @DisplayName("removes intersection")
        void removesIntersection() {
            controller.createStandardIntersection("INT-001", "Test");

            boolean removed = controller.removeIntersection("INT-001");

            assertThat(removed).isTrue();
            assertThat(controller.getIntersection("INT-001")).isEmpty();
        }
    }


    @Nested
    class LightStateManagement {

        @BeforeEach
        void setUp() {
            controller.createStandardIntersection("INT-001", "Test");
        }

        @Test
        @DisplayName("gets current state snapshot")
        void getsCurrentStateSnapshot() {
            Intersection.IntersectionSnapshot snapshot = controller.getState("INT-001");

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.id()).isEqualTo("INT-001");
            assertThat(snapshot.lights()).hasSize(4);
        }

        @Test
        @DisplayName("sets individual light state")
        void setsIndividualLightState() {
            StateChangeEvent event = controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN);

            assertThat(event.newState()).isEqualTo(LightState.GREEN);
            assertThat(controller.getState("INT-001").lights().get(Direction.NORTH).state())
                    .isEqualTo(LightState.GREEN);
        }

        @Test
        @DisplayName("sets multiple light states atomically")
        void setsMultipleLightStatesAtomically() {
            List<StateChangeEvent> events = controller.setLightStates("INT-001", Map.of(
                    Direction.NORTH, LightState.GREEN,
                    Direction.SOUTH, LightState.GREEN
            ));

            assertThat(events).hasSize(2);
            Intersection.IntersectionSnapshot snapshot = controller.getState("INT-001");
            assertThat(snapshot.lights().get(Direction.NORTH).state()).isEqualTo(LightState.GREEN);
            assertThat(snapshot.lights().get(Direction.SOUTH).state()).isEqualTo(LightState.GREEN);
        }

        @Test
        @DisplayName("throws for non-existent intersection")
        void throwsForNonExistentIntersection() {
            assertThatThrownBy(() -> controller.getState("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("prevents conflicting states")
        void preventsConflictingStates() {
            controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN);

            assertThatThrownBy(() ->
                    controller.setLightState("INT-001", Direction.EAST, LightState.GREEN))
                    .isInstanceOf(ConflictingStateException.class);
        }
    }

    @Nested
    @DisplayName("Pause and Resume")
    class PauseAndResume {

        @BeforeEach
        void setUp() {
            controller.createStandardIntersection("INT-001", "Test");
        }

        @Test
        @DisplayName("pause sets all lights to red")
        void pauseSetsAllLightsToRed() {
            controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN);

            List<StateChangeEvent> events = controller.pause("INT-001");

            assertThat(events).isNotEmpty();
            Intersection.IntersectionSnapshot snapshot = controller.getState("INT-001");
            assertThat(snapshot.paused()).isTrue();
            assertThat(snapshot.lights().values())
                    .allMatch(light -> light.state() == LightState.RED);
        }

        @Test
        @DisplayName("resume allows state changes")
        void resumeAllowsStateChanges() {
            controller.pause("INT-001");
            controller.resume("INT-001");

            assertThatNoException().isThrownBy(() ->
                    controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN));
        }

        @Test
        @DisplayName("emergency stop pauses all intersections")
        void emergencyStopPausesAllIntersections() {
            controller.createStandardIntersection("INT-002", "Second");
            controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN);
            controller.setLightState("INT-002", Direction.EAST, LightState.GREEN);

            Map<String, List<StateChangeEvent>> results = controller.emergencyStopAll();

            assertThat(results).hasSize(2);
            assertThat(controller.getState("INT-001").paused()).isTrue();
            assertThat(controller.getState("INT-002").paused()).isTrue();
        }
    }

    @Nested
    @DisplayName("History")
    class History {

        @BeforeEach
        void setUp() {
            controller.createStandardIntersection("INT-001", "Test");
        }

        @Test
        @DisplayName("tracks state change history")
        void tracksStateChangeHistory() {
            controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN);
            controller.setLightState("INT-001", Direction.NORTH, LightState.YELLOW);

            List<StateChangeEvent> history = controller.getHistory("INT-001");

            // 4 initial + 2 changes
            assertThat(history).hasSize(6);
        }

        @Test
        @DisplayName("filters history by direction")
        void filtersHistoryByDirection() {
            controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN);
            controller.setLightState("INT-001", Direction.SOUTH, LightState.GREEN);

            List<StateChangeEvent> northHistory = controller.getHistory("INT-001", Direction.NORTH);

            assertThat(northHistory).hasSize(2); // initial + change
            assertThat(northHistory).allMatch(e -> e.direction() == Direction.NORTH);
        }

        @Test
        @DisplayName("filters history by timestamp")
        void filtersHistoryByTimestamp() throws InterruptedException {
            controller.setLightState("INT-001", Direction.NORTH, LightState.GREEN);

            Thread.sleep(50);
            Instant cutoff = Instant.now();
            Thread.sleep(50);

            controller.setLightState("INT-001", Direction.NORTH, LightState.YELLOW);

            List<StateChangeEvent> recentHistory = controller.getHistorySince("INT-001", cutoff);

            assertThat(recentHistory).hasSize(1);
            assertThat(recentHistory.getFirst().newState()).isEqualTo(LightState.YELLOW);
        }
    }
}
