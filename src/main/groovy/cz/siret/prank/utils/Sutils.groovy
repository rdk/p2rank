package cz.siret.prank.utils

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import groovy.transform.CompileStatic
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * String utils
 */
@CompileStatic
class Sutils {

    static DateFormat DATE_LABEL_FORMAT = new SimpleDateFormat("yyyy.MM.dd_HHmm")
    
    static final Splitter WHITESPACE_SPLITTER = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().trimResults()

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

    static <T> T parseJson(String json, Class<T> classOfT) throws JsonSyntaxException {
        return GSON.fromJson(json, classOfT)
    }

    static prefixLines(String prefix, String text) {
        prefix + text.readLines().join("\n"+prefix)
    }

    static List<String> prefixEach(String prefix, List<String> list) {
        list.collect { prefix + it }
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
        WHITESPACE_SPLITTER.split(str).toList()
    }

    static List<String> split(String str) {
        split(str, " ")
    }

    static String removeSuffix(String s, String suffix) {
        if (s == null || suffix == null) return null
        if (s.endsWith(suffix)) {
            return s.substring(0, s.length()-suffix.length())
        } else {
            return s
        }
    }

    static String removePrefix(String s, String prefix) {
        if (s == null || prefix == null) return null
        if (s.startsWith(prefix)) {
            return s.substring(prefix.length())
        } else {
            return s
        }
    }

    /**
     * @returns prefix made out of digits
     */
    static String digitsPrefix(String s) {
        if (s == null) return null
        if (s.empty) return s
        int end = 0
        while (end < s.size() && Character.isDigit(s.charAt(end))) {
            end++
        }
        if (end == 0) return ""
        return s.substring(0, end)
    }

    /**
     *
     * @param liststr format: "(a, b,c)"
     * @return
     */
    static List<String> parseList(String liststr) {
        if (StringUtils.isBlank(liststr) || liststr=='()' || liststr=='[]' ) {
            return Collections.emptyList()
        }

        liststr = liststr.trim()

        if (liststr.startsWith("(") && liststr.endsWith(")")) {
            liststr = liststr.substring(1, liststr.length()-1) // list is in parentheses "(...)"
        }

        return split(liststr, ",")
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

    static String replaceEach(String template, Map<String, Object> params) {
        List<String> search = Lists.newArrayList()
        List<String> repl = Lists.newArrayList()
        params.each {
            search.add(it.key)
            repl.add(""+it.value)
        }

        return StringUtils.replaceEachRepeatedly(template, search as String[], repl as String[])
    }

    static List<String> trimEach(List<String> list) {
        return list.collect { (it==null) ? null : it.trim()}
    }

    static String sortString(String s) {
        PerfUtils.sortString(s)
    }
    
//===========================================================================================================//

    static List<String> splitRespectInnerParentheses(String str, char delimiter) {
        return splitRespectInnerParentheses(str, delimiter, '(' as char, ')' as char)
    }

    /**
     * Recursively respects inner parentheses.
     * Includes empty tokens.
     *
     * @param str
     * @param delimiter
     * @return
     */
    static List<String> splitRespectInnerParentheses(String str, char delimiter, char leftParenthese, char rightParenthese) {
        List<String> res = new ArrayList<>()

        int i = 0
        int tokenStart = 0
        while (i < str.size()) {
            if (str.charAt(i).equals(delimiter)) {
                res.add str.substring(tokenStart, i)
                tokenStart = i + 1
            }
            if (str.charAt(i).equals(leftParenthese)) {
                i = findClosingParenthese(str, i, leftParenthese, rightParenthese)
            }
            i++
        }
        if (tokenStart <= str.size()) {
            res.add str.substring(tokenStart, str.size())
        }

        return res
    }

    /**
     * @return index of a closing ')' for opening '(' at start (recursively respects inner parentheses) or str.length if not found
     */
    static int findClosingParenthese(String str, int start, char leftParenthese, char rightParenthese) {
        int i = start + 1
        while (i < str.size()) {
            if (str.charAt(i).equals(rightParenthese)) {
                return i
            }
            if (str.charAt(i).equals(leftParenthese)) {
                i = findClosingParenthese(str, i, leftParenthese, rightParenthese)
            }
            i++
        }
        return str.length()
    }

}
