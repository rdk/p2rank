package cz.siret.prank.program.params

import cz.siret.prank.utils.Sutils
import org.junit.jupiter.api.*
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for Params.setParam() type coercion and parseBoolean() validation.
 */
@Isolated
@ResourceLock("Params")
class ParamsTypeCoercionTest {

    static Params originalParams

    @BeforeAll
    static void setup() {
        originalParams = (Params) Params.inst.clone()
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = originalParams
    }

    @BeforeEach
    void resetParams() {
        Params.INSTANCE = new Params()
    }

    // ===== String parameter coercion =====

    @Test
    void setParamStringStripsDoubleQuotes() {
        Params.inst.setParam("model", '"my_model"')
        assertEquals("my_model", Params.inst.model)
    }

    @Test
    void setParamStringWithoutQuotesIsUnchanged() {
        Params.inst.setParam("model", "random_forest")
        assertEquals("random_forest", Params.inst.model)
    }

    // ===== Boolean parameter coercion =====

    @Test
    void setParamBooleanTrueString() {
        Params.inst.visualizations = false
        Params.inst.setParam("visualizations", "true")
        assertTrue(Params.inst.visualizations)
    }

    @Test
    void setParamBooleanFalseString() {
        Params.inst.visualizations = true
        Params.inst.setParam("visualizations", "false")
        assertFalse(Params.inst.visualizations)
    }

    @Test
    void setParamBooleanOneString() {
        Params.inst.visualizations = false
        Params.inst.setParam("visualizations", "1")
        assertTrue(Params.inst.visualizations)
    }

    @Test
    void setParamBooleanZeroString() {
        Params.inst.visualizations = true
        Params.inst.setParam("visualizations", "0")
        assertFalse(Params.inst.visualizations)
    }

    @Test
    void setParamBooleanOnePointZero() {
        Params.inst.visualizations = false
        Params.inst.setParam("visualizations", "1.0")
        assertTrue(Params.inst.visualizations)
    }

    @Test
    void setParamBooleanZeroPointZero() {
        Params.inst.visualizations = true
        Params.inst.setParam("visualizations", "0.0")
        assertFalse(Params.inst.visualizations)
    }

    // ===== Integer parameter coercion =====

    @Test
    void setParamIntegerTruncatesDouble() {
        Params.inst.setParam("threads", "3.7")
        assertEquals(3, Params.inst.threads)
    }

    @Test
    void setParamIntegerFromWholeNumber() {
        Params.inst.setParam("threads", "8")
        assertEquals(8, Params.inst.threads)
    }

    @Test
    void setParamIntegerFromDoubleZero() {
        Params.inst.setParam("seed", "0.0")
        assertEquals(0, Params.inst.seed)
    }

    // ===== parseBoolean validation =====

    @Test
    void parseBooleanRejectsYes() {
        assertThrows(IllegalArgumentException) {
            Params.inst.setParam("visualizations", "yes")
        }
    }

    @Test
    void parseBooleanRejectsMaybe() {
        assertThrows(IllegalArgumentException) {
            Params.inst.setParam("visualizations", "maybe")
        }
    }

    @Test
    void parseBooleanRejectsArbitraryString() {
        assertThrows(IllegalArgumentException) {
            Params.inst.setParam("visualizations", "on")
        }
    }

    // ===== List parameter coercion =====

    @Test
    void setParamListParsesParenthesizedFormat() {
        Params.inst.setParam("features", "(a,b,c)")
        assertEquals(["a", "b", "c"], Params.inst.features)
    }

    @Test
    void setParamListParsesEmptyParens() {
        Params.inst.setParam("features", "()")
        assertTrue(Params.inst.features.isEmpty())
    }

    @Test
    void setParamListParsesConsistentlyWithSutils() {
        String input = "(protrusion,bfactor)"
        List<String> expected = Sutils.parseList(input)
        Params.inst.setParam("features", input)
        assertEquals(expected, Params.inst.features)
    }

}
