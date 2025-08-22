package io.github.venkateshamurthy.exceptional.examples;

import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.venkateshamurthy.exceptional.RxFunction;
import io.github.venkateshamurthy.exceptional.RxSupplier;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.vavr.control.Either;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;
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
public class CheckedFunctionExampleTest {
    private static final Map<Class<? extends Exception>, Integer> map = new HashMap<>();
    private static final BiFunction<Class<? extends Exception>, Integer, Integer> incrementor =
            (e, n)-> 1 + defaultIfNull(n,0);
    private static final AtomicInteger ai = new AtomicInteger();

    private static final Retry retry = Retry.of("test", RetryConfig.custom().intervalFunction(FIBONACCI.millis(1, 300))
            .retryExceptions(SQLException.class, IOException.class, AccountLockedException.class, ArrayIndexOutOfBoundsException.class)
            .maxAttempts(10).build());

    @AfterEach
    void cleanUp() {
        ai.set(0);
        map.clear();
    }

    /**
     * This test establishes a lesser know fact that exception such as {@link InterruptedException}, and errors such as
     * {@link LinkageError}, {@link VirtualMachineError} and may be few others are treated as <b>fatal</b> and coded as
     * such within {@link io.vavr.control.Try TryModule#isFatal}. So, they cannot be mapped/consumed with {@code Try}.
     * <p>
     *     <b>So do not try mapping {@code InterruptedException} and such above exceptions</b>
     * </p>
     */
    @Test
    void testInterruptedException() {
        //Just override all exception with this
        UnaryOperator<Exception> override = (x)-> new ParseException("",0);

        //this override is anyway useless as you are trying to map {@code InterruptedException}
        CheckedFunction<String, Integer> unaffectedOrUselessMapper = toCheckedFunction((String s) ->
                Optional.ofNullable(s).map(String::length).orElseThrow(InterruptedException::new))
                .errorMappedCheckedFunction(InterruptedException.class, override);
        assertThrows(InterruptedException.class, ()->unaffectedOrUselessMapper.apply(null));
    }

