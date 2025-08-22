package io.github.venkateshamurthy.enums;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DynamicEnumTest {

    @Test
    void testNameAndOrdinal() {
        assertEquals("ALPHA", TestDynamicEnum.ALPHA.name());
        assertEquals(0, TestDynamicEnum.ALPHA.ordinal());

        assertEquals("BETA", TestDynamicEnum.BETA.name());
        assertEquals(1, TestDynamicEnum.BETA.ordinal());
    }

    @Test
    void testEqualityAndHashCode() {
        // Same reference
        assertEquals(TestDynamicEnum.ALPHA, TestDynamicEnum.ALPHA);
        assertEquals(TestDynamicEnum.ALPHA.hashCode(), TestDynamicEnum.ALPHA.hashCode());

        // Different constants are not equal
        assertNotEquals(TestDynamicEnum.ALPHA, TestDynamicEnum.BETA);
    }

    @Test
    void testToStringContainsNameOnly() {
        String repr = TestDynamicEnum.ALPHA.toString();
        assertTrue(repr.contains("ALPHA"));
        assertFalse(repr.contains("ordinal"));
    }

    @Test
    void testDuplicateInstanceThrows() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            // Attempt to create another ALPHA
            new TestDynamicEnum("ALPHA");
        });
        assertTrue(ex.getMessage().contains("Duplicate instances are not allowed"));
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
}
