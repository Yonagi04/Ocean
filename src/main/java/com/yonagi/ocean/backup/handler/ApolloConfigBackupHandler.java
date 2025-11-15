package com.yonagi.ocean.backup.handler;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/14 20:24
 */
public interface ApolloConfigBackupHandler {
    boolean supports(String type);

    Object load(String namespace);

    void save(Object config, String backupPath) throws IOException;
}
