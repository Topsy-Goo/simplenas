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

public class JdbcAuthentificationProvider implements Authentificator
{
    public static final String FLD_LOGIN = "login";
    public static final String FLD_PASS = "password";
    //public static final String FLD_NICK = "nickname";
    //public static final String FRMT_DROP_TABLE = "DROP TABLE IF EXISTS [%s];";
    //public static final String FRMT_STMENT_SEL_1BY2 = "SELECT %s FROM [%s] WHERE %s = '%s' AND %s = '%s';";
    public static final String FRMT_STMENT_SEL_1BY1 = "SELECT %s FROM [%s] WHERE %s = '%s';";
    public static final String FRMT_PREPSTMENT_INS_3FLD = "INSERT INTO [%s] (%s, %s) VALUES (?, ?);";
    //public static final String FRMT_PREPSTMENT_DEL_BY1  = "DELETE FROM [%s] WHERE %s = ?;";
    //public static final String FRMT_PREPSTMENT_UPD_SET1BY1 = "UPDATE [%s] SET %s = ? WHERE %s = ?;";
    private Statement statement;
    //private PreparedStatement psUpdate1By1;
    private PreparedStatement psInsert3Fld;
    private PreparedStatement psDeleteBy1;
    private DbConnection dbConnection;
    private boolean jdbcReady;

    private static final Logger LOGGER = LogManager.getLogger(JdbcAuthentificationProvider.class);

    public JdbcAuthentificationProvider ()
    {
        dbConnection = getDbConnection();
        statement = dbConnection.getStatement();
        Connection connection = dbConnection.getConnection();

        //createDbTable (connection); < этот вызов помогает проверить, поддерживается ли используемый здесь синтаксис
        try
        {
            //psUpdate1By1 = connection.prepareStatement(
            //            sformat(FRMT_PREPSTMENT_UPD_SET1BY1, TABLE_NAME, FLD_NICK, FLD_NICK));

            psInsert3Fld = connection.prepareStatement(
                        sformat(FRMT_PREPSTMENT_INS_3FLD, TABLE_NAME, FLD_LOGIN, FLD_PASS));

            //psDeleteBy1 = connection.prepareStatement(
            //            sformat(FRMT_PREPSTMENT_DEL_BY1, TABLE_NAME, FLD_LOGIN));
            jdbcReady = true;
        }
        catch (SQLException sqle)
        {
            sqle.printStackTrace ();
            close();
            throw new RuntimeException("\nCannot create object JdbcAuthentificationProvider.");
        }
    }


    @Override public boolean isReady()  {   return this.jdbcReady;   }

