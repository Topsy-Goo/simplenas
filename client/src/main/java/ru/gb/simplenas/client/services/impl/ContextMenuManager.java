package ru.gb.simplenas.client.services.impl;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.WindowEvent;
import ru.gb.simplenas.client.structs.TableFileInfo;

public class ContextMenuManager
{

//Обработчик контекстного меню. Вызывается перед тем, как меню будет показано. Здесь мы выбираем, какие
//  пункты следует сделать доступными, а какие — нет. Чтобы не усложнять способ определения пунктов, им
//  назначены атрибуты userData (см. FXML-файл).
    public static void setContextMenuEventHandler_OnShoing (ContextMenu menu, TableView<TableFileInfo> tv)
    {
        if (menu != null && tv != null)
        menu.setOnShowing(new EventHandler<WindowEvent>()
        {
            @Override public void handle (WindowEvent event)
            {
                TableFileInfo tfi = tv.getSelectionModel().getSelectedItem();
                boolean noselection = tfi == null;      //< ни один из пунктов таблицы не выбран
                boolean dir = !noselection && tfi.getFolder();  //< выбранный пункт является папкой

                ObservableList<MenuItem> list = menu.getItems();
                for (MenuItem mi : list)
                {   //кажется, для userData в FXML-файле можно задать только строку.
                    String ud = (String) mi.getUserData();
                    if (ud != null)
                    {
                        if (ud.equals("dir"))
                            mi.setDisable (noselection || !dir);

                        if (ud.equals("file"))
                            mi.setDisable (noselection || dir);

                        if (ud.equals("any"))
                            mi.setDisable (noselection);
                    }
                }
            }
        });
    }

}
