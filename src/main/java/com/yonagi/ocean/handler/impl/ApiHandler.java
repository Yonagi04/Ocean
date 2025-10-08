package com.yonagi.ocean.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.handler.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 11:53
 */
public class ApiHandler implements RequestHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final XmlMapper xmlMapper = new XmlMapper();

    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);

    private final String webRoot;

    public ApiHandler(String webRoot) {
        this.webRoot = webRoot;
    }

    /**
     * Form-data字段表示类
     */
    public static class FormField {
        private String name;
        private String value;
        private String filename;
        private String contentType;
        private boolean isFile;

        public FormField(String name, String value) {
            this.name = name;
            this.value = value;
            this.isFile = false;
        }

        public FormField(String name, String value, String filename, String contentType) {
            this.name = name;
            this.value = value;
            this.filename = filename;
            this.contentType = contentType;
            this.isFile = true;
        }

        // Getters
        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        public boolean isFile() {
            return isFile;
        }

        @Override
        public String toString() {
            if (isFile) {
                return String.format("FormField{name='%s', filename='%s', contentType='%s', isFile=true}",
                        name, filename, contentType);
            } else {
                return String.format("FormField{name='%s', value='%s', isFile=false}", name, value);
            }
        }
    }

    @Override
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        handle(request, output, true); // Default to keep-alive
    }
    
    @Override
    public void handle(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        String contentType = request.getHeaders().getOrDefault("content-type", "");
        Map<String, Object> responseData = new HashMap<>();
        String charset = "UTF-8";
        String mimeType = contentType.split(";")[0].trim();
        if (contentType.contains("charset=")) {
            charset = contentType.split("charset=")[1].trim();
        }

        // 使用策略映射替代 if-else
        ContentProcessor processor = processors.get(mimeType);
        if (processor == null) {
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .statusCode(415)
                    .statusText("Unsupported Media Type")
                    .contentType("text/plain")
                    .body("Unsupported Media Type".getBytes())
                    .build();
            response.write(output, keepAlive);
            output.flush();
            log.warn("Client sent unsupported Content-Type: {}", contentType);
            return;
        }

        try {
            processor.process(request, contentType, charset, responseData);
        } catch (Exception e) {
            String msgPrefix = "application/xml".equals(mimeType) ? "XML parsing error: " : "Multipart form data parsing error: ";
            log.error("{}{}", msgPrefix, e.getMessage(), e);
            HttpResponse errorResponse = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .statusCode(400)
                    .statusText("Bad Request")
                    .contentType("text/plain")
                    .body((msgPrefix + e.getMessage()).getBytes())
                    .build();
            errorResponse.write(output, keepAlive);
            output.flush();
            return;
        }

        String responseBody = objectMapper.writeValueAsString(responseData);
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .statusCode(201)
                .statusText("Created")
                .contentType("application/json")
                .body(responseBody.getBytes())
                .build();
        response.write(output, keepAlive);
        output.flush();
    }

    @FunctionalInterface
    private interface ContentProcessor {
        void process(HttpRequest request, String contentType, String charset, Map<String, Object> responseData) throws Exception;
    }

    private final Map<String, ContentProcessor> processors = initProcessors();

    private Map<String, ContentProcessor> initProcessors() {
        Map<String, ContentProcessor> map = new HashMap<>();

        map.put("application/x-www-form-urlencoded", (request, contentType, charset, responseData) -> {
            Map<String, String> formParams = parseFormData(request.getBody(), charset);
            responseData.put("status", "ok");
            responseData.put("data", formParams);
        });

        map.put("application/json", (request, contentType, charset, responseData) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonData = objectMapper.readValue(request.getBody(), Map.class);
            responseData.put("status", "ok");
            responseData.put("data", jsonData);
        });

        map.put("application/xml", (request, contentType, charset, responseData) -> {
            Map<String, Object> xmlData = parseXmlData(request.getBody(), charset);
            responseData.put("status", "ok");
            responseData.put("data", xmlData);
        });

        map.put("multipart/form-data", (request, contentType, charset, responseData) -> {
            List<FormField> formFields = parseMultipartFormData(request.getBody(), contentType, charset);
            Map<String, Object> formData = new HashMap<>();
            for (FormField field : formFields) {
                if (field.isFile()) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("filename", field.getFilename());
                    fileInfo.put("contentType", field.getContentType());
                    fileInfo.put("size", field.getValue().length());
                    formData.put(field.getName(), fileInfo);
                } else {
                    formData.put(field.getName(), field.getValue());
                }
            }
            responseData.put("status", "ok");
            responseData.put("data", formData);
            responseData.put("fields", formFields.size());
        });

        return map;
    }

    private Map<String, String> parseFormData(byte[] body, String charset) {
        String formData = new String(body);
        Map<String, String> result = new HashMap<>();
        if (formData == null || formData.isEmpty()) {
            return result;
        }
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            try {
                String[] kv = pair.split("=", 2);
                String key = URLDecoder.decode(kv[0], charset);
                String value = kv.length > 1 ? URLDecoder.decode(kv[1], charset) : "";
                result.put(key, value);
            } catch (UnsupportedEncodingException e) {
                log.error("Error decoding form data: {}, client sent charset type: {}", e.getMessage(), charset, e);
            }
        }
        return result;
    }

    private Map<String, Object> parseXmlData(byte[] body, String charset) throws Exception {
        String xmlContent = new String(body, charset);
        log.debug("Parsing XML content: {}", xmlContent);
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> xmlMap = xmlMapper.readValue(xmlContent, Map.class);
        return xmlMap;
    }

    private List<FormField> parseMultipartFormData(byte[] body, String contentType, String charset) throws IOException {
        List<FormField> fields = new ArrayList<>();

        // 从Content-Type中提取boundary
        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            throw new IOException("Missing boundary in multipart/form-data");
        }

        String boundaryDelimiter = "--" + boundary;
        String bodyString = new String(body, charset);
        log.debug("Parsing multipart form data with boundary: {}", boundary);
        log.debug("Body length: {}", body.length);

        // 按边界分割数据
        String[] parts = bodyString.split(Pattern.quote(boundaryDelimiter));

        for (String part : parts) {
            if (part.trim().isEmpty() || part.equals("--")) {
                continue; // 跳过空部分和结束边界
            }

            try {
                FormField field = parseFormField(part, charset);
                if (field != null) {
                    fields.add(field);
                }
            } catch (Exception e) {
                log.warn("Failed to parse form field: {}", e.getMessage());
            }
        }

        return fields;
    }

    private String extractBoundary(String contentType) {
        Pattern boundaryPattern = Pattern.compile("boundary=([^;\\s]+)");
        Matcher matcher = boundaryPattern.matcher(contentType);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private FormField parseFormField(String part, String charset) throws IOException {
        // 查找头部和数据的分隔符
        int headerEndIndex = part.indexOf("\r\n\r\n");
        if (headerEndIndex == -1) {
            headerEndIndex = part.indexOf("\n\n");
            if (headerEndIndex == -1) {
                return null;
            }
            headerEndIndex += 2;
        } else {
            headerEndIndex += 4;
        }

        String headers = part.substring(0, headerEndIndex);
        String data = part.substring(headerEndIndex);

        // 移除末尾的换行符
        data = data.replaceAll("\r?\n$", "");

        // 解析Content-Disposition头部
        String name = extractFromHeader(headers, "name");
        String filename = extractFromHeader(headers, "filename");
        String contentType = extractFromHeader(headers, "Content-Type");

        if (name == null) {
            return null;
        }

        if (filename != null) {
            // 文件字段
            return new FormField(name, data, filename, contentType);
        } else {
            // 普通字段
            return new FormField(name, data);
        }
    }

    private String extractFromHeader(String headers, String attributeName) {
        Pattern pattern = Pattern.compile(attributeName + "=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 尝试不带引号的格式
        pattern = Pattern.compile(attributeName + "=([^;\\s]+)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
