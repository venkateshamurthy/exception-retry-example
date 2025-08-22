package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import io.github.venkateshamurthy.exceptional.RxFunction;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Config;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.*;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.measure.Quantity;
import javax.measure.quantity.Dimensionless;
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

import static io.github.venkateshamurthy.exceptional.RxCallable.toCallable;
import static io.github.venkateshamurthy.exceptional.RxFunction.toCheckedBiFunction;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.FileUtils.gatherDiskSpace;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.FileUtils.listFiles;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.Storage.UNIT.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
/**
 * This is simple / trivial demonstration of how ephemeral storage with memory on kubernetes container can be understood.
 * <p>In the given context,<br>
 * We have a kubernetes container with about 750MiB of space (drawn for memory not disk) <br>
 * we have the below Horizon Agent URls with each approximating to about 242MB or slight more/less <br>
 * When we copy these (3 agents) they must get copied but however when again tried the copy should not even be attempted
 */
@Slf4j
@RequiredArgsConstructor
@With
@Builder
@Getter
public class EphemeralStorageExample {

    public static final int maxAgentsOfAType = 3;
    public static final String HCS_AGENTS_STABLE_PACKAGES = "https://softwareupdate.omnissa.com/hcs-agents-stable/packages/";
    public static final String HZE_AGENT = "Horizon-Enterprise-Agent";
    public static final String AV_AGENT = "App-Volumes-Agent";
    public static final String DEM_AGENT = "DEM-Agent";

    private static final Map<URI, Agents> inputMap = Agents.getUriToAgentsMap();
    private final AtomicReference<File> destinationFolder = new AtomicReference<>();                // = new File("/tmp/agent");

    private final AgentDownloader agentDownloader = new AgentDownloader();
    private final ConcurrentMap<URI, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private final Function<URI, Callable<Either<Exception, Storage>>> callableMaker = (URI uri) -> () ->
            FileUtils.copy(uri.toURL(), new File(destinationFolder.get(), uri.toURL().getFile()), KB.of(8), getTimeOut());

    private final Quantity<Dimensionless> MIN_FREE_SPACE;// = Quantities.getQuantity(243, MEGABYTE);
    private final Duration timeOut;                      // = Duration.ofSeconds(300L);

    public EphemeralStorageExample destinationFolder(File file){destinationFolder.set(file); return this;}
    public File destinationFolder(){return destinationFolder.get();}

    public static void main(String[] args) throws IOException, ApiException {
        final Duration timeOut = Duration.ofMinutes(5);
        var ephemeralStorageAgentCopier = EphemeralStorageExample.builder()
                //.destinationFolder(new File("/tmp/agent"))
                .MIN_FREE_SPACE(MB.toQuantity(245)).timeOut(timeOut);
        final String kubeSvcHost = System.getenv("KUBERNETES_SERVICE_HOST");
        URI[] uris = Arrays.stream(Agents.values()).map(Agents::getUri).toArray(URI[]::new);
        File targetFolder;
        if (StringUtils.isNotBlank(kubeSvcHost)) {
            targetFolder = new File("/agent");
            ephemeralStorageAgentCopier.build().destinationFolder(targetFolder)
                    .doCopyWithinKubernetes(uris);
        } else {
            log.info("No it is not running in kubernetes..its a direct machine on which this program runs");
            targetFolder = new File("/tmp/agent");
            ephemeralStorageAgentCopier.build().destinationFolder(targetFolder)
                    .doCopy( uris);
        }
        var listOfHzeAgents = listFiles(targetFolder,
                file -> file.getAbsolutePath().contains("-Agent"), Comparator.naturalOrder(),0);
        log.info("**** BEGIN ******");
        log.info("Final List of agents:{}", listOfHzeAgents);
        log.info("**** COMPLETED ******");
    }

    @SneakyThrows
    public void doCopy(@NonNull URI... uris)  {
        agentDownloader.doCopy(true, uris);
    }

    private void doCopyWithinKubernetes(@NonNull URI... uris) throws IOException, ApiException {
        log.info("Running on a kubernetes ...to store at emptyDir at:{}", destinationFolder);
        ApiClient client = Config.defaultClient();
        CoreV1Api api = new CoreV1Api(client);

        // Replace with your namespace and pod name
        var nodeName = System.getenv("KUBERNETES_NODE_NAME");
        var namespace = System.getenv("POD_NAMESPACE");//"default";
        var podName = System.getenv("POD_NAME");//"exception-retry-example-deployment";//"nginx";
        log.info("Node Name:{}, Pod Name:{}, POd Namespace:{}", nodeName, podName, namespace);

        V1Pod pod = api.readNamespacedPod(podName, namespace, null, false, false);
        for (V1Container container : Objects.requireNonNull(pod.getSpec()).getContainers()) {
            V1ResourceRequirements resources = container.getResources();
            if (resources != null) {
                log.info("Container:{},Requests:{},Limits:{}", container.getName(),
                        resources.getRequests(), resources.getLimits());
                // ephemeral-storage will appear as a key in the map if set
                if (resources.getRequests() != null && resources.getRequests().containsKey("ephemeral-storage")) {
                    log.info("Ephemeral storage request: {}", resources.getRequests().get("ephemeral-storage"));
                }
                if (resources.getLimits() != null && resources.getLimits().containsKey("ephemeral-storage")) {
                    log.info("Ephemeral storage limit: {}", resources.getLimits().get("ephemeral-storage"));
                }
                doCopy(uris);
            }
        }
    }

