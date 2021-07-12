package ru.gb.simplenas.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static ru.gb.simplenas.server.SFactory.startServer;


public class ServerApp {
    private static final Logger LOGGER = LogManager.getLogger(ServerApp.class.getName());


    public static void main (String[] args) {
        LOGGER.info("------------------------------------ ");
        LOGGER.info("main(): Начало работы сервера");
        startServer();
        LOGGER.info("main(): Сервер прекратил работу");
    }

}
