package ru.gb.simplenas.common.structs;


import org.jetbrains.annotations.NotNull;
import ru.gb.simplenas.common.CommonData;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import static ru.gb.simplenas.common.Factory.readBasicFileAttributes2;


public class FileInfo implements Serializable {

    static final String FORMAT_FILEINFO = "(%s•%s%s%s•sz%d•tm%s•tc%s)";
    private String  fileName;
    private boolean directory;
    private boolean exists;
    private boolean symbolic;
    private long filesize;
    private long created;   //< единицы измерения : filetimeUnits
    private long modified;  //< единицы измерения : filetimeUnits

    private FileInfo () {}

    public FileInfo (String name, boolean dir, boolean e) {
        this.fileName = name;
        this.directory = dir;
        this.exists = e;
    }

    public FileInfo (String name, boolean dir, boolean exists, boolean symbolic, long size, long created, long modified) {
        this(name, dir, exists);
        this.modified = modified;
        this.created = created;
        this.filesize = size;
        this.symbolic = symbolic;
    }
//------------------------------- гетеры и сетеры ---------------------------------------------------------------*/

    public FileInfo (@NotNull Path path) {
        if (path != null && path.getNameCount() > 0) {
            fileName = path.getFileName().toString();
            exists = Files.exists(path);
            if (exists) {
                BasicFileAttributes attributes = readBasicFileAttributes2(path);
                if (attributes != null) {
                    symbolic = attributes.isSymbolicLink();
                    directory = attributes.isDirectory();
                    if (!directory) {
                        filesize = attributes.size();
                        modified = attributes.lastModifiedTime().to(CommonData.filetimeUnits);
                        created = attributes.creationTime().to(CommonData.filetimeUnits);
                    }
                }
            }
        }
    }

    public static FileInfo copy (FileInfo fi) {
        if (fi == null) {
            return null;
        }
        return new FileInfo(fi.fileName, fi.directory, fi.exists, fi.symbolic, fi.filesize, fi.created, fi.modified);
    }

    public String getFileName () { return fileName; }

    public void setFileName (String fileName) { this.fileName = fileName; }

    public boolean isDirectory () { return directory; }

    public boolean isExists () { return exists; }

    public boolean isSymbolic () { return symbolic; }

    public void setDirectory (boolean directory) { this.directory = directory; }

    public void setExists (boolean exists) { this.exists = exists; }

    public void setSymbolic (boolean symbolic) { this.symbolic = symbolic; }

    public long getFilesize () { return filesize; }

    public long getCreated () { return created; }

    public long getModified () { return modified; }

    public void setFilesize (long filesize) { this.filesize = filesize; }

//---------------------------------------------------------------------------------------------------------------*/

    public void setCreated (long created) { this.created = created; }

//---------------------------------------------------------------------------------------------------------------*/

    public void setModified (long modified) { this.modified = modified; }

    @Override
    public String toString () {

        //Для папок вместо времени создания и изменения возвращаются 0L, поэтому не будем их высчитывать.
        String tm = (directory) ? "-" : FileTime.from (modified, CommonData.filetimeUnits).toString();
        String tc = (directory) ? "-" : FileTime.from (created, CommonData.filetimeUnits).toString();
        String name = fileName;

        if (name == null)
            name = "null";
        return String.format (FORMAT_FILEINFO, //(%s•%s%s%s•sz%d•tm%s•tc%s)
                              name,
                              directory ? 'D' : 'F',
                              exists    ? 'E' : '-',
                              symbolic  ? 'S' : '-',
                              filesize, tm, tc);
    }
}
