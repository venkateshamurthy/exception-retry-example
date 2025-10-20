package io.github.venkateshamurthy.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vavr.control.Try;
import lombok.Builder;

//@JsonTypeName("TestDynamicEnum")
public final class TestDynamicEnum extends DynamicEnum<TestDynamicEnum> {
    public static final TestDynamicEnum ALPHA = new TestDynamicEnum("ALPHA");
    public static final TestDynamicEnum BETA  = new TestDynamicEnum("BETA");
    public static final TestDynamicEnum UNKNOWN = new TestDynamicEnum("UNKNOWN");

    public static TestDynamicEnum[] values() {
        return DynamicEnum.values(TestDynamicEnum.class, TestDynamicEnum[]::new);
    }

    //@JsonCreator
    @Builder
    private TestDynamicEnum(@JsonProperty String name) {
        super(name);
    }

   /** @JsonCreator
    public static TestDynamicEnum valueOf(Map<String, Object> mapObj) {
        return Try.of(()->DynamicEnum.valueOf(TestDynamicEnum.class, MapUtils.getString(mapObj,"name")))
                .getOrElse(()->UNKNOWN);
    }*/
    @JsonCreator
    public static TestDynamicEnum valueOf(@JsonProperty String name) {
        return Try.of(()->DynamicEnum.valueOf(TestDynamicEnum.class, name))
                .getOrElse(()->UNKNOWN);
    }
}