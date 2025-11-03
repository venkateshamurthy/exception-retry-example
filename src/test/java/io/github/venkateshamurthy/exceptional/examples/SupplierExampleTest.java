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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;
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
public class SupplierExampleTest {
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
        UnaryOperator<Exception> override = IllegalCallerException::new;

        //Input function
        Function<String, Integer> function = toFunction((String s) -> {
            if (s.equals("a")) throw new UnsupportedOperationException();
            else if (s.equals("b")) throw new TestInstantiationException("");
            else return s.length();
        });
        var aSupplier = toSupplier(function, "a");
        var bSupplier = toSupplier(function,"b");

        //Assert as-is exception thrown by function
        assertThrows(UnsupportedOperationException.class, aSupplier::get);
        assertThrows(TestInstantiationException.class, bSupplier::get);

        // A BiFunction taking a Function and its input to return an Either
        Function<Supplier<Integer>, Either<Throwable, Integer>> mappedTrier = (s)->
                s.tryWrap().mapException(UnsupportedOperationException.class, override,
                                TestInstantiationException.class, override)
                .toEither();

        var aEither = mappedTrier.apply(aSupplier);
        var bEither = mappedTrier.apply(bSupplier);

        assertTrue(aEither.isLeft());
        assertEquals(IllegalCallerException.class, aEither.mapLeft(Object::getClass).getLeft());

        assertTrue(bEither.isLeft());
        assertEquals(IllegalCallerException.class, bEither.mapLeft(Object::getClass).getLeft());

