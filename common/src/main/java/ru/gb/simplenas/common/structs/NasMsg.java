package ru.gb.simplenas.common.structs;

import com.sun.istack.internal.NotNull;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ru.gb.simplenas.common.Factory.ficopy;

// Структура данных, которая является едиственным типом данных, передаваеым по каналу связи с сервером
// (помимо, может быть, самих файлов). Она также используется и для обмена между методами и классами.
public class NasMsg implements Serializable
{
    private static final int version = 1;
    private OperationCodes opCode;
    private boolean inbound;  //< индикатор входящего/исходящего сообщения (для NasDialogue и для логирования)
    private String msg;
    private FileInfo fileInfo;
    private Object data;


    private NasMsg() {}

    public NasMsg (@NotNull OperationCodes code, boolean inbound)
    {
        this();
        this.opCode = code;
        this.inbound = inbound;
    }

    public NasMsg (@NotNull OperationCodes code, String msg, boolean inbound)
    {
        this (code, inbound);
        this.msg = msg;
    }

    public NasMsg (@NotNull OperationCodes code, String msg, String fullPath, boolean inbound)
    {
        this (code, msg, inbound);
        if (fullPath != null)
        {
            Path path = Paths.get(fullPath).toAbsolutePath().normalize();
            this.fileInfo = new FileInfo(path);
        }
    }

    public NasMsg (@NotNull OperationCodes code, String msg, String filePath, String fileName, boolean inbound)
    {
        this (code, msg, inbound);
        if (filePath != null && fileName != null && !fileName.isEmpty())
        {
            Path path = Paths.get (filePath, fileName);
            this.fileInfo = new FileInfo (path);
        }
    }

//---------------------------------------------------------------------------------------------------------------*/

    public OperationCodes opCode()               {   return opCode;   }
    public void setOpCode (OperationCodes opCode){   this.opCode = opCode;   }

    public boolean inbound()                {   return inbound;   }
    public void setinbound (boolean inbound){   this.inbound = inbound;   }

    public String msg()             {   return msg;   }
    public void setmsg (String msg) {   this.msg = msg;   }

    public FileInfo fileInfo()                  {   return fileInfo;   }
    public void setfileInfo (FileInfo fileInfo) {   this.fileInfo = fileInfo;   }

    public Object data()              {   return data;   }
    public void setdata (Object data) {   this.data = data;   }

//---------------------------------------------------------------------------------------------------------------*/

    public static NasMsg nmcopy (NasMsg nm)
    {
        NasMsg copy = null;
        if (nm != null)
        {
            copy = new NasMsg (nm.opCode, nm.msg, nm.inbound);
            copy.data = nm.data;
            if (nm.fileInfo != null)
            {
                copy.fileInfo = ficopy(nm.fileInfo);
            }
        }
        else copy = new NasMsg();
        return copy;
    }

    private static final String FORMAT_NASMSG = "<v%d•%s•%s•«%s»__fi%s__dt[%s]>"
    ;
    @Override public String toString()
    {
        return String.format(FORMAT_NASMSG,
                             version,
                             opCode,
                             inbound ? "IN" : "OUT",
                             msg,
                             (fileInfo == null ? "(null)" : fileInfo.toString()),
                             (data == null) ? "null" : "data"
                             );
    }

}
