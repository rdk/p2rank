package cz.siret.prank.utils.text;

/**
 *
 */

import com.google.common.base.CharMatcher;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

/**
 * more than 10x faster than java.util.Scanner
 */
public class FastTokenizer implements Tokenizer, Closeable {

    private final CharMatcher whitespaceMatcher;
    private final BufferedReader reader;

    private ArrayList<String> row = new ArrayList<>();  // null=empty
    private int rowPos = 0;
    private boolean done = false;

    public FastTokenizer(Reader reader, CharMatcher whitespaceMatcher) {
        if (reader instanceof BufferedReader) {
            this.reader = (BufferedReader)reader;
        } else {
            this.reader = new BufferedReader(reader);
        }
        this.whitespaceMatcher = whitespaceMatcher;
    }

    private String readLine() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readNextRow() {
        String line = readLine();
        if (line == null) {
            done = true;
        } else {
            //FastStringTokenizer.splitTo(line, whitespaceMatcher, row);
            row = FastSplitter.split(line, whitespaceMatcher, 6);
            rowPos = 0;
        }
    }

    private void ensureNextNonemptyRow() {
        while (!done && rowPos==row.size()) {
            readNextRow();
        }
    }

    public boolean hasNext() {
        ensureNextNonemptyRow();
        return !done;
    }

    public String nextToken() {
        ensureNextNonemptyRow();
        if (done) {
            return null;
        } else {
            return row.get(rowPos++);
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
