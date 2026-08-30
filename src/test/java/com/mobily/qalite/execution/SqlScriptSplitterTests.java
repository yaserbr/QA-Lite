package com.mobily.qalite.execution;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptSplitterTests {

    @Test
    void singleStatementWithoutTrailingSemicolonIsReturnedAsIs() {
        List<String> statements = SqlScriptSplitter.split("select 1 as value");

        assertEquals(List.of("select 1 as value"), statements);
    }

    @Test
    void splitsMultipleStatementsOnSemicolons() {
        String script = """
                CREATE TABLE departments (dept_id INT AUTO_INCREMENT PRIMARY KEY, dept_name VARCHAR(50) NOT NULL);
                CREATE TABLE employees (emp_id INT AUTO_INCREMENT PRIMARY KEY, dept_id INT, FOREIGN KEY (dept_id) REFERENCES departments(dept_id));
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE departments"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE employees"));
    }

    @Test
    void ignoresSemicolonsInsideSingleQuotedStrings() {
        String script = "INSERT INTO t (a) VALUES ('a;b'); INSERT INTO t (a) VALUES ('c')";

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(List.of("INSERT INTO t (a) VALUES ('a;b')", "INSERT INTO t (a) VALUES ('c')"), statements);
    }

    @Test
    void ignoresSemicolonsInsideJsonStringLiterals() {
        String script = "INSERT INTO employees (metadata) VALUES ('{\"skills\": [\"sql\", \"python\"]}')";

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(List.of(script), statements);
    }

    @Test
    void handlesDoubledSingleQuoteEscape() {
        String script = "INSERT INTO t (a) VALUES ('it''s; still one string'); SELECT 1";

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t (a) VALUES ('it''s; still one string')", statements.get(0));
        assertEquals("SELECT 1", statements.get(1));
    }

    @Test
    void ignoresSemicolonsInsideBacktickIdentifiers() {
        String script = "SELECT `weird;name` FROM t; SELECT 2";

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(List.of("SELECT `weird;name` FROM t", "SELECT 2"), statements);
    }

    @Test
    void stripsLineAndBlockComments() {
        String script = """
                -- leading comment
                SELECT 1; /* block ; comment */ SELECT 2 -- trailing comment
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).endsWith("SELECT 1"));
        assertTrue(statements.get(1).contains("SELECT 2"));
    }

    @Test
    void blankAndCommentOnlyScriptsProduceNoStatements() {
        assertEquals(List.of(), SqlScriptSplitter.split("   "));
        assertEquals(List.of(), SqlScriptSplitter.split("-- just a comment\n"));
    }

    @Test
    void splitsTheMysqlScriptFromTheBugReportIntoFiveStatements() {
        String script = """
                CREATE DATABASE company_db;
                USE company_db;

                CREATE TABLE departments (dept_id INT AUTO_INCREMENT PRIMARY KEY, dept_name VARCHAR(50) NOT NULL);
                CREATE TABLE employees (emp_id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), dept_id INT, salary DECIMAL(10,2), hire_date DATE, metadata JSON, FOREIGN KEY (dept_id) REFERENCES departments(dept_id));
                CREATE TABLE sales (sale_id INT AUTO_INCREMENT PRIMARY KEY, emp_id INT, amount DECIMAL(10,2), sale_date DATE, FOREIGN KEY (emp_id) REFERENCES employees(emp_id));
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(5, statements.size());
        assertEquals("CREATE DATABASE company_db", statements.get(0));
        assertEquals("USE company_db", statements.get(1));
    }

    @Test
    void splitsThePostgresScriptFromTheBugReportIntoSixStatements() {
        String script = """
                CREATE TABLE departments (
                    dept_id SERIAL PRIMARY KEY,
                    dept_name VARCHAR(50) NOT NULL
                );

                CREATE TABLE employees (
                    emp_id SERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    dept_id INT REFERENCES departments(dept_id),
                    salary NUMERIC(10,2),
                    hire_date DATE,
                    metadata JSONB
                );

                CREATE TABLE sales (
                    sale_id SERIAL PRIMARY KEY,
                    emp_id INT REFERENCES employees(emp_id),
                    amount NUMERIC(10,2),
                    sale_date DATE
                );

                INSERT INTO departments (dept_name) VALUES
                ('Engineering'), ('Sales'), ('HR');

                INSERT INTO employees (name, dept_id, salary, hire_date, metadata) VALUES
                ('Ahmed', 1, 9000, '2022-01-15', '{"skills": ["sql", "python"]}'),
                ('Sara', 2, 7000, '2023-03-10', '{"skills": ["excel"]}'),
                ('Mohammed', 1, 12000, '2021-07-01', '{"skills": ["sql", "java"]}'),
                ('Noura', 3, 6500, '2024-02-20', '{}');

                INSERT INTO sales (emp_id, amount, sale_date) VALUES
                (2, 1500, '2026-01-05'),
                (2, 2200, '2026-02-10'),
                (1, 500, '2026-01-20');
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(6, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE departments"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE employees"));
        assertTrue(statements.get(2).startsWith("CREATE TABLE sales"));
        assertTrue(statements.get(3).startsWith("INSERT INTO departments"));
        assertTrue(statements.get(4).startsWith("INSERT INTO employees"));
        assertTrue(statements.get(5).startsWith("INSERT INTO sales"));
    }

    @Test
    void oracleAnonymousBlockWithInternalSemicolonsStaysOneStatementUntilSlash() {
        String script = """
                BEGIN
                    INSERT INTO departments (dept_name) VALUES ('Engineering');
                    INSERT INTO departments (dept_name) VALUES ('Sales');
                END;
                /
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).startsWith("BEGIN"));
        assertTrue(statements.get(0).endsWith("END;"));
        assertTrue(statements.get(0).contains("Engineering"));
        assertTrue(statements.get(0).contains("Sales"));
    }

    @Test
    void oracleCreateProcedureBodyIsOneStatementAndScriptContinuesAfterSlash() {
        String script = """
                CREATE TABLE departments (dept_id INT PRIMARY KEY, dept_name VARCHAR2(50));

                CREATE OR REPLACE PROCEDURE add_department(p_name IN VARCHAR2) IS
                BEGIN
                    INSERT INTO departments (dept_name) VALUES (p_name);
                    COMMIT;
                END add_department;
                /

                SELECT * FROM departments;
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(3, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE departments"));
        assertTrue(statements.get(1).startsWith("CREATE OR REPLACE PROCEDURE add_department"));
        assertTrue(statements.get(1).contains("COMMIT;"));
        assertTrue(statements.get(1).trim().endsWith("END add_department;"));
        assertEquals("SELECT * FROM departments", statements.get(2));
    }

    @Test
    void oracleNestedBeginEndAndControlStatementsDoNotBreakOutOfTheBlock() {
        String script = """
                BEGIN
                    IF 1 = 1 THEN
                        BEGIN
                            NULL;
                        END;
                    END IF;

                    FOR i IN 1..3 LOOP
                        NULL;
                    END LOOP;
                END;
                /
                SELECT 1 FROM dual;
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("BEGIN"));
        assertTrue(statements.get(0).trim().endsWith("END;"));
        assertEquals("SELECT 1 FROM dual", statements.get(1));
    }

    @Test
    void mysqlCreateProcedureBodyStaysOneStatementWithoutRequiringSlash() {
        String script = """
                CREATE TABLE departments (dept_id INT AUTO_INCREMENT PRIMARY KEY, dept_name VARCHAR(50));

                CREATE PROCEDURE add_department(IN p_name VARCHAR(50))
                BEGIN
                    INSERT INTO departments (dept_name) VALUES (p_name);
                END;

                SELECT * FROM departments;
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(3, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE departments"));
        assertTrue(statements.get(1).startsWith("CREATE PROCEDURE add_department"));
        assertTrue(statements.get(1).trim().endsWith("END;"));
        assertEquals("SELECT * FROM departments", statements.get(2));
    }

    @Test
    void postgresDollarQuotedFunctionBodyIsOneStatement() {
        String script = """
                CREATE OR REPLACE FUNCTION add_department(p_name TEXT) RETURNS VOID AS $$
                BEGIN
                    INSERT INTO departments (dept_name) VALUES (p_name);
                END;
                $$ LANGUAGE plpgsql;

                SELECT * FROM departments;
                """;

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE OR REPLACE FUNCTION add_department"));
        assertTrue(statements.get(0).contains("$$"));
        assertTrue(statements.get(0).trim().endsWith("LANGUAGE plpgsql"));
        assertEquals("SELECT * FROM departments", statements.get(1));
    }

    @Test
    void postgresDollarQuotedBodyContainingSemicolonsIsNotSplit() {
        String script = "CREATE FUNCTION f() RETURNS int AS $tag$ SELECT 1; SELECT 2; $tag$ LANGUAGE sql; SELECT 3";

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("$tag$ SELECT 1; SELECT 2; $tag$"));
        assertEquals("SELECT 3", statements.get(1));
    }

    @Test
    void bareDollarSignThatIsNotATagIsTreatedAsALiteralCharacter() {
        String script = "SELECT * FROM t WHERE id = $1; SELECT 2";

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(List.of("SELECT * FROM t WHERE id = $1", "SELECT 2"), statements);
    }

    @Test
    void ordinaryCaseExpressionOutsideAnyBlockDoesNotAffectSplitting() {
        String script = "SELECT CASE WHEN 1 = 1 THEN 'a' ELSE 'b' END AS v; SELECT 2";

        List<String> statements = SqlScriptSplitter.split(script);

        assertEquals(
                List.of("SELECT CASE WHEN 1 = 1 THEN 'a' ELSE 'b' END AS v", "SELECT 2"),
                statements
        );
    }
}
