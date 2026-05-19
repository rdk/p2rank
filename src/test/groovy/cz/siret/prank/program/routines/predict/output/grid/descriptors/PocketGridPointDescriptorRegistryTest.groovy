package cz.siret.prank.program.routines.predict.output.grid.descriptors

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PocketGridPointDescriptorRegistryTest {

    @Test
    void shippedDescriptorsAreRegistered() {
        // If someone removes one of the shipped descriptors, the CLI -pocket_grid_point_descriptors
        // default no longer resolves and existing user config files start to fail. This test
        // pins both shipped names.
        assertNotNull(PocketGridPointDescriptorRegistry.get('volsite'))
        assertNotNull(PocketGridPointDescriptorRegistry.get('volsite_smooth'))
    }

    @Test
    void unknownNameThrowsWithKnownList() {
        PrankException e = assertThrows(PrankException.class) {
            PocketGridPointDescriptorRegistry.get('does_not_exist')
        } as PrankException
        // The error message must name the typo AND list the known names so the
        // user can see the correct spelling.
        assertTrue(e.message.contains('does_not_exist'), "missing typo in: ${e.message}")
        assertTrue(e.message.contains('volsite'), "missing 'volsite' in known list: ${e.message}")
    }

}
