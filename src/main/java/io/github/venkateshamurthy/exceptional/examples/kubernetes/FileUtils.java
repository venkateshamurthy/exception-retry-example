package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.venkateshamurthy.exceptional.Eithers;
import io.github.venkateshamurthy.exceptional.RxTry;

import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.RxFunction.toCheckedFunction;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.Storage.UNIT.B;
import static io.github.venkateshamurthy.exceptional.examples.kubernetes.Storage.UNIT.KB;

@Slf4j
@ExtensionMethod({RxTry.class, Eithers.class})
public class FileUtils {
    private static final CheckedFunction<Path, Path> checkedCreateDirectories = toCheckedFunction(Files::createDirectories);

    public static Either<Exception, Storage> copy(@NonNull final URL in,
                                                  @NonNull final File out,
                                                  @NonNull final Storage bufferSize,
                                                  @NonNull final Duration timeout) {

        var either = checkedCreateDirectories.either(out.toPath().getParent(),
                ()->new Exception("Directories could not be created for "+out));
        if (either.isLeft()) return either.map(ignore->Storage.ZERO);

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
                        bytes = fileChannel.transferFrom(urlIn, position.get(), bufferSize.getBytes());
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
                    return B.of(position.get()); //position always gives in bytes
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
                OptionalInt deletedFiles = paths
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .filter(f -> !f.equals(directoryPath)) // Do not delete the base folder but remove all its children contents
                        .map(File::delete)
                        .mapToInt(BooleanUtils::toInteger)
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
     * Gather disk space periodically to check if the disk ahs free space for copying other files
     *
     * @param volumeMount shared volume/drive
     */
    @SneakyThrows
    static Map<String, Storage> gatherDiskSpace(@NonNull final File volumeMount) {
        Process p = new ProcessBuilder("df", "-k", volumeMount.getAbsolutePath()).start();
        Map<String, Storage> map = new HashMap<>();
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
                            map.put(head, KB.of(vScanner.nextLong()));
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
        log.debug("Available space under {} is :{}", volumeMount, map.get("Available"));
        return map;
    }
}