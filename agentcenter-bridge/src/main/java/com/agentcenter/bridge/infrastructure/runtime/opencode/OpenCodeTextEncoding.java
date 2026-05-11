package com.agentcenter.bridge.infrastructure.runtime.opencode;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class OpenCodeTextEncoding {

    public static final Charset WIRE_CHARSET = StandardCharsets.UTF_8;

    private static final String JAVA_STDOUT_UTF8 = "-Dfile.encoding=UTF-8";
    private static final String JAVA_SUN_STDOUT_UTF8 = "-Dsun.stdout.encoding=UTF-8";
    private static final String JAVA_SUN_STDERR_UTF8 = "-Dsun.stderr.encoding=UTF-8";

    private OpenCodeTextEncoding() {
    }

    public static String decodeProcessOutput(byte[] bytes) {
        String utf8 = decodeStrict(bytes, WIRE_CHARSET);
        if (utf8 != null) {
            return utf8;
        }

        for (Charset charset : fallbackCharsets()) {
            String decoded = decodeStrict(bytes, charset);
            if (decoded != null) {
                return decoded;
            }
        }

        return WIRE_CHARSET.decode(ByteBuffer.wrap(bytes)).toString();
    }

    public static void configureUtf8Environment(Map<String, String> environment, boolean windows) {
        environment.putIfAbsent("PYTHONIOENCODING", "utf-8");
        environment.putIfAbsent("PYTHONUTF8", "1");
        environment.put("JAVA_TOOL_OPTIONS", appendJavaToolOptions(environment.get("JAVA_TOOL_OPTIONS")));
        if (windows) {
            environment.putIfAbsent("CHCP", "65001");
        } else {
            environment.putIfAbsent("LANG", "C.UTF-8");
            environment.putIfAbsent("LC_ALL", "C.UTF-8");
        }
    }

    private static String appendJavaToolOptions(String current) {
        String value = current == null ? "" : current.trim();
        for (String option : Set.of(JAVA_STDOUT_UTF8, JAVA_SUN_STDOUT_UTF8, JAVA_SUN_STDERR_UTF8)) {
            if (!value.contains(option)) {
                value = value.isBlank() ? option : value + " " + option;
            }
        }
        return value;
    }

    private static Set<Charset> fallbackCharsets() {
        Set<Charset> charsets = new LinkedHashSet<>();
        charsets.add(Charset.defaultCharset());
        addIfSupported(charsets, "GB18030");
        addIfSupported(charsets, "GBK");
        addIfSupported(charsets, "windows-1252");
        return charsets;
    }

    private static void addIfSupported(Set<Charset> charsets, String name) {
        if (Charset.isSupported(name)) {
            charsets.add(Charset.forName(name));
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        try {
            CharBuffer decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
