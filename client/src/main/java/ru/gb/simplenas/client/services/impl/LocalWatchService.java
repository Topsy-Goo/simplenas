package ru.gb.simplenas.client.services.impl;

import ru.gb.simplenas.client.services.ClientWatchService;
import ru.gb.simplenas.common.NasCallback;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static ru.gb.simplenas.common.CommonData.DEBUG;
import static ru.gb.simplenas.common.Factory.*;
import static ru.gb.simplenas.common.services.impl.NasFileManager.*;

public class LocalWatchService implements ClientWatchService {
    private static final boolean WS_DEBUG = false;
    private static final Object            MON_INST  = new Object();
    private        final Object            MON_WATCH = new Object();
    private static       LocalWatchService instance;
    private              WatchService      localWatcher;
    private              WatchKey          localWatchingKey;
    private              NasCallback       callbackOnCurrentFolderEvents = this::callbackDummy;

    Thread threadWatcher;
    static int level;

    public LocalWatchService ()
    {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.LocalWatchService() start");
        try {
            localWatcher = FileSystems.getDefault().newWatchService();
            lnprint ("Создана служба наблюдения за каталогами.");

            threadWatcher = new Thread(()->threadDoWatching (localWatcher), "Local watcher");
            threadWatcher.setDaemon (true);
            threadWatcher.start();
        }
        catch (IOException e) {e.printStackTrace();}
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.LocalWatchService() end");
    }

    public static LocalWatchService getInstance ()
    {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.getInstance() start");
        if (instance == null) {
            synchronized (MON_INST) {
                if (instance == null)
                    instance = new LocalWatchService();
            }
        }
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.getInstance() end");
        return instance;
    }

    void callbackDummy (Object... objects) {}

    @Override public boolean setCallBack (NasCallback cb)
    {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.setCallBack() start");
        boolean ok = cb != null;
        if (ok)
            callbackOnCurrentFolderEvents = cb;
        //if (WS_DEBUG) printf ("\n"+ tt[level--] +"WS.setCallBack() end (%b)", ok);
        return ok;
    }
//----------------------------------------------------------------------------------------

    @Override public void startWatchingOnFolder (String strFolder)
    {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.startWatchingOnFolder() start");
        stopWatching();
        startWatching (strFolder);
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.startWatchingOnFolder() end");
    }

    private void threadDoWatching (WatchService service)
    {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.threadDoWatching() start");
        String threadName = Thread.currentThread().getName();
        printf ("\n www www www Поток [%s] службы наблюдения начал работу.", threadName);
        List<NasEvent> watchEvents = new ArrayList<>();

        while (service != null) {
            try {
                WatchKey key = service.poll (250, MILLISECONDS); //< извлекает СЛЕДУЮЩИЙ ключ
                if (key == null)
                    Thread.yield(); //< ?????????? пока нет уверенности, что это нужно делать
                else {
            //Забираем список необработанных событий и тутже возвращаем ключ к наблюдению за его папкой:
                    List<WatchEvent<?>> eventList = key.pollEvents();
                    Path path = (Path) key.watchable();
                    if (!key.reset()) {
            //Если ключ оказался отменён (canceled), то его события нас не интересуют, как и те, что,
            // возможно, остались в нашем watchEvents, — все они относятся к папке, которая уже не
            // является текущей. Просто удаляем устаревшие данные:
                        eventList = null;
                        watchEvents.clear();
                    }
                    else {
                        for (WatchEvent<?> event : eventList)
                        {
                            //WatchEvent.Kind<?> kind = ;

            //Если мы что-то пропустили, не беда, — в очереди всегда найдётся событие, обрабатывая
            // которое мы обновим TableView для текущей папки, и все изменения проявятся:
                            if (event.kind().equals (OVERFLOW)) {
                                errprintf ("\nсобытие OVERFLOW получено при наблюдении за <%s>.\n", path);
                                continue;
                            }

            //Составление списка событий (в текущей реализации контроллера из всего списка выбирается
            // только первое событие, относящееся к текущей папке, и на основании факта существования
            // такого события обновляется TableView. Но здесь мы перестраховываемся и собираем все
            // события):
                            if (event.context() instanceof Path) {
                                NasEvent e = new NasEvent (event.kind().name(),
                                                           key.watchable().toString(),
                                                           event.context().toString());
                                watchEvents.add (e);
                                /*if (WS_DEBUG) printf ("\nсобытие [%s] папка <%s> файл <%s>",
                                                   e.event, e.path, e.name);*/
                            }
                        }
            //Отдаём содержимое списка контроллеру на обработку и очищаем список. Синхронизация в этом
            // месте гарантирует, что в колбэке мы будем работать с актуальным имененм текущей папки.
                        if (!watchEvents.isEmpty())
                            synchronized (MON_WATCH) {
                                callbackOnCurrentFolderEvents.callback (watchEvents.toArray());
                                watchEvents.clear();
                            }//synchronized
                    }
                }
            }
            catch (InterruptedException e) { e.printStackTrace(); } //< для poll()
            catch (ClosedWatchServiceException e) {  service = null; } //< Перехват ClosedWatchServiceException помогает завершить работу сервиса без исключений.
        }//while
        printf ("\n www www www Поток [%s] службы наблюдения завершил работу.", threadName);
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.threadDoWatching() end");
    }

