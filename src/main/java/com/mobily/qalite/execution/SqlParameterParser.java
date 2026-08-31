package com.mobily.qalite.execution;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds and binds named parameters (":name") in a SQL statement so a QA_USER can supply values for
 * an admin-authored SQL command without ever concatenating user input into SQL text - every value
 * is bound through a JDBC PreparedStatement placeholder ("?"), never embedded literally, so this is
 * not vulnerable to SQL injection through the parameter values themselves.
 *
 * <p>Scanning skips '...'/"..."/`...` quoting, $$...$$ dollar-quoting, and -- / # / slash-star
 * comments (mirroring {@link SqlScriptSplitter}), so a colon inside a string literal or comment is
 * never mistaken for a parameter. A literal Postgres cast operator ("::type") is also not mistaken
 * for one, since either colon of the pair is rejected by the other being adjacent. A name must look
 * like an identifier (a letter or underscore, then letters/digits/underscores) - Oracle's
 * ":new"/":old" trigger-correlation names and positional binds like ":1" are therefore the one case
 * this can misread as a named parameter; not expected in the DML/DDL/query text this tool is meant
 * for.
 */
public final class SqlParameterParser {

    private SqlParameterParser() {
    }

    public static Set<String> extractParameterNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        for (Token token : scan(sql)) {
            names.add(token.name());
        }
        return names;
    }

    /**
     * Rewrites every ":name" occurrence in sql to "?" and returns the values to bind, in the same
     * order the placeholders appear (a name used more than once is bound that many times, once per
     * occurrence, all with the same value). Throws IllegalArgumentException naming the first missing
     * parameter if parameterValues has no entry for a name the SQL references.
     */
    public static PreparedSql prepare(String sql, Map<String, String> parameterValues) {
        List<Token> tokens = scan(sql);
        StringBuilder rewritten = new StringBuilder();
        List<String> values = new ArrayList<>(tokens.size());

        int cursor = 0;
        for (Token token : tokens) {
            rewritten.append(sql, cursor, token.start());
            rewritten.append('?');
            cursor = token.end();

            if (parameterValues == null || !parameterValues.containsKey(token.name())) {
                throw new IllegalArgumentException("Missing value for parameter :" + token.name());
            }
            values.add(parameterValues.get(token.name()));
        }
        rewritten.append(sql, cursor, sql.length());

        return new PreparedSql(rewritten.toString(), values);
    }

    private static List<Token> scan(String sql) {
        List<Token> tokens = new ArrayList<>();
        int length = sql.length();
        int i = 0;

        while (i < length) {
            char c = sql.charAt(i);

            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                i = end == -1 ? length : end;
                continue;
            }

            if (c == '#') {
                int end = sql.indexOf('\n', i);
                i = end == -1 ? length : end;
                continue;
            }

            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end == -1 ? length : end + 2;
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                i = findClosingQuote(sql, i, c);
                continue;
            }

            if (c == '$') {
                int end = findDollarQuoteEnd(sql, i);
                if (end != -1) {
                    i = end;
                    continue;
                }
            }

            if (c == ':'
                    && (i == 0 || sql.charAt(i - 1) != ':')
                    && i + 1 < length && sql.charAt(i + 1) != ':'
                    && isNameStart(sql.charAt(i + 1))) {
                int nameStart = i + 1;
                int nameEnd = nameStart + 1;
                while (nameEnd < length && isNameChar(sql.charAt(nameEnd))) {
                    nameEnd++;
                }
                tokens.add(new Token(sql.substring(nameStart, nameEnd), i, nameEnd));
                i = nameEnd;
                continue;
            }

            i++;
        }

        return tokens;
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int findClosingQuote(String sql, int start, char quoteChar) {
        int length = sql.length();
        boolean backslashEscapes = quoteChar != '`';
        int i = start + 1;

        while (i < length) {
            char c = sql.charAt(i);

            if (backslashEscapes && c == '\\' && i + 1 < length) {
                i += 2;
                continue;
            }

            if (c == quoteChar) {
                if (i + 1 < length && sql.charAt(i + 1) == quoteChar) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }

            i++;
        }

        return length;
    }

    private static int findDollarQuoteEnd(String sql, int start) {
        int length = sql.length();
        int i = start + 1;
        while (i < length && isNameChar(sql.charAt(i))) {
            i++;
        }
        if (i >= length || sql.charAt(i) != '$') {
            return -1;
        }

        String tag = sql.substring(start, i + 1);
        int closing = sql.indexOf(tag, i + 1);
        return closing == -1 ? length : closing + tag.length();
    }

    private record Token(String name, int start, int end) {
    }

    public record PreparedSql(String sql, List<String> values) {
    }
}
