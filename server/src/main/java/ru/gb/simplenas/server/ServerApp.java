package ru.gb.simplenas.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;

import static ru.gb.simplenas.server.SFactory.*;


public class ServerApp {

    private static final Logger LOGGER = LogManager.getLogger(ServerApp.class.getName());

    public static void main (String[] args) {
        LOGGER.info("------------------------------------ ");
        LOGGER.info("main(): Начало работы сервера");
        if (initApplication())
            startServer().run(); //< Здесь поток main переходит на обслуживание только метода run(), а всю работу по обслуживанию клиентов выполняют потоки пулов.
        LOGGER.info("main(): Сервер прекратил работу");
    }

    private static boolean initApplication () {
        boolean ok = false;
        /*if (initFlyway())*/ {
            ok = true;
        }
        return ok;
    }

    private static boolean initFlyway () {
        Flyway flyway = Flyway.configure()
                              .dataSource ("jdbc:h2://localhost:3306./target/foobar", "root", null)
                              .load();
        flyway.migrate();
        return true;
    }
}
