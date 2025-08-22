package io.github.venkateshamurthy.exceptional.examples;

import io.github.venkateshamurthy.exceptional.RxFunction;
import io.github.venkateshamurthy.exceptional.RxSupplier;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.vavr.control.Either;
import lombok.experimental.ExtensionMethod;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.function.TriFunction;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.venkateshamurthy.exceptional.RxTry.tryWrap;

@ExtensionMethod({RxFunction.class, RxSupplier.class, RxTry.class})
@UtilityClass
public class Eitherables {

    public <U,V> Either<Throwable, V> either(Function<U, V> f, U u,
                                             Map<Class<Exception>, Supplier<Exception>> mapper) {
        var trier = f.tryWrap(u);
        mapper.forEach((key, value) -> trier.mapException(key, value));
        return trier.toEither();
    }

    public <T, U, V> Either<Throwable, V> either(BiFunction<T, U, V> f, T t, U u,
                                                Map<Class<Exception>, Supplier<Exception>> mapper) {
        var trier = f.tryWrap(t, u);
        mapper.forEach((key, value) -> trier.mapException(key, value));
        return trier.toEither();
    }
/*
    public <T, U, V> Either<Throwable, V> either(TriFunction<T, U, V> f, T t, U u, V v,
                                                 Map<Class<Exception>, Supplier<Exception>> mapper) {
        var trier = tryWrap(f, t, u, v);
        mapper.forEach((key, value) -> trier.mapException(key, value));
        return trier.toEither();
    }*/
}
