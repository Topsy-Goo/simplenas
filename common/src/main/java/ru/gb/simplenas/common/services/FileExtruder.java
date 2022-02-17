package ru.gb.simplenas.common.services;

import ru.gb.simplenas.common.structs.NasMsg;

import java.io.IOException;


public interface FileExtruder {

    void writeDataBytes2File (byte[] data, int size) throws IOException;

    boolean endupExtruding (NasMsg nm);

    void discard();

    void close();
}
