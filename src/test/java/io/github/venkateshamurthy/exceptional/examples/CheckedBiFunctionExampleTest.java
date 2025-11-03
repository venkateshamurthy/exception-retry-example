package io.github.venkateshamurthy.exceptional.examples;

import io.github.resilience4j.core.functions.CheckedBiFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.venkateshamurthy.exceptional.RxFunction;
import io.github.venkateshamurthy.exceptional.RxSupplier;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.vavr.control.Either;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import javax.security.auth.login.AccountLockedException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.Delayer.FIBONACCI;
import static io.github.venkateshamurthy.exceptional.RxFunction.toCheckedBiFunction;
import static io.github.venkateshamurthy.exceptional.RxFunction.toUnaryOperator;
import static io.github.venkateshamurthy.exceptional.RxSupplier.toCheckedSupplier;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ExtensionMethod({RxFunction.class, RxSupplier.class, RxTry.class})
public class CheckedBiFunctionExampleTest {
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
        Supplier<Exception> override = ()-> new ParseException("",0);

        //Input function
        CheckedBiFunction<String, String, Integer> function = toCheckedBiFunction((String s, String u) -> {
            if (s.equals(u)) throw new AccountLockedException("");
            else if (s.contains(u)) throw new IOException("");
            else return s.concat(u).length();
        });

        //Assert as-is exception thrown by function
        assertThrows(AccountLockedException.class, toCheckedSupplier(function,"a","a")::get);
        assertThrows(IOException.class, toCheckedSupplier(function,"ab", "b")::get);

        // A TriFunction taking a Function and its inputs to return an Either
        TriFunction<CheckedBiFunction<String, String, Integer>, String, String, Either<Throwable, Integer>>
                mappedTrier = (f, s, u) ->
                f.tryWrap(s, u)
                .mapExceptions( Map.of(AccountLockedException.class, override, IOException.class,override))
                .toEither();

        var bEither = mappedTrier.apply(function,"ab", "b");
        var aEither = mappedTrier.apply(function,"a","a");

        assertTrue(aEither.isLeft());
        assertEquals(ParseException.class, aEither.mapLeft(Object::getClass).getLeft());

        assertTrue(bEither.isLeft());
        assertEquals(ParseException.class, bEither.mapLeft(Object::getClass).getLeft());

