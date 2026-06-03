package cz.siret.prank.program

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

/**
 * Tests for the command/config purpose compatibility check (issue #73).
 *
 * check() takes command / configPurpose / failOnWrong explicitly, so these tests
 * do not touch the Params singleton and need no isolation. "Does not throw" cases
 * call check() directly: any thrown exception fails the test.
 */
@CompileStatic
class CommandConfigCompatibilityTest {

    private static void check(String command, String purpose, boolean failOnWrong) {
        CommandConfigCompatibility.check(command, purpose, command + "-config", failOnWrong)
    }

    @Test
    void "matching purpose passes"() {
        check('predict', 'prediction', true)
        check('eval-predict', 'prediction', true)
        check('rescore', 'rescoring', true)
        check('fpocket-rescore', 'rescoring', true)
        check('eval-rescore', 'rescoring', true)
    }

    @Test
    void "wrong purpose fails when failOnWrong is true"() {
        assertThrows(PrankException) { check('rescore', 'prediction', true) }   // rescore -c alphafold
        assertThrows(PrankException) { check('predict', 'rescoring', true) }    // predict -c rescore_2024
        assertThrows(PrankException) { check('eval-rescore', 'prediction', true) }
    }

    @Test
    void "wrong purpose only warns when failOnWrong is false"() {
        check('rescore', 'prediction', false)
        check('predict', 'rescoring', false)
    }

    @Test
    void "unmapped command is never checked"() {
        check('export-points', 'prediction', true)
        check('export-points', 'rescoring', true)
        check('eval', 'rescoring', true)
        check('traineval', 'prediction', true)
    }

    @Test
    void "empty purpose is unrestricted"() {
        check('predict', '', true)
        check('rescore', '', true)
        CommandConfigCompatibility.check('rescore', null, null, true)
    }

    @Test
    void "invalid purpose value fails fast"() {
        assertThrows(PrankException) { check('predict', 'prediciton', true) }   // typo
        assertThrows(PrankException) { check('rescore', 'whatever', false) }    // even with failOnWrong=false
    }
}
