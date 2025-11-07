package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.venkateshamurthy.exceptional.RxTry;
import io.vavr.control.Either;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.venkateshamurthy.exceptional.examples.kubernetes.StoreUnit.B;

/**
 * All agents to be downloaded
 */
@Slf4j
@Getter
@ExtensionMethod(RxTry.class)
public enum Agents {
    /** DEM 10.15.*/
    DEM15( "DEM-Agent/10.15.0/2268/agent.tar", 12206080L,
            "ee85b69ee51c0ad07cf4cdd6f93ec0228589e62d1c96bdeb26be5c0b9870f7e6"),
    /** DEM 10.16.*/
    DEM16( "DEM-Agent/10.16.0/2292/agent.tar", 12247040L,
            "38bc7498dc45596c8653040d1d236ecb2dd8af0f702c11ac4d28dc4f97c79700"),
    /** App-Volumes 4.18.*/
    AV18( "App-Volumes-Agent/4.18.0/2851/Omnissa-AppVolumes-Agent-x64-2506-4.18.0-2851.msi", 10895360L,
            "85c75c53505695380836f134284f831276303d6982fc613aa7e234f11352bb2e"),
    /** App-Volumes 4.17.*/
    AV17( "App-Volumes-Agent/4.17.0/2117/Omnissa-AppVolumes-Agent-x64-2503-4.17.0-2117.msi", 10207232L,
            "4e4f01393ab89201d82a7e13fb7288740f4b5c59131d2b212d5bb4b1a008d567"),
    /** HZE 8.12.*/
    HZE12( "Horizon-Enterprise-Agent/8.12.0/23142606/agent.tar", 280872960L,
            "db78fe3da791482ff2ecb320434a1f0659c1e4d0c71aed6ffbd29b85cfc65381"),
    /** HZE 8.16.*/
    HZE16( "Horizon-Enterprise-Agent/8.16.0/16560454767/agent.tar", 243271680L,
            "c968ba5bfcbac2de741c7e17d890f0d14bfbfd28c8e91215bfcd7c8e6ccb2687"),
    /** HZE 8.15.*/
    HZE15( "Horizon-Enterprise-Agent/8.15.0/14304348675/agent.tar", 242862080L,
            "c36614e224880dba410ccd51c54cc029bbd105dbba5f3aadca57783d0f6a0420"),
    /** HZE 8.14.*/
    HZE14( "Horizon-Enterprise-Agent/8.14.0/12994395200/agent.tar", 257064960L,
            "e015add0d7216e67335ed3f505331634c27874f747f861460cee6fb7b7d5dcda"),
    /** HZE 8.14.*/
    HZE13( "Horizon-Enterprise-Agent/8.13.0/10002333884/agent.tar", 255825920L,
            "0a7af36ba4ab21c7a5293ef8475e329ac81c3cbba122186488cb60a56597be17");

    /** HCS_AGENTS_STABLE_PACKAGES prefix url.*/
    public static final String HCS_AGENTS_STABLE_PACKAGES = "https://softwareupdate.omnissa.com/hcs-agents-stable/packages/";

    /** URI from where this agent is downloaded.*/
    final URI uri;
    /** An accurate file size to be compared with when the agent file gets downloaded.*/
    final Storage fileSize;
    /** The checksum algorithm is currently defaulted to SHA-256.*/
    final String checkSumType = "SHA-256";
    /** The checksum (SHA-256) as may be computed by shaSum -A 256 <file>.*/
    final String checkSum;

    private final CheckedFunction<File, String> hexComputer = destFile -> {
        var hash = MessageDigest.getInstance(checkSumType).digest(Files.readAllBytes(destFile.toPath()));
        return String.format("%0" + (hash.length * 2) + "x", new BigInteger(1, hash));
    };

    /**
     * Constructor
     * @param uri the file download uri
     * @param fileLengthInBytes file length expressed in bytes
     * @param checkSum256 a SHA256 checksum to the file
     */
    Agents(@NonNull final String uri, final long fileLengthInBytes, @NonNull final String checkSum256) {
        this.uri = URI.create(HCS_AGENTS_STABLE_PACKAGES + uri);
        this.fileSize = B.toStorage(fileLengthInBytes);
        this.checkSum = checkSum256;
    }

    /**
     * Checks the file integrity.
     * @param destinationFolder to the place where file needs to be downloaded
     * @return an Either with exception or the {@link Storage}
     */
    @SneakyThrows
    public Either<Exception, Storage> checkFile(@NonNull final File destinationFolder) {
        final File destFile = new File(destinationFolder, uri.toURL().getFile());
        if (destFile.exists() &&
                B.toStorage(destFile.length()).isEquivalentTo(fileSize) &&
                isEqualCheckSum(hexComputer.tryWrap(destFile).get())) {
            log.debug("File is present (with length and checksum matching); so not copying... {}", destFile);
            return Either.right(Storage.ZERO);
        }
        return Either.left(new IllegalStateException("Length / Checksum did not match for " + destFile +
                " Check if this is the intended file??"));
    }

    /** An equal check for a passed in sum.*/
    private boolean isEqualCheckSum(@NonNull final String checkSum) {
        return checkSum.equalsIgnoreCase(this.checkSum);
    }

    /**
     * Returns a map of URI to Agents.
     * @return map
     */
    public static Map<URI, Agents> getUriToAgentsMap() {
        return Arrays.stream(values()).collect(Collectors.toMap(Agents::getUri, Function.identity()));
    }

    /**
     * An array of URIs to iterate through while downloading.
     * @return URI[] corresponding to agent
     */
    public static URI[] getUris() {
        return Arrays.stream(values()).map(Agents::getUri).toArray(URI[]::new);
    }
}
