package cz.siret.prank.utils.text;

import com.google.common.base.CharMatcher;

import java.util.ArrayList;

/**
 *
 */
public class FastSplitter implements Tokenizer {

    private final CharMatcher whitespaceMatcher;

    private String nextToken;

    private String str;
    private int len;
    private int pos;


    public FastSplitter(String str, CharMatcher charMatcher) {
        this.whitespaceMatcher = charMatcher;
        resetFor(str);
    }

    public void resetFor(String str) {
        this.str = str;
        this.len = str.length();
        this.pos = 0;
        this.nextToken = null;
    }

    private void readNextToken() {
        while (pos < len && whitespaceMatcher.matches(str.charAt(pos))) {
            pos++;
        }
        int tokenStart = pos;
        while (pos < len && !whitespaceMatcher.matches(str.charAt(pos))) {
            pos++;
        }
        if (tokenStart < pos) {
            nextToken = str.substring(tokenStart, pos);
        } else {
            nextToken = null;
        }
    }

    private String getAndResetNextToken() {
        String res = nextToken;
        nextToken = null;
        return res;
    }

    public String nextToken() {
        if (nextToken != null) {
            return getAndResetNextToken();
        }
        if (pos >= len) {
            return null;
        }
        readNextToken();
        return getAndResetNextToken();
    }

    public boolean hasNext() {
        if (pos >= len) {
            return false;
        } else {
            readNextToken();
            return nextToken!=null;
        }
    }
    
    public static ArrayList<String> splitTo(String str, CharMatcher whitespaceMatcher, ArrayList<String> out) {
        out.clear();
        FastSplitter tokenizer = new FastSplitter(str, whitespaceMatcher);
        String next = null;
        while ((next = tokenizer.nextToken()) != null) {
            out.add(next);
        }
        return out;
    }

    public static ArrayList<String> split(String str, CharMatcher whitespaceMatcher, int expectedSize) {
        ArrayList<String> out = new ArrayList<>(expectedSize);
        FastSplitter tokenizer = new FastSplitter(str, whitespaceMatcher);
        String next = null;
        while ((next = tokenizer.nextToken()) != null) {
            out.add(next);
        }
        return out;
    }

}
