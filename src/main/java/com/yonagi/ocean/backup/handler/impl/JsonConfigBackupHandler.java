package com.yonagi.ocean.backup.handler.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yonagi.ocean.backup.NacosBackupWriter;
import com.yonagi.ocean.utils.NacosConfigLoader;
import com.yonagi.ocean.backup.handler.ConfigBackupHandler;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 10:07
 */
public class JsonConfigBackupHandler implements ConfigBackupHandler {

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
        NacosBackupWriter.writeBackup((ArrayNode) config, backupPath);
    }
}
