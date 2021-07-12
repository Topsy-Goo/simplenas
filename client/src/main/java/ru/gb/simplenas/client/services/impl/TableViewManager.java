package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import ru.gb.simplenas.client.CFactory;
import ru.gb.simplenas.client.Controller;
import ru.gb.simplenas.client.services.NetClient;
import ru.gb.simplenas.client.structs.TableFileInfo;
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
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;
import static ru.gb.simplenas.common.structs.OperationCodes.OK;

public class TableViewManager {
    private final Controller controller;
    private TableView<TableFileInfo> clientTv;
    private TableView<TableFileInfo> serverTv;
    private TableColumn<TableFileInfo, String> columnClientFileName;
    private TableColumn<TableFileInfo, String> columnServerFileName;


    public TableViewManager (Controller controller) {
        this.controller = controller;
        clientTv = controller.getTvClient();
        serverTv = controller.getTvServer();

        columnClientFileName = controller.getColumnClientFileName();
        columnServerFileName = controller.getColumnServerFileName();

        columnClientFileName.setCellFactory(TextFieldTableCell.<TableFileInfo>forTableColumn());
        columnServerFileName.setCellFactory(TextFieldTableCell.<TableFileInfo>forTableColumn());
        columnClientFileName.setOnEditCommit(this::eventHandlerFolderRenameing);
        columnServerFileName.setOnEditCommit(this::eventHandlerFolderRenameing);
    }

    public static Point populateTv (@NotNull TableView<TableFileInfo> tv, @NotNull List<FileInfo> infolist) {
        Point point = new Point(0, 0);
        if (tv != null) {
            ObservableList<TableFileInfo> lines = tv.getItems();
            lines.clear();
            if (infolist.size() > 0) {
                for (FileInfo fi : infolist) {
                    lines.add(new TableFileInfo(fi));
                    if (fi.isDirectory()) { point.x++; }
                    else { point.y++; }
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
            if (data != null) {
                for (TableFileInfo tfi : data) {
                    if (tfi.getFolder()) { point.x++; }
                    else { point.y++; }
                }
            }
        }
        return point;
    }

    public static TableFileInfo addItemAsFolder (@NotNull String name, @NotNull TableView<TableFileInfo> tv) {
        ObservableList<TableFileInfo> list = tv.getItems();

        int position = list.size();
        TableFileInfo t = new TableFileInfo(new FileInfo(name, FOLDER, EXISTS));
        list.add(position, t);

        tv.getSelectionModel().select(t);
        return t;
    }

    public static void deleteTvItem (TableView<TableFileInfo> tv, TableFileInfo t) {
        ObservableList<TableFileInfo> list = tv.getItems();
        list.remove(t);
    }

    void eventHandlerFolderRenameing (TableColumn.CellEditEvent<TableFileInfo, String> event) {
        TablePosition<TableFileInfo, String> tablePosition = event.getTablePosition();
        boolean local = event.getTableView() == clientTv;

        TableFileInfo tfi = event.getRowValue();
        String newName = event.getNewValue();
        String old = event.getOldValue();

        boolean ok = local ? doRenameLocalFolder(tablePosition.getRow(), tfi.toFileInfo(), newName) : doRenameRemoteFolder(tablePosition.getRow(), tfi.toFileInfo(), newName);
        if (ok) {
            tfi.setFileName(newName);
        }
        else { Controller.messageBox(ALERTHEADER_RENAMING, String.format(PROMPT_FORMAT_RENAMING_ALREADY_EXISTS, newName), WARNING); }
    }

    boolean doRenameRemoteFolder (int position, FileInfo old, String newName) {
        boolean ok = false;
        NetClient netClient = controller.getNetClient();
        String errMsg = ERROR_UNABLE_TO_PERFORM;

        if (netClient != null && position >= 0 && old != null && sayNoToEmptyStrings(newName)) {
            NasMsg nm = netClient.rename(old, newName);
            if (nm != null) {
                if (nm.opCode() == OK) {
                    ok = true;
                }
                else if (nm.msg() != null) {
                    errMsg = nm.msg();
                }
            }
        }
        if (!ok) { Controller.messageBox(ALERTHEADER_RENAMING, errMsg, ERROR); }
        return ok;
    }

    boolean doRenameLocalFolder (int position, FileInfo old, String newName) {
        boolean ok = false;
        if (position >= 0 && old != null && sayNoToEmptyStrings(newName)) {
            Path pLocalCurrent = Paths.get(controller.getStrCurrentLocalPath()).toAbsolutePath().normalize();
            ok = null != rename(pLocalCurrent, old.getFileName(), newName);
        }
        if (!ok) { Controller.messageBox(CFactory.ALERTHEADER_LOCAL_STORAGE, ERROR_UNABLE_TO_PERFORM, ERROR); }
        return ok;
    }

}
