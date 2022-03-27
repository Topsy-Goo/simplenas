package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import ru.gb.simplenas.client.Controller;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.structs.TableFileInfo;
import ru.gb.simplenas.common.NasCallback;
import ru.gb.simplenas.common.structs.FileInfo;
import ru.gb.simplenas.common.structs.NasMsg;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static javafx.scene.control.Alert.AlertType.ERROR;
import static javafx.scene.control.Alert.AlertType.WARNING;
import static ru.gb.simplenas.client.CFactory.*;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.services.impl.NasFileManager.rename;
import static ru.gb.simplenas.common.structs.OperationCodes.NM_OPCODE_OK;

public class TableViewManager
{
    private final Controller controller;
    private final TableView<TableFileInfo> clientTv;
    private final TableView<TableFileInfo> serverTv;
    private final TableColumn<TableFileInfo, String> columnClientFileName;
    private final TableColumn<TableFileInfo, String> columnServerFileName;
    //private static final Logger LOGGER = LogManager.getLogger(TableViewManager.class.getName());
    private       NasCallback callbackOnCancelRenaming = this::callbackDummy;


    public TableViewManager (Controller controller, NasCallback cb2)  {
        this.controller = controller;
        clientTv = controller.getTvClient();
        serverTv = controller.getTvServer();

        columnClientFileName = controller.getColumnClientFileName();
        columnServerFileName = controller.getColumnServerFileName();

    // Наделяем таблицы возможностью редактирования имён файлов и папок. (F2 тоже работает.)
        columnClientFileName.setCellFactory (TextFieldTableCell.forTableColumn());
        columnServerFileName.setCellFactory (TextFieldTableCell.forTableColumn());
        columnClientFileName.setOnEditCommit (this::eventHandlerFolderRenameing);
        columnServerFileName.setOnEditCommit (this::eventHandlerFolderRenameing);

        callbackOnCancelRenaming = cb2;
    }

    public static Point populateTv (@NotNull TableView<TableFileInfo> tv, @NotNull List<FileInfo> infolist) {
        Point point = new Point(0, 0);
        if (tv != null) {
            ObservableList<TableFileInfo> lines = tv.getItems();
            lines.clear();
            if (infolist.size() > 0) {
                for (FileInfo fi : infolist) {
                    lines.add(new TableFileInfo(fi));
                    if (fi.isDirectory()) point.x++;
                    else point.y++;
                }
                tv.sort();
            }
        }
        return point;
    }

    public static Point statisticsTv (@NotNull TableView<TableFileInfo> tv) {
        Point point = new Point(0, 0);
        if (tv != null) {
            ObservableList<TableFileInfo> data = tv.getItems();
            if (data != null) for (TableFileInfo tfi : data) {
                if (tfi.getFolder()) point.x++;
                else point.y++;
            }
        }
        return point;
    }

    public static TableFileInfo addItemAsFolder (@NotNull String name, @NotNull TableView<TableFileInfo> tv) {
        ObservableList<TableFileInfo> list = tv.getItems();

        int           position = list.size();
        TableFileInfo t        = new TableFileInfo(new FileInfo(name, FOLDER, EXISTS));
        list.add(position, t);

        tv.getSelectionModel().select(t/*position, columnServerFileName*/);
        return t;
    }

    public static void deleteTvItem (TableView<TableFileInfo> tv, TableFileInfo t) {
        ObservableList<TableFileInfo> list = tv.getItems();
        list.remove(t);
    }

//Обработчик для редактирования «Имени файла». (Источник озарения: https://betacode.net/11079/javafx-tableview )
    void eventHandlerFolderRenameing (TableColumn.CellEditEvent<TableFileInfo, String> event) {

        //This event handler will be fired when the user successfully commits their editing.
        TablePosition<TableFileInfo, String> tablePosition = event.getTablePosition();
        boolean       local   = event.getTableView() == clientTv;
        TableFileInfo tfi     = event.getRowValue();
        String        newName = event.getNewValue();
        String        old     = event.getOldValue();
//lnprint("-------------------- "+ Thread.currentThread().getName());
        String errMsg = local ? doRenameLocalFolder (tablePosition.getRow(), tfi.toFileInfo(), newName)
                              : doRenameRemoteFolder (tablePosition.getRow(), tfi.toFileInfo(), newName);
        if (errMsg == null)
            tfi.setFileName (newName);
        else {
            Controller.messageBox (ALERTHEADER_RENAMING, errMsg, WARNING);
            tfi.setFileName (old); //< не работает, сцуко! А вот если вместо old задать строку "…", то работает,
                                   //  но перестаёт создавать папки. Я манал такие API !…
                                   //  Пока только колбэк на перезаливку списка смог решить проблему.
            callbackOnCancelRenaming.callback (local);
        }
    }

//пробуем переименовать удалённую папку или файл.
    String doRenameRemoteFolder (int position, FileInfo old, String newName) {
        NetClient netClient = controller.getNetClient();
        String    errMsg    = ERROR_UNABLE_TO_PERFORM;

        if (netClient != null && position >= 0 && old != null && sayNoToEmptyStrings(newName)) {
            NasMsg nm = netClient.rename(old, newName);

            if (nm != null)
            if (nm.opCode() == NM_OPCODE_OK) {
                errMsg = null;
            }
            else if (nm.msg() != null) {
                errMsg = nm.msg();
            }
        }
        return errMsg;
    }

//пробуем переименовать локальные папку или файл
    String doRenameLocalFolder (int position, FileInfo old, String newName) {
        String errMsg = ERROR_UNABLE_TO_PERFORM;
        if (position >= 0 && old != null && sayNoToEmptyStrings (newName)) {

            Path pLocalCurrent = Paths.get (controller.getStrCurrentLocalPath()).toAbsolutePath().normalize();
            if (null != rename (pLocalCurrent, old.getFileName(), newName))
                errMsg = null;
        }
        return errMsg;
    }

    void callbackDummy (Object... objects) {}
}
