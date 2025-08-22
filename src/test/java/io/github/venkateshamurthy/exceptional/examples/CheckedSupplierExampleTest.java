package io.github.venkateshamurthy.exceptional.examples;

import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.venkateshamurthy.exceptional.RxFunction;
import io.github.venkateshamurthy.exceptional.RxSupplier;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.vavr.control.Either;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import javax.security.auth.login.AccountLockedException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.Delayer.FIBONACCI;
import static io.github.venkateshamurthy.exceptional.RxFunction.toCheckedFunction;
import static io.github.venkateshamurthy.exceptional.RxFunction.toUnaryOperator;
import static io.github.venkateshamurthy.exceptional.RxSupplier.toCheckedSupplier;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ExtensionMethod({RxFunction.class, RxSupplier.class, RxTry.class})
public class CheckedSupplierExampleTest {
    private static final Map<Class<? extends Exception>, Integer> map = new HashMap<>();
    private static final BiFunction<Class<? extends Exception>, Integer, Integer> incrementor = (e, n)-> 1+defaultIfNull(n,0);
    private static final AtomicInteger ai = new AtomicInteger();

    private static final Retry retry = Retry.of("test", RetryConfig.custom().intervalFunction(FIBONACCI.millis(1, 300))
            .retryExceptions(SQLException.class, IOException.class, AccountLockedException.class, ArrayIndexOutOfBoundsException.class)
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
        CheckedFunction<String, Integer> function = toCheckedFunction((String s) -> {
            if (s.equals("a")) throw new SQLException();
            else if (s.equals("b")) throw new IOException("");
            else return s.length();
        });
        var aSupplier = toCheckedSupplier(function, "a");
        var bSupplier = toCheckedSupplier(function, "b");

        //Assert as-is exception thrown by function
        assertThrows(SQLException.class, aSupplier::get);
        assertThrows(IOException.class, bSupplier::get);

        // A BiFunction taking a Function and its input to return an Either
        Function<CheckedSupplier<Integer>, Either<Throwable, Integer>> mappedTrier = (s)->
                s.tryWrap()
                        .mapException(SQLException.class, override, IOException.class, override)
                        .toEither();

        var aEither = mappedTrier.apply(aSupplier);
        var bEither = mappedTrier.apply(bSupplier);

        assertTrue(aEither.isLeft());
        assertEquals(IllegalCallerException.class, aEither.mapLeft(Object::getClass).getLeft());

        assertTrue(bEither.isLeft());
        assertEquals(IllegalCallerException.class, bEither.mapLeft(Object::getClass).getLeft());

        var hwSupplier = toCheckedSupplier(()->function.apply("Hello world!"));
        assertEquals("Hello world!".length(), mappedTrier.apply(hwSupplier).get());
    }

