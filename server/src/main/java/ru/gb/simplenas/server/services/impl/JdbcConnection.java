package ru.gb.simplenas.server.services.impl;

import ru.gb.simplenas.server.services.DbConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static ru.gb.simplenas.common.Factory.errprintf;
import static ru.gb.simplenas.server.SFactory.CLASS_NAME;
import static ru.gb.simplenas.server.SFactory.DATABASE_URL;

public class JdbcConnection implements DbConnection {

    private static DbConnection instance;
    private        Connection   connection;
    private        Statement    statement;


    private JdbcConnection () {
        try {
            connection = DriverManager.getConnection(DATABASE_URL);
            Class.forName(CLASS_NAME);
            statement = connection.createStatement();
        }
        catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        catch (ClassNotFoundException e) {
            errprintf("\nERROR @ JdbcConnection(): Class «%s» not found.", CLASS_NAME);
            e.printStackTrace();
            throw new RuntimeException("\nCannot create object JdbcConnection.");
        }
    }

    public static DbConnection getInstance () {
        if (instance == null) instance = new JdbcConnection();
        return instance;
    }
//---------------------------------------------------------------------------------------------------------------*/

    @Override public void close () {
        try {
            if (connection != null) connection.close();
            if (statement != null) statement.close();
        }
        catch (SQLException e) {e.printStackTrace();}
        finally {
            connection = null;
            statement = null;
        }
    }

    @Override public Connection getConnection () { return connection; }

    @Override public Statement getStatement ()   { return statement; }
}
