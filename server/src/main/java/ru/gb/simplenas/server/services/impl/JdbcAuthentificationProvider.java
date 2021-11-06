package ru.gb.simplenas.server.services.impl;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.server.services.Authentificator;
import ru.gb.simplenas.server.services.DbConnection;

import java.sql.*;

import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.Factory.sformat;
import static ru.gb.simplenas.server.SFactory.TABLE_NAME;
import static ru.gb.simplenas.server.SFactory.getDbConnection;

public class JdbcAuthentificationProvider implements Authentificator {

    public static final  String FILD_LOGIN                              = "login";
    public static final  String FILD_PASS                               = "password";
    public static final  String FORMAT_STATEMENT_SELECT_1BY1            = "SELECT %s FROM [%s] WHERE %s = '%s';";
    public static final  String FORMAT_PREPARED_STATEMENT_INSERT_2FILDS = "INSERT INTO [%s] (%s, %s) VALUES (?, ?);";
    private static final Logger LOGGER = LogManager.getLogger(JdbcAuthentificationProvider.class);

    private Statement         statement;
    private PreparedStatement psInsert3Fld;
    private PreparedStatement psDeleteBy1;
    private DbConnection      dbConnection;
    private boolean           jdbcReady;

    public JdbcAuthentificationProvider () {
        dbConnection = getDbConnection();
        statement = dbConnection.getStatement();
        Connection connection = dbConnection.getConnection();

        try {
            psInsert3Fld = connection.prepareStatement (sformat(FORMAT_PREPARED_STATEMENT_INSERT_2FILDS,
                                                                TABLE_NAME, FILD_LOGIN, FILD_PASS));
            jdbcReady = true;
        }
        catch (SQLException sqle) {
            sqle.printStackTrace();
            close();
            throw new RuntimeException("\nCannot create object JdbcAuthentificationProvider.");
        }
    }
//---------------------------------------------------------------------------------------------------------------*/

    @Override public boolean isReady () { return this.jdbcReady; }

    @Override public void close () {
        if (dbConnection != null) dbConnection.close();
        dbConnection = null;
        statement = null; //< закрывается на стороне jdbcConnection
        try {
            if (psInsert3Fld != null) psInsert3Fld.close();
            if (psDeleteBy1 != null) psDeleteBy1.close();
        }
        catch (SQLException sqle) { sqle.printStackTrace(); }
        finally {
            psInsert3Fld = null;
            psDeleteBy1 = null;
        }
    }
//---------------------------------------------------------------------------------------------------------------*/

/** Возвращаем ник пользователя, которому в БД соответствуют введённые им логин и пароль. */
    @Override public boolean authenticate (String login, String password) {
        boolean ok = false;
        if (sayNoToEmptyStrings(login, password)) {
            String strRequest = sformat(FORMAT_STATEMENT_SELECT_1BY1, FILD_PASS, TABLE_NAME, FILD_LOGIN, login);
            try (ResultSet rs = statement.executeQuery(strRequest)) {
                if (rs.next()) {
                    String pswrd = rs.getString(FILD_PASS);
                    ok = pswrd.equals(password);
                }
            }
            catch (SQLException e) {e.printStackTrace();}
        }
        return ok;
    }

/** Добавляем данные пользователя в БД. */
    @Override public boolean add (String login, String password) {
        boolean boolOk = false;
        if (sayNoToEmptyStrings(login, password)) {
            try {
                psInsert3Fld.setString(1, login);
                psInsert3Fld.setString(2, password);
                psInsert3Fld.executeUpdate();
            }
            catch (SQLException e) {
                LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
            }
        }
        return boolOk;
    }

/** Удаляем пользователя из БД по нику. */
    @Override public void remove (String login) {
        if (sayNoToEmptyStrings(login)) {
            try {
                psDeleteBy1.setString(1, login);
                psDeleteBy1.executeUpdate();
            }
            catch (SQLException e) {
                LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
            }
        }
    }
}
