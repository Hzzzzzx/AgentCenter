package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OpenCodeTextEncodingTest {

    @Test
    void decodeProcessOutputPreservesUtf8() {
        String text = "搜索结果：类文件 ✓";

        assertEquals(text, OpenCodeTextEncoding.decodeProcessOutput(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void decodeProcessOutputFallsBackToGb18030ForWindowsConsoleOutput() {
        String text = "搜索结果：类文件";

        assertEquals(text, OpenCodeTextEncoding.decodeProcessOutput(text.getBytes(Charset.forName("GB18030"))));
    }

    @Test
    void configureUtf8EnvironmentAddsRuntimeEncodingHints() {
        Map<String, String> environment = new HashMap<>();

        OpenCodeTextEncoding.configureUtf8Environment(environment, true);

        assertEquals("utf-8", environment.get("PYTHONIOENCODING"));
        assertEquals("1", environment.get("PYTHONUTF8"));
        assertEquals("65001", environment.get("CHCP"));
        assertTrue(environment.get("JAVA_TOOL_OPTIONS").contains("-Dfile.encoding=UTF-8"));
        assertTrue(environment.get("JAVA_TOOL_OPTIONS").contains("-Dsun.stdout.encoding=UTF-8"));
        assertTrue(environment.get("JAVA_TOOL_OPTIONS").contains("-Dsun.stderr.encoding=UTF-8"));
    }
}
