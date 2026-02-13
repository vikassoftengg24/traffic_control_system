package com.trafficLight.api;

import com.trafficLight.exception.ConflictingStateException;
import com.trafficLight.model.Direction;
import com.trafficLight.model.Intersection;
import com.trafficLight.model.LightState;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
