package io.github.venkateshamurthy.enums;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import lombok.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static java.util.Collections.emptyMap;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

/**
 * DynamicEnum is a convenience over static {@link Enum} to add dynamically more instances. Please keep the child class
 * final and keep the child class constructor as private with @Builder from lombok on private constructor.
 * @param <E> a type of self-referential generic.
 */
@EqualsAndHashCode(of = "name")
@ToString(of = "name", includeFieldNames = false)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public abstract class DynamicEnum<E extends DynamicEnum<E>> {
    /** Only child classes can access this to make sure avoiding duplicate instances.*/
    protected static final Map<Class<?>, Map<String, DynamicEnum<?>>> instances = new LinkedHashMap<>();

    /** A map of dynamic enum type to its {@link ObjectMapper}.*/
    protected static final Map<Class<?>, ObjectMapper> mappers = new HashMap<>();

    /** name of the enum instance for a given type &lt;E&gt;.*/
    private  final String name;

    /** ordinal as in the static {@link Enum}.*/
    private  final int ordinal;

    /**
     * Constructor
     * @param name should be unique for a given child class type.
     */
    public DynamicEnum(@NonNull final String name) {
        this.name = name;
        var map = instances.computeIfAbsent(getClass(), clazz -> new LinkedHashMap<>());
        if (map.containsKey(name))
            throw new RuntimeException(getClass().getName() + " - Duplicate instances are not allowed :" + name);
        map.putIfAbsent(name, this);
        mappers.computeIfAbsent(getClass(), c -> {
                    final SimpleModule module = new SimpleModule();
                    module.registerSubtypes(getClass());
                    return new ObjectMapper().registerModule(module);
                });
        ordinal = map.size() - 1; //as the instance is already added to map
    }

    /**
     * allOf DynamicEnum instances for a given class
     *
     * @param clazz is the dynamic enum class
     * @return collection of dynamic enum instances
     * @param <T> type of the dynamic enum
     */
    public static <T extends DynamicEnum<T>> Collection<? super T> allOf(@NonNull final Class<T> clazz) {
        return instances.getOrDefault(clazz, emptyMap()).values();
    }

    /**
     * values provides DynamicEnum instances array for a given type
     *
     * @param clazz is the dynamic enum class
     * @param newArrayMaker a function to create an array
     * @param <T> type of the dynamic enum
     * @return array of the type
     */
    public static <T extends DynamicEnum<T>> T[] values(@NonNull final Class<T> clazz,
                                                        @NonNull final IntFunction<T[]> newArrayMaker) {
        return allOf(clazz).toArray(newArrayMaker);
    }

    /**
     * valueOf provides the dynamic enum corresponding to the name.
     *
     * @param clazz is the dynamic enum class
     * @param name of the dynamic enum for which the corresponding subcclass of {@link DynamicEnum} is to fetched
     * @return T
     * @param <T> is basically the &lt;T extends DynamicEnum&lt;T&gt;&gt;
     * @throws RuntimeException when name does not match what is cached with in
     */
    public static <T extends DynamicEnum<T>> T valueOf(@NonNull final Class<T> clazz, @NonNull final String name) {
        return emptyIfNull(instances.get(clazz).values()).stream()
                .filter(e -> e.name().equalsIgnoreCase(name))
                .findFirst()
                .map(clazz::cast)
                .orElseThrow(()->new RuntimeException(name+" is not found in this Enumerator"));
    }

    /**
     * Gets the {@link ObjectMapper}
     * @param clazz of the dynamic enum for which the mapper is configured
     * @return {@code ObjectMapper}
     * @param <T> type of &lt;T extends DynamicEnum&lt;T&gt;&gt;
     */
    public static <T extends DynamicEnum<T>> ObjectMapper getDefaultMapper(Class<T> clazz) {
        return mappers.get(clazz);
    }

    /**
     * returns an existing {@link DynamicEnum} if not create one using the given supplier.
     * @param clazz DynamicEnum child
     * @param name of the dynamic enum
     * @param createIfAbsent flag whether to create o new one if absent
     * @param supplier to make use when to create a new one
     * @return instance of T
     * @param <T> a type of &lt;T extends {@link DynamicEnum}&lt;T&gt;
     */
    public static <T extends DynamicEnum<T>> T valueOf(@NonNull final Class<T> clazz, @NonNull final String name,
                                                       final boolean createIfAbsent, Supplier<T> supplier) {
        try {
            return valueOf(clazz, name);
        } catch (RuntimeException rte) {
            if (createIfAbsent)
                return supplier.get();
            throw rte;
        }
    }

    /**
     * Gets the name
     * @return name
     */
    @JsonValue
    public String name() {return name;}

    /**
     * Gets the ordinal which might be useful to store instance in an array.
     * @return ordinal.
     */
    public int ordinal() {return ordinal;}
}