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
    private WatchService localWatcher;
    private WatchKey     localWatchingKey;
    private NasCallback  callbackOnCurrentFolderEvents = this::callbackDummy;

    private static  LocalWatchService instance;
    private Lock lockSuspendWatching; /*< При загрузке маленьких файлов с сервера временная папка
 существует очень короткое время. Служба наблюдения успевает на неё среагировать, а некоторые
 методы не успевают, из-за чего они бросаются исключениями. Теперь такие файлы не будут
 обрабатываться службой наблюдения.
     Кроме того всегда есть вероятность, что служба слежения начнёт обрабатывать изменения
 в каталоге во время какой-то операции в контроллере.*/

    public LocalWatchService (NasCallback cb, Lock lock) {

        if (instance == null) {
            try {
                localWatcher = FileSystems.getDefault().newWatchService();
                lnprint ("Создана служба наблюдения за каталогами.");
                lockSuspendWatching = lock;

                Thread t = new Thread(()->threadDoWatching (localWatcher));
                t.setDaemon(true);
                t.start();
                instance = this;
            }
            catch (IOException e) {e.printStackTrace();}
        }
        callbackOnCurrentFolderEvents = cb;
    }

    void callbackDummy (Object... objects) {}
//----------------------------------------------------------------------------------------

    @Override public void startWatchingOnFolder (String strFolder) {
        changeWatchingKey(strFolder);
    }

    private void threadDoWatching (WatchService service) {

        lnprint ("Поток службы наблюдения начал работу.");
        WatchKey key;
        while (service != null) {
            try {
                key = service.poll (250, MILLISECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {

                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == OVERFLOW) {

                            lnprint ("Произошло событие [OVERFLOW].\t•\tПропущено.");
                            continue;
                        }
                        WatchEvent<Path> wev = (WatchEvent<Path>) event;
                        printf ("\nсобытие [%s] папка <%s> файл <%s>", wev.kind(), key.watchable(), wev.context());

                        if (lockSuspendWatching.tryLock()) {

                            callbackOnCurrentFolderEvents.callback(key.watchable().toString());
                            lockSuspendWatching.unlock();
                            lnprint ("\t•\tОбработано.");
                        }
                        else lnprint ("\t•\tЗаперто.");
                    }
                    key.reset();
                }
            }
            catch (InterruptedException | ClosedWatchServiceException e) {  service = null; }
            /* Перехват ClosedWatchServiceException помогает завершить работу сервиса без исключений. */
        }
        lnprint ("Поток службы наблюдения завершил работу.");
    }

    private void changeWatchingKey (String strFolder) {

        if (sayNoToEmptyStrings (strFolder) && localWatcher != null)
        if (localWatchingKey != null) {

            localWatchingKey.cancel();  //< Это можно делать многократно;
            localWatchingKey = null;    //  после отмены ключ делается недействтельным навсегда, но может
        }                               //  дообработать события, которые он уже ждёт или обрабатывает.

        Path p = Paths.get(strFolder).toAbsolutePath().normalize();
        if (Files.exists(p) && Files.isDirectory(p)) {

            try {   //(Если за указанной папкой уже ведётся наблюдение при помощи ключа К, то у ключа К
                // меняется набор событий на указанный, и возвращается ключ К. Иначе возвращается
                // новый ключ. Все зарегистрированные ранее события остаются в очереди.)
                localWatchingKey = p.register (localWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                printf ("\nСоздан ключ для наблюдения за папкой <%s>.", p);
            }
            catch (IOException e) {e.printStackTrace();}
        }
        else printf ("\nПапка не существует <%s>.", strFolder);
    }

    @Override public void close () {
        stopWatching();
        //Если поток этого сервиса заблокирован, то он немедленно получает ClosedWatchServiceException;
        // все ключи этого сервиса делаются недействительными. Сервис нельзя использовать повторно, но
        // можно многократно закрывать.
        try {
            localWatcher.close();
        }
        catch (IOException e) {e.printStackTrace();}
    }

    private void stopWatching () {

        if (localWatchingKey != null) {
            printf ("\nПрекращено наблюдение за папкой <%s>.", localWatchingKey.watchable());
            localWatchingKey.cancel();
            localWatchingKey = null;
        }
    }
}
