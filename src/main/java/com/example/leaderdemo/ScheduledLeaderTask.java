package com.example.leaderdemo;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This component runs scheduled tasks only on the leader node.
 * Uses Kubernetes ConfigMap for simple leader election.
 */
@Component
public class ScheduledLeaderTask {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledLeaderTask.class);
    private static final String LEADER_CONFIGMAP = "leader-demo-leader";
    private static final String NAMESPACE = "default";
    private static final String LEADER_KEY = "leader";
    private static final int LEASE_DURATION_SECONDS = 30;

    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final CoreV1Api api;
    private final String podName;

    public ScheduledLeaderTask() throws IOException, ApiException {
        // Initialize Kubernetes client
        ApiClient client;
        try {
            // Try in-cluster config first
            client = ClientBuilder.cluster().build();
        } catch (Exception e) {
            // Fall back to kubeconfig
            String kubeConfigPath = System.getenv().getOrDefault("KUBECONFIG", System.getProperty("user.home") + "/.kube/config");
            client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
        }
        client.setHttpClient(client.getHttpClient().newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build());
        Configuration.setDefaultApiClient(client);
        this.api = new CoreV1Api();

        String hostname = System.getenv("HOSTNAME");
        if (hostname == null) {
            hostname = "unknown-pod-" + System.currentTimeMillis();
        }
        this.podName = hostname;
        logger.info("ScheduledLeaderTask initialized for pod: {}", podName);
    }

    public void setLeader(boolean leader) {
        if (this.isLeader.getAndSet(leader) != leader) {
            if (leader) {
                logger.info("💡 I AM THE LEADER: leader-demo (pod: {})", podName);
                System.out.println("💡 I AM THE LEADER: leader-demo (pod: " + podName + ")");
            } else {
                logger.info("❌ Leadership revoked: leader-demo (pod: {})", podName);
                System.out.println("❌ Leadership revoked: leader-demo (pod: " + podName + ")");
            }
        }
    }

    public boolean getLeader() {
        return isLeader.get();
    }

    /**
     * Check leadership every 5 seconds
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void checkLeadership() {
        try {
            boolean currentlyLeader = isCurrentLeader();
            setLeader(currentlyLeader);
        } catch (Exception e) {
            logger.warn("Error checking leadership: {}", e.getMessage());
        }
    }

    private boolean isCurrentLeader() {
        try {
            V1ConfigMap configMap = api.readNamespacedConfigMap(LEADER_CONFIGMAP, NAMESPACE, null, null, null);
            if (configMap != null && configMap.getData() != null) {
                String leaderPod = configMap.getData().get(LEADER_KEY);
                if (podName.equals(leaderPod)) {
                    // Check if our leadership is still valid (not expired)
                    String timestampStr = configMap.getData().get("timestamp");
                    if (timestampStr != null) {
                        try {
                            long timestamp = Long.parseLong(timestampStr);
                            long now = System.currentTimeMillis();
                            if ((now - timestamp) < (LEASE_DURATION_SECONDS * 1000)) {
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            logger.debug("Invalid timestamp in leader configmap");
                        }
                    }
                }
            }
        } catch (ApiException e) {
            if (e.getCode() != 404) { // 404 is expected when configmap doesn't exist
                logger.debug("Error reading leader configmap: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Try to acquire leadership every 10 seconds
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public void tryAcquireLeadership() {
        try {
            V1ConfigMap existingConfigMap = null;
            try {
                existingConfigMap = api.readNamespacedConfigMap(LEADER_CONFIGMAP, NAMESPACE, null, null, null);
            } catch (ApiException e) {
                if (e.getCode() != 404) {
                    throw e;
                }
            }

            if (existingConfigMap == null) {
                // Try to create new leader configmap
                createLeaderConfigMap();
            } else {
                // Check if we can take leadership
                tryTakeLeadership(existingConfigMap);
            }
        } catch (Exception e) {
            logger.debug("Error in leadership acquisition: {}", e.getMessage());
        }
    }

    private void createLeaderConfigMap() {
        try {
            Map<String, String> data = new HashMap<>();
            data.put(LEADER_KEY, podName);
            data.put("timestamp", String.valueOf(System.currentTimeMillis()));

            V1ConfigMap configMap = new V1ConfigMapBuilder()
                .withNewMetadata()
                    .withName(LEADER_CONFIGMAP)
                    .withNamespace(NAMESPACE)
                .endMetadata()
                .withData(data)
                .build();

            api.createNamespacedConfigMap(NAMESPACE, configMap, null, null, null);
            logger.info("Created leader configmap for pod: {}", podName);
        } catch (ApiException e) {
            // Another pod might have created it first
            logger.debug("Failed to create leader configmap: {}", e.getMessage());
        }
    }

    private void tryTakeLeadership(V1ConfigMap existingConfigMap) {
        try {
            String currentLeader = existingConfigMap.getData().get(LEADER_KEY);
            String timestampStr = existingConfigMap.getData().get("timestamp");

            // Check if leadership is expired
            boolean isExpired = false;
            if (timestampStr != null) {
                try {
                    long timestamp = Long.parseLong(timestampStr);
                    long now = System.currentTimeMillis();
                    isExpired = (now - timestamp) > (LEASE_DURATION_SECONDS * 1000);
                } catch (NumberFormatException e) {
                    isExpired = true; // Invalid timestamp, consider expired
                }
            }

            if (isExpired || podName.equals(currentLeader)) {
                // Update the configmap with our leadership
                Map<String, String> newData = new HashMap<>();
                newData.put(LEADER_KEY, podName);
                newData.put("timestamp", String.valueOf(System.currentTimeMillis()));

                V1ConfigMap updatedConfigMap = new V1ConfigMapBuilder(existingConfigMap)
                    .withData(newData)
                    .build();

                api.replaceNamespacedConfigMap(LEADER_CONFIGMAP, NAMESPACE, updatedConfigMap, null, null, null);
                logger.info("Acquired/renewed leadership for pod: {}", podName);
            }
        } catch (ApiException e) {
            // Another pod might have updated it first
            logger.debug("Failed to update leader configmap: {}", e.getMessage());
        }
    }

    /**
     * This task runs every 10 seconds, but only on the leader.
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 20000)
    public void leaderOnlyTask() {
        if (isLeader.get()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("[" + timestamp + "] 🎯 LEADER TASK EXECUTED on pod: " + podName);
            logger.info("[{}] 🎯 LEADER TASK EXECUTED on pod: {}", timestamp, podName);
            // Add your business logic here
        }
    }

    /**
     * Example of a longer running task (every minute)
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void leaderMinutelyTask() {
        if (isLeader.get()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("[" + timestamp + "] 📊 MINUTELY LEADER TASK on pod: " + podName);
            logger.info("[{}] 📊 MINUTELY LEADER TASK on pod: {}", timestamp, podName);
            // Add your business logic here
        }
    }
}
