package io.github.venkateshamurthy.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.github.venkateshamurthy.enums.TestDynamicEnum.ALPHA;
import static io.github.venkateshamurthy.enums.TestDynamicEnum.UNKNOWN;
import static org.junit.jupiter.api.Assertions.*;
@Slf4j
class DynamicEnumTest {

    @Test
    void testNameAndOrdinal() {
        assertEquals("ALPHA", ALPHA.name());
        assertEquals(0, ALPHA.ordinal());

        assertEquals("BETA", TestDynamicEnum.BETA.name());
        assertEquals(1, TestDynamicEnum.BETA.ordinal());
    }

    @Test
    void testEqualityAndHashCode() {
        // Same reference
        assertEquals(ALPHA, ALPHA);
        assertEquals(ALPHA.hashCode(), ALPHA.hashCode());

        // Different constants are not equal
        assertNotEquals(ALPHA, TestDynamicEnum.BETA);
    }

    @Test
    void testToStringContainsNameOnly() {
        String repr = ALPHA.toString();
        assertTrue(repr.contains("ALPHA"));
        assertFalse(repr.contains("ordinal"));
    }

    @Test
    void testDuplicateInstanceThrows() throws NoSuchMethodException {
        final Constructor<TestDynamicEnum> c = TestDynamicEnum.class.getDeclaredConstructor(String.class);
        c.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> {
            c.newInstance("ALPHA");
        });
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertEquals(TestDynamicEnum.class.getName() + " - Duplicate instances are not allowed :ALPHA",
                ex.getCause().getMessage());
        c.setAccessible(false);
    }

    @Test
    void testValues() {
        Color[] colors = Color.values(Color[]::new);
        assertEquals(5, colors.length);
        Set<String> colorSet =  Set.of("UNKNOWN", "RED", "GREEN", "BLUE", "CYAN");
        assertEquals(colorSet, Arrays.stream(colors).map(DynamicEnum::name).collect(Collectors.toSet()));
    }

    @Test
    @SneakyThrows
    void testSerDeser() {
        TestDynamicEnum testEnum = TestDynamicEnum.UNKNOWN;
        final ObjectMapper mapper = DynamicEnum.getDefaultMapper(TestDynamicEnum.class);
        final String alpha = mapper.writeValueAsString(ALPHA);
        //assertEquals("{\"type\":\"TestDynamicEnum\",\"name\":\"ALPHA\"}", alpha);
        assertEquals("[\"TestDynamicEnum\",\"ALPHA\"]", alpha);
        final TestDynamicEnum enumAlpha = mapper.readValue(alpha, TestDynamicEnum.class);
        assertEquals(ALPHA, enumAlpha);
    }

    @Test
    void testReturnsExistingInstanceWhenFound() {
        Color result = DynamicEnum.valueOf(Color.class, "RED", true, () -> new Color("RED", Currency.getInstance("INR")));
        assertSame(Color.valueOf(Color.class, "RED"), result);
        assertEquals("RED", result.name());
        assertEquals(1, result.ordinal());
    }

    @Test
    void testCreatesNewInstanceWhenAbsentAndCreateIfTrue() {
        Supplier<Color> supplier = () -> new Color("BLUE", Currency.getInstance("CAD"));

        // "BLUE" does not exist yet
        Color result = DynamicEnum.valueOf(Color.class, "BLUE", true, supplier);

        assertNotNull(result);
        assertEquals("BLUE", result.name());

        // Verify it got registered
        Color fetched = DynamicEnum.valueOf(Color.class, "BLUE");
        assertSame(result, fetched);
    }

    @Test
    void testThrowsWhenAbsentAndCreateIfFalse() {
        Supplier<Color> supplier = () -> new Color("PURPLE", Currency.getInstance("INR"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                DynamicEnum.valueOf(Color.class, "NON_EXISTENT", false, supplier)
        );

        assertTrue(ex.getMessage().contains("NON_EXISTENT"));
    }

    @Test
    void testSupplierNotCalledWhenValueExists() {
        Supplier<Color> supplier = () -> {
            fail("Supplier should not be called for existing value");
            return new Color("SHOULD_NOT_BE_CREATED", Currency.getInstance("INR"));
        };

        Color result = DynamicEnum.valueOf(Color.class, "GREEN", true, supplier);
        assertEquals("GREEN", result.name());
    }

    @Test
    void testSupplierCalledExactlyOnceWhenCreating() {
        final int[] count = {0};
        Supplier<Color> supplier = () -> {
            count[0]++;
            return new Color("CYAN", Currency.getInstance("INR"));
        };

        Color result = DynamicEnum.valueOf(Color.class, "CYAN", true, supplier);
        assertEquals(1, count[0], "Supplier should be invoked once");
        assertEquals("CYAN", result.name());
    }

    @Test
    void testThrowsIfDuplicateAddedBySupplier() {
        // Supplier intentionally tries to re-add an existing one
        Supplier<Color> supplier = () -> new Color("RED", Currency.getInstance("INR"));
        assertThrows(RuntimeException.class, () ->
                DynamicEnum.valueOf(Color.class, "RED", true, supplier)
        );
    }

    @AfterAll
    static void cleanup() throws Exception {
        // Remove TestDynamicEnum entries from DynamicEnum.values map
        Field valuesField = DynamicEnum.class.getDeclaredField("instances");
        valuesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Class<? extends DynamicEnum>, ?> values =
                (Map<Class<? extends DynamicEnum>, ?>) valuesField.get(null);
        values.remove(TestDynamicEnum.class);
    }

    private static class Color extends DynamicEnum<Color> {
        public static final Color UNKNOWN = new Color("UNKNOWN", Currency.getInstance(Locale.getDefault()));
        public static final Color RED = new Color("RED", Currency.getInstance("INR"));
        public static final Color GREEN = new Color("GREEN", Currency.getInstance("USD"));
        public static final Color BLUE = new Color("BLUE", Currency.getInstance("CAD"));
        @JsonProperty
        private final Currency currency;

        @JsonCreator @Builder
        private Color(@JsonProperty String name, @JsonProperty Currency currency) {
            super(name);
            this.currency = currency;
        }

        public static Color[] values(IntFunction<Color[]> function) {
            return DynamicEnum.values(Color.class, function);
        }
    }
}