    private void startWatching (String strFolder)
    {
        //if (WS_DEBUG) printf ("\n"+ tt[++level] +"WS.startWatching (%s) start", strFolder);
        if (sayNoToEmptyStrings (strFolder) && localWatcher != null)
        {
            Path p = stringToExistingFolder (strFolder);
            if (p != null)
                // Если за время ожидания папка p куда-то денется, register() бросит исключение.
                synchronized (MON_WATCH) {
                    try {
                        localWatchingKey = p.register (localWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    }
                    catch (Exception e) { e.printStackTrace(); }
                }
        }
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.startWatching() end");
    }

    private void stopWatching ()
    {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.stopWatching() start");
        synchronized (MON_WATCH) {
            if (localWatchingKey != null) {
                localWatchingKey.cancel();  //< Это можно делать многократно;
                //if (WS_DEBUG) printf ("\nПрекращено наблюдение за папкой <%s>.", localWatchingKey.watchable());
                localWatchingKey = null;  /* после «отмены» ключ делается недействтельным навсегда, но может
                получить список событий, произошедших с момента последнего извлечения до момента «отмены». */
            }
        }
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.stopWatching() end");
    }

    @Override public void close ()
    {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.close() start");
        stopWatching();
        try {
            localWatcher.close();
            //Если поток этого сервиса заблокирован, то он немедленно получает ClosedWatchServiceException;
            // все ключи этого сервиса делаются недействительными. Сервис нельзя использовать повторно, но
            // можно многократно закрывать.
        }
        catch (IOException e) {e.printStackTrace();}
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.close() end");
    }

    public static class NasEvent {
        public final String event;
        public final String path;
        public final String name;

        public NasEvent (String e, String p, String n) {
            event = e;
            path = p;
            name = n;
        }
    }

    @Override public synchronized void suspendWatching () {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.suspendWatching() start");
        stopWatching();
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.suspendWatching() end");
    }

    @Override public synchronized void resumeWatching (String strFolder) {
        //if (WS_DEBUG) lnprint (tt[++level] +"WS.resumeWatching() start");
        startWatching (strFolder);
        //if (WS_DEBUG) lnprint (tt[level--] +"WS.resumeWatching() end");
    }

/*    static String[] tt = {
        "",
        "\t",
        "\t\t",
        "\t\t\t",
        "\t\t\t\t",
        "\t\t\t\t\t",
        "\t\t\t\t\t\t",
        "\t\t\t\t\t\t\t",
        "\t\t\t\t\t\t\t\t",
        "\t\t\t\t\t\t\t\t\t",
        "\t\t\t\t\t\t\t\t\t\t",
        "\t\t\t\t\t\t\t\t\t\t\t",
        "\t\t\t\t\t\t\t\t\t\t\t\t",
        "\t\t\t\t\t\t\t\t\t\t\t\t\t"};*/
}
/* Как работает служба наблюдения:

    - для наблюдения за папкой службе наблюдения (СН) нужен спец.ключ (некий объект,
      привязвываемй к этой папке в момент его создания);

    - ключ создаётся и привязывается к СН при пом. Path.register(имя_папки, параметры). Созданный т.о.
      ключ сразу начинает использоваться в СН;

    - нельзя создать несколько ключей для наблюдения
      за одной папкой, — такая попытка только приведёт к изменению параметров существующего
      ключа, если в register(…) параметры отличаются от прежних, и зарегистрированные ранее
      необработанные события остаются в очереди;

    - чтобы узнать, произошли ли изменения в папке, связанной с ключом, нужно вызвать метод
      WatchService.poll(). Этот метод извлекает ключ из процесса наблюдения. При пом.
      pollEvents() можно получить список необработанный событий, произошедших в папке с
      момента последнего извлечения ключа. Чтобы вернуть ключ к наблюдению за папкой, нужно
      вызвать его метод reset();

    - ключ может быть действительным и НЕдействительным. Сделать ключ недействительным
      (раз и навсегда) можно вызвав его метод cancel(). Недействительный ключ (НК)
      остаётся в очереди (если на момент вызова cancel() он не был извлечён из очереди),
      события, зарегистрированные для него, ждут обработки, но новые события для ключа
      не регистрируются. Недействительный ключ может быть извлечён и для него может
      быть получен список необработанных событий при пом pollEvents(). НК нельзя вернуть
      к наблюдению.
*/
