package com.yonagi.ocean.core.loadbalance;

import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.loadbalance.config.enums.HealthCheckMode;
import com.yonagi.ocean.core.loadbalance.config.enums.Strategy;
import com.yonagi.ocean.core.loadbalance.impl.WeightRandomLoadBalancer;
import com.yonagi.ocean.core.loadbalance.utils.GrayReleaseUtils;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:51
 */
public abstract class AbstractLoadBalancer implements LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(AbstractLoadBalancer.class);

    protected final LoadBalancerConfig config;

    protected final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AbstractLoadBalancer(LoadBalancerConfig config) {
        this.config = config;
    }

    protected List<Upstream> selectHealthyUpstreams(List<Upstream> upstreams) {
        return upstreams.stream()
                .filter(Upstream::isHealthy)
                .collect(Collectors.toList());
    }

    protected List<Upstream> getTargetUpstreams(HttpRequest request) {
        LoadBalancerConfig lbConfig = config;
        boolean useCanary = false;
        String sessionId = request.getAttribute().getSessionId();
        if (sessionId != null && lbConfig.getCanaryUpstreams() != null) {
            useCanary = GrayReleaseUtils.isGrayRelease(request, config.getCanaryPercent());
            if (useCanary) {
                log.debug("Using canary upstreams for session ID: {}", sessionId);
            }
        }
        return useCanary ? lbConfig.getCanaryUpstreams() : lbConfig.getUpstreams();
    }

    @Override
    public void reportFailure(String url, long failureTime) {
        if (config.getHealthCheckMode() != HealthCheckMode.PASSIVE_CHECK) {
            return;
        }
        URI uri = URI.create(url);
        String failedHost = uri.getScheme() + "://" + uri.getAuthority();

        findUpstreamByHost(failedHost).ifPresent(upstream -> {
            if (!upstream.getRecovering().compareAndSet(false, true)) {
                return;
            }

            upstream.setHealthy(false);
            log.error("PASSIVE CHECK: Upstream {} marked UNHEALTHY due to request failure.", url);

            long recoveryIntervalMillis = Long.parseLong(LocalConfigLoader.getProperty("server.load_balance.recovery_interval_millis", "30000L"));
            scheduler.schedule(() -> {
                try {
                    boolean ok = HttpClient.checkHealth(upstream);
                    if (ok) {
                        upstream.setHealthy(true);
                        upstream.getCurrentWeight().set(0.0d);
                        log.info("PASSIVE CHECK: Upstream {} automatically recovered.", failedHost);
                    } else {
                        upstream.getRecovering().set(false);
                        upstream.getRetryCount().getAndIncrement();
                        if (upstream.getRetryCount().get() == Integer.parseInt(LocalConfigLoader.getProperty("server.load_balance.failure_threshold", "3"))) {
                            log.warn("PASSIVE CHECK: Upstream {} reached max retry attempts, will not retry further until next failure.", failedHost);
                            return;
                        }
                        log.warn("PASSIVE CHECK: Upstream {} recovery failed, retrying...", failedHost);
                        reportFailure(url, failureTime);
                    }
                } finally {
                    upstream.getRecovering().set(false);
                }

            }, recoveryIntervalMillis, TimeUnit.MILLISECONDS);
        });
    }

    private Optional<Upstream> findUpstreamByHost(String host) {
        Stream<Upstream> allUpstreams = Stream.concat(
                config.getUpstreams().stream(),
                config.getCanaryUpstreams().stream()
        );
        return allUpstreams.filter(u -> u.getUrl().equals(host)).findFirst();
    }
}
