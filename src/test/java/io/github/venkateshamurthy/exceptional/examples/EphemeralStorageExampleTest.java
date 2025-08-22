package io.github.venkateshamurthy.exceptional.examples;

import io.vavr.control.Try;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.units.indriya.quantity.Quantities;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.examples.EphemeralStorageExample.MEGABYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class EphemeralStorageExampleTest {
    final Duration timeOut = Duration.ofMinutes(5);
    final File localTmpAgentFolder = new File("/tmp/agent");
    final EphemeralStorageExample ephemeralStorageAgentCopier = new EphemeralStorageExample(localTmpAgentFolder, Quantities.getQuantity(245, MEGABYTE), timeOut, (URI uri, File agentDir) -> () -> EphemeralStorageExample.copy(uri.toURL(), new File(agentDir, uri.toURL().getFile()), 8192L, timeOut));

    @BeforeEach
    void cleanUp() {
        EphemeralStorageExample.cleanupDirectory(localTmpAgentFolder);
    }

    @Test
    @SneakyThrows
    void testEphemeralWriteForAllAgents() {
        Try.of(() -> {
            ephemeralStorageAgentCopier.doCopy(
                    EphemeralStorageExample.uri15,
                    EphemeralStorageExample.uri14,
                    EphemeralStorageExample.uri13);
            return null;
        }).onSuccess(r -> assertEquals(3, getNoOfAgents().get(), "Expected 3 agent files"))
                .getOrElseThrow(Function.identity());
        assertEquals(3, getNoOfAgents().get());
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(longs = {500, 1000, 5000, 120_000})
    void testEphemeralWriteFor1AgentWithDifferentTimeouts(long timeOutInMillis) {
        Try.of(() -> {
            ephemeralStorageAgentCopier.withTimeOut(Duration.ofMillis(timeOutInMillis))
                    .doCopy(EphemeralStorageExample.uri15);
            return null;
        }).onSuccess(r -> assertEquals(1, getNoOfAgents().get(), "Expected 1 agent files"))
                .onFailure(e -> assertTrue(timeOutInMillis < 100_000))
                .getOrElseThrow(Function.identity());
    }

    private AtomicInteger getNoOfAgents() {
        AtomicInteger noOfAgents = new AtomicInteger(0);
        if (localTmpAgentFolder.exists()) {
            try (Stream<Path> paths = Files.walk(localTmpAgentFolder.toPath())) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).filter(File::isFile) // Do not delete the base folder but remove all its children contents
                        .forEach(f -> noOfAgents.incrementAndGet());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return noOfAgents;
    }
/*
    @SneakyThrows
    @ParameterizedTest
    @ValueSource(longs = {500,  35_000})
    void testFutureWrite(long timeOut) {
        var executor = ForkJoinPool.commonPool();
        var callable = callableMaker.apply(timeOut);
        var start = System.currentTimeMillis();
        var future = executor.submit(callable);
        Schedulers.computation().scheduleDirect(() -> future.cancel(true), timeOut, MILLISECONDS);
        Try.of(() -> future.get(timeOut + 100, MILLISECONDS))
                .onSuccess(result -> {
                    var end = System.currentTimeMillis();
                    log.info("Copied in {} (ms) and result={}", (end - start), (result.isLeft() ? "Error:" + result.getLeft() : "Bytes Written:" + result.get()));
                })
                .onFailure(t -> {
                    var end = System.currentTimeMillis();
                    log.error("Could not copy even after {} (ms); Error encountered",(end-start), t);
                });
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(longs = {500,  35_000})
    void testThreadInterruptedWrite(long timeOut) throws Throwable {
        var callable = ;
        AtomicReference<Either<Exception, Long>> either = new AtomicReference<>();
        final long start = System.currentTimeMillis();
        var thread = new Thread(() -> either.set(Try.ofCallable(callable).get()));
        thread.start();
        Schedulers.computation().scheduleDirect(thread::interrupt, timeOut - 500, MILLISECONDS);
        Thread.sleep(timeOut + 100);
        Try.of(either::get)
                .onSuccess(result -> {
                    var end = System.currentTimeMillis();
                    if (result != null) {
                        if (result.isLeft()) log.error("Time spent: {} (ms) and Error:", (end-start), result.getLeft());
                        else log.info("Time spent: {} (ms) and result={};", (end - start), result.get());
                    }
                    else log.info("Result is NULL!!");
                })
                .onFailure(t -> log.error("Error encountered", t));
    }
    */

}
