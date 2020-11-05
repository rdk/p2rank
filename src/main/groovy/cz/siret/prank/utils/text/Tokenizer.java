package cz.siret.prank.utils.text;

import javax.annotation.Nullable;

/**
 *
 */
public interface Tokenizer {

    @Nullable
    String nextToken();

    boolean hasNext();

}
