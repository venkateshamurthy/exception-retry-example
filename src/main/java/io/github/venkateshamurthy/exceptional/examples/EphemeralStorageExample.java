package io.github.venkateshamurthy.exceptional.examples;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Config;
import io.micrometer.common.util.StringUtils;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Dimensionless;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This is simple / trivial demonstration of how epheral storage with memory on kubernates container can be understood.
 * <p>In the given context,<br>
 * We have a kubernetes container with about 750MiB of space (drawn for memory not disk) <br>
 * we have the below Horizon Agent URls with each approximating to about 242MB or slight more/less <br>
 * When we copy these (3 agents) they must get copied but however when again tried the copy should not even be attempted
 */
@Slf4j
@RequiredArgsConstructor
@With
public class EphemeralStorageExample {
    private final File destinationFolder;// = new File("/tmp/agent");
    private final Quantity<Dimensionless> MIN_FREE_SPACE;// = Quantities.getQuantity(243, MEGABYTE);
    private final Duration timeOut;// = Duration.ofSeconds(300L);
    private final BiFunction<URI, File, Callable<Either<Exception, Long>>> callableMaker;

    /**
     * Will need few of these Units for calculating free space and comparison etc
     */
    public static final Unit<Dimensionless> BYTE = AbstractUnit.ONE.alternate("B");
    public static final Unit<Dimensionless> KILOBYTE = BYTE.multiply(1024).asType(Dimensionless.class);
    public static final Unit<Dimensionless> MEGABYTE = KILOBYTE.multiply(1024).asType(Dimensionless.class);
    public static final Unit<Dimensionless> GIGABYTE = MEGABYTE.multiply(1024).asType(Dimensionless.class);
    public static final Unit<Dimensionless> TERABYTE = GIGABYTE.multiply(1024).asType(Dimensionless.class);
    public static final Quantity<Dimensionless> ZERO_SPACE = Quantities.getQuantity(0, BYTE);

    public static final URI uri15 = URI.create("https://softwareupdate.omnissa.com/hcs-agents-stable/packages/" +
            "Horizon-Enterprise-Agent/8.15.0/14304348675/agent.tar");
    public static final URI uri14 = URI.create("https://softwareupdate.omnissa.com/hcs-agents-stable/packages/" +
            "Horizon-Enterprise-Agent/8.14.0/12994395200/agent.tar");
    public static final URI uri13 = URI.create("https://softwareupdate.omnissa.com/hcs-agents-stable/packages/" +
            "Horizon-Enterprise-Agent/8.13.0/10002333884/agent.tar");

    private void testAgentCopy(final URI uri, final File agentDir) {
        var callable = callableMaker.apply(uri, agentDir);
        var start = System.currentTimeMillis();
        var single = Single.fromCallable(callable).subscribeOn(Schedulers.io())
                .timeout(timeOut.toMillis() + 100L, MILLISECONDS, Schedulers.computation())
                .doOnEvent((result, error) -> {
                    if (error != null) log.error("Outer Error encountered:", error);
                    else if (result.isLeft()) log.error("Inner Error encountered:", result.getLeft());
                    else log.info("Copied in {} (ms) and bytes written={}",
                                (System.currentTimeMillis() - start), result.get());
                });
        Try.ofSupplier(single::blockingGet)
                .onFailure(t -> log.error("Time spent:{} ms. Blocking Get Error encountered:", (System.currentTimeMillis() - start), t));
    }

    public static Either<Exception, Long> copy(URL in, File out, long bufferSize, Duration timeout) throws IOException {
        log.info("Copying agent file:{} to {},Timeout:{} ms", in.getFile(), out, timeout.toMillis());
        if (out.isDirectory())
            return Either.left(new IllegalArgumentException("'out' argument needs to a be (java.io.)File to be written." +
                    " but you sent  a directory"));
        if (!out.getParentFile().exists()) out.getParentFile().mkdirs();

        final AtomicLong position = new AtomicLong(0L);
        final long start = System.currentTimeMillis();

        return Try.withResources(
                        () -> Channels.newChannel(in.openStream()),
                        () -> new FileOutputStream(out, false).getChannel())
                .of((urlin, fout) -> {
                    long bytes = 0L;
                    do {
                        bytes = fout.transferFrom(urlin, position.get(), bufferSize);
                        position.addAndGet(bytes);
                        //log.info("Copied {} so far..{}", in.getFile(), position.get());
                        final long currentTime = System.currentTimeMillis();
                        final boolean timedOut = (currentTime - start) > timeout.toMillis();
                        if (Thread.interrupted()) {
                            throw new InterruptedIOException(Thread.currentThread().getName() +
                                    "; Interrupted and cancelled at position: " + position.get() +
                                    "; time duration(ms): " + (currentTime - start)
                            );
                        } else if (timedOut) {
                            throw new TimeoutException(Thread.currentThread().getName() +
                                    "; Timedout and cancelled out at position: " + position.get() +
                                    "; time duration(ms): " + (currentTime - start)
                            );
                        }
                    } while (bytes > 0);
                    return position.get();
                })
                .toEither()
                .mapLeft(t -> (t instanceof Exception) ? (Exception) t : new Exception(t));
    }

