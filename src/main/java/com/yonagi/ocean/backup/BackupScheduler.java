package com.yonagi.ocean.backup;

import com.yonagi.ocean.backup.handler.ApolloConfigBackupHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.backup.handler.NacosConfigBackupHandler;
import com.yonagi.ocean.backup.handler.impl.nacos.JsonConfigBackupHandler;
import com.yonagi.ocean.backup.handler.impl.nacos.PropertiesConfigBackupHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 09:41
 */
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private static final Map<String, ScheduledFuture<?>> TASKS = new ConcurrentHashMap<>();

    private static final int POOL_SIZE = Math.max(Integer.parseInt(LocalConfigLoader.getProperty("configuration_management.backup.thread_pool_size", "5")), 1);

    private static final ScheduledExecutorService backupService = Executors.newScheduledThreadPool(POOL_SIZE, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("BackupScheduler");
        return t;
    });

    public static void startNacosTask(String dataId,
                             String group,
                             String backupPath,
                             int intervalSeconds,
                             int timeoutMs) {
        if (dataId == null || dataId.isEmpty() || group == null || group.isEmpty() || backupPath == null || backupPath.isEmpty()) {
            log.error("dataId, group, and backupPath must be provided, backup task not started");
            return;
        }
        if (intervalSeconds <= 0) {
            log.warn("Invalid intervalSeconds: {}, must be > 0", intervalSeconds);
            intervalSeconds = 7200;
        }
        if (timeoutMs <= 0) {
            log.warn("Invalid timeoutMs: {}, must be > 0", timeoutMs);
            timeoutMs = 3000;
        }
        String key = dataId + "-" + group;
        if (TASKS.containsKey(key)) {
            log.info("Nacos backup task already running for {}:{}", dataId, group);
            return;
        }
        List<NacosConfigBackupHandler> handlers = Arrays.asList(
                new JsonConfigBackupHandler(),
                new PropertiesConfigBackupHandler()
        );

        String fileName = backupPath.substring(backupPath.lastIndexOf('/') + 1);
        String contentType = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";
        String resolvedBackupPath = backupPath.replace("${timestamp}", new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));

        int finalTimeoutMs = timeoutMs;
        Runnable task = () -> {
            try {
                NacosConfigBackupHandler handler = handlers.stream()
                        .filter(h -> h.supports(contentType))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No handler found for " + contentType));
                Object config = handler.load(dataId, group, finalTimeoutMs);
                if (config != null) {
                    handler.save(config, resolvedBackupPath);
                    log.info("Nacos config [{}] synced to {}", key, resolvedBackupPath);
                }
            } catch (Exception e) {
                log.error("Failed to sync Nacos config [{}]: {}", key, e.getMessage(), e);
            }
        };

        ScheduledFuture<?> future = backupService.scheduleAtFixedRate(task, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        TASKS.put(key, future);
        log.info("Started Nacos backup task for {} every {} second(s)", key, intervalSeconds);
    }

    public static void stopNacosTask(String dataId, String group) {
        String key = dataId + "-" + group;
        ScheduledFuture<?> future = TASKS.remove(key);
        if (future != null) {
            future.cancel(true);
            log.info("Stopped Nacos backup task for {}", key);
        }
    }

    public static void startApolloTask(String namespace, String backupPath, int intervalSeconds) {
        if (namespace == null || namespace.isEmpty() || backupPath == null || backupPath.isEmpty()) {
            log.error("namespace and backupPath must be provided, backup task not started");
            return;
        }
        if (intervalSeconds <= 0) {
            log.warn("Invalid intervalSeconds: {}, must be > 0", intervalSeconds);
            intervalSeconds = 7200;
        }
        if (TASKS.containsKey(namespace)) {
            log.info("Apollo backup task already running for {}", namespace);
            return;
        }
        List<ApolloConfigBackupHandler> handlers = Arrays.asList(
                new com.yonagi.ocean.backup.handler.impl.apollo.JsonConfigBackupHandler(),
                new com.yonagi.ocean.backup.handler.impl.apollo.PropertiesConfigBackupHandler()
        );
        String fileName = backupPath.substring(backupPath.lastIndexOf('/') + 1);
        String contentType = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";
        String resolvedBackupPath = backupPath.replace("${timestamp}", new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));

        Runnable task = () -> {
            try {
                ApolloConfigBackupHandler handler = handlers.stream()
                        .filter(h -> h.supports(contentType))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No handler found for " + contentType));
                Object config = handler.load(namespace);
                if (config != null) {
                    handler.save(config, resolvedBackupPath);
                    log.info("Apollo config [{}] synced to {}", namespace, resolvedBackupPath);
                }
            } catch (Exception e) {
                log.error("Failed to sync Apollo config [{}]: {}", namespace, e.getMessage());
            }
        };

        ScheduledFuture<?> future = backupService.scheduleAtFixedRate(task, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        TASKS.put(namespace, future);
        log.info("Started Apollo backup task for {} for every {} second(s)", namespace, intervalSeconds);
    }

    public static void stopApolloTask(String namespace) {
        ScheduledFuture<?> future = TASKS.remove(namespace);
        if (future != null) {
            future.cancel(true);
            log.info("Stopped Apollo backup task for {}", namespace);
        }
    }

    public static void shutdownAll() {
        for (ScheduledFuture<?> f : TASKS.values()) {
            f.cancel(true);
        }
        TASKS.clear();
        log.info("All Nacos backup tasks have been shut down");
    }
}
