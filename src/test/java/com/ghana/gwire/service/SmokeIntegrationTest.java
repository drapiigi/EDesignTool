package com.ghana.gwire.service;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.DatabaseConfig;
import com.ghana.gwire.db.DatabaseManager;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.samples.SampleProjectFactory;
import com.ghana.gwire.service.calc.CalcEngine;
import com.ghana.gwire.service.export.PdfExportService;
import com.ghana.gwire.service.persist.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 10 CI smoke: sample project → calc → PDF (no display required).
 */
class SmokeIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void sampleCalcAndPdfExport() throws Exception {
        String name = "smoke_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager dbm = new DatabaseManager(DatabaseConfig.inMemory(name));
        ComponentLibraryService lib = new ComponentLibraryService(dbm);
        lib.ensureInitialized();
        assertTrue(lib.count() >= 40);

        Project project = SampleProjectFactory.createThreeBedBungalow();
        assertTrue(project.totalDeviceCount() > 0);

        ProjectStore store = new ProjectStore();
        Path gwire = temp.resolve("sample.gwire");
        store.save(project, gwire);
        Project reloaded = store.load(gwire);
        assertTrue(reloaded.totalDeviceCount() == project.totalDeviceCount());
        assertTrue(reloaded.totalRoomCount() == project.totalRoomCount());

        DesignReport report = new CalcEngine().calculate(reloaded, lib);
        assertNotNull(report);
        reloaded.setLastReport(report);

        Path pdf = temp.resolve("report.pdf");
        new PdfExportService().export(reloaded, pdf);
        assertTrue(Files.size(pdf) > 500);

        lib.close();
    }
}
