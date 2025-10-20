package io.github.venkateshamurthy.enums;

import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A Map&lt;DynamicEnum&lt;K&gt;, V&gt; along the lines of EnumMap.
 * @param <K> key which is of the form K extends DynamicEnum&lt;K&gt;
 * @param <V> value
 */
@RequiredArgsConstructor
public class DynamicEnumMap<K extends DynamicEnum<K>, V> extends LinkedHashMap<K,V> {
    /** The subclass of {@link DynamicEnum} to which all the keys of this map MUST exactly match with.*/
    private final Class<K> enumClass;

    /**
     * Creates a {@link DynamicEnumMap}
     *
     * @param cls is Class&lt;A&gt;
     * @param a instance of type A
     * @param b instance of type B
     * @return DynamicEnumMap
     * @param <A> keu type
     * @param <B> value type
     */
    public static <A extends DynamicEnum<A>, B> DynamicEnumMap<A,B> of(Class<A> cls, A a, B b) {
        return new DynamicEnumMap<A, B>(cls).add(a, b);
    }

    /**
     * Creates a {@link DynamicEnumMap}
     *
     * @param cls is Class&lt;A&gt;
     * @param a1 instance of type A
     * @param b1 instance of type B
     * @param a2 instance of type A
     * @param b2 instance of type B
     * @return DynamicEnumMap
     * @param <A> keu type
     * @param <B> value type
     */
    public static <A extends DynamicEnum<A>, B> DynamicEnumMap<A,B> of(Class<A> cls, A a1, B b1, A a2, B b2) {
        return new DynamicEnumMap<A, B>(cls).add(a1, b1).add(a2, b2);
    }

    /**
     * Creates a {@link DynamicEnumMap}
     *
     * @param cls is Class&lt;A&gt;
     * @param a1 instance of type A
     * @param b1 instance of type B
     * @param a2 instance of type A
     * @param b2 instance of type B
     * @param a3 instance of type A
     * @param b3 instance of type B
     * @return DynamicEnumMap
     * @param <A> keu type
     * @param <B> value type
     */
    public static <A extends DynamicEnum<A>, B> DynamicEnumMap<A,B> of(Class<A> cls, A a1, B b1, A a2, B b2, A a3, B b3) {
        return new DynamicEnumMap<A, B>(cls).add(a1, b1).add(a2, b2).add(a3, b3);
    }

    @Override
    public boolean replace(K k, V oldV, V newV) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.replace(k, oldV, newV);
    }

    @Override
    public V replace(K k, V v) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.replace(k, v);
    }

    @Override
    public V merge(K k, V v, BiFunction<? super V, ? super V, ? extends V> mergeFunction) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.merge(k, v, mergeFunction);
    }

    @Override
    public V computeIfAbsent(K k, Function<? super K, ? extends V> remappingFunction) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.computeIfAbsent(k, remappingFunction);
    }

    @Override
    public V computeIfPresent(K k, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.computeIfPresent(k, remappingFunction);
    }

    @Override
    public V compute(K k, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.compute(k, remappingFunction);
    }

    @Override
    public V putIfAbsent(K k, V v) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.putIfAbsent(k, v);
    }

    @Override
    public V put(K k, V v) {
        if (! enumClass.equals(k.getClass()))
            throw new IllegalArgumentException("Passed key "+k+" does not match "+enumClass);
        return super.put(k, v);
    }

    /**
     * A fluent method to put key value pair to the map
     *
     * @param k key
     * @param v value
     * @return this
     */
    public DynamicEnumMap<K, V> add(K k, V v) {
        this.put(k, v);
        return this;
    }
}