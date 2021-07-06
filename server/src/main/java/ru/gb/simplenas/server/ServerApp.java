package ru.gb.simplenas.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;

import static ru.gb.simplenas.common.CommonData.STRPATH_CLOUD;
import static ru.gb.simplenas.common.Factory.createCloudFolder;
import static ru.gb.simplenas.server.SFactory.nasProperyManager;
import static ru.gb.simplenas.server.SFactory.startSеrver;


public class ServerApp
{
    private static final Logger LOGGER = LogManager.getLogger(ServerApp.class.getName());


    public static void main(String[] args)
    {
        LOGGER.fatal("------------------------------------ ");
        LOGGER.info("main(): Начало работы сервера");
        if (initApplication())
        {
            startSеrver();
            freeAppData();
        }
        LOGGER.info("main(): Сервер прекратил работу");
    }

    private static boolean initApplication()
    {
        boolean ok = false;

        nasProperyManager();

        if (createCloudFolder (STRPATH_CLOUD))
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

    private static void freeAppData()
    {
    }

}