        String in1= "Hello world!", in2 = " from exception-retry";
        assertEquals((in1+in2).length(), mappedTrier.apply(function, in1, in2).get());
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorConsumedCheckedFunctionTestArgs.class)
    void testErrorCountAndPrinting(String testName, int counter,
                                   CheckedBiFunction<String, String, Integer> f,
                                   UnaryOperator<CheckedBiFunction<String, String, Integer>> g,
                                   @NonNull String greeting, String person,
                                   Map<Class<? extends Exception>, Integer> expected) {
        ai.set(counter);
        map.clear();
        assertNotNull(greeting, testName);
        final int stringLength = g.apply(f).apply(greeting, person);
        assertEquals(greeting.concat(person).length(), stringLength, testName);
        assertEquals(expected, map, testName);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorMappedCheckedFunctionTestArgs.class)
    void testErrorMapAndPrinting(String testName,
                                 CheckedBiFunction<String, String, Integer> f,
                                 UnaryOperator<CheckedBiFunction<String, String, Integer>> g,
                                 Pair<String[], Either<Class<Exception>, Integer>>[] pairs)  {
        Arrays.stream(pairs)
                .forEach(pair -> {
                    var input = pair.getLeft();
                    var expected = pair.getRight();
                    var obtained = toCheckedSupplier(() -> g.apply(f).apply(input[0], input[1]))
                            .tryWrap().toEither().mapLeft(Object::getClass)
                            .peekLeft(e->log.debug("{}", e)).peek(e->log.debug("{}", e));

                    assertEquals(expected.isLeft(), obtained.isLeft(), testName);
                    assertEquals(expected.isRight(), obtained.isRight(), testName);

                    if (obtained.isLeft()) {
                        assertThat(obtained.getLeft()).isEqualTo(expected.getLeft());
                    } else {
                        assertThat(obtained.get()).isEqualTo(expected.get());
                    }
                });
    }

    @NoArgsConstructor
    private static class ErrorConsumedCheckedFunctionTestArgs implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(
                            "Testing errorConsumedCheckedFunction with 3 arguments",
                            10,
                            toCheckedBiFunction((String s, String u) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 6)  throw new IOException(i + "");
                                else if (i > 3)  throw new AccountLockedException(i + "");
                                else if (i > 0)  throw new SQLException(i + "");
                                else return s.concat(u).length();}),
                            toUnaryOperator((CheckedBiFunction<?, ?, ?> f) ->
                                    f.errorConsumedCheckedBiFunction(
                                    AccountLockedException.class,x->map.compute(x.getClass(), incrementor),
                                    IOException.class,          x->map.compute(x.getClass(), incrementor),
                                    SQLException.class,         x->map.compute(x.getClass(), incrementor))
                                    .retryCheckedBiFunction(retry)),
                            "Hello World!", " Mr Universe!",
                            Map.of(AccountLockedException.class, 3, IOException.class,3, SQLException.class, 3  )
                    ),

                    Arguments.of(
                            "Testing errorConsumedCheckedFunction with 2 arguments",
                            6,
                            toCheckedBiFunction((String s, String u) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 3) throw new IOException(i + "");
                                else if (i > 0) throw new SQLException(i + "");
                                else return s.concat(u).length();}),
                            toUnaryOperator((CheckedBiFunction<?, ?, ?> f) ->
                                    f.errorConsumedCheckedBiFunction(
                                                    IOException.class,  x->map.compute(x.getClass(), incrementor),
                                                    SQLException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryCheckedBiFunction(retry)),
                            "Hello World!", " Mr Universe!",
                            Map.of(IOException.class, 2, SQLException.class,3 )
                    ),

                    Arguments.of(
                            "Testing errorConsumedCheckedFunction with 1 arguments",
                            3,
                            toCheckedBiFunction((String s, String u) -> {
                                int i = ai.decrementAndGet();
                                if (i > 0) throw new SQLException(i + "");
                                else return s.concat(u).length();}),
                            toUnaryOperator((CheckedBiFunction<?, ?, ?> f) ->
                                    f.errorConsumedCheckedBiFunction(SQLException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryCheckedBiFunction(retry)),
                            "Hello World!", " Mr Universe!",
                            Map.of(SQLException.class,2 )
                    )
            );
        }
    }

    @NoArgsConstructor
    private static class ErrorMappedCheckedFunctionTestArgs implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of("Testing errorMappedCheckedFunction with 3 arguments",
                            toCheckedBiFunction((String s, String u) -> {
                                if (s.equals(u)) throw new AccessDeniedException("");
                                else if (s.contains(u)) throw new IOException("");
                                else return s.concat(u).length();
                            }),
                            toUnaryOperator((CheckedBiFunction<?,?,?> f) -> f.errorMappedCheckedBiFunction(
                                AccessDeniedException.class,x->new SQLException(),
                                NullPointerException.class, x->new Exception(),
                                IOException.class,          x->new ParseException("",0))),
                            new Pair[]{
                                Pair.of(new String[]{null,null} , Either.left(Exception.class)),
                                Pair.of(new String[]{"a", "a"}  , Either.left(ParseException.class)),//due to hierarchical sort
                                Pair.of(new String[]{"ab", "b"} , Either.left(ParseException.class)),
                                Pair.of(new String[]{"gbugy","tfvyv"}   , Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedFunction with 2 arguments",
                            toCheckedBiFunction((String s, String u) -> {
                                if (s.contains(u)) throw new IOException("");
                                else return s.concat(u).length();
                            }),
                            toUnaryOperator((CheckedBiFunction<?,?,?> f) -> f.errorMappedCheckedBiFunction(
                                    NullPointerException.class, x->new Exception(),
                                    IOException.class,          x->new ParseException("",0)) ),
                            new Pair[]{
                                Pair.of(new String[]{null,null}      , Either.left(Exception.class)),
                                //Pair.of(new String[]{"a", "a"}     , Either.left(SQLException.class)),
                                Pair.of(new String[]{"ab", "b"}      , Either.left(ParseException.class)),
                                Pair.of(new String[]{"gbugy","tfvyv"}, Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedCheckedFunction with 1 argument",
                            toCheckedBiFunction((String s1, String s2)->s1.concat(s2).length()),
                            toUnaryOperator((CheckedBiFunction<?,?,?> f) -> f.errorMappedCheckedBiFunction(
                                NullPointerException.class,          Exception::new)),
                            new Pair[]{
                                Pair.of(new String[]{null,null}      , Either.left(Exception.class)),
                                //Pair.of(new String[]{"a", "a"}     , Either.left(SQLException.class)),
                                //Pair.of(new String[]{"ab", "b"}    , Either.left(ParseException.class)),
                                Pair.of(new String[]{"gbugy","tfvyv"}, Either.right(10))}
                            )
            );
        }
    }
}
