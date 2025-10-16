package com.yonagi.ocean.backup.handler.impl;

import com.yonagi.ocean.backup.NacosBackupWriter;
import com.yonagi.ocean.utils.NacosConfigLoader;
import com.yonagi.ocean.backup.handler.ConfigBackupHandler;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 10:09
 */
public class PropertiesConfigBackupHandler implements ConfigBackupHandler {

    @Override
    public boolean supports(String type) {
        return "properties".equalsIgnoreCase(type);
    }

    @Override
    public Object load(String dataId, String group, long timeoutMs) {
        return NacosConfigLoader.getPropertiesConfig(dataId, group, timeoutMs);
    }

    @Override
    public void save(Object config, String backupPath) throws IOException {
        NacosBackupWriter.writeBackup((Properties) config, backupPath);
    }
}
