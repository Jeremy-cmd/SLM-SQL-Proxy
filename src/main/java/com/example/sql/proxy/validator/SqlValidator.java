package com.example.sql.proxy;

import com.example.sql.proxy.Exception.SqlSafetyException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

@Component
public class SqlValidator {

    public void validateSQLQuery(String generatedSQL)  {

        try {
            Statement statement = CCJSqlParserUtil.parse(generatedSQL);

            if (!(statement instanceof Select)) {
                throw new SqlSafetyException("Only SELECT queries are allowed!");
            }
        }
        catch(JSQLParserException e) {
            throw new SqlSafetyException("AI generated invalid SQL syntax: " + e.getMessage());
        }

    }
}
