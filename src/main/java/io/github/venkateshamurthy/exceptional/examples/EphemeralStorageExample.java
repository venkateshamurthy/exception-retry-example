package io.github.venkateshamurthy.exceptional.examples;

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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Dimensionless;
import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.examples.EphemeralStorageExample.FileUtils.listFiles;
import static io.github.venkateshamurthy.exceptional.examples.EphemeralStorageExample.STORAGE.*;
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
@Builder
@Getter
public class EphemeralStorageExample {

    public static final ComparableQuantity<Dimensionless> ZERO = B.toQuantity(0L);
    public static final int maxAgentsOfAType = 3;
    public static final String HCS_AGENTS_STABLE_PACKAGES = "https://softwareupdate.omnissa.com/hcs-agents-stable/packages/";
    public static final String HZE_AGENT = "Horizon-Enterprise-Agent";
    public static final String AV_AGENT = "App-Volumes-Agent";
    public static final String DEM_AGENT = "DEM-Agent";
    private static final ConcurrentMap<String, Quantity<Dimensionless>> spaceMap = new ConcurrentHashMap<>();
    private static final Map<URI, AGENTS> inputMap = AGENTS.getUriToAgentsMap();

    private final AgentDownloader agentDownloader = new AgentDownloader();
    private final ConcurrentMap<URI, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private final Function<URI, Callable<Either<Exception, ComparableQuantity<Dimensionless>>>> callableMaker =
            (URI uri) -> () -> FileUtils.copy(uri.toURL(), new File(getDestinationFolder(), uri.toURL().getFile()),
                    KB.toQuantity(8), getTimeOut());

    private final File destinationFolder;                // = new File("/tmp/agent");
    private final Quantity<Dimensionless> MIN_FREE_SPACE;// = Quantities.getQuantity(243, MEGABYTE);
    private final Duration timeOut;                      // = Duration.ofSeconds(300L);

    private final AtomicBoolean postConstructed = new AtomicBoolean(false);

