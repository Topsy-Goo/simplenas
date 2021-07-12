package ru.gb.simplenas.client.services;

import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.structs.NasMsg;

public interface ClientManipulator extends Manipulator {
    boolean startListRequest (NasMsg nm);

    boolean startLoad2LocalRequest (String toLocalFolder, NasMsg nm);

    boolean startLoad2ServerRequest (String fromLocalFolder, NasMsg nm);

    boolean startSimpleRequest (NasMsg nm);

}
