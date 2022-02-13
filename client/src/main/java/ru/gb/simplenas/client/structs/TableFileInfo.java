package ru.gb.simplenas.client.structs;

import org.jetbrains.annotations.NotNull;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import ru.gb.simplenas.common.structs.FileInfo;

import java.nio.file.attribute.FileTime;

import static ru.gb.simplenas.client.CFactory.NO_SIZE_VALUE;
import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.client.CFactory.formatFileTime;

/** Класс содержит данные для одной строки таблицы, а также методы, необходимые для того, чтобы JFX могла
 работать со строкой.<p>
Переменные — для отображения данных в таблице имеют особый формат. Если требуется форматирование
числовых значений, то требуется специальный callback-обработчик (в качестве примера такого
обработчика см. в FormattedTableCellFactory).<p>
Гетеры и сетеры для этих переменных тоже имеют особый формат, потому что ими пользуется javafx
при добавлении значений в таблицу и при извлечении значений.
*/
public class TableFileInfo {

//строковые поля служат для отображения в таблице. Ими оперирует jfx.
    private final SimpleStringProperty folderMark   = new SimpleStringProperty(STR_EMPTY);    //< пустая строка останется пустой для файлов
    private final SimpleStringProperty fileName     = new SimpleStringProperty (STR_EMPTY);
    private final SimpleStringProperty timeModified = new SimpleStringProperty (STR_EMPTY); //< пустая строка останется пустой для папок
    private final SimpleStringProperty timeCreated  = new SimpleStringProperty (STR_EMPTY); //< ...

//числовые и логические поля удобны для обмена данными в остальной части программы.
//(jfx может без особого труда пользоваться и НЕстроковыми полями, о чём свидетельствует поле size.)
    private final SimpleLongProperty size     = new SimpleLongProperty (NO_SIZE_VALUE); //< NO_SIZE_VALUE заставит колбэк поместить в ячейку пустую строку (см.FormattedTableCellFactory)
    private final SimpleLongProperty modified = new SimpleLongProperty();
    private final SimpleLongProperty created  = new SimpleLongProperty();
    private final SimpleBooleanProperty folder    = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty symbolic  = new SimpleBooleanProperty(false);

    static final String FORMAT_FILEINFO = "(%s:%s%s%s:sz%d:tm%s:tc%s)";


    public TableFileInfo() { this("", 0L, 0L, 0L, false, false); }

    public TableFileInfo (String name, long size, long modified, long created, boolean folder, boolean symbolic) {
        setFileName (name);
        setFolder   (folder);
        setSymbolic (symbolic);
        if (!folder) {
            setSize (size);
            setCreated      (created);
            setTimeCreated  (created);
            setModified     (modified);
            setTimeModified (modified);
            setFolderMark (STR_EMPTY);
        }
        else setFolderMark (FOLDER_MARK);
    }

    public TableFileInfo (FileInfo fi) {
        if (fi == null)
            throw new IllegalArgumentException ("ERROR @ TableFileInfo (FileInfo): bad FileInfo passed in.");

        setFileName (fi.getFileName());
        setSymbolic (fi.isSymbolic());
        if (fi.isDirectory()) {
            setFolder (true);
            setFolderMark (FOLDER_MARK); /* < Этот индикатор лучше назначать именно так. Дело в том, что в пустую таблицу мы добавляем пустую строку при пом.: new TableFileInfo ("",0L,0L,0L, FOLDER, NOT_SYMBOLIC) что избавляет нас от индикатора DIR в этой пустой строке   */
        }
        else {
            setTimeModified (fi.getModified());
            setModified     (fi.getModified());
            setTimeCreated (fi.getCreated());
            setCreated     (fi.getCreated());
            setSize (fi.getFilesize());
            setFolder (NOT_FOLDER);
            setFolderMark (STR_EMPTY);
        }
    }

//Добавлено генератором кода (кроме boolean)
    public String getFileName()      {   return fileName.get();   }
    public String getTimeCreated()   {   return timeCreated.get();   }
    public String getTimeModified()  {   return timeModified.get();   }
    public long getSize()      {   return size.get();   }
    public long getCreated()   {   return created.get();   }
    public long getModified()  {   return modified.get();   }
    public boolean getFolder()      {   return folder.get();   }
    public boolean getSymbolic()    {   return symbolic.get();   }
    public String getFolderMark()   {   return folder.get() ? FOLDER_MARK : STR_EMPTY;   }

//Этот набор необходим для javafx, если какие-то строки таблицы заполняются через FXML-файл.
    public void setFileName (String name)       {   this.fileName.set (name);   }
    public void setTimeCreated (long created)   {   this.timeCreated.set (formatFileTime (created));   }
    public void setTimeModified (long modified) {   this.timeModified.set (formatFileTime (modified));   }
    public void setSize (long size)         {   this.size.set (size);   }
    public void setCreated (long created)   {   this.created.set (created);   }
    public void setModified (long modified) {   this.modified.set (modified);   }
    public void setFolder (boolean folder)  {   this.folder.set (folder);   }
    public void setSymbolic (boolean symbolic)    {   this.folder.set (symbolic);   }
    public void setFolderMark (String folderMark) {   this.folderMark.set (folderMark);   }

//Эти методы созданы генератором кода, и пока непонятно, нужны ли они.
    public SimpleStringProperty fileNameProperty () {   return fileName;  }
    public SimpleLongProperty fileSizeProperty ()   {   return size;      }
    public SimpleLongProperty modifiedProperty ()   {   return modified;  }
    public SimpleLongProperty createdProperty ()    {   return created;   }

//---------------------- другие методы ------------------------------------------

    public @NotNull FileInfo toFileInfo() {
        return new FileInfo (fileName.get(), folder.get(), EXISTS, symbolic.get(),
                             size.get(), created.get(), modified.get());
    }

    @Override public String toString() {
    //Для папок вместо времени создания и изменения возвращаются 0L, поэтому не будем их высчитывать.
        String  tm = (getFolder()) ? "-" : FileTime.from (getModified(), FILETIME_UNITS).toString(),
                tc = (getFolder()) ? "-" : FileTime.from (getCreated(), FILETIME_UNITS).toString();

        String name = getFileName();
        if (name == null)
            name = "null";
        return String.format (FORMAT_FILEINFO,
                              name,
                              getFolder() ? 'D' : 'F',
                              'E',
                              getSymbolic() ?  'S' : '-',
                              getSize(), tm, tc);
    }

}
/*  Так можно навязать таблице свой список:

    UserType user1 = new UserType (1L, "smith",  "smith@gmail.com",  "Susan",  "Smith",  true);
    UserType user2 = new UserType (2L, "mcneil", "mcneil@gmail.com", "Anne",   "McNeil", true);
    UserType user3 = new UserType (3L, "white",  "white@gmail.com",  "Kenvin", "White",  false);

    ObservableList<UserType> list = FXCollections.observableArrayList(user1, user2, user3);
    tableView.setItems (list);
*/