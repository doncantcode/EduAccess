package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsbServiceTest {

    @TempDir
    Path tempDir;

    private final UsbService usb = new UsbService();

    @Test
    void copiesFilesIntoTheResourceFolder() throws Exception {
        Path pdf = Files.writeString(tempDir.resolve("grade1-math.pdf"), "%PDF-1.4 sample");
        Path drive = Files.createDirectories(tempDir.resolve("usb"));

        UsbService.CopyResult result = usb.copyTo(List.of(pdf), drive);

        assertEquals(drive.resolve(UsbService.FOLDER_NAME), result.destination());
        assertEquals(1, result.copied().size());
        assertTrue(Files.exists(result.copied().get(0)));
        assertTrue(result.failed().isEmpty());
    }

    @Test
    void sameNameTwiceGetsANumberedSuffixInsteadOfOverwriting() throws Exception {
        Path pdf = Files.writeString(tempDir.resolve("module.pdf"), "first");
        Path drive = Files.createDirectories(tempDir.resolve("usb"));

        UsbService.CopyResult first = usb.copyTo(List.of(pdf), drive);
        UsbService.CopyResult second = usb.copyTo(List.of(pdf), drive);

        assertEquals("module.pdf", first.copied().get(0).getFileName().toString());
        assertEquals("module (2).pdf", second.copied().get(0).getFileName().toString());
        assertEquals("first", Files.readString(first.copied().get(0)));
    }

    @Test
    void missingFilesAreReportedAndTheRestStillCopy() throws Exception {
        Path present = Files.writeString(tempDir.resolve("present.pdf"), "ok");
        Path missing = tempDir.resolve("gone.pdf");
        Path drive = Files.createDirectories(tempDir.resolve("usb"));

        UsbService.CopyResult result = usb.copyTo(List.of(missing, present), drive);

        assertEquals(1, result.copied().size());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().get(0).contains("gone.pdf"));
    }

    @Test
    void unwritableTargetIsRefusedBeforeAnyCopy() throws Exception {
        Path pdf = Files.writeString(tempDir.resolve("x.pdf"), "x");
        Path notAFolder = tempDir.resolve("missing-drive");

        assertThrows(ServiceException.class, () -> usb.copyTo(List.of(pdf), notAFolder));
        assertFalse(Files.exists(notAFolder.resolve(UsbService.FOLDER_NAME)));
    }
}
