package com.yonagi.ocean.backup.handler.impl.nacos;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yonagi.ocean.backup.BackupWriter;
import com.yonagi.ocean.utils.NacosConfigLoader;
import com.yonagi.ocean.backup.handler.NacosConfigBackupHandler;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 10:07
 */
public class JsonConfigBackupHandler implements NacosConfigBackupHandler {

    @Override
    public boolean supports(String type) {
        return "json".equalsIgnoreCase(type);
    }

    @Override
    public Object load(String dataId, String group, long timeoutMs) {
        return NacosConfigLoader.getJsonArrayConfig(dataId, group, timeoutMs);
    }

    @Override
    public void save(Object config, String backupPath) throws IOException {
        BackupWriter.writeBackup((ArrayNode) config, backupPath);
    }
}