    @ParameterizedTest(name = "{0}")
    @SneakyThrows
    @ArgumentsSource(value = ErrorConsumedCheckedSupplierTestArgs.class)
    void testErrorCountAndPrinting(String testName, int counter,
                                   CheckedSupplier<Integer> f,
                                   UnaryOperator<CheckedSupplier<Integer>> g,
                                   Map<Class<? extends Exception>, Integer> expected) {
        ai.set(counter);
        assertTrue(g.apply(f).get()>0);
        assertEquals(expected, map);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorMappedCheckedSupplierTestArgs.class)
    void testErrorMapAndPrinting(String testName,
                                 CheckedSupplier<Integer>[] fs,
                                 UnaryOperator<CheckedSupplier<Integer>> g,
                                 Either<Class<Exception>, Integer>[] expecteds) {
        for(int i =0; i < fs.length; i++) {
            var f = fs[i];
            var expected = expecteds[i];
            var obtained = g.apply(f)
                    .tryWrap().toEither().mapLeft(Object::getClass)
                    .peekLeft(e -> log.info("{}", e)).peek(e -> log.info("{}", e));

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
    private static class ErrorConsumedCheckedSupplierTestArgs implements ArgumentsProvider {
        private final String greet = "Hello world!";
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(
                            "Testing errorConsumedCheckedSupplier with 3 arguments",
                            10,
                            toCheckedSupplier(() -> {
                                int i = ai.decrementAndGet();
                                if      (i > 6)  throw new SQLException(i + "");
                                else if (i > 3)  throw new IOException(i + "");
                                else if (i > 0)  throw new AccountLockedException(i + "");
                                else return greet.length();}),
                            toUnaryOperator((CheckedSupplier<?> f) ->
                                    f.errorConsumedCheckedSupplier(
                                        SQLException.class,          x->map.compute(x.getClass(), incrementor),
                                        IOException.class,           x->map.compute(x.getClass(), incrementor),
                                        AccountLockedException.class,x->map.compute(x.getClass(), incrementor))
                                    .retryCheckedSupplier(retry)),
                            Map.of(SQLException.class, 3, IOException.class,3,
                                    AccountLockedException.class, 3  )
                    ),

                    Arguments.of(
                            "Testing errorConsumedCheckedSupplier with 2 arguments",
                            6,
                            toCheckedSupplier(() -> {
                                int i = ai.decrementAndGet();
                                if      (i > 3)  throw new IOException(i + "");
                                else if (i > 0)  throw new AccountLockedException(i + "");
                                else return greet.length();}),
                            toUnaryOperator((CheckedSupplier<?> f) ->
                                    f.errorConsumedCheckedSupplier(
                                                    IOException.class,           x->map.compute(x.getClass(), incrementor),
                                                    AccountLockedException.class,x->map.compute(x.getClass(), incrementor))
                                            .retryCheckedSupplier(retry)),
                            Map.of(IOException.class,2, AccountLockedException.class, 3  )
                    ),

                    Arguments.of(
                            "Testing errorConsumedCheckedSupplier with 1 argument",
                            3,
                            toCheckedSupplier(() -> {
                                int i = ai.decrementAndGet();
                                if (i > 0)  throw new AccountLockedException(i + "");
                                else return greet.length();}),
                            toUnaryOperator((CheckedSupplier<?> f) ->
                                    f.errorConsumedCheckedSupplier(
                                                AccountLockedException.class,x->map.compute(x.getClass(), incrementor))
                                        .retryCheckedSupplier(retry)),
                            Map.of(AccountLockedException.class, 2  )
                    )
            );
        }
    }

    @NoArgsConstructor
    private static class ErrorMappedCheckedSupplierTestArgs implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            final String greet = "gbugytfvyv";
            return Stream.of(
                    Arguments.of("Testing errorMappedCheckedSupplier with 3 arguments",
                            new CheckedSupplier[]{()->{throw new NullPointerException();}, () -> {throw new SQLException();},
                                    ()->{throw new IOException("");}, greet::length},
                            toUnaryOperator((CheckedSupplier<?> f) -> f.errorMappedCheckedSupplier(
                                SQLException.class,         IllegalStateException::new,
                                NullPointerException.class, IllegalArgumentException::new,
                                IOException.class,          IllegalArgumentException::new)),
                            new Either[]{Either.left(IllegalArgumentException.class), Either.left(IllegalStateException.class),
                                    Either.left(IllegalArgumentException.class), Either.right(10)}
                            ),

                    Arguments.of("Testing errorMappedCheckedSupplier with 2 arguments",
                            new CheckedSupplier[]{()->{throw new NullPointerException();},// () -> {throw new SQLException();},
                                    ()->{throw new IOException("");}, greet::length},
                            toUnaryOperator((CheckedSupplier<?> f) -> f.errorMappedCheckedSupplier(
                                    //SQLException.class,         IllegalStateException::new,
                                    NullPointerException.class, IllegalArgumentException::new,
                                    IOException.class,          IllegalArgumentException::new)),
                            new Either[]{Either.left(IllegalArgumentException.class), //Either.left(IllegalStateException.class),
                                    Either.left(IllegalArgumentException.class), Either.right(10)}
                    ),
                    Arguments.of("Testing errorMappedCheckedSupplier with 1 argument",
                            new CheckedSupplier[]{()->{throw new NullPointerException();},// () -> {throw new SQLException();},
                                   // ()->{throw new IOException("");},
                                    greet::length},
                            toUnaryOperator((CheckedSupplier<?> f) -> f.errorMappedCheckedSupplier(
                                    //SQLException.class,         IllegalStateException::new,
                                    NullPointerException.class, IllegalArgumentException::new
                                    //IOException.class,          IllegalArgumentException::new
                            )),
                            new Either[]{Either.left(IllegalArgumentException.class), //Either.left(IllegalStateException.class),
                                    //Either.left(IllegalArgumentException.class),
                                    Either.right(10)}
                    )
            );
        }
    }
}
