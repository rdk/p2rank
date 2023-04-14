package cz.siret.prank.features.implementation.histogram


import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 *
 */
public class DistancePairHistTest {

    DistancePairHist newSmooth(int n) {
        return new DistancePairHist(n, 0, 1, true)
    }

    DistancePairHist newSharp(int n) {
        return new DistancePairHist(n, 0, 1, false)
    }

    private void aeq(List a, double[] b) {
        try {
            for (int i=0; i!=b.size(); ++i) {
                assertEqualValue((double) (a.get(i)), b[i])
            }
        } catch (Throwable e) {
            throw new AssertionError("\nExpected: $a \n Actual: $b")
        }
    }

    private void assertEqualValue(double a, double b) {
        assertEquals(a, b, 0.0001d)
    }

    @Test
    public void testSharp() throws Exception {

        DistancePairHist hist = new DistancePairHist(2, 0, 10, false)

        aeq( [1, 0], newSharp(2).add(-1).bins   )
        aeq( [1, 0], newSharp(2).add(0).bins    )
        aeq( [0, 1], newSharp(2).add(1).bins    )
        aeq( [0, 1], newSharp(2).add(2).bins    )
        aeq( [0, 1], newSharp(2).add(0.5).bins  )
        aeq( [1, 0], newSharp(2).add(0.25).bins )
        aeq( [0, 1], newSharp(2).add(0.75).bins )

    }

    @Test
    public void testSharp3() throws Exception {

        DistancePairHist hist = new DistancePairHist(2, 0, 10, false)

        aeq( [1, 0, 0], newSharp(3).add(-1).bins   )
        aeq( [1, 0, 0], newSharp(3).add(0).bins    )
        aeq( [0, 0, 1], newSharp(3).add(1).bins    )
        aeq( [0, 0, 1], newSharp(3).add(2).bins    )
        aeq( [0, 1, 0], newSharp(3).add(0.5).bins  )
        aeq( [1, 0, 0], newSharp(3).add(0.25).bins )
        aeq( [0, 0, 1], newSharp(3).add(0.75).bins )

    }

    @Test
    public void testSmooth() throws Exception {

        aeq( [1, 0], newSmooth(2).add(-1).bins           )
        aeq( [1, 0], newSmooth(2).add(0).bins            )
        aeq( [0, 1], newSmooth(2).add(1).bins            )
        aeq( [0, 1], newSmooth(2).add(2).bins            )
        aeq( [0.5, 0.5], newSmooth(2).add(0.5).bins      )
        aeq( [0.75, 0.25], newSmooth(2).add(0.25).bins   )
        aeq( [0.25, 0.75], newSmooth(2).add(0.75).bins   )

    }

    @Test
    public void testSmooth3() throws Exception {

        aeq( [1, 0, 0], newSmooth(3).add(-1).bins         )
        aeq( [1, 0, 0], newSmooth(3).add(0).bins          )
        aeq( [0, 0, 1], newSmooth(3).add(1).bins          )
        aeq( [0, 0, 1], newSmooth(3).add(2).bins          )
        aeq( [0, 1, 0], newSmooth(3).add(0.5).bins        )
        aeq( [0.5, 0.5, 0], newSmooth(3).add(0.25).bins   )
        aeq( [0, 0.5, 0.5], newSmooth(3).add(0.75).bins   )

    }

}