package com.mobily.qalite.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Splits a SQL script into individual statements on top-level ';' characters, ignoring ones
 * inside string/identifier literals, comments, and procedural blocks. Needed because MySQL's JDBC
 * driver rejects a multi-statement script sent as a single Statement.execute() call (unless
 * allowMultiQueries is enabled on the JDBC URL), while PostgreSQL's and Oracle's drivers accept it
 * but only expose the first statement's outcome. Executing statements one at a time via this split
 * gives consistent, driver-independent behavior across all supported database types.
 *
 * <p>The scan is a lightweight heuristic, not a full SQL parser. It tracks:
 * <ul>
 *   <li>'...' / "..." / `...` quoting, with doubled-quote escapes (e.g. '' inside a string).
 *       Backslash is treated as an escape character inside '...' and "..." literals, which matches
 *       MySQL's default sql_mode; PostgreSQL only honors backslash escapes in E'...' literals, so a
 *       plain Postgres string that itself ends in a literal backslash right before the closing quote
 *       is the one case this can mis-split. Not expected in admin-authored QA scripts.</li>
 *   <li>{@code $$...$$} / {@code $tag$...$tag$} dollar-quoting, as used by Postgres function/procedure
 *       bodies (e.g. {@code CREATE FUNCTION ... AS $$ ... $$ LANGUAGE plpgsql;}).</li>
 *   <li>-- / # / slash-star comments.</li>
 *   <li>BEGIN/END nesting: a bare {@code BEGIN} opens a block and a bare {@code END} closes one
 *       (used by Oracle anonymous PL/SQL blocks and CREATE PROCEDURE/FUNCTION/PACKAGE/TRIGGER
 *       bodies, and by MySQL's CREATE PROCEDURE/FUNCTION/TRIGGER bodies). ';' inside an open block
 *       is part of the block, not a statement separator. {@code END IF}/{@code END LOOP}/
 *       {@code END CASE}/{@code END WHILE} close a control statement, not a BEGIN block, so they
 *       don't affect the nesting count. A CASE *expression* (as opposed to a CASE *statement*) ends
 *       with a bare END with nothing to distinguish it from a block's closing END - this is a known,
 *       accepted ambiguity of token-level scanning; it is why the '/' terminator below is the more
 *       reliable way to close a PL/SQL block.</li>
 *   <li>A line containing only {@code /} - the standard SQL*Plus/Oracle convention for terminating a
 *       PL/SQL block - always flushes whatever has been accumulated as one statement (and resets
 *       block nesting), regardless of ';' or BEGIN/END state. The '/' itself is discarded, not sent
 *       to the driver.</li>
 * </ul>
 */
final class SqlScriptSplitter {

    private static final Set<String> BLOCK_CONTROL_CLOSERS = Set.of("IF", "LOOP", "CASE", "WHILE");

    private SqlScriptSplitter() {
    }

    static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int length = script.length();
        int i = 0;
        int blockDepth = 0;
        boolean blockClosePendingSemicolon = false;
        while (i < length) {
            char c = script.charAt(i);

            if (c == '-' && i + 1 < length && script.charAt(i + 1) == '-') {
                int end = script.indexOf('\n', i);
                end = end == -1 ? length : end;
                current.append(' ');
                i = end;
                continue;
            }

            if (c == '#') {
                int end = script.indexOf('\n', i);
                end = end == -1 ? length : end;
                current.append(' ');
                i = end;
                continue;
            }

            if (c == '/' && i + 1 < length && script.charAt(i + 1) == '*') {
                int end = script.indexOf("*/", i + 2);
                end = end == -1 ? length : end + 2;
                current.append(' ');
                i = end;
                continue;
            }

            if (c == '/' && isLoneSlashLine(script, i)) {
                int lineEnd = script.indexOf('\n', i);
                lineEnd = lineEnd == -1 ? length : lineEnd;
                statements.add(current.toString());
                current.setLength(0);
                blockDepth = 0;
                blockClosePendingSemicolon = false;
                i = lineEnd;
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                int end = findClosingQuote(script, i, c);
                current.append(script, i, end);
                i = end;
                continue;
            }

            if (c == '$') {
                int end = findDollarQuoteEnd(script, i);
                if (end != -1) {
                    current.append(script, i, end);
                    i = end;
                    continue;
                }
            }

            if (c == ';') {
                if (blockClosePendingSemicolon) {
                    current.append(c);
                    blockClosePendingSemicolon = false;
                    i++;
                    if (blockDepth == 0) {
                        statements.add(current.toString());
                        current.setLength(0);
                    }
                    continue;
                }
                if (blockDepth == 0) {
                    statements.add(current.toString());
                    current.setLength(0);
                    i++;
                    continue;
                }
            }

            if (Character.isLetter(c) && isWordStart(script, i)) {
                int wordEnd = i + 1;
                while (wordEnd < length && isWordChar(script.charAt(wordEnd))) {
                    wordEnd++;
                }

                String upperWord = script.substring(i, wordEnd).toUpperCase(Locale.ROOT);
                if ("BEGIN".equals(upperWord)) {
                    blockDepth++;
                } else if ("END".equals(upperWord) && blockDepth > 0) {
                    String nextWord = peekNextWord(script, wordEnd);
                    if (nextWord == null || !BLOCK_CONTROL_CLOSERS.contains(nextWord.toUpperCase(Locale.ROOT))) {
                        blockDepth--;
                        blockClosePendingSemicolon = true;
                    }
                }

                current.append(script, i, wordEnd);
                i = wordEnd;
                continue;
            }

            current.append(c);
            i++;
        }

        if (!current.isEmpty()) {
            statements.add(current.toString());
        }

        return statements.stream()
                .map(String::strip)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private static boolean isWordStart(String script, int i) {
        return i == 0 || !isWordChar(script.charAt(i - 1));
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static String peekNextWord(String script, int from) {
        int length = script.length();
        int i = from;
        while (i < length && Character.isWhitespace(script.charAt(i))) {
            i++;
        }
        if (i >= length || !Character.isLetter(script.charAt(i))) {
            return null;
        }
        int wordEnd = i + 1;
        while (wordEnd < length && isWordChar(script.charAt(wordEnd))) {
            wordEnd++;
        }
        return script.substring(i, wordEnd);
    }

    /**
     * True when the '/' at position i is alone on its line (only whitespace before and after it up
     * to the surrounding newlines) - the SQL*Plus/Oracle convention for ending a PL/SQL block.
     */
    private static boolean isLoneSlashLine(String script, int i) {
        int lineStart = script.lastIndexOf('\n', i - 1) + 1;
        for (int j = lineStart; j < i; j++) {
            if (!Character.isWhitespace(script.charAt(j))) {
                return false;
            }
        }

        int length = script.length();
        int lineEnd = script.indexOf('\n', i);
        lineEnd = lineEnd == -1 ? length : lineEnd;
        for (int j = i + 1; j < lineEnd; j++) {
            if (!Character.isWhitespace(script.charAt(j))) {
                return false;
            }
        }

        return true;
    }

    private static int findClosingQuote(String script, int start, char quoteChar) {
        int length = script.length();
        boolean backslashEscapes = quoteChar != '`';
        int i = start + 1;

        while (i < length) {
            char c = script.charAt(i);

            if (backslashEscapes && c == '\\' && i + 1 < length) {
                i += 2;
                continue;
            }

            if (c == quoteChar) {
                if (i + 1 < length && script.charAt(i + 1) == quoteChar) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }

            i++;
        }

        return length;
    }

    /**
     * If a valid dollar-quote tag (e.g. {@code $$} or {@code $tag$}) starts at position i, returns
     * the index right after its matching closing tag (or the end of the script, if unterminated).
     * Returns -1 if position i is not a valid dollar-quote tag start (e.g. a bare '$' placeholder).
     */
    private static int findDollarQuoteEnd(String script, int start) {
        int length = script.length();
        int i = start + 1;
        while (i < length && isWordChar(script.charAt(i))) {
            i++;
        }
        if (i >= length || script.charAt(i) != '$') {
            return -1;
        }

        String tag = script.substring(start, i + 1);
        int closing = script.indexOf(tag, i + 1);
        return closing == -1 ? length : closing + tag.length();
    }
}