    private void doCopyWithinKubernetes() throws IOException, ApiException {
        log.info("Running on a kubernetes ...to store at emptyDir at:{}", destinationFolder);
        ApiClient client = Config.defaultClient();
        CoreV1Api api = new CoreV1Api(client);

        // Replace with your namespace and pod name
        String nodeName = System.getenv("KUBERNETES_NODE_NAME");
        String namespace = System.getenv("POD_NAMESPACE");//"default";
        String podName = System.getenv("POD_NAME");//"exception-retry-example-deployment";//"nginx";
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

                doCopy(uri15, uri14, uri13);
            }
        }
    }

    @SneakyThrows
    public void doCopy(@NonNull URI... uris) throws IOException {
        log.info("Running to store at:{}", destinationFolder);
        cleanupDirectory(destinationFolder);//"/agent/hcs-agents-stable");
        // Assumes a good 750-800MB free space
        Consumer<URI> runner = uri -> Try.run(() -> downloadAgent(uri, destinationFolder))
                .onFailure(e->log.error("Error downloading {}", uri,e));
        Arrays.stream(uris).forEach(runner::accept);
    }

    private void downloadAgent(URI uri, File volumeMount) throws IOException {
        if(volumeMount.exists())volumeMount.mkdirs();
        var spaceMap = getDiskSpace(volumeMount);
        if (Comparator
                .comparing((Quantity q) -> q.getValue().longValue())
                .compare(spaceMap.getOrDefault("Available", ZERO_SPACE).to(KILOBYTE), MIN_FREE_SPACE.to(KILOBYTE)) > 0) {
            log.info("Downloading uri:{}", uri);
            Try.run(() -> testAgentCopy(uri, volumeMount)).onFailure(e ->
                    log.error("Error writing {} with Exception:{}", uri, e.getMessage()));
        } else {
            log.error("No space left on device!!!(Used up:{}) to write {}",
                    spaceMap.get("Used"), uri);
        }
    }

    public static void cleanupDirectory(@NonNull final File directoryPath) {
        if (directoryPath.exists()) {
            try (Stream<Path> paths = Files.walk(directoryPath.toPath())) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .filter(f -> !f.equals(directoryPath)) // Do not delete the base folder but remove all its children contents
                        .forEach(File::delete);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        log.info("Any child directory for {} exists?{}: and children:{}", directoryPath, directoryPath.exists(),
                Optional.ofNullable(directoryPath.listFiles()).map(a -> a.length).orElse(0));
    }

    public static Map<String, Quantity<Dimensionless>> getDiskSpace(@NonNull final File volumeMount) throws IOException {
        log.info("Calculating the free space for {}", volumeMount.getAbsolutePath());
        Process p = new ProcessBuilder("df", "-k", volumeMount.getAbsolutePath()).start();
        Map<String, Quantity<Dimensionless>> spaceMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));) {
            String title = br.readLine();
            //log.info("title:{}", title);
            if (StringUtils.isNotBlank(title)) {
                Scanner tScanner = new Scanner(title);
                String value = br.readLine();
                //log.info("value:{}", value);
                if (StringUtils.isNotBlank(value)) {
                    Scanner vScanner = new Scanner(value);
                    while (tScanner.hasNext()) {
                        var head = tScanner.next();
                        if (vScanner.hasNextLong())
                            spaceMap.put(head, Quantities.getQuantity(vScanner.nextLong(), KILOBYTE));
                        else if (vScanner.hasNext()) vScanner.next();
                    }
                    vScanner.close();
                }
                tScanner.close();
            }
        }
        log.info("Space Map:{}", spaceMap);
        return spaceMap;
    }

    public static void main(String[] args) throws IOException, ApiException, InterruptedException {
        final Duration timeOut = Duration.ofMinutes(5);
        var ephemeralStorageAgentCopier =
                new EphemeralStorageExample(
                        new File("/tmp/agent"),
                        Quantities.getQuantity(245, MEGABYTE),
                        timeOut,
                        (URI uri, File agentDir) -> () ->
                                copy(uri.toURL(), new File(agentDir, uri.toURL().getFile()), 8192L, timeOut));
        final String kubeSvcHost = System.getenv("KUBERNETES_SERVICE_HOST");
        if (StringUtils.isNotBlank(kubeSvcHost)) {
            ephemeralStorageAgentCopier.withDestinationFolder(new File("/agent")).doCopyWithinKubernetes();
        } else {
            log.info("No it is not running in kubernetes..its a direct machine on which this program runs");
            ephemeralStorageAgentCopier.doCopy();
        }
        log.info("**** COMPLETED ******");
    }
}
