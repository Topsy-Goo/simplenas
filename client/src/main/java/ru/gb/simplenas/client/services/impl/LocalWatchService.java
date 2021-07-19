package ru.gb.simplenas.client.services.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.client.services.ClientWatchService;
import ru.gb.simplenas.common.NasCallback;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.locks.Lock;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static ru.gb.simplenas.common.Factory.*;

public class LocalWatchService implements ClientWatchService
{
    private static LocalWatchService instance;
    private WatchService localWatcher;
    private WatchKey localWatchingKey;
    private NasCallback callbackOnRegisteredEvents = this::callbackDummy;
    private Lock lockOnWatching;
    private static final Logger LOGGER = LogManager.getLogger (LocalWatchService.class.getName());


    public LocalWatchService (NasCallback cb, Lock lock)
    {
        if (instance == null)
        {
            try
            {   localWatcher = FileSystems.getDefault().newWatchService();
                LOGGER.debug("Создана служба наблюдения за каталогами");

                lockOnWatching = lock;

                Thread t = new Thread(()->threadDoWatching (localWatcher));
                t.setDaemon(true);
                t.start();
            }
            catch (IOException e) {e.printStackTrace();}
        }
        callbackOnRegisteredEvents = cb;
    }

    //public static ClientWatchService getClientWatchService ()
    //{
    //    if (instance == null)
    //        instance = new LocalWatchService();
    //    return instance;
    //}

    void callbackDummy (Object ... objects){}

//---------------------------------------------------------------------------------------------------------------*/

    @Override public void startWatchingOnFolder (String strFolder)
    {
        changeWatchingKey (strFolder);
    }

    private void threadDoWatching (WatchService service)
    {
        LOGGER.debug("Поток службы наблюдения начал работу");
        WatchKey key;
        while (service != null)
        {
            try
            {   key = service.poll(250, MILLISECONDS);
                if (key != null)
                {
                    for (WatchEvent<?> event : key.pollEvents())
                    {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == OVERFLOW)
                        {
                            LOGGER.info("Произошло событие [OVERFLOW]\t•\tПропущено");
                            continue;
                        }

                        WatchEvent<Path> wev = (WatchEvent<Path>) event;
                        LOGGER.debug(String.format("событие [%s] папка <%s> файл <%s>",
                                   wev.kind(),
                                   key.watchable(),
                                   wev.context()));

                        if (lockOnWatching.tryLock())
                        {   //      При загрузке маленьких файлов с сервера временная папка существует очень короткое время.
                            // Служба наблюдения успевает на неё среагировать, а некоторые методы — не успевают, из-за
                            // чего они бросаются исключениями.
                            //      Теперь такие файлы не будут обрабатываться службой наблюдения.
                            callbackOnRegisteredEvents.callback (key.watchable().toString());
                            lockOnWatching.unlock();
                            LOGGER.debug("\t•\tОбработано");
                        }
                        else LOGGER.debug("\t•\tЗаперто");
                    }
                    key.reset();
                }
            }
            catch (InterruptedException e)
            {
                service = null;
            }
        }
        LOGGER.debug("Поток службы наблюдения завершил работу");
    }

    private void changeWatchingKey (String strFolder)
    {
        if (sayNoToEmptyStrings (strFolder) && localWatcher != null)
        if (localWatchingKey != null)
        {
            localWatchingKey.cancel();  //< Это можно делать многократно;
            localWatchingKey = null;    //  после отмены ключ делается недействтельным навсегда, но может
        }                               //  дообработать события, которые он уже ждёт или обрабатывает.

        Path p = Paths.get(strFolder).toAbsolutePath().normalize();
        if (Files.exists(p) && Files.isDirectory(p))
        {
            try
            {   //(Если за указанной папкой уже ведётся наблюдение при помощи ключа К, то у ключа К
                // меняется набор событий на указанный, и возвращается ключ К. Иначе возвращается
                // новый ключ. Все зарегистрированные ранее события остаются в очереди.)
                localWatchingKey = p.register(localWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                printf("\nСоздан ключ для наблюдения за папкой <%s>", p);
            }
            catch (IOException e){e.printStackTrace();}
        }
        else LOGGER.error(String.format("Папка не существует <%s>", strFolder));
    }

    @Override public void close()
    {
        stopWatching();
        //Если поток этого сервиса заблокирован, то он немедленно получает ClosedWatchServiceException;
        // все ключи этого сервиса делаются недействительными. Сервис нельзя использовать повторно, но
        // можно многократно закрывать.
        try {   localWatcher.close();
            }
        catch(IOException e){e.printStackTrace();}
    }

    //@Override public
    private void stopWatching()
    {
        if (localWatchingKey != null)
        {
            LOGGER.debug(String.format("Прекращено наблюдение за папкой <%s>", localWatchingKey.watchable()));
            localWatchingKey.cancel();
        }
    }
}
//---------------------------------------------------------------------------------------------------------------*/
