package cz.siret.prank.domain

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class AminoAcidMapperTest {

    @AfterEach
    void cleanup() {
        AminoAcidMapper.reset()
    }

    // === Initialization ===

    @Test
    void minimalModeLoadsTwoMappings() {
        AminoAcidMapper.initialize("minimal")
        assertEquals(2, AminoAcidMapper.getInstance().getMappingCount())
    }

    @Test
    void pdbfixerModeLoadsFromResource() {
        AminoAcidMapper.initialize("pdbfixer")
        assertTrue(AminoAcidMapper.getInstance().getMappingCount() > 2)
    }

    @Test
    void customFileLoadsFromPath() {
        // Create temp file
        File tempFile = File.createTempFile("aa-mapping", ".csv")
        tempFile.text = "LLP,LYS\nTQP,TRP\n"
        tempFile.deleteOnExit()

        AminoAcidMapper.initialize(tempFile.absolutePath)
        assertEquals(2, AminoAcidMapper.getInstance().getMappingCount())
    }

    // === Mapping Behavior ===

    @Test
    void mapsMseToMetInMinimalMode() {
        AminoAcidMapper.initialize("minimal")
        assertEquals("MET", AminoAcidMapper.getInstance().map("MSE"))
    }

    @Test
    void mapsMenToAsnInMinimalMode() {
        AminoAcidMapper.initialize("minimal")
        assertEquals("ASN", AminoAcidMapper.getInstance().map("MEN"))
    }

    @Test
    void preservesStandardAminoAcidCodes() {
        AminoAcidMapper.initialize("minimal")
        def mapper = AminoAcidMapper.getInstance()

        // All 20 standard AAs should pass through unchanged
        for (AA aa : AA.values()) {
            assertEquals(aa.code, mapper.map(aa.code))
        }
    }

    @Test
    void returnsOriginalWhenNoMapping() {
        AminoAcidMapper.initialize("minimal")
        assertEquals("XYZ", AminoAcidMapper.getInstance().map("XYZ"))
    }

    @Test
    void handlesCaseInsensitiveInput() {
        AminoAcidMapper.initialize("minimal")
        def mapper = AminoAcidMapper.getInstance()

        assertEquals("MET", mapper.map("mse"))
        assertEquals("MET", mapper.map("Mse"))
        assertEquals("MET", mapper.map("MSE"))
    }

    @Test
    void handlesNullInput() {
        AminoAcidMapper.initialize("minimal")
        assertNull(AminoAcidMapper.getInstance().map(null))
    }

    @Test
    void handlesEmptyStringInput() {
        AminoAcidMapper.initialize("minimal")
        assertEquals("", AminoAcidMapper.getInstance().map(""))
    }

    // === Pdbfixer Mode ===

    @Test
    void pdbfixerMapsLlpToLys() {
        AminoAcidMapper.initialize("pdbfixer")
        assertEquals("LYS", AminoAcidMapper.getInstance().map("LLP"))
    }

    @Test
    void pdbfixerMapsSepToSer() {
        AminoAcidMapper.initialize("pdbfixer")
        assertEquals("SER", AminoAcidMapper.getInstance().map("SEP"))
    }

    @Test
    void pdbfixerMapsHypToPro() {
        AminoAcidMapper.initialize("pdbfixer")
        assertEquals("PRO", AminoAcidMapper.getInstance().map("HYP"))
    }

    // === Error Handling ===

    @Test
    void failsOnMissingFile() {
        assertThrows(PrankException.class) {
            AminoAcidMapper.initialize("/nonexistent/path/file.csv")
        }
    }

    @Test
    void skipsInvalidLinesWithWarning() {
        File tempFile = File.createTempFile("aa-mapping", ".csv")
        tempFile.text = """
# Comment line
LLP,LYS
INVALID
TQP,TRP
"""
        tempFile.deleteOnExit()

        AminoAcidMapper.initialize(tempFile.absolutePath)
        // Should have 2 valid mappings, invalid line skipped
        assertEquals(2, AminoAcidMapper.getInstance().getMappingCount())
    }

    @Test
    void keepFirstOnDuplicateMappings() {
        File tempFile = File.createTempFile("aa-mapping", ".csv")
        tempFile.text = "LLP,LYS\nLLP,ARG\n"
        tempFile.deleteOnExit()

        AminoAcidMapper.initialize(tempFile.absolutePath)
        assertEquals("LYS", AminoAcidMapper.getInstance().map("LLP"))
    }

    @Test
    void skipsEmptyLines() {
        File tempFile = File.createTempFile("aa-mapping", ".csv")
        tempFile.text = "\n\nLLP,LYS\n\n"
        tempFile.deleteOnExit()

        AminoAcidMapper.initialize(tempFile.absolutePath)
        assertEquals(1, AminoAcidMapper.getInstance().getMappingCount())
    }

    // === Auto-initialization ===

    @Test
    void autoInitializesWithDefaultForLibraryUsage() {
        // Simulate library usage - getInstance() without explicit initialize()
        // Should auto-init with "minimal" mode
        def mapper = AminoAcidMapper.getInstance()
        assertNotNull(mapper)
        assertEquals("minimal", mapper.getMode())
        assertEquals("MET", mapper.map("MSE"))
    }

    // === Thread Safety ===

    @Test
    void threadSafeInitialization() {
        def threads = (1..10).collect {
            Thread.start {
                def mapper = AminoAcidMapper.getInstance()
                assertEquals("MET", mapper.map("MSE"))
            }
        }
        threads*.join()
    }

    @Test
    void reinitializationWorks() {
        AminoAcidMapper.initialize("minimal")
        assertEquals(2, AminoAcidMapper.getInstance().getMappingCount())

        AminoAcidMapper.initialize("pdbfixer")
        assertTrue(AminoAcidMapper.getInstance().getMappingCount() > 2)
    }

    // === Diagnostic Methods ===

    @Test
    void getMappingsReturnsUnmodifiableView() {
        AminoAcidMapper.initialize("minimal")
        def mappings = AminoAcidMapper.getInstance().getMappings()

        assertEquals("MET", mappings.get("MSE"))
        assertEquals("ASN", mappings.get("MEN"))

        // Verify it's unmodifiable
        assertThrows(UnsupportedOperationException.class) {
            mappings.put("TEST", "VAL")
        }
    }

    @Test
    void toStringContainsModeAndCount() {
        AminoAcidMapper.initialize("minimal")
        def str = AminoAcidMapper.getInstance().toString()

        assertTrue(str.contains("mode=minimal"))
        assertTrue(str.contains("mappings=2"))
    }

    @Test
    void skipsSelfMappings() {
        File tempFile = File.createTempFile("aa-mapping", ".csv")
        tempFile.text = "LLP,LYS\nALA,ALA\nTQP,TRP\n"
        tempFile.deleteOnExit()

        AminoAcidMapper.initialize(tempFile.absolutePath)
        // Should have 2 valid mappings - ALA→ALA is skipped as a no-op
        assertEquals(2, AminoAcidMapper.getInstance().getMappingCount())
    }

    @Test
    void nullModeNormalizesToMinimal() {
        AminoAcidMapper.initialize(null)
        def mapper = AminoAcidMapper.getInstance()

        assertEquals("minimal", mapper.getMode())
        assertEquals(2, mapper.getMappingCount())
        assertEquals("MET", mapper.map("MSE"))
    }

    // === Built-in Mode Detection ===

    @Test
    void isBuiltInModeDetectsReservedNames() {
        assertTrue(AminoAcidMapper.isBuiltInMode(null))
        assertTrue(AminoAcidMapper.isBuiltInMode("minimal"))
        assertTrue(AminoAcidMapper.isBuiltInMode("pdbfixer"))
        assertFalse(AminoAcidMapper.isBuiltInMode("./custom.csv"))
        assertFalse(AminoAcidMapper.isBuiltInMode("/path/to/file.csv"))
        assertFalse(AminoAcidMapper.isBuiltInMode("other"))
    }
}