    @Test
    void testErrorSupplied() {
        //Just override all exception with this
        Supplier<Exception> override = ()-> new ParseException("",0);

        //Input function
        CheckedFunction<String, Integer> function = toCheckedFunction((String s) -> {
            if (s.equals("a")) throw new AccountLockedException("");
            else if (s.equals("b")) throw new IOException("");
            else return s.length();
        });

        //Assert as-is exception thrown by function
        assertThrows(AccountLockedException.class,toCheckedSupplier(function,"a")::get);
        assertThrows(IOException.class, toCheckedSupplier(function,"b")::get);

        // A BiFunction taking a Function and its input to return an Either
        BiFunction<CheckedFunction<String, Integer>, String, Either<Throwable, Integer>> mappedTrier = (f,s)->
                f.tryWrap(s)
                .mapException(AccountLockedException.class, override)
                .mapException(IOException.class, override)
                .toEither();

        var bEither = mappedTrier.apply(function,"b");
        var aEither = mappedTrier.apply(function,"a");

        assertTrue(aEither.isLeft());
        assertEquals(ParseException.class, aEither.mapLeft(Object::getClass).getLeft());

        assertTrue(bEither.isLeft());
        assertEquals(ParseException.class, bEither.mapLeft(Object::getClass).getLeft());

        String in = "Hello world!";
        assertEquals(in.length(), mappedTrier.apply(function, in).get());
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorConsumedCheckedFunctionTestArgs.class)
    void testErrorCountAndPrinting(String testName, int counter,
                                   CheckedFunction<String, Integer> f,
                                   UnaryOperator<CheckedFunction<String, Integer>> g,
                                   @NonNull String greeting,
                                   Map<Class<? extends Exception>, Integer> expected) {
        ai.set(counter);
        map.clear();
        assertNotNull(greeting, testName);
        final int stringLength = g.apply(f).unchecked().apply(greeting);
        assertEquals(greeting.length(), stringLength, testName);
        assertEquals(expected, map, testName);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(value = ErrorMappedCheckedFunctionTestArgs.class)
    void testErrorMapAndPrinting(String testName,
                                 CheckedFunction<String, Integer> f,
                                 UnaryOperator<CheckedFunction<String, Integer>> g,
                                 Pair<String, Either<Class<Exception>, Integer>>[] pairs)  {
        Arrays.stream(pairs)
                .forEach(pair -> {
                    var input = pair.getLeft();
                    var expected = pair.getRight();
                    var obtained = toCheckedSupplier(() -> g.apply(f).apply(input))
                            .tryWrap().toEither().mapLeft(Object::getClass)
                            .peekLeft(e->log.info("{}", e)).peek(e->log.info("{}", e));

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
                            toCheckedFunction((String s) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 6)  throw new IOException(i + "");
                                else if (i > 3)  throw new AccountLockedException(i + "");
                                else if (i > 0)  throw new SQLException(i + "");
                                else return s.length();}),
                            toUnaryOperator((CheckedFunction<?, ?> f) ->
                                    f.errorConsumedCheckedFunction(
                                    AccountLockedException.class,x->map.compute(x.getClass(), incrementor),
                                    IOException.class,          x->map.compute(x.getClass(), incrementor),
                                    SQLException.class,         x->map.compute(x.getClass(), incrementor))
                                    .retryCheckedFunction(retry)),
                            "Hello World!",
                            Map.of(AccountLockedException.class, 3, IOException.class,3, SQLException.class, 3  )
                    ),

                    Arguments.of(
                            "Testing errorConsumedCheckedFunction with 2 arguments",
                            6,
                            toCheckedFunction((String s) -> {
                                int i = ai.decrementAndGet();
                                if      (i > 3) throw new IOException(i + "");
                                else if (i > 0) throw new SQLException(i + "");
                                else return s.length();}),
                            toUnaryOperator((CheckedFunction<?, ?> f) ->
                                    f.errorConsumedCheckedFunction(
                                                    IOException.class,  x->map.compute(x.getClass(), incrementor),
                                                    SQLException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryCheckedFunction(retry)),
                            "Hello World!",
                            Map.of(IOException.class, 2, SQLException.class,3 )
                    ),

                    Arguments.of(
                            "Testing errorConsumedCheckedFunction with 1 arguments",
                            3,
                            toCheckedFunction((String s) -> {
                                int i = ai.decrementAndGet();
                                if (i > 0) throw new SQLException(i + "");
                                else return s.length();}),
                            toUnaryOperator((CheckedFunction<?, ?> f) ->
                                    f.errorConsumedCheckedFunction(SQLException.class, x->map.compute(x.getClass(), incrementor))
                                    .retryCheckedFunction(retry)),
                            "Hello World!",
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
                            toCheckedFunction((String s) -> {
                                if (s.equals("a")) throw new AccessDeniedException("");
                                else if (s.equals("b")) throw new IOException("");
                                else return s.length();
                            }),
                            toUnaryOperator((CheckedFunction<?,?> f) -> f.errorMappedCheckedFunction(
                                AccessDeniedException.class,x->new SQLException(),
                                NullPointerException.class, x->new Exception(),
                                IOException.class,          x->new ParseException("",0))),
                            new Pair[]{
                                Pair.of((String) null      , Either.left(Exception.class)),
                                Pair.of("a"            , Either.left(ParseException.class)),
                                Pair.of("b"            , Either.left(ParseException.class)),
                                Pair.of("gbugytfvyv"   , Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedFunction with 2 arguments",
                            toCheckedFunction((String s) -> {
                                if (s.equals("b")) throw new IOException("");
                                else return s.length();
                            }),
                            toUnaryOperator((CheckedFunction<?,?> f) -> f.errorMappedCheckedFunction(
                                    NullPointerException.class, x->new Exception(),
                                    IOException.class,          x->new ParseException("",0)) ),
                            new Pair[]{
                                Pair.of((String) null      , Either.left(Exception.class)),
                                //Pair.of("a"              , Either.left(IllegalStateException.class)),
                                Pair.of("b"            , Either.left(ParseException.class)),
                                Pair.of("gbugytfvyv"   , Either.right(10))}
                            ),

                    Arguments.of("Testing errorMappedCheckedFunction with 1 argument",
                            toCheckedFunction(String::length),
                            toUnaryOperator((CheckedFunction<?,?> f) -> f.errorMappedCheckedFunction(
                                NullPointerException.class,          Exception::new)),
                            new Pair[]{
                                Pair.of((String) null      , Either.left(Exception.class)),
                                //Pair.of("a"              , Either.left(IllegalStateException.class)),
                                //Pair.of("b"              , Either.left(IllegalArgumentException.class)),
                                Pair.of("gbugytfvyv"   , Either.right(10))}
                            )
            );
        }
    }
}