    @Override public void close() //< сейчас Authentificator extends Closable
    {
        if (dbConnection != null)
            dbConnection.close(); //< одной строчкой закрываем и приравниваем нулю.
        dbConnection = null;
        statement = null; //< закрывается на стороне jdbcConnection
        try
        {
            //if (psUpdate1By1 != null)  psUpdate1By1.close();
            if (psInsert3Fld != null)  psInsert3Fld.close();
            if (psDeleteBy1 != null)   psDeleteBy1.close();
        }
        catch (SQLException sqle)  {  sqle.printStackTrace();  }
        finally
        {
            //psUpdate1By1 = null;
            psInsert3Fld = null;
            psDeleteBy1 = null;
        }
        //return null;
    }

//Возвращаем ник пользователя, которому в БД соответствуют введённые им логин и пароль.
    @Override public boolean authenticate (String login, String password)
    {
        boolean ok = false;
        if (sayNoToEmptyStrings (login, password))
        {
            String strRequest = sformat(FRMT_STMENT_SEL_1BY1, FLD_PASS, TABLE_NAME, FLD_LOGIN, login);
            try (ResultSet rs = statement.executeQuery (strRequest))
            {
                if (rs.next())
                {
                    String pswrd = rs.getString (FLD_PASS);
                    ok = pswrd.equals(password);
                }
            }
            catch (SQLException e){e.printStackTrace();}
        }
        return ok;
    }




// Добавляем данные пользователя в БД. (Сейчас он не используется.)
    @Override public boolean add (String login, String password)
    {
        boolean boolOk = false;
        if (sayNoToEmptyStrings (login, password))
        {
            try
            {
                psInsert3Fld.setString(1, login);
                psInsert3Fld.setString(2, password);
                psInsert3Fld.executeUpdate();
                //void psInsert3Fld.addBatch();
                //int[] psInsert.executeBatch();
            }
            catch (SQLException e)
            {
                LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
            }
        }
        return boolOk;
    }


// Удаляем пользователя из БД по нику. (Этот метод сейчас не используется.)
    @Override public void remove (String login)
    {
        if (sayNoToEmptyStrings (login))
        try
        {   psDeleteBy1.setString(1, login);
            psDeleteBy1.executeUpdate();
        }
        catch (SQLException e)
        {
            LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
        }
    }


/*/ В БД изменяем поле nickname == prevName на newName. (У поля nickname есть атрибут UNIQUE.)
    @Override public String rename (String prevName, String newName)
    {
        String result = null;   //< индикатор ошибки
        if (sayNoToEmptyStrings (prevName, newName))
        {
            LOGGER.debug(String.format("переименование %s >> %s", prevName, newName));
            try
            {
            //Вносим имя newName в БД вместо prevName (БД настроена так, что если такое имя уже используется, то
            // она не вернёт ошибку, но и изменять ничего не станет):
                psUpdate1By1.setString (1, newName);
                psUpdate1By1.setString (2, prevName);
                if (psUpdate1By1.executeUpdate() > 0)
            // Теперь проверяем, сделана ли в БД соотв. запись. (Соотв. графа в БД настроена на уникальные значения.)
            //  Кроме того, этот метод должен вызываться из синхронизированного контекста.
                     result = isNicknamePresent(newName);
                else result = "";
            }
            catch (SQLException e)
            {
                LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
            }
        } else LOGGER.error("переименование : битые параметры.");
        return result;
    }//*/

/*/ Удаляем таблицу с базой данных. (Этот метод сейчас не используется.)
    private void dropDbTable ()
    {
        try
        {   statement.executeUpdate(String.format(FRMT_DROP_TABLE, TABLE_NAME));
        }
        catch (SQLException e)
        {
            LOGGER.error("dropDbTable(): Data base deletion failed.");
            e.printStackTrace();
        }
    }//*/

/*/ Создаём SQL-таблицу, если она ещё не создана.
    static final String FORMAT_CREATE_TABLE_IFEXISTS_SQLITE =
            "CREATE TABLE IF NOT EXISTS [%s] (" +
            "%s STRING NOT NULL UNIQUE ON CONFLICT IGNORE PRIMARY KEY, " +
            "%s STRING NOT NULL, " +
            "%s STRING NOT NULL UNIQUE ON CONFLICT IGNORE);"
            ;
//  CREATE SCHEMA `marchchat`;
    static final String FORMAT_CREATE_TABLE_IFEXISTS_MYSQL =    //< попробовали, выходит ли на связь MySQL: выходит. ура.
            "CREATE TABLE `marchchat`.`marchchat users` (" +
            "`login` VARCHAR(45) NOT NULL," +
            "`password` VARCHAR(45) NOT NULL," +
            "`nickname` VARCHAR(45) NOT NULL," +
            "PRIMARY KEY (`login`)," +
            "UNIQUE INDEX `nickname_UNIQUE` (`nickname` ASC) VISIBLE," +
            "UNIQUE INDEX `login_UNIQUE` (`login` ASC) VISIBLE);"
            ;
    private int createDbTable (Connection connection)
    {
        int result = -1;
        try (Statement stnt = connection.createStatement())
        {
            result = stnt.executeUpdate (String.format(FORMAT_CREATE_TABLE_IFEXISTS_SQLITE,
                                         TABLE_NAME, FLD_LOGIN, FLD_PASS, FLD_NICK, FLD_LOGIN));
        }
        catch (SQLException e){e.printStackTrace();}
        return result;
    }//*/

/*/(Вспомогательная.) Проверяет наличие ника в базе.
    private String isNicknamePresent (String nickname)
    {
        String result = null;
        if (sayNoToEmptyStrings (nickname))
        {
            String strRequest = sformat(FRMT_STMENT_SEL_1BY1, FLD_NICK, TABLE_NAME, FLD_NICK, nickname);
            try (ResultSet rs = statement.executeQuery (strRequest))
            {
                if (rs.next())
                    result = rs.getString (FLD_NICK); //или rs.getString (№);
                else
                    result = ""; //< индикатор того, что чтение не состоялось

                LOGGER.debug("ответ базы : "+ result);
            }
            catch (SQLException e)
            {
                LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
            }
        }
        return result;
    }//*/


}
