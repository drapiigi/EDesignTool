package com.ghana.gwire.service.persist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileWriterTest {

    @TempDir
    Path temp;

    @Test
    void writeCreatesFile() throws Exception {
        Path target = temp.resolve("out.gwire");
        AtomicFileWriter.writeAtomically(target, "hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(target));
        assertEquals("hello", Files.readString(target));
        assertFalse(Files.exists(temp.resolve("out.gwire.tmp")));
    }

    @Test
    void writeReplacesWithoutLeavingTmp() throws Exception {
        Path target = temp.resolve("data.txt");
        Files.writeString(target, "v1");
        BackupService.rotate(target);
        AtomicFileWriter.writeAtomically(target, "v2".getBytes(StandardCharsets.UTF_8));
        assertEquals("v2", Files.readString(target));
        assertTrue(Files.isRegularFile(temp.resolve("data.txt.bak")));
        assertEquals("v1", Files.readString(temp.resolve("data.txt.bak")));
    }
}
