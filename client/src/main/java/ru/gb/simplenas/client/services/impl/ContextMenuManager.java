package ru.gb.simplenas.client.services.impl;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.WindowEvent;
import ru.gb.simplenas.client.Controller;
import ru.gb.simplenas.client.structs.TableFileInfo;

public class ContextMenuManager
{
    private final Controller controller;

    public ContextMenuManager (Controller c) {
        controller = c;
    }

/** Универсальный обработчик контекстного меню для обоих панелей. Вызывается перед тем, как меню будет показано.<p>
    Здесь мы выбираем, какие пункты следует сделать доступными, а какие — нет.
    Чтобы не усложнять способ определения пунктов, им назначены атрибуты userData (см. FXML-файл):<br>
    • opendir — открыть папку;<br>
    • sendfile — отправить файл;<br>
    • newdir — создать папку;<br>
    • del — удалить папку или файл;<br>
    • conn — подключение к удалённому хранилищу или отключение от него;<br>
    .    */
    public void setEventHandler_OnShoing (ContextMenu menu, TableView<TableFileInfo> tv)
    {
        if (menu != null && tv != null)
            menu.setOnShowing (new EventHandler<WindowEvent>() {
                @Override public void handle (WindowEvent event)
                {
                    TableFileInfo tfi       = tv.getSelectionModel().getSelectedItem();
                    boolean       local     = controller.isLocalView (tv); //< true == конт.меню вызвано для локальной панели
                    boolean       connected = controller.isConnected(); //< есть ли соединение с удал.хранилищем
                    boolean       selected  = tfi != null;  //< один из пунктов таблицы выбран
                    boolean       dir       = selected && tfi.getFolder();  //< выбранный пункт является папкой

                    ObservableList<MenuItem> list = menu.getItems();
                    for (MenuItem mi : list) {   //кажется, для userData в FXML-файле можно задать только строку.
                        String ud = (String) mi.getUserData();
                        if (ud != null)
                        switch (ud)
                        {
                            case "opendir":
                                mi.setDisable (!(dir && (local || connected)));
                                break;
                            case "sendfile":
                                mi.setDisable (!(selected && !dir && connected));
                                break;
                            case "del":
                                mi.setDisable (!((local || connected) && selected));
                                break;
                            case "newdir":
                                mi.setDisable (!(local || connected));
                                break;
                            case "conn":
                                mi.setText (connected ? "Отключиться" : "Подключиться");
                            default:
                                mi.setDisable (false);
                        }
                    }
                }
        });
    }
}
