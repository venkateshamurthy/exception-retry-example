package io.github.venkateshamurthy.enums;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vavr.control.Try;
import lombok.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized and unit tests for {@link DynamicEnumMap}.
 */
public class DynamicEnumMapTest {

    // --- Test DynamicEnum subclasses ---
    private static class Color extends DynamicEnum<Color> {
        public static final Color RED   = new Color("RED");
        public static final Color GREEN = new Color("GREEN");
        public static final Color BLUE  = new Color("BLUE");
        public static final Color UNKNOWN  = new Color("UNKNOWN");
        @Builder
        private Color(@JsonProperty String name) {super(name);}

        public static Color valueOf(String name) {
            return Try.of(()->DynamicEnum.valueOf(Color.class, name))
                    .getOrElse(()->UNKNOWN);
        }
    }

    /** Deliberate extended class to ensure map actions will fail.*/
    private static class Shade extends Color{
        public static final Shade DEEPRED = new Shade("DEEPRED");
        private Shade(String name) {
            super(name);
        }
    }

    private DynamicEnumMap<Color, String> map;

    @BeforeEach
    void setup() {
        map = new DynamicEnumMap<>(Color.class);
    }

    // --------------------------------------------------------------------
    // Parameterized Tests for "of" Methods
    // --------------------------------------------------------------------

    record MapInput(Color[] colors, String[] values, int expectedSize) {}

    static Stream<MapInput> provideMapInputs() {
        return Stream.of(
                new MapInput(new Color[]{Color.RED}, new String[]{"r"}, 1),
                new MapInput(new Color[]{Color.RED, Color.GREEN}, new String[]{"a", "b"}, 2),
                new MapInput(new Color[]{Color.RED, Color.GREEN, Color.BLUE}, new String[]{"1", "2", "3"}, 3)
        );
    }

    @ParameterizedTest(name = "DynamicEnumMap.of() with {0} keys should create map of size {2}")
    @MethodSource("provideMapInputs")
    void testOfMethods(MapInput input) {
        DynamicEnumMap<Color, String> result;
        if (input.colors().length == 1) {
            result = DynamicEnumMap.of(Color.class, input.colors()[0], input.values()[0]);
        } else if (input.colors().length == 2) {
            result = DynamicEnumMap.of(Color.class,
                    input.colors()[0], input.values()[0],
                    input.colors()[1], input.values()[1]);
        } else {
            result = DynamicEnumMap.of(Color.class,
                    input.colors()[0], input.values()[0],
                    input.colors()[1], input.values()[1],
                    input.colors()[2], input.values()[2]);
        }

        assertEquals(input.expectedSize(), result.size());
        for (int i = 0; i < input.colors().length; i++) {
            assertEquals(input.values()[i], result.get(input.colors()[i]));
        }
    }

    // --------------------------------------------------------------------
    // Basic functionality and validation
    // --------------------------------------------------------------------

    @Test
    void testAddAndGet() {
        map.add(Color.RED, "apple");
        assertEquals("apple", map.get(Color.RED));
        assertEquals(1, map.size());
    }

    @Test
    void testPutAndReplace() {
        map.put(Color.GREEN, "leaf");
        map.replace(Color.GREEN, "leaf", "forest");
        assertEquals("forest", map.get(Color.GREEN));
    }

    @Test
    void testReplaceValueDirectly() {
        map.put(Color.BLUE, "sky");
        map.replace(Color.BLUE, "ocean");
        assertEquals("ocean", map.get(Color.BLUE));
    }

    @Test
    void testMergeCombinesValues() {
        map.put(Color.RED, "a");
        map.merge(Color.RED, "b", (v1, v2) -> v1 + v2);
        assertEquals("ab", map.get(Color.RED));
    }

    @Test
    void testComputeIfAbsentAndComputeIfPresent() {
        map.computeIfAbsent(Color.RED, c -> "auto");
        assertEquals("auto", map.get(Color.RED));

        map.computeIfPresent(Color.RED, (k, v) -> v + "-updated");
        assertEquals("auto-updated", map.get(Color.RED));
    }

    @Test
    void testComputeAlwaysRunsFunction() {
        map.put(Color.RED, "alpha");
        map.compute(Color.RED, (k, v) -> v + "-beta");
        assertEquals("alpha-beta", map.get(Color.RED));
    }

    // --------------------------------------------------------------------
    // Validation (type safety & exceptions)
    // --------------------------------------------------------------------

    @Test
    void testPutRejectsDifferentType() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            @SuppressWarnings("unchecked")
            Color wrong = Shade.DEEPRED;
            map.put(wrong, "invalid");
        });
        assertTrue(ex.getMessage().contains("does not match"));
    }

    @Test
    void testAllMutatorsRejectWrongType() {
        Color wrong = Shade.DEEPRED;
        assertThrows(IllegalArgumentException.class, () -> map.replace(wrong, "x"));
        assertThrows(IllegalArgumentException.class, () -> map.replace(wrong, "old", "new"));
        assertThrows(IllegalArgumentException.class, () -> map.merge(wrong, "v", String::concat));
        assertThrows(IllegalArgumentException.class, () -> map.compute(wrong, (k, v) -> "v2"));
        assertThrows(IllegalArgumentException.class, () -> map.putIfAbsent(wrong, "v"));
        assertThrows(IllegalArgumentException.class, () -> map.computeIfAbsent(wrong, k -> "v"));
        assertThrows(IllegalArgumentException.class, () -> map.computeIfPresent(wrong, (k, v) -> "v2"));
    }

    // --------------------------------------------------------------------
    // Fluent chaining and map behavior
    // --------------------------------------------------------------------

    @Test
    void testAddIsFluentAndChained() {
        DynamicEnumMap<Color, String> result = map.add(Color.RED, "rose").add(Color.GREEN, "leaf");
        assertSame(map, result);
        assertEquals(2, map.size());
    }

    @Test
    void testLinkedHashMapOrderPreserved() {
        map.add(Color.RED, "1").add(Color.GREEN, "2").add(Color.BLUE, "3");
        assertEquals("[RED, GREEN, BLUE]", map.keySet()
                .stream().map(DynamicEnum::name).toList().toString());
    }

    // --------------------------------------------------------------------
    // Map mechanics and overwriting
    // --------------------------------------------------------------------

    @Test
    void testPutIfAbsentOnlyAddsWhenMissing() {
        map.put(Color.RED, "a");
        map.putIfAbsent(Color.RED, "b");
        assertEquals("a", map.get(Color.RED));
    }

    @Test
    void testReplaceThreeArgFailsIfOldValueWrong() {
        map.put(Color.GREEN, "grass");
        assertFalse(map.replace(Color.GREEN, "wrong", "leaf"));
        assertEquals("grass", map.get(Color.GREEN));
    }

    @Test
    void testEmptyMapInitially() {
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }
}