    public EphemeralStorageExample postConstruct() {
        if (!postConstructed.getAndSet(true)) {
            log.info("Post construct invoking the scheduler to comput disk free space on ongoing basis");
            Schedulers.computation().schedulePeriodicallyDirect(
                    () -> {
                        try {
                            FileUtils.gatherDiskSpace(getDestinationFolder());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    0, 3, SECONDS);
        }
        return this;
    }

    public static void main(String[] args) throws IOException, ApiException {
        final Duration timeOut = Duration.ofMinutes(5);
        var ephemeralStorageAgentCopier = EphemeralStorageExample.builder()
                .destinationFolder(new File("/tmp/agent"))
                .MIN_FREE_SPACE(MB.toQuantity(245)).timeOut(timeOut);
        final String kubeSvcHost = System.getenv("KUBERNETES_SERVICE_HOST");
        URI[] uris = Arrays.stream(AGENTS.values()).map(AGENTS::getUri).toArray(URI[]::new);
        if (StringUtils.isNotBlank(kubeSvcHost)) {
            ephemeralStorageAgentCopier.destinationFolder(new File("/agent")).build()
                    .postConstruct()
                    .doCopyWithinKubernetes(uris);
        } else {
            log.info("No it is not running in kubernetes..its a direct machine on which this program runs");
            ephemeralStorageAgentCopier.build()
                    .postConstruct()
                    .doCopy( uris);
        }
        var listOfHzeAgents = listFiles(ephemeralStorageAgentCopier.destinationFolder,
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

    class AgentDownloader {
        private final Map<String, Object> agentTypeLock = Map.of(
                HZE_AGENT, new Object(),
                AV_AGENT, new Object(),
                DEM_AGENT, new Object());
        private final Set<String> agentTypes = agentTypeLock.keySet();

        String agentType(URI uri) {
            return agentTypes.stream().filter(uri.getPath()::contains).findFirst().orElseThrow();
        }

        Object getAgentTypeLock(URI uri) {
            return agentTypeLock.get(agentType(uri));
        }

        @SneakyThrows
        public void doCopy(boolean isParallel, @NonNull URI... uris)  {
            log.info("Running to store at:{}", destinationFolder);
            if (!destinationFolder.exists()) {
                boolean created = destinationFolder.mkdirs();
                log.info("volumeMount created:{}", created);
            }
            FileUtils.cleanupDirectory(destinationFolder);//"/agent/hcs-agents-stable");

            Function<URI, String> uriToFile = uri -> Try.of(() -> uri.toURL().getFile()).get();
            Consumer<URI> runner = uri -> Try.run(() -> downloadAgent(uri))
                    .onFailure(e -> log.error("Error downloading {}:{}", uriToFile.apply(uri), e.getMessage()))
                    ;
            var stream = isParallel ? Arrays.stream(uris).parallel() : Arrays.stream(uris) ;
            stream.forEach(runner);
        }

        @SneakyThrows
        private void downloadAgent(URI uri) {//}, File volumeMount)  {
            final File destFile = new File(getDestinationFolder(), uri.toURL().getFile());
            final String agentType = agentType(uri);
            final Object lockObject = getAgentTypeLock(uri);
            log.debug("Waiting to acquire lock for URI:{}; lock Object is {}",
                    StringUtils.substringAfter(uri.getPath(), "-Agent"), lockObject);

            synchronized (lockObject) {
                var payload = inputMap.get(uri);
                if (payload.checkFile(getDestinationFolder()).isRight()) {
                    log.info("No need to download this file:{}", destFile);
                    return;
                }

                final ReentrantLock downloadLockOnUri = lockMap.computeIfAbsent(uri, k->new ReentrantLock());
                try {
                    int counter = 0;
                    while (!downloadLockOnUri.tryLock(150, SECONDS) && counter++ < 3) {
                        log.warn("{}:Trying to acquire lock on {}...", counter, uri);
                    }
                    if (!downloadLockOnUri.isLocked()) {
                        throw new IllegalStateException("Unable to get Download URI lock for " + uri.getFragment());
                    }
                    var agentFilesToBeRemoved = listFiles(getDestinationFolder(),
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
                    if (Comparator
                            .comparing((Quantity<?> q) -> q.getValue().longValue())
                            .compare(spaceMap.getOrDefault("Available", ZERO).to(KB.unit), MIN_FREE_SPACE.to(KB.unit)) >= 0) {
                        var trier = Try.ofCallable(() -> performAgentCopy(uri));
                        var result = trier.getOrElseThrow(Function.identity());
                        if (result.isRight() && result.get().isGreaterThan(ZERO)) {
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

        @SneakyThrows
        private Either<Exception, ComparableQuantity<Dimensionless>> performAgentCopy(final URI uri) {//}, final File agentDir) {
            var downloadLockOnUri = lockMap.computeIfAbsent(uri, k -> new ReentrantLock());
            var start = System.currentTimeMillis();
            return Try.<Either<Exception, ComparableQuantity<Dimensionless>>>ofCallable(() -> {
                        if (!downloadLockOnUri.isHeldByCurrentThread()) {
                            return Either.left(new IllegalStateException("Unable to get  download lock on " + uri));
                        }
                        var destFile = new File(getDestinationFolder(), uri.toURL().getFile());
                        if (destFile.exists()) {
                            return inputMap.get(uri).checkFile(getDestinationFolder());
                        }
                        log.debug("Lock obtained for {}!", destFile);

                        var callable = callableMaker.apply(uri);
                        var single = Single.fromCallable(callable).subscribeOn(Schedulers.io())
                                .timeout(timeOut.toMillis() + 100L, MILLISECONDS, Schedulers.computation())
                                .doOnEvent((result, error) -> {
                                    if (error != null) log.error("Outer Error encountered:{}", error.getMessage());
                                    else if (result.isLeft())
                                        log.error("Inner Error encountered:{}", result.getLeft().getMessage());
                                    else log.info("Copied {} in {} (ms) and bytes written={}", destFile,
                                                (System.currentTimeMillis() - start), result.get());
                                });
                        return single.blockingGet();
                    })
                    .onFailure(t -> log.error("Time spent:{} ms. Blocking Get Error encountered:{}", (System.currentTimeMillis() - start), t.getMessage()))
                    .andFinallyTry(Try.run(downloadLockOnUri::unlock).onFailure(e->{})::get)
                    .get();
        }
    }

    static class FileUtils {

        static Either<Exception, ComparableQuantity<Dimensionless>> copy(@NonNull final URL in, @NonNull final File out,
                                                                         @NonNull final ComparableQuantity<Dimensionless> bufferSize,
                                                                         @NonNull final Duration timeout) {
            if (out.isDirectory())
                return Either.left(new IllegalArgumentException("'out' argument needs to a be (java.io.)File to be written." +
                        " but you sent  a directory"));
            Try.run(() -> {
                if (out.toPath().getParent() != null) Files.createDirectories(out.toPath().getParent());
            }).get();

            final AtomicLong position = new AtomicLong(0L);
            final long start = System.currentTimeMillis();
            final AtomicReference<FileLock> fileLockRef = new AtomicReference<>();

            return Try.withResources(
                            () -> {
                                URLConnection conn = in.openConnection();
                                conn.setConnectTimeout((int) timeout.toMillis());
                                conn.setReadTimeout((int) timeout.toMillis());
                                return Channels.newChannel(conn.getInputStream());
                            },
                            () -> new FileOutputStream(out, false).getChannel())
                    .of((urlIn, fileChannel) -> {
                        // Get exclusive file lock to avoid any overwrite on this file (by other process/thread)
                        fileLockRef.set(fileChannel.tryLock());
                        if (fileLockRef.get() == null) {
                            throw new IllegalStateException(
                                    "Some other thread/process has locked up the file: " + out,
                                    new OverlappingFileLockException());
                        }
                        log.trace("Obtained exclusive lock; Copying agent file:{} to {},Timeout:{} ms", in.getFile(), out, timeout.toMillis());

                        long bytes;
                        do {
                            bytes = fileChannel.transferFrom(urlIn, position.get(), B.toLong(bufferSize));
                            position.addAndGet(bytes);
                            //log.info("Copied {} so far..{}", in.getFile(), position.get());
                            final long currentTime = System.currentTimeMillis();
                            if (Thread.interrupted()) {
                                throw new InterruptedIOException(Thread.currentThread().getName() +
                                        "; Interrupted and cancelled at position: " + position.get() +
                                        "; time duration(ms): " + (currentTime - start)
                                );
                            } else if ((currentTime - start) > timeout.toMillis()) {
                                throw new TimeoutException(Thread.currentThread().getName() +
                                        "; Timed out and cancelled out at position: " + position.get() +
                                        "; time duration(ms): " + (currentTime - start)
                                );
                            }
                        } while (bytes > 0);
                        return B.toQuantity(position.get()); //position always gives in bytes
                    })
                    .andFinallyTry(() -> {
                        var fileLock = fileLockRef.get();
                        if (fileLock != null) Try.run(fileLock::close);
                    })
                    .toEither()
                    .mapLeft(t -> (t instanceof Exception) ? (Exception) t : new Exception(t));
        }

        static synchronized void cleanupDirectory(@NonNull final File directoryPath) {
            if (directoryPath.exists()) {
                try (Stream<Path> paths = Files.walk(directoryPath.toPath())) {
                    OptionalInt deletedFiles = paths.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .filter(f -> !f.equals(directoryPath)) // Do not delete the base folder but remove all its children contents
                            .map(File::delete)
                            .mapToInt(b -> b ? 1 : 0)
                            .reduce(Integer::sum);
                    log.info("Cleaned up all internal files and folder :{}", deletedFiles);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        static List<File> listFiles(@NonNull final File agentDir,
                                    @NonNull final Predicate<File> filterPattern,
                                    @NonNull final Comparator<File> agentFileComparator,
                                    final int retainCount) {
            List<File> agentFiles = new ArrayList<>();
            if (agentDir.exists()) {
                try (Stream<Path> paths = Files.walk(agentDir.toPath())) {
                    paths.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .filter(f -> !f.equals(agentDir))
                            .filter(File::isFile)
                            .filter(filterPattern) //file -> file.getAbsolutePath().contains("Horizon-Enterprise-Agent");
                            .sorted(agentFileComparator)//Comparator.comparing(File::lastModified).reversed())
                            .skip(retainCount)
                            //.peek(f -> log.info("Listing the agent file :{}",f))
                            .forEach(agentFiles::add);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return agentFiles;
        }

        /**
         * Gather disk apce periodically to check if the disk ahs free space for copying other files
         * @param volumeMount
         * @throws IOException
         */
        static void gatherDiskSpace(@NonNull final File volumeMount) throws IOException {
            Process p = new ProcessBuilder("df", "-k", volumeMount.getAbsolutePath()).start();
            Map<String, Quantity<Dimensionless>> map = new HashMap<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
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
                                // please note you are doing a df -k above and hence this storage is STORAGE.KB
                                map.put(head, KB.toQuantity(vScanner.nextLong()));
                            else if (vScanner.hasNext()) vScanner.next();
                        }
                        vScanner.close();
                    }
                    tScanner.close();
                }
            }
            try {
                p.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Normalize keys for availability between mac and linux
            if (map.containsKey("Avail") && !map.containsKey("Available"))
                map.put("Available", map.get("Avail"));
            spaceMap.putAll(map);
            log.debug("Available space under {} is :{}", volumeMount, spaceMap.get("Available"));
        }
    }

    /**
     * Will need few of these Units for calculating free space and comparison etc
     */
    @RequiredArgsConstructor
    enum STORAGE {
        B(AbstractUnit.ONE.alternate("B")),
        KB(B.unit.multiply(1024).asType(Dimensionless.class)),
        MB(KB.unit.multiply(1024).asType(Dimensionless.class)),
        GB(MB.unit.multiply(1024).asType(Dimensionless.class)),
        TB(GB.unit.multiply(1024).asType(Dimensionless.class));

        final Unit<Dimensionless> unit;

        public long toLong(final @NonNull ComparableQuantity<Dimensionless> quantity) {
            return quantity.to(unit).getValue().longValue();
        }

        public ComparableQuantity<Dimensionless> toQuantity(final long value) {
            return Quantities.getQuantity(value, unit);
        }

    }

    @Getter
    enum AGENTS {
        DEM15(DEM_AGENT +"/10.15.0/2268/agent.tar", 12206080L,
                "ee85b69ee51c0ad07cf4cdd6f93ec0228589e62d1c96bdeb26be5c0b9870f7e6"),
        DEM16(DEM_AGENT +"/10.16.0/2292/agent.tar",12247040L,
                "38bc7498dc45596c8653040d1d236ecb2dd8af0f702c11ac4d28dc4f97c79700"),
        AV18(AV_AGENT + "/4.18.0/2851/Omnissa-AppVolumes-Agent-x64-2506-4.18.0-2851.msi",10895360L,
                "85c75c53505695380836f134284f831276303d6982fc613aa7e234f11352bb2e"),
        AV17(AV_AGENT +"/4.17.0/2117/Omnissa-AppVolumes-Agent-x64-2503-4.17.0-2117.msi",10207232L,
                "4e4f01393ab89201d82a7e13fb7288740f4b5c59131d2b212d5bb4b1a008d567"),
        HZE12( HZE_AGENT + "/8.12.0/23142606/agent.tar",280872960L,
                "db78fe3da791482ff2ecb320434a1f0659c1e4d0c71aed6ffbd29b85cfc65381"),
        HZE16(HZE_AGENT + "/8.16.0/16560454767/agent.tar",243271680L,
                "c968ba5bfcbac2de741c7e17d890f0d14bfbfd28c8e91215bfcd7c8e6ccb2687"),
        HZE15(HZE_AGENT + "/8.15.0/14304348675/agent.tar",242862080L,
                "c36614e224880dba410ccd51c54cc029bbd105dbba5f3aadca57783d0f6a0420"),
        HZE14(HZE_AGENT + "/8.14.0/12994395200/agent.tar",257064960L,
                "e015add0d7216e67335ed3f505331634c27874f747f861460cee6fb7b7d5dcda"),
        HZE13(HZE_AGENT + "/8.13.0/10002333884/agent.tar",255825920L,
                "0a7af36ba4ab21c7a5293ef8475e329ac81c3cbba122186488cb60a56597be17");

        /** URI from where this agent is downloaded.*/
        final URI uri;
        /** An accurate file length to be compared with when the agent file gets downloaded.*/
        final ComparableQuantity<Dimensionless> fileLength;
        /** The checksum algorithm is currently defaulted to SHA-256.*/
        final String checkSumType = "SHA-256";
        /** The checksum (SHA-256) as may be computed by shaSum -A 256 <file>.*/
        final String checkSum;

        AGENTS(@NonNull final String uri, final long fileLengthInBytes, @NonNull final String checkSum256) {
            this.uri = URI.create(HCS_AGENTS_STABLE_PACKAGES + uri);
            this.fileLength = B.toQuantity(fileLengthInBytes);
            this.checkSum = checkSum256;
        }

        @SneakyThrows
        public Either<Exception, ComparableQuantity<Dimensionless>> checkFile(File destinationFolder) {
            final File destFile = new File(destinationFolder, uri.toURL().getFile());
            if (destFile.exists() && B.toQuantity(destFile.length()).isEquivalentTo(fileLength) && isEqualCheckSum(computeHashHexString(destFile))) {
                log.debug("File ALREADY EXISTS!! (with length and checksum matching); so not copying... {}", destFile);
                return Either.right(ZERO);
            }
            return Either.left(new IllegalStateException("Length / Checksum did not match for " + destFile+
                    " Check if this is the intended file??"));
        }

        private String computeHashHexString(final File destFile) {
            return Try.of(() -> {
                var hash = MessageDigest.getInstance(checkSumType).digest(Files.readAllBytes(destFile.toPath()));
                return String.format("%0" + (hash.length * 2) + "x",new BigInteger(1,hash));
            }).get();
        }

        private boolean isEqualCheckSum(@NonNull final String checkSum) {
            return StringUtils.equalsIgnoreCase(this.checkSum, checkSum);
        }

        public static Map<URI, AGENTS> getUriToAgentsMap() {
            return Arrays.stream(values()).collect(Collectors.toMap(AGENTS::getUri, Function.identity()));
        }

        public static URI[] getUris() {
            return Arrays.stream(values()).map(AGENTS::getUri).toArray(URI[]::new);
        }
    }
}