        var hwSupplier = toSupplier(function, "Hello world!");
        assertEquals("Hello world!".length(), mappedTrier.apply(hwSupplier).get());
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorConsumedSupplierTestArgs.class)
    void testErrorCountAndPrinting(String testName, int counter,
                                   Supplier<Integer> f,
                                   UnaryOperator<Supplier<Integer>> g,
                                   Map<Class<? extends Exception>, Integer> expected) {
        ai.set(counter);
        assertTrue(g.apply(f).get()>0);
        assertEquals(expected, map);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorMappedSupplierTestArgs.class)
    void testErrorMapAndPrinting(String testName,
                                 Supplier<Integer>[] fs,
                                 UnaryOperator<Supplier<Integer>> g,
                                 Either<Class<Exception>, Integer>[] expecteds) {
        for(int i =0; i < fs.length; i++) {
            var f = fs[i];
            var expected = expecteds[i];
            var obtained = g.apply(f)
                    .tryWrap().toEither().mapLeft(Object::getClass)
                    .peekLeft(e -> log.debug("{}", e)).peek(e -> log.debug("{}", e));

            assertEquals(expected.isLeft(), obtained.isLeft());
            assertEquals(expected.isRight(), obtained.isRight());

            if (obtained.isLeft()) {
                assertThat(obtained.getLeft()).isEqualTo(expected.getLeft());
            } else {
                assertThat(obtained.get()).isEqualTo(expected.get());
            }
        }
    }

    @NoArgsConstructor
    private static class ErrorConsumedSupplierTestArgs implements ArgumentsProvider {
        private final String greet = "Hello world!";
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context)  {
            return Stream.of(
                    Arguments.of(
                            "Testing errorConsumedSupplier with 3 arguments",
                            10,
                            toSupplier(() -> {
                                int i = ai.decrementAndGet();
                                if      (i > 6)  throw new ArrayIndexOutOfBoundsException(i + "");
                                else if (i > 3)  throw new UnsupportedOperationException(i + "");
                                else if (i > 0)  throw new NullPointerException(i + "");
                                else return greet.length();}),
                            toUnaryOperator((Supplier<?> f) ->
                                    f.errorConsumedSupplier(
                                        UnsupportedOperationException.class,  x->map.compute(x.getClass(), incrementor),
                                        NullPointerException.class,           x->map.compute(x.getClass(), incrementor),
                                        ArrayIndexOutOfBoundsException.class, x->map.compute(x.getClass(), incrementor))
                                    .retrySupplier(retry)),
                            Map.of(UnsupportedOperationException.class, 3, NullPointerException.class,3,
                                ArrayIndexOutOfBoundsException.class, 3  )
                    ),

                    Arguments.of(
                            "Testing errorConsumedSupplier with 2 arguments",
                            6,
                            toSupplier(() -> {
                                int i = ai.decrementAndGet();
                                if      (i > 3) throw new UnsupportedOperationException(i + "");
                                else if (i > 0) throw new NullPointerException(i + "");
                                else return greet.length();}),
                            toUnaryOperator((Supplier<?> f) ->
                                    f.errorConsumedSupplier(
                                            UnsupportedOperationException.class,  x->map.compute(x.getClass(), incrementor),
                                            NullPointerException.class,           x->map.compute(x.getClass(), incrementor))
                                    .retrySupplier(retry)),
                            Map.of(UnsupportedOperationException.class, 2, NullPointerException.class,3 )
                    ),

                    Arguments.of(
                            "Testing errorConsumedSupplier with 1 arguments",
                            3,
                            toSupplier(() -> {
                                int i = ai.decrementAndGet();
                                if (i > 0) throw new NullPointerException(i + "");
                                else return greet.length();}),
                            toUnaryOperator((Supplier<?> f) ->
                                    f.errorConsumedSupplier(NullPointerException.class, x->map.compute(x.getClass(), incrementor))
                                    .retrySupplier(retry)),
                            Map.of(NullPointerException.class,2 )
                    )
            );
        }
    }

    @NoArgsConstructor
    private static class ErrorMappedSupplierTestArgs implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            final String greet = "gbugytfvyv";
            return Stream.of(
                    Arguments.of("Testing errorMappedSupplier with 3 arguments",
                            new Supplier[]{()->{throw new NullPointerException();}, () -> {throw new UnsupportedOperationException();},
                                    ()->{throw new TestInstantiationException("");}, greet::length},
                            toUnaryOperator((Supplier<?> f) -> f.errorMappedSupplier(
                                UnsupportedOperationException.class, IllegalStateException::new,
                                NullPointerException.class,          IllegalArgumentException::new,
                                TestInstantiationException.class,    IllegalArgumentException::new)),
                            new Either[]{Either.left(IllegalArgumentException.class), Either.left(IllegalStateException.class),
                                    Either.left(IllegalArgumentException.class), Either.right(10)}
                            ),

                    Arguments.of("Testing errorMappedSupplier with 3 arguments",
                            new Supplier[]{()->{throw new NullPointerException();}, //() -> {throw new UnsupportedOperationException();},
                                    ()->{throw new TestInstantiationException("");}, greet::length},
                            toUnaryOperator((Supplier<?> f) -> f.errorMappedSupplier(
                                    //UnsupportedOperationException.class, IllegalStateException::new,
                                    NullPointerException.class,          IllegalArgumentException::new,
                                    TestInstantiationException.class,    IllegalArgumentException::new)),
                            new Either[]{Either.left(IllegalArgumentException.class), //Either.left(IllegalStateException.class),
                                    Either.left(IllegalArgumentException.class), Either.right(10)}
                    ),
                    Arguments.of("Testing errorMappedSupplier with 3 arguments",
                            new Supplier[]{()->{throw new NullPointerException();}, //() -> {throw new UnsupportedOperationException();},
                                    //()->{throw new TestInstantiationException("");},
                                    greet::length},
                            toUnaryOperator((Supplier<?> f) -> f.errorMappedSupplier(
                                    //UnsupportedOperationException.class, IllegalStateException::new,
                                    NullPointerException.class,          IllegalArgumentException::new
                                    //TestInstantiationException.class,    IllegalArgumentException::new
                                    )),
                            new Either[]{Either.left(IllegalArgumentException.class), //Either.left(IllegalStateException.class),
                                    //Either.left(IllegalArgumentException.class),
                                    Either.right(10)}
                    )
            );
        }
    }
}
