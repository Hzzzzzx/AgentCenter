package com.agentcenter.bridge.infrastructure.runtime.opencode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeProcessManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveCommand_windowsFindsNpmCmdShimOnPath() throws Exception {
        Path shim = tempDir.resolve("opencode.cmd");
        Files.writeString(shim, "@echo off\r\n");

        String resolved = OpenCodeProcessManager.resolveCommand(
                "opencode", true, tempDir.toString(), ".cmd;.exe");

        assertEquals(shim.toString(), resolved);
    }

    @Test
    void resolveCommand_windowsAcceptsConfiguredCmdPath() throws Exception {
        Path shim = tempDir.resolve("opencode.cmd");
        Files.writeString(shim, "@echo off\r\n");

        String resolved = OpenCodeProcessManager.resolveCommand(
                shim.toString(), true, "", ".cmd;.exe");

        assertEquals(shim.toString(), resolved);
    }

    @Test
    void resolveCommand_unixRequiresExecutableOnPath() throws Exception {
        Path binary = tempDir.resolve("opencode");
        Files.writeString(binary, "#!/usr/bin/env sh\n");
        binary.toFile().setExecutable(true);

        String resolved = OpenCodeProcessManager.resolveCommand(
                "opencode", false, tempDir.toString(), null);

        assertEquals(binary.toString(), resolved);
    }

    @Test
    void resolveCommand_unixRejectsNonExecutableCandidate() throws Exception {
        Path binary = tempDir.resolve("opencode");
        Files.writeString(binary, "#!/usr/bin/env sh\n");
        binary.toFile().setExecutable(false);

        String resolved = OpenCodeProcessManager.resolveCommand(
                "opencode", false, tempDir.toString(), null);

        assertNull(resolved);
    }

    @Test
    void buildCommandLine_windowsWrapsCmdShimWithCommandInterpreter() {
        List<String> commandLine = OpenCodeProcessManager.buildCommandLine(
                "C:\\tools\\opencode.cmd",
                List.of("serve", "--port", "4097"),
                true);

        assertIterableEquals(
                List.of("cmd.exe", "/d", "/s", "/c",
                        "chcp 65001 >NUL && \"C:\\tools\\opencode.cmd\" \"serve\" \"--port\" \"4097\""),
                commandLine);
    }

    @Test
    void buildCommandLine_windowsRunsExeDirectly() {
        List<String> commandLine = OpenCodeProcessManager.buildCommandLine(
                "C:\\tools\\opencode.exe",
                List.of("serve"),
                true);

        assertIterableEquals(List.of("C:\\tools\\opencode.exe", "serve"), commandLine);
    }
}