    @ExtensionMethod({RxFunction.class, RxTry.class})
    class AgentDownloader {
        private static final ConcurrentMap<String, Storage> spaceMap = new ConcurrentHashMap<>();
        private final Map<String, Object> agentTypeLock = Map.of(
                HZE_AGENT, new Object(),
                AV_AGENT, new Object(),
                DEM_AGENT, new Object());
        private final Set<String> agentTypes = agentTypeLock.keySet();

        AgentDownloader() {
            Schedulers.computation().schedulePeriodicallyDirect(
                    () -> Optional.ofNullable(destinationFolder)
                            .map(AtomicReference::get).map(FileUtils::gatherDiskSpace)
                            .ifPresentOrElse(spaceMap::putAll, ()->log.warn("Unable to get spacemap")),
                    0, 10, SECONDS);
        }

        String agentType(URI uri) {
            return agentTypes.stream().filter(uri.getPath()::contains).findFirst().orElseThrow();
        }

        Object getAgentTypeLock(URI uri) {
            return agentTypeLock.get(agentType(uri));
        }

        @SneakyThrows
        private void doCopy(boolean isParallel, @NonNull URI... uris)  {
            log.info("Running to store at:{}", destinationFolder());
            if (!destinationFolder().exists()) {
                boolean created = destinationFolder().mkdirs();
                log.info("volumeMount created:{}", created);
            }
            FileUtils.cleanupDirectory(destinationFolder());//"/agent/hcs-agents-stable");

            Function<URI, String> uriToFile = uri -> Try.of(() -> uri.toURL().getFile()).get();
            Consumer<URI> runner = uri -> Try.run(() -> downloadAgent(uri))
                    .onFailure(e -> log.error("Error downloading {}:{}", uriToFile.apply(uri), e.getMessage()))
                    ;
            var stream = isParallel ? Arrays.stream(uris).parallel() : Arrays.stream(uris) ;
            stream.forEach(runner);
        }

        @SneakyThrows
        private void downloadAgent(URI uri) {
            var destFile = new File(destinationFolder(), uri.toURL().getFile());
            var agentType = agentType(uri);
            var typeLock = getAgentTypeLock(uri);
            log.debug("Waiting to acquire lock for URI:{}",
                    StringUtils.substringAfter(uri.getPath(), "-Agent"));

            synchronized (typeLock) {
                var payload = inputMap.get(uri);
                if (payload.checkFile(destinationFolder()).isRight()) {
                    log.info("No need to download this file:{}", destFile);
                    return;
                }

                var downloadLockOnUri = lockMap.computeIfAbsent(uri, k->new ReentrantLock());
                try {
                    int counter = 0;
                    while (!downloadLockOnUri.tryLock(150, SECONDS) && counter++ < 3) {
                        log.warn("{}:Trying to acquire lock on {}...", counter, uri);
                    }
                    if (!downloadLockOnUri.isLocked()) {
                        throw new IllegalStateException("Unable to get Download URI lock for " + uri.getFragment());
                    }

                    var agentFilesToBeRemoved = listFiles(destinationFolder(),
                            file -> StringUtils.containsIgnoreCase(file.getAbsolutePath(), agentType),
                            Comparator.comparing(File::lastModified).reversed(),
                            maxAgentsOfAType - 1); // please note beyond maximum hz agents-1 all agents list up
                    var count = agentFilesToBeRemoved.stream()
                            .filter(File::exists)
                            .map(File::delete)
                            .map(b->b?1:0)
                            .reduce(Integer::sum).orElse(0);

                    if (count > 0)
                        log.info("All old agent files deleted:{}; Files:{}", count, agentFilesToBeRemoved);

                    while(spaceMap.isEmpty()) {
                        log.info("Sleeping as spaceMap is empty");
                        SECONDS.sleep(5);
                    }

                    if (getAvailableSpace().isGreaterThanOrEqualTo(MIN_FREE_SPACE)) {
                        var start = System.currentTimeMillis();
                        var trier = toCheckedBiFunction(this::doAgentCopy).tryWrap(uri, start).onFailure(t ->
                                log.error("Time spent:{} ms. Blocking Get Error encountered:{}",
                                        (System.currentTimeMillis() - start), t.getMessage()));
                        var result = trier.getOrElseThrow(Function.identity());
                        if (result.isRight() && result.get().isGreaterThan(Storage.ZERO)) {
                            log.debug("File copied length: {}", result.get().to(KB.unit));
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
            var destFile = new File(destinationFolder(), uri.toURL().getFile());
            if (destFile.exists()) {
                return inputMap.get(uri).checkFile(destinationFolder());
            }
            log.debug("Lock obtained for {}!", destFile);

            var callable = callableMaker.apply(uri);
            var single = Single.fromCallable(callable).subscribeOn(Schedulers.io())
                    .timeout(timeOut.toMillis() + 100L, MILLISECONDS, Schedulers.computation())
                    .doOnEvent((result, error) -> {
                        if (error != null) log.error("Outer Error encountered:{}", error.getMessage(),error);
                        else if (result.isLeft())
                            log.error("Inner Error encountered:{}", result.getLeft().getMessage(),result.getLeft());
                        else log.info("Copied {} in {} (ms) and bytes written={}", destFile,
                                    (System.currentTimeMillis() - start), result.get());
                    });
            return single.blockingGet();
        }
    }
}
