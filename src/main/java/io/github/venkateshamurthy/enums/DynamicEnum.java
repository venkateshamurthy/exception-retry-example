package io.github.venkateshamurthy.enums;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

import static java.util.Collections.emptyMap;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

/**
 * DynamicEnum is a convenience over static {@link Enum} to add dynamically more instances. Please keep the child class
 * final and keep the child class constructor as provate to contain the instances within. This eanbels all the instances
 * to be located within for more manageability but yet you can add more instances
 * @param <E>
 */
@EqualsAndHashCode(of="name")
@ToString(of="name", includeFieldNames = false)
public abstract class DynamicEnum<E extends DynamicEnum<E>> {
    /** Only child classes can access this to make sure avoiding duplicate instances.*/
    protected static final Map<Class<?>, Map<String, DynamicEnum<?>>> instances = new LinkedHashMap<>();

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
                .filter(e->e.name().equalsIgnoreCase(name))
                .findFirst()
                .map(clazz::cast)
                .orElseThrow(()->new RuntimeException(name+" is not found in this Enumerator"));
    }

    public String name() {
        return name;
    }
    public int ordinal() {
        return ordinal;
    }
}
