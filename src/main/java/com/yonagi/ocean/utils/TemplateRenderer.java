package com.yonagi.ocean.utils;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.StringWriter;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 16:17
 */
public class TemplateRenderer {

    private static final PebbleEngine engine;

    static {
        ClasspathLoader loader = new ClasspathLoader();
        loader.setPrefix("templates");
        loader.setSuffix(".peb");
        engine = new PebbleEngine.Builder()
                .loader(loader)
                .autoEscaping(true)
                .build();
    }

    public static String render(String templateName, Map<String, Object> context) throws Exception {
        PebbleTemplate template = engine.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.evaluate(writer, context);
        return writer.toString();
    }
}
