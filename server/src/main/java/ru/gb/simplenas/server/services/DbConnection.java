package ru.gb.simplenas.server.services;

import java.sql.Connection;
import java.sql.Statement;

public interface DbConnection
{
    void close();

    Connection getConnection();

    Statement getStatement();
}
