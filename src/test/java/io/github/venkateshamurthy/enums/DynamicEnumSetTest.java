package io.github.venkateshamurthy.enums;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DynamicEnumSet}.
 */
@Slf4j
public class DynamicEnumSetTest {

    // Concrete test dynamic enum type
    public static class Color extends DynamicEnum<Color> {
        public static final Color RED = new Color("RED");
        public static final Color GREEN = new Color("GREEN");
        public static final Color BLUE = new Color("BLUE");

        private Color(String name) {
            super(name);
            log.info("ctor:values:{}", instances);
        }

        public static Color[] values() {
            return DynamicEnum.values(Color.class, Color[]::new);
        }

        public static Color valueOf(String name) {
            return DynamicEnum.valueOf(Color.class, name);
        }
    }

    /**
     * Deliberate extended class to ensure map actions will fail.
     */
    private static class Shade extends DynamicEnumSetTest.Color {
        public static final Shade DEEPRED = new Shade("DEEPRED");
        public static final Shade DEEPBLUE = new Shade("DEEPBLUE");

        private Shade(String name) {
            super(name);
        }
    }

    private DynamicEnumSet<Color> colorSet;

    @BeforeEach
    void setup() {
        colorSet = new DynamicEnumSet<>(Color.class);
    }

    @Test
    void testAddAndContains() {
        assertTrue(colorSet.add(Color.RED));
        assertTrue(colorSet.contains(Color.RED));
        assertEquals(1, colorSet.size());
    }

    @Test
    void testAddNullThrows() {
        assertThrows(NullPointerException.class, () -> colorSet.add(null));
    }

    @Test
    void testOfCreatesSetWithElements() {
        DynamicEnumSet<Color> set = DynamicEnumSet.of(Color.RED, Color.GREEN);
        assertEquals(2, set.size());
        assertTrue(set.contains(Color.RED));
        assertTrue(set.contains(Color.GREEN));
        assertEquals(Color.class, set.iterator().next().getClass());
    }

    @Test
    void testAllOfCreatesFullSet() {
        DynamicEnumSet<Color> set = DynamicEnumSet.dynamicEnumSet(Color.class);
        assertEquals(3, set.size());
        assertTrue(set.containsAll(List.of(Color.RED, Color.GREEN, Color.BLUE)));
    }

    @Test
    void testOrderIsPreservedFromLinkedHashSet() {
        DynamicEnumSet<Color> set = DynamicEnumSet.of(Color.GREEN, Color.BLUE, Color.RED);
        assertEquals(List.of(Color.GREEN, Color.BLUE, Color.RED), List.copyOf(set));
    }

    @Test
    void testValueOfWorksCorrectlyFromDynamicEnum() {
        Color c = Color.valueOf("RED");
        assertEquals(Color.RED, c);
    }

    @Test
    void testValueOfThrowsForInvalidName() {
        assertThrows(RuntimeException.class, () -> Color.valueOf("PURPLE"));
    }

    @Test
    void testOrdinalIncreasesSequentially() {
        assertEquals(0, Color.RED.ordinal());
        assertEquals(1, Color.GREEN.ordinal());
        assertEquals(2, Color.BLUE.ordinal());
    }

    @Test
    void testAllMutatorsRejectWrongType() {
        Color wrongRed = Shade.DEEPRED, wrongBlue = Shade.DEEPBLUE;
        assertThrows(IllegalArgumentException.class, () -> colorSet.add(wrongRed));
        assertThrows(IllegalArgumentException.class, () -> colorSet.addAll(Set.of(wrongRed, wrongBlue)));
    }
}