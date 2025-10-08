package com.yonagi.ocean.core;

import com.yonagi.ocean.config.KeepAliveConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages HTTP Keep-Alive connections
 * 
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description Manages persistent HTTP connections for Keep-Alive functionality
 * @date 2025/01/15
 */
public class ConnectionManager implements Closeable {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);
    
    private final KeepAliveConfig config;
    private final ConcurrentHashMap<Socket, ConnectionInfo> connections;
    private final ScheduledExecutorService cleanupScheduler;
    
    public ConnectionManager(KeepAliveConfig config) {
        this.config = config;
        this.connections = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConnectionManager-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        if (config.isEnabled()) {
            startCleanupTask();
        }
    }
    
    /**
     * Register a new connection for Keep-Alive management
     */
    public void registerConnection(Socket socket) {
        if (!config.isEnabled()) {
            return;
        }
        
        ConnectionInfo info = new ConnectionInfo(socket, config.getTimeoutMillis(), config.getMaxRequests());
        connections.put(socket, info);
        log.debug("Registered Keep-Alive connection: {}", socket.getRemoteSocketAddress());
    }
    
    /**
     * Check if a connection should be kept alive
     */
    public boolean shouldKeepAlive(Socket socket) {
        if (!config.isEnabled()) {
            return false;
        }
        
        ConnectionInfo info = connections.get(socket);
        if (info == null) {
            return false;
        }
        
        return info.canHandleMoreRequests() && !info.isExpired();
    }
    
    /**
     * Record a request processed on this connection
     */
    public void recordRequest(Socket socket) {
        ConnectionInfo info = connections.get(socket);
        if (info != null) {
            info.incrementRequestCount();
        }
    }
    
    /**
     * Remove a connection from management
     */
    public void removeConnection(Socket socket) {
        ConnectionInfo info = connections.remove(socket);
        if (info != null) {
            log.debug("Removed Keep-Alive connection: {}", socket.getRemoteSocketAddress());
        }
    }
    
    /**
     * Get the number of active Keep-Alive connections
     */
    public int getActiveConnectionCount() {
        return connections.size();
    }
    
    private void startCleanupTask() {
        cleanupScheduler.scheduleWithFixedDelay(
            this::cleanupExpiredConnections,
            config.getTimeoutCheckIntervalSeconds(),
            config.getTimeoutCheckIntervalSeconds(),
            TimeUnit.SECONDS
        );
    }
    
    private void cleanupExpiredConnections() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        for (var entry : connections.entrySet()) {
            Socket socket = entry.getKey();
            ConnectionInfo info = entry.getValue();
            
            if (info.isExpired() || !info.canHandleMoreRequests()) {
                try {
                    socket.close();
                    connections.remove(socket);
                    cleaned++;
                    log.debug("Cleaned up expired Keep-Alive connection: {}", socket.getRemoteSocketAddress());
                } catch (IOException e) {
                    log.warn("Error closing expired connection: {}", e.getMessage());
                }
            }
        }
        
        if (cleaned > 0) {
            log.info("Cleaned up {} expired Keep-Alive connections", cleaned);
        }
    }
    
    @Override
    public void close() throws IOException {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Close all active connections
        for (Socket socket : connections.keySet()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.warn("Error closing connection during shutdown: {}", e.getMessage());
            }
        }
        connections.clear();
    }
    
    /**
     * Information about a Keep-Alive connection
     */
    private static class ConnectionInfo {
        private final Socket socket;
        private final long timeoutMillis;
        private final int maxRequests;
        private final long createdAt;
        private long lastActivityAt;
        private int requestCount;
        
        public ConnectionInfo(Socket socket, long timeoutMillis, int maxRequests) {
            this.socket = socket;
            this.timeoutMillis = timeoutMillis;
            this.maxRequests = maxRequests;
            this.createdAt = System.currentTimeMillis();
            this.lastActivityAt = createdAt;
            this.requestCount = 0;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - lastActivityAt > timeoutMillis;
        }
        
        public boolean canHandleMoreRequests() {
            return requestCount < maxRequests;
        }
        
        public void incrementRequestCount() {
            this.requestCount++;
            this.lastActivityAt = System.currentTimeMillis();
        }
        
        public Socket getSocket() {
            return socket;
        }
        
        public long getLastActivityAt() {
            return lastActivityAt;
        }
        
        public int getRequestCount() {
            return requestCount;
        }
    }
}
