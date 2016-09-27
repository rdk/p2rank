package cz.siret.prank.utils

import groovy.transform.CompileStatic

import java.awt.*
import java.util.List

@CompileStatic
class ColorUtils {

    /**
     Generates a series of colors such that the
     distribution of the colors is (fairly) evenly spaced
     throughout the color spectrum. This is especially
     useful for generating unique color codes to be used
     in a legend or on a graph.

     @param numColors the number of colors to generate
     @return an array of Color objects representing the
      colors in the table
     */
    public static List<Color> createSpectrum(int numColors, double sat, double hueMin, double hueMax) {

        Color[] table = new Color[numColors];

        if (numColors == 1) {
            // Special case for only one color
            table[0] = createSpectrum(2, sat, hueMin, hueMax)[0]
        } else {
            double hueRange = hueMax - hueMin

            for (int i = 0; i < numColors; i++) {
                double hue = hueMin + hueRange * (i / numColors)

                if (hue > 1)
                    hue -= 1

                // Here we interleave light colors and dark colors
                // to get a wider distribution of colors.
                if (i % 2 == 0)
                    table[i] = Color.getHSBColor((float) hue, (float) sat, (float) 0.9);
                else
                    table[i] = Color.getHSBColor((float) hue, (float) sat, (float) 0.7);
            }
        }

        return table.toList();
    }

    /**
     Generates a series of colors such that the
     distribution of the colors is (fairly) evenly spaced
     throughout the color spectrum. This is especially
     useful for generating unique color codes to be used
     in a legend or on a graph.

     @param numColors the number of colors to generate
     @return an array of Color objects representing the
      colors in the table
     */
    public static List<Color> createSpectrumAll(int numColors) {

        Color[] table = new Color[numColors];

        if (numColors == 1) {
            // Special case for only one color
            table[0] = createSpectrumAll(2)[0]
        } else {
            double hueMax = (float) 0.85;
            double sat = (float) 0.8;

            for (int i = 0; i < numColors; i++) {
                double hue = hueMax * i / (numColors - 1);

                // Here we interleave light colors and dark colors
                // to get a wider distribution of colors.
                if (i % 2 == 0)
                    table[i] = Color.getHSBColor((float) hue, (float) sat, (float) 0.9);
                else
                    table[i] = Color.getHSBColor((float) hue, (float) sat, (float) 0.7);
            }
        }

        return table.toList();
    }

}
