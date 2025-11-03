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
import org.apache.commons.lang3.function.TriFunction;
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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.Delayer.FIBONACCI;
import static io.github.venkateshamurthy.exceptional.RxFunction.toBiFunction;
import static io.github.venkateshamurthy.exceptional.RxFunction.toUnaryOperator;
import static io.github.venkateshamurthy.exceptional.RxSupplier.toSupplier;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ExtensionMethod({RxFunction.class, RxSupplier.class, RxTry.class})
public class BiFunctionExampleTest {
    private static final Map<Class<? extends Exception>, Integer> map = new HashMap<>();
    private static final BiFunction<Class<? extends Exception>, Integer, Integer> incrementor = (e, n)-> 1+defaultIfNull(n,0);
    private static final AtomicInteger ai = new AtomicInteger();

    private static final Retry retry = Retry.of("test",
            RetryConfig.custom()
                    .intervalFunction(FIBONACCI.millis(1, 300))
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
        var function = toBiFunction((String s, String u) -> {
            if (s.equals(u)) throw new UnsupportedOperationException();
            else if (s.contains(u)) throw new TestInstantiationException("");
            else return s.concat(u).length();
        });

        //Assert as-is exception thrown by function
        assertThrows(UnsupportedOperationException.class, toSupplier(function,"a","a")::get);
        assertThrows(TestInstantiationException.class, toSupplier(function,"ab","b")::get);

        // A TriFunction taking a Function and its inputs to return an Either
        TriFunction<BiFunction<String, String, Integer>, String, String, Either<Throwable, Integer>>
                mappedTrier = (f, s, u)->
                f.tryWrap(s, u)
                .mapException(UnsupportedOperationException.class, override)
                .mapException(TestInstantiationException.class, override)
                .toEither();

        var aEither = mappedTrier.apply(function,"a", "a");
        var bEither = mappedTrier.apply(function,"ab","b");

        assertTrue(aEither.isLeft());
        assertEquals(IllegalCallerException.class, aEither.mapLeft(Object::getClass).getLeft());

        assertTrue(bEither.isLeft());
        assertEquals(IllegalCallerException.class, bEither.mapLeft(Object::getClass).getLeft());

        String in = "Hello world!", in2=" Mr Universe";
        assertEquals((in+in2).length(), mappedTrier.apply(function, in, in2).get());
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorConsumedFunctionTestArgs.class)
    void testErrorCountAndPrinting(String testName, int counter,
                                   BiFunction<String, String, Integer> f,
                                   UnaryOperator<BiFunction<String, String, Integer>> g,
                                   String greeting, String person,
                                   Map<Class<? extends Exception>, Integer> expected) {
        ai.set(counter);
        final int stringLength = g.apply(f).apply(greeting, person);
        assertEquals(greeting.concat(person).length(), stringLength);
        assertEquals(expected, map);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorMappedFunctionTestArgs.class)
    void testErrorMapAndPrinting(String testName,
                                 BiFunction<String,String, Integer> f,
                                 UnaryOperator<BiFunction<String,String, Integer>> g,
                                 Pair<String[], Either<Class<Exception>, Integer>>[] pairs) {
        Arrays.stream(pairs)
                .forEach(pair -> {
                    var input = pair.getLeft();
                    var expected = pair.getRight();
                    var obtained = toSupplier(() -> g.apply(f).apply(input[0], input[1]))
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
                            toBiFunction((String s, String u) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 6)  throw new ArrayIndexOutOfBoundsException(i + "");
                                else if (i > 3)  throw new UnsupportedOperationException(i + "");
                                else if (i > 0)  throw new NullPointerException(i + "");
                                else return s.concat(u).length();}),
                            toUnaryOperator((BiFunction<?, ?, ?> f) ->
                                    f.errorConsumedBiFunction(
                                        UnsupportedOperationException.class,  x->map.compute(x.getClass(), incrementor),
                                        NullPointerException.class,           x->map.compute(x.getClass(), incrementor),
                                        ArrayIndexOutOfBoundsException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryBiFunction(retry)),
                            "Hello World!", " Mr Universe",
                            Map.of(UnsupportedOperationException.class, 3, NullPointerException.class,3,
                                ArrayIndexOutOfBoundsException.class, 3  )
                    ),

                    Arguments.of(
                            "Testing errorConsumedFunction with 2 arguments",
                            6,
                            toBiFunction((String s, String u) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 3) throw new UnsupportedOperationException(i + "");
                                else if (i > 0) throw new NullPointerException(i + "");
                                else return s.concat(u).length();}),
                            toUnaryOperator((BiFunction<?, ?, ?> f) ->
                                    f.errorConsumedBiFunction(
                                            UnsupportedOperationException.class,  x->map.compute(x.getClass(), incrementor),
                                            NullPointerException.class,           x->map.compute(x.getClass(), incrementor))
                                    .retryBiFunction(retry)),
                            "Hello World!"," Mr Universe",
                            Map.of(UnsupportedOperationException.class, 2, NullPointerException.class,3 )
                    ),
                    Arguments.of(
                            "Testing errorConsumedFunction with 1 arguments",
                            3,
                            toBiFunction((String s, String u) -> {
                                int i = ai.decrementAndGet();
                                if (i > 0) throw new NullPointerException(i + "");
                                else return s.concat(u).length();}),
                            toUnaryOperator((BiFunction<?, ?, ?> f) ->
                                    f.errorConsumedBiFunction(NullPointerException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryBiFunction(retry)),
                            "Hello World!", " Mr Universe",
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
                            toBiFunction((String s, String u) -> {
                                if (s.equals(u)) throw new UnsupportedOperationException();
                                else if (s.contains(u)) throw new TestInstantiationException("");
                                else return s.concat(u).length();
                            }),
                            toUnaryOperator((BiFunction<?,?,?> f) -> f.errorMappedBiFunction(
                                UnsupportedOperationException.class, IllegalStateException::new,
                                NullPointerException.class,          IllegalArgumentException::new,
                                TestInstantiationException.class,    IllegalArgumentException::new)),
                            new Pair[]{
                                Pair.of(new String[]{null,null}      , Either.left(IllegalArgumentException.class)),
                                Pair.of(new String[]{"a","a"}        , Either.left(IllegalStateException.class)),
                                Pair.of(new String[]{"ab","b"}       , Either.left(IllegalArgumentException.class)),
                                Pair.of(new String[]{"gbugy","tfvyv"}, Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedFunction with 2 arguments",
                            toBiFunction((String s, String u) -> {
                                if (s.contains(u)) throw new TestInstantiationException("");
                                else return s.concat(u).length();
                            }),
                            toUnaryOperator((BiFunction<?,?,?> f) -> f.errorMappedBiFunction(
                                NullPointerException.class,          IllegalArgumentException::new,
                                TestInstantiationException.class,    IllegalArgumentException::new) ),
                            new Pair[]{
                                Pair.of(new String[]{null,null}      , Either.left(IllegalArgumentException.class)),
                                //Pair.of(new String[]{"a","a"}      , Either.left(IllegalStateException.class)),
                                Pair.of(new String[]{"ab","b"}       , Either.left(IllegalArgumentException.class)),
                                Pair.of(new String[]{"gbugy","tfvyv"}, Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedFunction with 1 argument",
                            toBiFunction(String::concat).andThen(String::length),
                            toUnaryOperator((BiFunction<?,?, ?> f) -> f.errorMappedBiFunction(
                                NullPointerException.class,          IllegalArgumentException::new)),
                            new Pair[]{
                                Pair.of(new String[]{null,null}      , Either.left(IllegalArgumentException.class)),
                                //Pair.of(new String[]{"a","a"}      , Either.left(IllegalStateException.class)),
                                //Pair.of(new String[]{"ab","b"}     , Either.left(IllegalArgumentException.class)),
                                Pair.of(new String[]{"gbugy","tfvyv"}, Either.right(10))}
                            )
            );
        }
    }
}
