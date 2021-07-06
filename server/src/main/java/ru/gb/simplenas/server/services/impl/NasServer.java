package ru.gb.simplenas.server.services.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.services.impl.NasMsgInboundHandler;
import ru.gb.simplenas.server.services.Server;

import java.util.*;

import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.common.Factory.lnprint;

//
public class NasServer implements Server
{
    private static NasServer instance;
    private final static Map<String, NasServerManipulator> CHANNELS = new HashMap<>();
    private Thread consoleReader;
    private boolean serverGettingOff;
    private Channel channelOfChannelFuture;
    public static final String CMD_EXIT = "exit";
    private static final Logger LOGGER = LogManager.getLogger(NasServer.class.getName());


    private NasServer() {}

    public static Server getInstance()      //+
    {
        if (instance == null)
        {
            instance = new NasServer();
            instance.run();
        }
        return instance;
    }

    private void run()      //+l
    {
        //if (instance != null)     теперь нормально, что instance != null в момент запуска
        //    throw new RuntimeException("ERROR @ NasServer.run(): alredy running; Thread: "
        //                    +Thread.currentThread()+".");
        LOGGER.trace("run(): start");
    // В этом методе мы настраиваем сервер на приём клиентов и на обработку других запросов клиентов, а затем запускаем
    // бесконечный цикл ожидания клиентов и обработки их запросов. (Пока непонятно, что именно прерывает этот цикл.)
    // Цикл происходит где-то в недрах объекта ChannelFuture cfuture.
    // В конце работы метода — в блоке finally — происходит освобождение ресурсов.

            //  EventLoopGroup — Все операции ввода-вывода для канала выполняются в цикле событий EventLoop. Несколько циклов
            //  событий объединяются в группу EventLoopGroup.
        EventLoopGroup groupParent = new NioEventLoopGroup(1);	//< пул потоков для подключения клиентов (в пуле будет один поток)
        EventLoopGroup groupChild = new NioEventLoopGroup();	//< пул потоков для обработки запросов клиентов
        // (умолчальное значение рассчитывается из конфигурации ПК)

        try //< (try нам нужен только для ChannelFuture.sync(), но мы в него включаем и всё остальное, чтобы воспользоваться
        {   //   преимуществами блока finally для гарантированного закрытия групп.)

        // Bootstrap-объект предназначен для настройки.
            //  Bootstrap — для клиента создает один экземпляр EventLoopGroup.
            //  ServerBootstrap — для сервера назначает два экземпляра EventLoopGroup: один только принимает соединения
            //      от клиентов, второй обрабатывает остальные события (чтение/запись данных и т.д.). Это помогает избежать
            //      тайм-аута при подключении к серверу новых клиентов.

            ServerBootstrap sbts = new ServerBootstrap();	//< предварительная настройка сервера
            sbts.group (groupParent, groupChild)    //< отдаём ему наши группы потоков (это может быть одна и та же
            // группа потоков, но у нас это будут разные группы)

                .channel (NioServerSocketChannel.class)	//< задаём канал для подключения (используем класс канала для
            // стандартного серверного сокета)

            // Настраиваем обработку данных — формируем конвейер (pipeline) канала. Конвейер служит для обработки данных,
            // проходящих через канал. Для каждого клиента будет создан персональный конвейер.

                // ChannelInitializer — занимается инициализацией и конфигурацией обработчиков данных (ChannelHandler): он
                // выстраивает их вдоль конвейера (pipeline), предназначенного для обработки данных. Обработчики представляют
                // из себя различные реализации ChannelHandler, а в роли конвейера выступает ChannelPipeline. Добавление
                // каждого следующего обработчика происходит в конец очереди (в конец конвейера), поэтому добавляющий их
                // метод называется addLast().
                // SocketChannel — содержит информацию о подключившемся клиенте (адрес, потоки чтения и записи).

                .childHandler (new ChannelInitializer<SocketChannel>()	//< когда к нам кто-то подключиться, …
                {   @Override
                    protected void initChannel (SocketChannel sC) throws Exception //< … мы его инициализируем в этом методе
                    {
                        LOGGER.trace("\t{}.initChannel (SocketChannel "+ sC.toString()+") start");
                        // ChannelPipeline (конвейер канала) будет передавать данные на обработку всем обработчикам в том порядке,
                        // в котором они были добавлены. Каждому последующему обработчику передаются данные, уже обработанные в
                        // предыдущем.
                        // Первому обработчику на конвейере всегда данные предоставляются в виде ByteBuf. Это умолчальный тип
                        // буфера в Netty для данной ситуации.
                        sC.pipeline().addLast (
                                    //new StringDecoder(), new StringEncoder(),
                                    new ObjectEncoder(), //< An encoder which serializes a Java object into a ByteBuf.
                                    //new ObjectDecoder(ClassResolvers.cacheDisabled (null)), //< A decoder which deserializes the received ByteBufs into Java objects.
                                    new ObjectDecoder (Integer.MAX_VALUE, ClassResolvers.weakCachingConcurrentResolver(null)), //< то же самое, но конкурентно и с небольшим кэшированием
                                    new NasMsgInboundHandler (new NasServerManipulator (sC))
                                    );
                        LOGGER.trace("initChannel() end");

                    }
                });
    // ChannelFuture.sync() запускает исполнение.
            ChannelFuture cfuture = sbts.bind(PORT).sync();
            LOGGER.debug("run(): ChannelFuture "+ cfuture.toString());

            //понадобиться для остановки сервера из консоли:
            channelOfChannelFuture = cfuture.channel();
            if (consoleReader == null)
            {
                consoleReader = new Thread (this::runConsoleReader);
                consoleReader.start();
            }
    // ChannelFuture позволит отслеживать работу сервера (точнее работу sb). Из всех
    // состояний сервера нас здесь интересует только факт его остановки, поэтому мы следующей строкой указываем свои
    // намерения — ждать, когда сервер остановится:
    //      channel() — это геттер
    //      closeFuture() — описывает ожидаемое событие
    //      sync() — запускает ожидание.
    //  (К тому же это, кажется, предотвращает мгновенное закрытие канала. По крайней мере это так для клиента,
    //  который создаёт канал не в ответ на входящее соединение, а для установки соединения.)

            cfuture.channel().closeFuture().sync();
            LOGGER.trace("run(): ChannelFuture.channel closed");

    // Это как-бы подвесит (заблокирует) наш поток до остановки сервера. Если бы мы хотели выполнять какие-то действия
    // параллельно с работой сервера, то их нужно было бы вставить между вызовами
    // sb.bind(port).sync()
    // и
    // cfuture.channel().closeFuture().sync()

    // (Сервер представлен тем же объектом, который его
    // инициализировал. Не очень удачное название для объекта, учитывая, что bootstrap = шнуровка: шнуровка давно закончилась,
    // началась и закончилась работа, а объект всё ещё называется bootstrap.)
            //ещё можносделать одной строкой:
            //b.bind(PORT).sync().channel().closeFuture().sync();
    // Future — позволяет зарегистрировать слушателя, который будет уведомлен о выполнении операции.
    // ChannelFuture — может блокировать выполнение потока до окончания выполнения операции.
        }
        catch (Exception e)   {   LOGGER.error("run(): ", e);   }
        finally
        {   // В конце работы сервера освобождаем ресурсы и потоки, которыми пользовались циклы событий (иначе
            //  программа не завершиться):
            groupParent.shutdownGracefully();
            groupChild.shutdownGracefully();
            LOGGER.trace("run(): end");
            instance = null;
        }
    }

//Run-метод потока consoleReader.
    private void runConsoleReader()     //+l
    {
        LOGGER.trace("runConsoleReader(): start");
        Scanner scanner = new Scanner(System.in);
        try
        {   String msg;
            while (!serverGettingOff && scanner != null)
            {
                //TODO : тут периодически возникают исключения «java.util.NoSuchElementException: No line found
                //	at java.util.Scanner.nextLine». Пробовали следующее:
                //       добавили break -- не помогло;
                //       добавили hasNext() -- виснем;
                //       закомментировали break и nextLine, прошли отладчиком -- работает чисто;
                //       пару раз не беспокоило, и вот опять.
                //       убрали сканнер из try и убиваем его при получении CMD_EXIT -- пока работает.
                msg = scanner.nextLine().trim();

                if (!msg.isEmpty())
                if (msg.equalsIgnoreCase (CMD_EXIT)) //< Сервер можно закрыть руками.
                {
                    onCmdServerExit();
                    scanner.close();    scanner = null;
                }
                else LOGGER.warn("runConsoleReader(): unsupported command detected: "+ msg);
            }//while
        }
        finally
        {   consoleReader = null;
            if (scanner != null)
            {
                scanner.close();
                scanner = null;
            }
            LOGGER.trace("runConsoleReader(): end");
        }
    }

//---------------------------------------------------------------------------------------------------------------*/

