package cz.siret.prank.utils.console

import groovy.transform.CompileStatic

import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * Simple text box for console output alignment
 */
@CompileStatic
class TextBox {

    @Nonnull
    List<String> lines

    TextBox(@Nonnull List<String> lines) {
        this.lines = lines
    }

    int getWidth() {
        int w = 0
        for (String line : lines) {
            w = Math.max(w, line.length())
        }
        return w
    }

    /**
     * Joins this box with another box horizontally with given delimiter.
     * Returns new box, this and other are not modified.
     * If other is null, returns this box.
     */
    TextBox joinedWith(@Nullable TextBox other, String delimiter) {
        if (other == null) {
            return this
        }

        return join([this, other], delimiter)
    }

    @Override
    String toString() {
        return lines.join("\n")
    }

    static TextBox of(@Nonnull List<String> lines) {
        return new TextBox(lines)
    }

    static TextBox join(@Nonnull List<TextBox> boxes, String delimiter) {
        if (boxes.isEmpty()) {
            return new TextBox([])
        }

        int[] widths = new int[boxes.size()]
        int maxHeight = 0

        // Calculate width of each box and find maximum height
        for (int i = 0; i < boxes.size(); i++) {
            widths[i] = boxes[i].getWidth()
            maxHeight = Math.max(maxHeight, boxes[i].lines.size())
        }

        List<String> resultLines = []

        // Build each line by concatenating from all boxes
        for (int row = 0; row < maxHeight; row++) {
            StringBuilder line = new StringBuilder()

            for (int i = 0; i < boxes.size(); i++) {
                if (i > 0) {
                    line.append(delimiter)
                }

                TextBox box = boxes[i]
                String content = ""

                if (row < box.lines.size()) {
                    content = box.lines[row]
                }

                // Pad content to match box width for alignment
                content = content.padRight(widths[i])
                line.append(content)
            }

            resultLines.add(line.toString())
        }

        return new TextBox(resultLines)

    }

}
