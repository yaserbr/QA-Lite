package com.mobily.qalite.execution;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlParameterParserTests {

    @Test
    void extractsNoParametersFromAPlainStatement() {
        assertEquals(Set.of(), SqlParameterParser.extractParameterNames("select * from employees"));
    }

    @Test
    void extractsParameterNamesInFirstOccurrenceOrderDeduplicated() {
        String sql = "select * from employees where dept_id = :dept_id or mgr_dept_id = :dept_id "
                + "and hire_date > :since";

        Set<String> names = SqlParameterParser.extractParameterNames(sql);

        assertEquals(List.of("dept_id", "since"), List.copyOf(names));
    }

    @Test
    void ignoresColonsInsideSingleQuotedStrings() {
        assertEquals(
                Set.of(),
                SqlParameterParser.extractParameterNames("select '2026-01-01T00:00:00' as ts")
        );
    }

    @Test
    void ignoresColonsInsideJsonStringLiterals() {
        assertEquals(
                Set.of(),
                SqlParameterParser.extractParameterNames(
                        "insert into t (metadata) values ('{\"time\": \"12:30\"}')")
        );
    }

    @Test
    void ignoresColonsInsideComments() {
        String sql = "select 1 -- comment with a :fake param\n"
                + "/* another :fake one */";

        assertEquals(Set.of(), SqlParameterParser.extractParameterNames(sql));
    }

    @Test
    void ignoresColonsInsideDollarQuotedBodies() {
        String sql = "select $$ this looks like :not_a_param $$ as v";

        assertEquals(Set.of(), SqlParameterParser.extractParameterNames(sql));
    }

    @Test
    void doesNotMistakePostgresCastOperatorForAParameter() {
        assertEquals(
                Set.of(),
                SqlParameterParser.extractParameterNames("select amount::numeric from sales")
        );
    }

    @Test
    void aRealParameterRightAfterACastStillMatches() {
        assertEquals(
                Set.of("amount"),
                SqlParameterParser.extractParameterNames("select :amount::numeric from sales")
        );
    }

    @Test
    void prepareRewritesEachOccurrenceToAPlaceholderAndBindsInOrder() {
        String sql = "select * from employees where dept_id = :dept_id and mgr_dept_id = :dept_id "
                + "and hire_date > :since";

        SqlParameterParser.PreparedSql prepared = SqlParameterParser.prepare(
                sql,
                Map.of("dept_id", "10", "since", "2022-01-01")
        );

        assertEquals(
                "select * from employees where dept_id = ? and mgr_dept_id = ? and hire_date > ?",
                prepared.sql()
        );
        assertEquals(List.of("10", "10", "2022-01-01"), prepared.values());
    }

    @Test
    void prepareWithNoParametersReturnsSqlUnchangedAndNoValues() {
        SqlParameterParser.PreparedSql prepared = SqlParameterParser.prepare("select 1", Map.of());

        assertEquals("select 1", prepared.sql());
        assertEquals(List.of(), prepared.values());
    }

    @Test
    void prepareThrowsWhenAReferencedParameterHasNoValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SqlParameterParser.prepare("select * from t where id = :id", Map.of())
        );

        assertEquals("Missing value for parameter :id", exception.getMessage());
    }

    @Test
    void prepareThrowsWhenParameterValuesMapIsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SqlParameterParser.prepare("select * from t where id = :id", null)
        );
    }
}
