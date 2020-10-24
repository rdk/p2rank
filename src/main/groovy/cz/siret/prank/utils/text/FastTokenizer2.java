package cz.siret.prank.utils.text;

/**
 *
 */

import com.google.common.base.CharMatcher;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

/**
 * more than 10x faster than java.util.Scanner
 */
public class FastTokenizer2 implements Tokenizer, Closeable {

    private final CharMatcher whitespaceMatcher;
    private final BufferedReader reader;

    private FastSplitter rowTokenizer;
    private boolean done = false;

    public FastTokenizer2(Reader reader, CharMatcher whitespaceMatcher) {
        if (reader instanceof BufferedReader) {
            this.reader = (BufferedReader)reader;
        } else {
            this.reader = new BufferedReader(reader);
        }
        this.whitespaceMatcher = whitespaceMatcher;
        this.rowTokenizer = new FastSplitter("", whitespaceMatcher);
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
            rowTokenizer.resetFor(line);
        }
    }

    private void readNextNonemptyRow() {
        while (!done && !rowTokenizer.hasNext()) {
            readNextRow();
        }
    }

    public boolean hasNext() {
        readNextNonemptyRow();
        return !done;
    }

    public String nextToken() {
        readNextNonemptyRow();
        if (done) {
            return null;
        } else {
            return rowTokenizer.nextToken();
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
