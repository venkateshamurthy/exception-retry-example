package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import io.github.venkateshamurthy.exceptional.RxFunction;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.*;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.venkateshamurthy.exceptional.RxFunction.toCheckedBiFunction;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.FileUtils.listFiles;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.StoreUnit.B;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.StoreUnit.KB;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@ExtensionMethod({RxFunction.class, RxTry.class})
@Getter
@With
class AgentDownloader {
    private static final int maxAgentsOfAType = 3;
    private final Duration timeOut;                      // = Duration.ofSeconds(300L);
    private final Storage minFreeSpace;
    private final AtomicReference<File> destinationFolder;
    private final Function<URI, Callable<Either<Exception, Storage>>> callableMaker = (URI uri) -> () ->
            FileUtils.copy(uri.toURL(), new File(getDestinationFolder().get(), uri.toURL().getFile()),
                    KB.toStorage(8), getTimeOut());
    private final ConcurrentMap<URI, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Storage> spaceMap = new ConcurrentHashMap<>();
    public static final String DEM_AGENT = "DEM-Agent";
    public static final String AV_AGENT = "App-Volumes-Agent";
    public static final String HZE_AGENT = "Horizon-Enterprise-Agent";
    private final Map<String, Object> agentTypeLock = Map.of(HZE_AGENT, new Object(), AV_AGENT, new Object(), DEM_AGENT, new Object());
    private final Set<String> agentTypes = agentTypeLock.keySet();

    AgentDownloader(Duration timeOut, Storage minFreeSpace, AtomicReference<File> folder) {
        this.timeOut=timeOut;
        this.minFreeSpace=minFreeSpace;
        this.destinationFolder = (folder);
        Schedulers.computation().schedulePeriodicallyDirect(
                () -> Optional.ofNullable(getDestinationFolder())
                        .map(AtomicReference::get).map(FileUtils::gatherDiskSpace)
                        .ifPresentOrElse(spaceMap::putAll, () -> log.warn("Unable to get spacemap")),
                0, 10, SECONDS);
    }

    String agentType(URI uri) {
        return agentTypes.stream().filter(uri.getPath()::contains).findFirst().orElseThrow();
    }

    Object getAgentTypeLock(URI uri) {
        return agentTypeLock.get(agentType(uri));
    }

    @SneakyThrows
    void doCopy(boolean isParallel, @NonNull URI... uris) {
        log.info("Running to store at:{}", destinationFolder.get());
        if (!destinationFolder.get().exists()) {
            boolean created = destinationFolder.get().mkdirs();
            log.info("volumeMount created:{}", created);
        }
        FileUtils.cleanupDirectory(destinationFolder.get());//"/agent/hcs-agents-stable");

        Function<URI, String> uriToFile = uri -> Try.of(() -> uri.toURL().getFile()).get();
        Consumer<URI> runner = uri -> Try.run(() -> downloadAgent(uri))
                .onFailure(e -> log.error("Error downloading {}:{}", uriToFile.apply(uri), e.getMessage()));
        var stream = isParallel ? Arrays.stream(uris).parallel() : Arrays.stream(uris);
        stream.forEach(runner);
    }

    @SneakyThrows
    private void downloadAgent(URI uri) {
        var destFile = new File(destinationFolder.get(), uri.toURL().getFile());
        var agentType = agentType(uri);
        var typeLock = getAgentTypeLock(uri);
        log.debug("Waiting to acquire lock for URI:{}",
                StringUtils.substringAfter(uri.getPath(), "-Agent"));

        synchronized (typeLock) {
            var payload = Agents.getUriToAgentsMap().get(uri);
            if (payload.checkFile(destinationFolder.get()).isRight()) {
                log.info("No need to download this file:{}", destFile);
                return;
            }

            var downloadLockOnUri = lockMap.computeIfAbsent(uri, k -> new ReentrantLock());
            try {
                int counter = 0;
                while (!downloadLockOnUri.tryLock(150, SECONDS) && counter++ < 3) {
                    log.warn("{}:Trying to acquire lock on {}...", counter, uri);
                }
                if (!downloadLockOnUri.isLocked()) {
                    throw new IllegalStateException("Unable to get Download URI lock for " + uri.getFragment());
                }

                var agentFilesToBeRemoved = listFiles(destinationFolder.get(),
                        file -> StringUtils.containsIgnoreCase(file.getAbsolutePath(), agentType),
                        Comparator.comparing(File::lastModified).reversed(),
                        maxAgentsOfAType - 1); // please note beyond maximum hz agents-1 all agents list up
                var count = agentFilesToBeRemoved.stream()
                        .filter(File::exists)
                        .map(File::delete)
                        .map(BooleanUtils::toInteger)
                        .reduce(0, Integer::sum);

                if (count > 0)
                    log.info("All old agent files deleted:{}; Files:{}", count, agentFilesToBeRemoved);

                while (spaceMap.isEmpty()) {
                    log.info("Sleeping as spaceMap is empty");
                    SECONDS.sleep(5);
                }

                if (getAvailableSpace().isGreaterThanOrEqualTo(getMinFreeSpace())) {
                    var start = System.currentTimeMillis();
                    var trier = toCheckedBiFunction(this::doAgentCopy).tryWrap(uri, start).onFailure(t ->
                            log.error("Time spent:{} ms. Blocking Get Error encountered:{}",
                                    (System.currentTimeMillis() - start), t.getMessage()));
                    var result = trier.getOrElseThrow(Function.identity());
                    if (result.isRight() && result.get().isGreaterThan(Storage.ZERO)) {
                        log.debug("File copied length: {}", result.get());
                    } else if (result.isLeft()) {
                        throw result.getLeft();
                    }
                } else throw new IllegalStateException("No space left on device!!! to write " +
                        uri.getPath() + " Available:" + spaceMap.get("Available") +
                        " Used:" + spaceMap.get("Used"));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while locking for " + uri, ie);
            } finally {
                try {
                    if (downloadLockOnUri.isHeldByCurrentThread())
                        downloadLockOnUri.unlock();
                } catch (IllegalMonitorStateException ignore) {
                }
            }
        }
    }

    Storage getAvailableSpace() {
        return spaceMap.getOrDefault("Available", Storage.ZERO);
    }

    Storage getUsedSpace() {
        return spaceMap.getOrDefault("Used", Storage.ZERO);
    }

    private Either<Exception, Storage> doAgentCopy(URI uri, long start) throws MalformedURLException {
        var destFile = new File(destinationFolder.get(), uri.toURL().getFile());
        if (destFile.exists()) {
            return Agents.getUriToAgentsMap().get(uri).checkFile(destinationFolder.get());
        }
        log.debug("Lock obtained for {}!", destFile);

        var callable = callableMaker.apply(uri);
        var single = Single.fromCallable(callable).subscribeOn(Schedulers.io())
                .timeout(timeOut.toMillis() + 100L, MILLISECONDS, Schedulers.computation())
                .doOnEvent((result, error) -> {
                    if (error != null) log.error("Outer Error encountered:{}", error.getMessage(), error);
                    else if (result.isLeft()) log.error("Inner Error encountered:{}", result.getLeft().getMessage());
                    else log.info("Copied {} in {} (ms) and bytes written={}", destFile,
                                (System.currentTimeMillis() - start), result.get());
                });
        return single.blockingGet();
    }
}
