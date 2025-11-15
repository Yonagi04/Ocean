package com.yonagi.ocean.backup.handler.impl.apollo;

import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.yonagi.ocean.backup.BackupWriter;
import com.yonagi.ocean.backup.handler.ApolloConfigBackupHandler;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/14 20:32
 */
public class JsonConfigBackupHandler implements ApolloConfigBackupHandler {

    @Override
    public boolean supports(String type) {
        return "json".equalsIgnoreCase(type);
    }

    @Override
    public Object load(String namespace) {
        return ConfigService.getConfigFile(namespace, ConfigFileFormat.JSON);
    }

    @Override
    public void save(Object config, String backupPath) throws IOException {
        String content = ((ConfigFile) config).getContent();
        if (content == null) {
            return;
        }
        BackupWriter.writeBackup(config, "json", backupPath);
    }
}
