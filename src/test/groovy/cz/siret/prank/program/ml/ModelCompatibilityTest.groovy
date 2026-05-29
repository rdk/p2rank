package cz.siret.prank.program.ml

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for the config/model feature-header compatibility check (I20).
 *
 * The comparator and check() take the current header / failOnMismatch flag explicitly,
 * so these tests do not touch the Params singleton and need no isolation.
 */
@CompileStatic
class ModelCompatibilityTest {

    private static Model modelWithHeader(List<String> header) {
        Model m = new Model("test-model", new Object())
        m.storedFeatureHeader = header
        m.sourceDir = "/tmp/test-model"
        return m
    }

    @Test
    void "identical headers match"() {
        ModelCompatibility.Result r = ModelCompatibility.compare(["a", "b", "c"], ["a", "b", "c"])
        assertTrue r.match
        // check() must not throw for a matching header
        ModelCompatibility.check(modelWithHeader(["a", "b", "c"]), ["a", "b", "c"], true)
    }

    @Test
    void "extra feature in current config is detected"() {
        ModelCompatibility.Result r = ModelCompatibility.compare(["a", "b"], ["a", "b", "c"])
        assertFalse r.match
        assertEquals(["c"], r.unexpectedInCurrent)
        assertTrue r.missingInCurrent.isEmpty()
        assertFalse r.sameSetWrongOrder
    }

    @Test
    void "missing feature in current config is detected"() {
        ModelCompatibility.Result r = ModelCompatibility.compare(["a", "b", "c"], ["a", "b"])
        assertFalse r.match
        assertEquals(["c"], r.missingInCurrent)
        assertTrue r.unexpectedInCurrent.isEmpty()
        assertFalse r.sameSetWrongOrder
    }

    @Test
    void "same set in wrong order is detected"() {
        ModelCompatibility.Result r = ModelCompatibility.compare(["a", "b", "c"], ["a", "c", "b"])
        assertFalse r.match
        assertTrue r.sameSetWrongOrder
        assertEquals(Integer.valueOf(1), r.firstDivergenceIndex)
        assertTrue r.missingInCurrent.isEmpty()
        assertTrue r.unexpectedInCurrent.isEmpty()
    }

    @Test
    void "check throws on mismatch when failOnMismatch is true"() {
        Model m = modelWithHeader(["a", "b", "c"])
        PrankException ex = assertThrows(PrankException) {
            ModelCompatibility.check(m, ["a", "b", "x"], true)
        }
        assertTrue ex.message.contains("feature mismatch")
    }

    @Test
    void "check only warns on mismatch when failOnMismatch is false"() {
        Model m = modelWithHeader(["a", "b", "c"])
        // must not throw
        ModelCompatibility.check(m, ["a", "b", "x"], false)
    }

    @Test
    void "check is a no-op when model has no stored header"() {
        Model m = new Model("legacy", new Object())  // storedFeatureHeader == null
        // must not throw even with failOnMismatch = true
        ModelCompatibility.check(m, ["a", "b"], true)
    }

}
