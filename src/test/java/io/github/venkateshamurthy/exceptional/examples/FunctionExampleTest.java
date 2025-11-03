package io.github.venkateshamurthy.exceptional.examples;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.venkateshamurthy.exceptional.RxFunction;
import io.github.venkateshamurthy.exceptional.RxSupplier;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.vavr.control.Either;
import lombok.NoArgsConstructor;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.Delayer.FIBONACCI;
import static io.github.venkateshamurthy.exceptional.RxFunction.toFunction;
import static io.github.venkateshamurthy.exceptional.RxFunction.toUnaryOperator;
import static io.github.venkateshamurthy.exceptional.RxSupplier.toSupplier;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
@Slf4j
@ExtensionMethod({RxFunction.class, RxSupplier.class, RxTry.class})
public class FunctionExampleTest {
    private static final Map<Class<? extends Exception>, Integer> map = new HashMap<>();
    private static final BiFunction<Class<? extends Exception>, Integer, Integer> incrementor = (e, n)-> 1+defaultIfNull(n,0);
    private static final AtomicInteger ai = new AtomicInteger();

    private static final Retry retry = Retry.of("test", RetryConfig.custom().intervalFunction(FIBONACCI.millis(1, 300))
            .retryExceptions(NullPointerException.class, IOException.class, UnsupportedOperationException.class, ArrayIndexOutOfBoundsException.class)
            .maxAttempts(10).build());

    @AfterEach
    void cleanUp() {
        ai.set(0);
        map.clear();
    }
    @Test
    void testErrorSupplied() {
        //Just override all exception with this
        Supplier<Exception> override = IllegalCallerException::new;

        //Input function
        Function<String, Integer> function = toFunction((String s) -> {
            if (s.equals("a")) throw new UnsupportedOperationException();
            else if (s.equals("b")) throw new TestInstantiationException("");
            else return s.length();
        });

        //Assert as-is exception thrown by function
        assertThrows(UnsupportedOperationException.class, toSupplier(function,"a")::get);
        assertThrows(TestInstantiationException.class, toSupplier(function, "b")::get);

        // A BiFunction taking a Function and its input to return an Either
        BiFunction<Function<String, Integer>, String, Either<Throwable, Integer>> mappedTrier = (f,s)->
                f.tryWrap(s)
                .mapException(UnsupportedOperationException.class, override)
                .mapException(TestInstantiationException.class, override)
                .toEither();

        var aEither = mappedTrier.apply(function,"a");
        var bEither = mappedTrier.apply(function,"b");

        assertTrue(aEither.isLeft());
        assertEquals(IllegalCallerException.class, aEither.mapLeft(Object::getClass).getLeft());

        assertTrue(bEither.isLeft());
        assertEquals(IllegalCallerException.class, bEither.mapLeft(Object::getClass).getLeft());

        String in = "Hello world!";
        assertEquals(in.length(), mappedTrier.apply(function, in).get());
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorConsumedFunctionTestArgs.class)
    void testErrorCountAndPrinting(String testName, int counter,
                                   Function<String, Integer> f,
                                   UnaryOperator<Function<String, Integer>> g,
                                   String greeting,
                                   Map<Class<? extends Exception>, Integer> expected) {
        ai.set(counter);
        final int stringLength = g.apply(f).apply(greeting);
        assertEquals(greeting.length(), stringLength);
        assertEquals(expected, map);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorMappedFunctionTestArgs.class)
    void testErrorMapAndPrinting(String testName,
                                 Function<String, Integer> f,
                                 UnaryOperator<Function<String, Integer>> g,
                                 Pair<String, Either<Class<Exception>, Integer>>[] pairs) {
        Arrays.stream(pairs)
                .forEach(pair -> {
                    var input = pair.getLeft();
                    var expected = pair.getRight();
                    var obtained = toSupplier(() -> g.apply(f).apply(input))
                            .tryWrap().toEither().mapLeft(Object::getClass)
                            .peekLeft(e->log.debug("{}", e)).peek(e->log.debug("{}", e));

                    assertEquals(expected.isLeft(), obtained.isLeft());
                    assertEquals(expected.isRight(), obtained.isRight());

                    if (obtained.isLeft()) {
                        assertThat(obtained.getLeft()).isEqualTo(expected.getLeft());
                    } else {
                        assertThat(obtained.get()).isEqualTo(expected.get());
                    }
                });
    }

