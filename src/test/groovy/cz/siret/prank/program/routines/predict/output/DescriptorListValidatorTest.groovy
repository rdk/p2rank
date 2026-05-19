package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class DescriptorListValidatorTest {

    private static final Set<String> KNOWN = ['volume', 'sphericity', 'num_residues'] as Set
    private static final String PARAM = 'pocket_descriptors'

    @Test
    void nullListIsAcceptedAsEmpty() {
        // A missing list should be a no-op so a default-empty Params field doesn't
        // require the caller to special-case it. Failure mode = any thrown exception.
        DescriptorListValidator.validate(null, KNOWN, PARAM)
    }

    @Test
    void emptyListIsAccepted() {
        DescriptorListValidator.validate([], KNOWN, PARAM)
    }

    @Test
    void validNamesAreAccepted() {
        DescriptorListValidator.validate(['volume', 'sphericity'], KNOWN, PARAM)
    }

    @Test
    void unknownNameThrowsAndNamesTheOffender() {
        // The error message MUST include the typo so the user can locate it in their
        // config, and MUST include the known list so they can correct it.
        PrankException e = assertThrows(PrankException.class) {
            DescriptorListValidator.validate(['volume', 'sphericty'], KNOWN, PARAM)
        } as PrankException
        assertTrue(e.message.contains("'sphericty'"), "missing typo: ${e.message}")
        assertTrue(e.message.contains('-pocket_descriptors'), "missing param: ${e.message}")
        assertTrue(e.message.contains('volume'), "missing known list: ${e.message}")
    }

    @Test
    void duplicateNameThrows() {
        PrankException e = assertThrows(PrankException.class) {
            DescriptorListValidator.validate(['volume', 'volume'], KNOWN, PARAM)
        } as PrankException
        assertTrue(e.message.contains("'volume'"), "missing dup name: ${e.message}")
        assertTrue(e.message.toLowerCase().contains('duplicate'), "missing 'duplicate': ${e.message}")
    }

    @Test
    void nullEntryThrows() {
        // Distinguishes "list of one valid name" from "list with a null inside" —
        // catches malformed Groovy config files (e.g. trailing comma in a list literal).
        PrankException e = assertThrows(PrankException.class) {
            DescriptorListValidator.validate([null] as List<String>, KNOWN, PARAM)
        } as PrankException
        assertTrue(e.message.toLowerCase().contains('empty/null'), e.message)
    }

    @Test
    void blankEntryThrows() {
        PrankException e = assertThrows(PrankException.class) {
            DescriptorListValidator.validate(['volume', '  '], KNOWN, PARAM)
        } as PrankException
        assertTrue(e.message.toLowerCase().contains('empty/null'), e.message)
    }

    @Test
    void paramNameIsRenderedWithDashPrefix() {
        // We don't want the validator to be inconsistent about CLI-flag formatting.
        PrankException e = assertThrows(PrankException.class) {
            DescriptorListValidator.validate(['xx'], KNOWN, 'some_param')
        } as PrankException
        assertTrue(e.message.contains('-some_param'),
                "expected leading dash on param name, got: ${e.message}")
    }

}
