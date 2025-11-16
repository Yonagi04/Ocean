package com.yonagi.ocean.backup.handler.impl.apollo;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.yonagi.ocean.backup.BackupWriter;
import com.yonagi.ocean.backup.handler.ApolloConfigBackupHandler;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/14 20:36
 */
public class PropertiesConfigBackupHandler implements ApolloConfigBackupHandler {

    @Override
    public void save(Object config, String backupPath) throws IOException {
        Config apolloConfig = (Config) config;
        StringBuilder contentBuilder = new StringBuilder();
        for (String key : apolloConfig.getPropertyNames()) {
            String value = apolloConfig.getProperty(key, "");
            contentBuilder.append(key).append("=").append(value).append("\n");
        }
        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            BackupWriter.writeBackup(apolloConfig, "properties", backupPath);
        }
    }

    @Override
    public Object load(String namespace) {
        return ConfigService.getConfig(namespace);
    }

    @Override
    public boolean supports(String type) {
        return "properties".equalsIgnoreCase(type);
    }
}
