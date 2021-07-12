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
import static ru.gb.simplenas.common.Factory.printf;
import static ru.gb.simplenas.common.Factory.sayNoToEmptyStrings;

public class LocalWatchService implements ClientWatchService {
    private static final Logger LOGGER = LogManager.getLogger(LocalWatchService.class.getName());
    private static LocalWatchService instance;
    private WatchService localWatcher;
    private WatchKey localWatchingKey;
    private NasCallback callbackOnRegisteredEvents = this::callbackDummy;
    private Lock lockOnWatching;


    public LocalWatchService (NasCallback cb, Lock lock) {
        if (instance == null) {
            try {
                localWatcher = FileSystems.getDefault().newWatchService();
                LOGGER.debug("Создана служба наблюдения за каталогами.");

                lockOnWatching = lock;

                Thread t = new Thread(()->threadDoWatching(localWatcher));
                t.setDaemon(true);
                t.start();
            }
            catch (IOException e) {e.printStackTrace();}
        }
        callbackOnRegisteredEvents = cb;
    }

    void callbackDummy (Object... objects) {}

    //---------------------------------------------------------------------------------------------------------------*/

    @Override public void startWatchingOnFolder (String strFolder) {
        changeWatchingKey(strFolder);
    }

    private void changeWatchingKey (String strFolder) {
        if (sayNoToEmptyStrings(strFolder) && localWatcher != null) {
            if (localWatchingKey != null) {
                localWatchingKey.cancel();
                localWatchingKey = null;
            }
        }

        Path p = Paths.get(strFolder).toAbsolutePath().normalize();
        if (Files.exists(p) && Files.isDirectory(p)) {
            try {
                localWatchingKey = p.register(localWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                printf("\nСоздан ключ для наблюдения за папкой <%s>", p);
            }
            catch (IOException e) {e.printStackTrace();}
        }
        else { LOGGER.error(String.format("Папка не существует <%s>", strFolder)); }
    }

    @SuppressWarnings ("unchecked") private void threadDoWatching (WatchService service) {
        LOGGER.debug("Поток службы наблюдения начал работу");
        WatchKey key;
        while (service != null) {
            try {
                key = service.poll(250, MILLISECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == OVERFLOW) {
                            LOGGER.info("Произошло событие [OVERFLOW]\t•\tПропущено");
                            continue;
                        }

                        WatchEvent<Path> wev = (WatchEvent<Path>) event;
                        LOGGER.debug(String.format("событие [%s] папка <%s> файл <%s>", wev.kind(), key.watchable(), wev.context()));

                        if (lockOnWatching.tryLock()) {
                            callbackOnRegisteredEvents.callback(key.watchable().toString());
                            lockOnWatching.unlock();
                            LOGGER.debug("\t•\tОбработано");
                        }
                        else { LOGGER.debug("\t•\tЗаперто"); }
                    }
                    key.reset();
                }
            }
            catch (InterruptedException e) {
                service = null;
            }
        }
        LOGGER.debug("Поток службы наблюдения завершил работу");
    }

    @Override public void close () {
        stopWatching();
        try {
            localWatcher.close();
        }
        catch (IOException e) {e.printStackTrace();}
    }

    private void stopWatching () {
        if (localWatchingKey != null) {
            LOGGER.debug(String.format("Прекращено наблюдение за папкой <%s>", localWatchingKey.watchable()));
            localWatchingKey.cancel();
        }
    }
}
