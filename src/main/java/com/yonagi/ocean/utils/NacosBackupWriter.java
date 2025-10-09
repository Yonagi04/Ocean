package com.yonagi.ocean.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 10:20
 */
public class NacosBackupWriter {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void writeBackup(Object config, String contentType, String backupPath) throws IOException {
        if (contentType == null) {
            contentType = "txt";
        }
        switch (contentType.toLowerCase()) {
            case "json":
                if (config instanceof ArrayNode) {
                    writeBackup((ArrayNode) config, backupPath);
                } else {
                    writeTextBackup(config.toString(), backupPath);
                }
                break;
            case "properties":
                if (config instanceof Properties) {
                    writeBackup((Properties) config, backupPath);
                } else {
                    writeTextBackup(config.toString(), backupPath);
                }
                break;
            case "yaml":
            case "yml":
                writeBackup((Yaml) new Yaml(), config, backupPath);
                break;
            default:
                writeTextBackup(config.toString(), backupPath);
                break;
        }
    }

    private static File resolveBackupFile(String backupPath) throws IOException {
        if (backupPath == null || backupPath.isEmpty()) {
            throw new IllegalArgumentException("Backup path cannot be null or empty");
        }
        File file = new File(backupPath);
        if (!file.isAbsolute()) {
            file = new File(System.getProperty("user.dir"), backupPath);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean created = parent.mkdirs();
            if (!created) {
                throw new IOException("Failed to create directories for backup path: " + parent.getAbsolutePath());
            }
        }
        return file;
    }

    public static void writeBackup(ArrayNode jsonConfig, String backupPath) throws IOException {
        File file = resolveBackupFile(backupPath);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonConfig));
        }
    }



    public static void writeBackup(Properties propertiesConfig, String backupPath) throws IOException {
        File file = resolveBackupFile(backupPath);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            propertiesConfig.store(outputStream, "Nacos Backup");
        }
    }

    public static void writeBackup(Yaml yaml, Object yamlConfig, String backupPath) throws IOException {
        File file = resolveBackupFile(backupPath);
        try (FileWriter writer = new FileWriter(file)) {
            yaml.dump(yamlConfig, writer);
        }
    }

    public static void writeTextBackup(String textConfig, String backupPath) throws IOException {
        File file = resolveBackupFile(backupPath);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(textConfig);
        }
    }
}