    @NoArgsConstructor
    private static class ErrorConsumedFunctionTestArgs implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(
                            "Testing errorConsumedFunction with 3 arguments",
                            10,
                            toFunction((String s) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 6)  throw new ArrayIndexOutOfBoundsException(i + "");
                                else if (i > 3)  throw new UnsupportedOperationException(i + "");
                                else if (i > 0)  throw new NullPointerException(i + "");
                                else return s.length();}),
                            toUnaryOperator((Function<?, ?> f) ->
                                    f.errorConsumedFunction(
                                        UnsupportedOperationException.class,  x->map.compute(x.getClass(), incrementor),
                                        NullPointerException.class,           x->map.compute(x.getClass(), incrementor),
                                        ArrayIndexOutOfBoundsException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryFunction(retry)),
                            "Hello World!",
                            Map.of(UnsupportedOperationException.class, 3, NullPointerException.class,3,
                                ArrayIndexOutOfBoundsException.class, 3  )
                    ),

                    Arguments.of(
                            "Testing errorConsumedFunction with 2 arguments",
                            6,
                            toFunction((String s) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 3) throw new UnsupportedOperationException(i + "");
                                else if (i > 0) throw new NullPointerException(i + "");
                                else return s.length();}),
                            toUnaryOperator((Function<?, ?> f) ->
                                    f.errorConsumedFunction(
                                            UnsupportedOperationException.class,  x->map.compute(x.getClass(), incrementor),
                                            NullPointerException.class,           x->map.compute(x.getClass(), incrementor))
                                    .retryFunction(retry)),
                            "Hello World!",
                            Map.of(UnsupportedOperationException.class, 2, NullPointerException.class,3 )
                    ),
                    Arguments.of(
                            "Testing errorConsumedFunction with 1 arguments",
                            3,
                            toFunction((String s) -> {
                                int i = ai.decrementAndGet();
                                if (i > 0) throw new NullPointerException(i + "");
                                else return s.length();}),
                            toUnaryOperator((Function<?, ?> f) ->
                                    f.errorConsumedFunction(NullPointerException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryFunction(retry)),
                            "Hello World!",
                            Map.of(NullPointerException.class,2 )
                    )
            );
        }
    }

    @NoArgsConstructor
    private static class ErrorMappedFunctionTestArgs implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of("Testing errorMappedFunction with 3 arguments",
                            toFunction((String s) -> {
                                if (s.equals("a")) throw new UnsupportedOperationException();
                                else if (s.equals("b")) throw new TestInstantiationException("");
                                else return s.length();
                            }),
                            toUnaryOperator((Function<?,?> f) -> f.errorMappedFunction(
                                UnsupportedOperationException.class, IllegalStateException::new,
                                NullPointerException.class,          IllegalArgumentException::new,
                                TestInstantiationException.class,    IllegalArgumentException::new)),
                            new Pair[]{
                                Pair.of((String) null      , Either.left(IllegalArgumentException.class)),
                                Pair.of("a"            , Either.left(IllegalStateException.class)),
                                Pair.of("b"            , Either.left(IllegalArgumentException.class)),
                                Pair.of("gbugytfvyv"   , Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedFunction with 2 arguments",
                            toFunction((String s) -> {
                                if (s.equals("b")) throw new TestInstantiationException("");
                                else return s.length();
                            }),
                            toUnaryOperator((Function<?,?> f) -> f.errorMappedFunction(
                                NullPointerException.class,          IllegalArgumentException::new,
                                TestInstantiationException.class,    IllegalArgumentException::new) ),
                            new Pair[]{
                                Pair.of((String) null      , Either.left(IllegalArgumentException.class)),
                                //Pair.of("a"              , Either.left(IllegalStateException.class)),
                                Pair.of("b"            , Either.left(IllegalArgumentException.class)),
                                Pair.of("gbugytfvyv"   , Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedFunction with 1 argument",
                            toFunction(String::length),
                            toUnaryOperator((Function<?,?> f) -> f.errorMappedFunction(
                                NullPointerException.class,          IllegalArgumentException::new)),
                            new Pair[]{
                                Pair.of((String) null      , Either.left(IllegalArgumentException.class)),
                                //Pair.of("a"              , Either.left(IllegalStateException.class)),
                                //Pair.of("b"              , Either.left(IllegalArgumentException.class)),
                                Pair.of("gbugytfvyv"   , Either.right(10))}
                            )
            );
        }
    }
}
