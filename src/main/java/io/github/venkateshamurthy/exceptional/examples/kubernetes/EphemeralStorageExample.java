package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Config;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.venkateshamurthy.exceptional.examples.kubernetes.FileUtils.listFiles;

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
    private static final Map<URI, Agents> inputMap = Agents.getUriToAgentsMap();
    private final AgentDownloader agentDownloader;

    public static void main(String[] args) throws IOException, ApiException {
        final Duration timeOut = Duration.ofMinutes(5);
        var ephemeralStorageAgentCopier = EphemeralStorageExample.builder();
        final String kubeSvcHost = System.getenv("KUBERNETES_SERVICE_HOST");
        URI[] uris = Arrays.stream(Agents.values()).map(Agents::getUri).toArray(URI[]::new);
        File targetFolder;
        if (StringUtils.isNotBlank(kubeSvcHost)) {
            targetFolder = new File("/agent");
            ephemeralStorageAgentCopier
                    .agentDownloader(new AgentDownloader(timeOut, Storage.mb(245),new AtomicReference<>(targetFolder )))
                    .build().doCopyWithinKubernetes(uris);
        } else {
            log.info("No it is not running in kubernetes..its a direct machine on which this program runs");
            targetFolder = new File("/tmp/agent");
            ephemeralStorageAgentCopier
                    .agentDownloader(new AgentDownloader(timeOut, Storage.mb(245),new AtomicReference<>(targetFolder ) ))
                    .build().doCopy( uris);
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
        log.info("Running on a kubernetes ...to store at emptyDir at:{}", agentDownloader.getDestinationFolder());
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
}
