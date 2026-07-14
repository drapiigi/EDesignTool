package com.ghana.gwire.service.calc;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.DatabaseConfig;
import com.ghana.gwire.db.DatabaseManager;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.samples.SampleProjectFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden calc suite: sample house + seed catalogue → normalized report.
 * Update expected file deliberately when formulas change (with CEWP review).
 */
class GoldenCalcTest {

    @TempDir
    Path temp;

    @Test
    void sampleThreeBedGolden() throws Exception {
        String dbName = "golden_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager db = new DatabaseManager(DatabaseConfig.inMemory(dbName));
        ComponentLibraryService lib = new ComponentLibraryService(db);
        lib.ensureInitialized();

        Project project = SampleProjectFactory.createThreeBedBungalow();
        DesignReport report = new CalcEngine().calculate(project, lib);
        assertFalse(report.assumptions().isEmpty(), "expected assumption codes");
        assertTrue(report.standardsEdition().contains("L.I. 2008"));
        assertTrue(report.assumptions().contains(AssumptionCodes.STANDARDS_HEURISTIC_LI2008));

        String actual = GoldenCalcHarness.normalizeJson(report);

        Path expectedPath = Path.of("src/test/resources/goldens/expected/sample-3bed.json");
        if (!Files.isRegularFile(expectedPath)) {
            // First-run write when missing (developer machine)
            Files.createDirectories(expectedPath.getParent());
            Files.writeString(expectedPath, actual, StandardCharsets.UTF_8);
        }
        String expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
        // Normalize line endings
        expected = expected.replace("\r\n", "\n").trim();
        actual = actual.replace("\r\n", "\n").trim();
        if (!expected.equals(actual)) {
            Path out = temp.resolve("actual-sample-3bed.json");
            Files.writeString(out, actual);
            // Soft assert with write for update: fail with path hint
            org.junit.jupiter.api.Assertions.assertEquals(
                    expected,
                    actual,
                    "Golden mismatch — review formulas; actual written to " + out
                            + "\nIf intentional, copy to src/test/resources/goldens/expected/sample-3bed.json"
            );
        }
        lib.close();
    }

    @Test
    void emptyProjectHasStandardsStamp() {
        Project p = new Project("Empty");
        DesignReport r = new CalcEngine().calculate(p, java.util.List.of());
        assertTrue(r.standardsEdition().contains("L.I. 2008"));
        assertTrue(r.assumptions().contains(AssumptionCodes.STANDARDS_HEURISTIC_LI2008));
    }
}
