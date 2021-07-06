package ru.gb.simplenas.common.services;

import ru.gb.simplenas.common.structs.NasMsg;


public interface FileExtruder {
    boolean initialize (final NasMsg nm, final String strData);

    boolean getState ();

    int dataBytes2File (final NasMsg nm);

    boolean endupExtruding (NasMsg nm);

    void discard ();

    void close ();

}
