package io.github.venkateshamurthy.enums;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashSet;
import java.util.stream.Stream;

/**
 * A Set&lt;DynamicEnum&lt;E&gt; along the lines of EnumSet.
 * @param <E> key which is of the form K extends DynamicEnum&lt;E&gt;
 */
@RequiredArgsConstructor
public class DynamicEnumSet<E extends DynamicEnum<E>> extends LinkedHashSet<E> {
    /** The subclass of {@link DynamicEnum} to which all the keys of this set MUST exactly match with.*/
    private final Class<E> enumClass;

    /**
     * Creates DynamicEnumSet&lt;T&gt;
     * @param first argument
     * @param rest argument
     * @return DynamicEnumSet
     * @param <T> type of the form T extends DynamicEnum&lt;T&gt;
     */
    public static <T extends DynamicEnum<T>> DynamicEnumSet<T> of(T first, T... rest) {
        DynamicEnumSet<T> set = new DynamicEnumSet<>(first.getClass());
        Stream.concat(Stream.of(first), Stream.of(rest)).forEach(set::add);
        return set;
    }

    /**
     * Creates DynamicEnumSet&lt;T&gt;
     * @param clazz the class of the dynamic enum for which all stored instances are retrieved
     * @return DynamicEnumSet
     * @param <T> type of the form T extends DynamicEnum&lt;T&gt;
     */
    public static <T extends DynamicEnum<T>> DynamicEnumSet<T> dynamicEnumSet(@NonNull final Class<T> clazz) {
        DynamicEnumSet<T> set = new DynamicEnumSet<>(clazz);
        DynamicEnum.allOf(clazz).forEach(e ->{set.add((T) e);});
        return set;
    }

    /**
     * {@inheritDoc}.
     * <p>
     * Further the element getting added will be checked for the class type
     */
    public boolean add(@NonNull E e) {
        if (! enumClass.equals(e.getClass()))
            throw new IllegalArgumentException("Passed key "+e+" does not match "+enumClass);
        return super.add(e);
    }
}