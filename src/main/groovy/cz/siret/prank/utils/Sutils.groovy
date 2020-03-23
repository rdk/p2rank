package cz.siret.prank.utils

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * String utils
 */
@CompileStatic
class Sutils {

//    static DateFormat DATE_LABEL_FORMAT = new SimpleDateFormat("yyyy.MM.dd_HHmm")
    static final DateFormat DATE_LABEL_FORMAT = new SimpleDateFormat("yyMMdd_HHmm")

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create()

    private static class MSN extends ToStringStyle {
        MSN() {
            super()

            this.setContentStart("[");
            this.setFieldSeparator(System.lineSeparator() + "  ");
            this.setFieldSeparatorAtStart(true);
            this.setContentEnd(System.lineSeparator() + "]");

            useShortClassName = true
            useIdentityHashCode = false
        }
    }

    public static final ToStringStyle MULTILINE_SIMPLE_NAMES = new MSN()


    static String toStr(Object obj) {

        return ToStringBuilder.reflectionToString(obj, MULTILINE_SIMPLE_NAMES)
    }

    static String toJson(Object obj) {
        GSON.toJson(obj)
    }

    static prefixLines(String prefix, String text) {
        prefix + text.readLines().join("\n"+prefix)
    }


    static String timeLabel() {
        DATE_LABEL_FORMAT.format(new Date())
    }



    static List<String> split(String str, String splitter) {
        Splitter.on(splitter).omitEmptyStrings().trimResults().split(str).toList()
    }

    static List<String> splitKeepEmpty(String str, String splitter) {
        Splitter.on(splitter).trimResults().split(str).toList()
    }


    static List<String> splitOnWhitespace(String str) {
        Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().trimResults().split(str).toList()
    }

    static List<String> split(String str) {
        split(str, " ")
    }

    /**
     *
     * @param liststr format: [a, b,c]
     * @return
     */
    static List<String> parseList(String liststr) {
        if (StringUtils.isBlank(liststr) || liststr=='()' || liststr=='[]' ) {
            return Collections.emptyList()
        }
        assert liststr.length()>=2 : "invalid list string: '$liststr'"

        String splitter = ","
        if (!liststr.contains(splitter)) {
            splitter = "."                    // list in ranged param lists (when running prank ploop) have to use oyher splitter
        }

        liststr = liststr.substring(1, liststr.length()-1) // list is in parentheses "(...)"

        return split(liststr, splitter)
    }

    static String partBefore(String str, String sub) {
        if (str==null) {
            return null
        }
        int split = str.indexOf(sub)
        if (split < 0) {
            return str
        } else {
            return str.substring(0, split)
        }
    }

    static String partBetween(String str, String left, String right) {
        if (str==null || left==null || right==null) {
            return str
        }
        int li = str.indexOf(left)
        int ri = str.lastIndexOf(right)

        if (li<0) li=0
        if (ri<0) ri=str.length()
        
        return str.substring(li+left.length(), ri)
    }

}
