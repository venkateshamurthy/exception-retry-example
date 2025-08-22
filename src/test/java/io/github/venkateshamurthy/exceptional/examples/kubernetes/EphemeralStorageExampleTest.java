package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import io.github.venkateshamurthy.exceptional.RxTry;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.RxRunnable.toRunnable;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.Storage.mb;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ExtensionMethod(RxTry.class)
public class EphemeralStorageExampleTest {

    private static final URI[] uris = Agents.getUris();
    private static final Map<URI, Agents> inputMap = Agents.getUriToAgentsMap();
    private final Duration timeOut = Duration.ofMinutes(5);
    private final File localTmpAgentFolder = new File("/tmp/agent");
    private final EphemeralStorageExample ephemeralStorageAgentCopier = EphemeralStorageExample.builder()
        .agentDownloader(new AgentDownloader(timeOut, mb(245), new AtomicReference<>(localTmpAgentFolder ))).build();


    @BeforeEach
    void cleanUp() {
        FileUtils.cleanupDirectory(localTmpAgentFolder);
    }

    @ParameterizedTest(name = "Test Different agent download:{index} whether parallel:{0}")
    @ValueSource(booleans = {false, true})
    @SneakyThrows
    void testEphemeralWriteForAllAgents(boolean isParallel) {
        final int countOfFiles = (EphemeralStorageExample.maxAgentsOfAType + 4)/*2AV, 2DEM, 3HzE*/;
        toRunnable(() -> ephemeralStorageAgentCopier.doCopy(uris)).tryWrap()
                .onSuccess(r -> assertEquals(countOfFiles, getNoOfAgents().size(), "Expected " + countOfFiles + " agent files"))
                .onFailure(e -> log.error("Exception encountered:{}->{}", e.getClass().getSimpleName(), e.getMessage()))
                .getOrElseThrow(Function.identity());
    }

    @SneakyThrows
    @ParameterizedTest(name = "Test same agent download:{index} with timeout value in milliseconds:{0}")
    @ValueSource(longs = {50, 200_000})
    void testEphemeralWriteFor1AgentWithDifferentTimeouts(long timeOutInMillis) {
        var uri = Agents.HZE15.getUri();
        var payload = inputMap.get(uri);
        var agentDownloader = ephemeralStorageAgentCopier.getAgentDownloader();
        toRunnable(() -> ephemeralStorageAgentCopier
                .withAgentDownloader(agentDownloader
                        .withTimeOut(Duration.ofMillis(timeOutInMillis))
                        .withDestinationFolder(new AtomicReference<>(localTmpAgentFolder))
                )
                .doCopy(uri)).tryWrap()
            .onSuccess(r -> {
                if (timeOutInMillis==50L)
                    assertThrows(IllegalStateException.class, ()->{throw payload.checkFile(localTmpAgentFolder).getLeft();});
                else
                    assertEquals(Storage.ZERO, payload.checkFile(localTmpAgentFolder).get() );})
            .onFailure(e -> {
                assertTrue(ExceptionUtils.hasCause(e, TimeoutException.class));
                assertEquals(50, timeOutInMillis);
            })
            .getOrElseThrow(Function.identity());
    }

    private List<File> getNoOfAgents() {
        List<File> listFiles = new ArrayList<>();
        if (localTmpAgentFolder.exists()) {
            try (Stream<Path> paths = Files.walk(localTmpAgentFolder.toPath())) {
                paths.map(Path::toFile).filter(File::isFile).forEach(listFiles::add);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else log.error("Destination folder is absent:{}", localTmpAgentFolder);

        log.info("List of files:{}",listFiles);
        return listFiles;
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