    @Override public boolean clientsListAdd (NasServerManipulator manipulator, String userName)     //+l
    {
        boolean ok = false;
        if (!CHANNELS.containsKey (userName))
        {
            CHANNELS.put (userName, manipulator);
            ok = true;
            LOGGER.trace("clientsListAdd(): клиент <"+userName+"> добавлен.");
        }
        return ok;
    }

    @Override public void clientsListRemove (NasServerManipulator manipulator, String userName)
    {
        if (DEBUG) lnprint("NasServer.clientsListRemove(): call.");
        //CHANNELS.remove (userName);
        if (userName != null && CHANNELS.get(userName) == manipulator)
        {
            CHANNELS.remove (userName);
            if (DEBUG) lnprint("NasServer.clientsListRemove(): клиент <"+userName+"> удалён.");
        }
    }

    private void closeAllClientConnections()    //+
    {
        Set<Map.Entry<String, NasServerManipulator>> entries = CHANNELS.entrySet();

        for (Map.Entry<String, NasServerManipulator> e : entries)
        {
            NasServerManipulator manipulator = e.getValue();
            manipulator.startExitRequest(null);
        }
    }

//Обработчик команды CMD_EXIT, введённой в консоли.
    private void onCmdServerExit()      //+l
    {
        LOGGER.trace("onCmdServerExit(): start");
        serverGettingOff = true;

        closeAllClientConnections();
        CHANNELS.clear(); //< чтобы не возиться с итераторами, удаляем всё разом, а не по одному элементу.

        Channel c = channelOfChannelFuture;
        if (channelOfChannelFuture != null && channelOfChannelFuture.isOpen())
        {
            channelOfChannelFuture.disconnect();    //TODO : кажется, так мы соединение не закроем.
        }
        channelOfChannelFuture = null;

        LOGGER.trace("onCmdServerExit(): end");
    }

}
//---------------------------------------------------------------------------------------------------------------*/

/*  Когда канал регистрируется, он привязывается к определенному циклу событий на все время своего существования.
    Цикл событий всегда выполняется в одном и том же потоке, поэтому не нужно заботиться о синхронизации операций
    ввода-вывода канала.
    Поскольку обычно один EventLoop работает с несколькими каналами, важно не выполнять никаких блокирующих операций
    в ChannelHandler. Но если это все же требуется, то Netty предоставляет возможность указать EventExecutorGroup при
    регистрации канала, EventExecutor которого будет выполнять все методы ChannelHandler в отдельном потоке, не
    нагружая EventLoop канала.
*/




