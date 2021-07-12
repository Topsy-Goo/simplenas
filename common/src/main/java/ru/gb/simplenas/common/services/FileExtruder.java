package ru.gb.simplenas.common.services;

import ru.gb.simplenas.common.structs.NasMsg;

import java.nio.file.Path;


public interface FileExtruder {
    boolean initialize (Path ptargetfile);

    boolean getState ();

    int writeDataBytes2File (final NasMsg nm);

    boolean endupExtruding (NasMsg nm);

    void discard ();

    void close ();

}
