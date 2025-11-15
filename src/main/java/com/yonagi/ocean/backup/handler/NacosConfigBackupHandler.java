package com.yonagi.ocean.backup.handler;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 10:06
 */
public interface NacosConfigBackupHandler {
    boolean supports(String type);

    Object load(String dataId, String group, long timeoutMs);

    void save(Object config, String backupPath) throws IOException;
}
