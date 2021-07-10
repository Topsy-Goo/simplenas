package ru.gb.simplenas.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;

import static ru.gb.simplenas.server.SFactory.*;


public class ServerApp
{
    private static final Logger LOGGER = LogManager.getLogger(ServerApp.class.getName());


    public static void main (String[] args)
    {
        LOGGER.info("------------------------------------ ");
        LOGGER.info("main(): Начало работы сервера");
        if (initApplication())
        {
            startServer();
        }
        LOGGER.info("main(): Сервер прекратил работу");
    }

    private static boolean initApplication()
    {
        boolean ok = false;

        //if (initFlyway())
        //if ()
        //if ()
        {
            ok = true;
        }
        return ok;
    }

    private static boolean initFlyway()
    {
        // Create the Flyway instance and point it to the database
        Flyway flyway = Flyway
                        .configure()
                        .dataSource("jdbc:h2://localhost:3306./target/foobar", "root", null)
                        .load();
        // Start the migration
        flyway.migrate();
        return true;
    }

}
