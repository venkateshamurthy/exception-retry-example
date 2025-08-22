package io.github.venkateshamurthy.enums;

import io.github.venkateshamurthy.exceptional.examples.errors.Faults;

public final class TestDynamicEnum extends DynamicEnum<TestDynamicEnum> {
    public static final TestDynamicEnum ALPHA = new TestDynamicEnum("ALPHA");
    public static final TestDynamicEnum BETA  = new TestDynamicEnum("BETA");

    public static TestDynamicEnum[] values() {
        return DynamicEnum.values(TestDynamicEnum.class, TestDynamicEnum[]::new);
    }

    public static TestDynamicEnum valueOf(String name) {
        return DynamicEnum.valueOf(TestDynamicEnum.class, name);
    }
    protected TestDynamicEnum(String name) {
        super(name);
    }
}
