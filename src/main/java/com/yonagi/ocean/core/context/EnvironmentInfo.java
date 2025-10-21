package com.yonagi.ocean.core.context;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/21 09:47
 */
public class EnvironmentInfo {

    private final Instant startTime;
    private final String appVersion;
    private final String javaVersion;
    private final String osName;

    public EnvironmentInfo(String appVersion) {
        this.startTime = Instant.now();
        this.appVersion = appVersion != null ? appVersion : "Unknown";
        this.javaVersion = System.getProperty("java.version");
        this.osName = System.getProperty("os.name");
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getOsName() {
        return osName;
    }

    public String getUptime() {
        Duration duration = Duration.between(startTime, Instant.now());
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%d day(s) %d:%d:%d", days, hours, minutes, seconds);
    }
}
