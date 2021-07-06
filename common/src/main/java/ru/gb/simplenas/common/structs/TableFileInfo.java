package ru.gb.simplenas.common.structs;

import com.sun.istack.internal.NotNull;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

import java.nio.file.attribute.FileTime;

import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.formatFileTime;

public class TableFileInfo {
    static final String FORMAT_FILEINFO = "(%s:%s%s%s:sz%d:tm%s:tc%s)";
    private final SimpleStringProperty fileName = new SimpleStringProperty(STR_EMPTY);
    private final SimpleStringProperty timeModified = new SimpleStringProperty(STR_EMPTY);
    private final SimpleStringProperty timeCreated = new SimpleStringProperty(STR_EMPTY);
    private final SimpleLongProperty size = new SimpleLongProperty(-1);
    private final SimpleLongProperty modified = new SimpleLongProperty();
    private final SimpleLongProperty created = new SimpleLongProperty();
    private final SimpleBooleanProperty folder = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty symbolic = new SimpleBooleanProperty(false);
    private final SimpleStringProperty folderMark = new SimpleStringProperty("");

    public TableFileInfo () {
        this("", 0L, 0L, 0L, false, false);
    }

    public TableFileInfo (String name, long size, long modified, long created, boolean folder, boolean symbolic) {
        setFileName(name);
        setFolder(folder);
        setSymbolic(symbolic);
        if (!folder) {
            setSize(size);
            setCreated(created);
            setTimeCreated(created);
            setModified(modified);
            setTimeModified(modified);
            setFolderMark(STR_EMPTY);
        }
        else { setFolderMark(FOLDER_MARK); }
    }

    public TableFileInfo (FileInfo fi) {
        if (fi == null) { throw new IllegalArgumentException("ERROR @ TableFileInfo (FileInfo): bad FileInfo passed in."); }

        setFileName(fi.getFileName());
        setSymbolic(fi.isSymbolic());
        if (fi.isDirectory()) {
            setFolder(true);
            setFolderMark(FOLDER_MARK);
        }
        else {
            setTimeModified(fi.getModified());
            setModified(fi.getModified());
            setTimeCreated(fi.getCreated());
            setCreated(fi.getCreated());
            setSize(fi.getFilesize());
            setFolder(false);
            setFolderMark(STR_EMPTY);
        }
    }

    public String getFileName () { return fileName.get(); }

    public String getTimeCreated () { return timeCreated.get(); }

    public String getTimeModified () { return timeModified.get(); }

    public long getSize () { return size.get(); }

    public long getCreated () { return created.get(); }

    public long getModified () { return modified.get(); }

    public boolean getFolder () { return folder.get(); }

    public boolean getSymbolic () { return symbolic.get(); }

    public String getFolderMark () { return folder.get() ? FOLDER_MARK : STR_EMPTY; }

    public void setFileName (String name) { this.fileName.set(name); }

    public void setTimeCreated (long created) { this.timeCreated.set(formatFileTime(created)); }

    public void setTimeModified (long modified) { this.timeModified.set(formatFileTime(modified)); }

    public void setSize (long size) { this.size.set(size); }

    public void setCreated (long created) { this.created.set(created); }

    public void setModified (long modified) { this.modified.set(modified); }

    public void setFolder (boolean folder) { this.folder.set(folder); }

    public void setSymbolic (boolean symbolic) { this.folder.set(symbolic); }

    public void setFolderMark (String folderMark) { this.folderMark.set(folderMark); }

    public SimpleStringProperty fileNameProperty () { return fileName; }

    public SimpleLongProperty fileSizeProperty () { return size; }

    public SimpleLongProperty modifiedProperty () { return modified; }

    public SimpleLongProperty createdProperty () { return created; }

    public @NotNull FileInfo toFileInfo () {
        return new FileInfo(fileName.get(), folder.get(), true, symbolic.get(), size.get(), created.get(), modified.get());
    }

    @Override public String toString () {
        String tm = (getFolder()) ? "-" : FileTime.from(getModified(), filetimeUnits).toString(), tc = (getFolder()) ? "-" : FileTime.from(getCreated(), filetimeUnits).toString();

        String name = getFileName();
        if (name == null) { name = "null"; }
        return String.format(FORMAT_FILEINFO, name, getFolder() ? 'D' : 'F', 'E', getSymbolic() ? 'S' : '-', getSize(), tm, tc);
    }

}
