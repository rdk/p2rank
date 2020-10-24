package cz.siret.prank.utils.text;

import com.google.common.base.CharMatcher;

/**
 *
 */
public class CharMatchers {

    public static final CharMatcher SPACE = isChar(' ');

    public static CharMatcher isChar(char c) {
        return CharMatcher.is(c);
    }

}
