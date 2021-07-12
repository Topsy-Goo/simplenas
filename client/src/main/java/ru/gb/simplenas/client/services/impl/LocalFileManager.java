package ru.gb.simplenas.client.services.impl;

import com.sun.istack.internal.NotNull;
import ru.gb.simplenas.common.CommonData;
import ru.gb.simplenas.common.services.impl.NasFileManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;

/*  Отличительной чертой этих методов и причиной их вынесения в отдельный класс является то, что они не
    содержат проверку на выход из дискового пространства пользователя (ДПП). Они предназначены для работы
    на локальном ПК пользователя.
*/
public class LocalFileManager extends NasFileManager
{

    private LocalFileManager () {}

    public static boolean isStringOfRealPath (@NotNull String string, String ... strings)   //Controller
    {
        boolean boolYes = false;
        if (string != null)
        {
            try
            {   boolYes = Files.exists(Paths.get(string, strings));
            }
            catch (InvalidPathException e) { e.printStackTrace(); }
        }
        return boolYes;
    }

//Возвращает строку пути к родительской папке. Родительская папка должна существовать.
    public static @NotNull String stringPath2StringAbsoluteParentPath (@NotNull String strPath)     //Controller
    {
        String parent = "";
        if (strPath != null)
        {
            Path path = Paths.get(strPath).toAbsolutePath().normalize().getParent();
            if (path != null && Files.exists (path))
            {
                parent = path.toString();
            }
        }
        return parent;
    }

//Преобразуем размер файла в строку, удобную для отображения в свойтсвах файла в GUI.
    public static String fileSizeToString (long fsize)      //-
    {
        long Kilo = CommonData.FILESIZE_KILOBYTE;
        long Mega = Kilo * CommonData.FILESIZE_KILOBYTE;
        long Giga = Mega * CommonData.FILESIZE_KILOBYTE;
        long Tera = Giga * CommonData.FILESIZE_KILOBYTE;
        long r = fsize;
        String units = " байтов";

        if (fsize >= Kilo)
        {
            if (fsize < Giga)   {   r = fsize/Kilo;    units=" Кб";   }
            else
            if (fsize < Tera)   {   r = fsize/Mega;    units=" Мб";   }
            else                {   r = fsize/Tera;    units=" Гб";   }
        }
        return new DecimalFormat("###,###,###,###,###").format(r) + units;
    }

    public static String formatFileTime (long time)     //TableFileInfo
    {
        FileTime ft = FileTime.from(time, CommonData.filetimeUnits);
        LocalDateTime ldt = LocalDateTime.ofInstant(ft.toInstant(), ZoneId.systemDefault());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(CommonData.FILETIME_FORMAT_PATTERN, CommonData.RU_LOCALE);
        return ldt.format(dtf);
    }

// !!! Метод НЕ проверяет, находится ли strChild в STRPATH_CLOUD или в адресном пространстве юзера.
    public static Path createSubfolder (@NotNull Path pParent, @NotNull String strChild)   //Controller
    {
        Path result = null;
        if (pParent != null && sayNoToEmptyStrings (strChild))
        {
            Path pChild = pParent.toAbsolutePath().resolve(strChild).normalize();
            result = createFolder (pChild);
        }
        return result;
    }


//    @SuppressWarnings("unchecked")
    void run() throws IOException
    {
        Path p = Paths.get("qq");
        WatchService watcher = FileSystems.getDefault().newWatchService();
//  Закрытие службы приведёт к тому, что поток, ожидающий событий, получит
//  ClosedWatchServiceException, а её ключи станут недействительными.
        WatchKey key = p.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        //(Когда ключ создаётся, его состояние == ready.)
        while (true)
        {
        //(Когда появятся события, ключ перейёдт в состояние signaled.)
            try
            {   key = watcher.poll (100, MILLISECONDS); // ждём события (блокирующая операция?)
                //можно использовать :
                //  - take() - ждать бесконечно или до прихода ClosedWatchServiceException
                //  - poll() - возращает сразу (очередь событий или null, если событий нет)
                //  - poll(100, TimeUnit.MILLISECONDS) - возвращает то же самое, но ожидает
                //         событий указанное время, или до прихода ClosedWatchServiceException.
            }
            catch (InterruptedException x) {  return; }
            //catch (ClosedWatchServiceException e) {;} < unchecked (приходит, когда закрывается весь сервис)

            for (WatchEvent<?> event : key.pollEvents()) //обрабатываем полученные события
            {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW)
                    continue;

                WatchEvent<Path> ev = (WatchEvent<Path>)event;
                Path filename = ev.context(); //< файл, с которым связано событие
/*  следующий фрагмент проверяет тип файла на тип «простой текстовый файл»
                try  {  Path child = p.resolve(filename);
                        if (Files.probeContentType(child).equals("text/plain"))
                            System.out.println("...");//< тут что-то делаем
                } catch (IOException e){e.printStackTrace();}                   */
            }
            //возвращаемся к наблюдению (переводим ключ в состояние ready), если ключ всё еще
            // действительный. (Ключ становится недействительным, если объект наблюдения
            // становится недоступен, закрывается WatchService, или процесс вызвал для ключа
            // метод cancel().)
            boolean valid = key.reset();
            if (!valid)
                break; //< ключ в состоянии invalid (теперь он бесполезен)
        }
    }

}
