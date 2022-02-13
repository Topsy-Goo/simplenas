package ru.gb.simplenas.server.services;

public interface Authentificator {

    boolean authenticate (String login, String password);

    boolean add (String lgn, String psw);

    void remove (String login);

    void close();
}
