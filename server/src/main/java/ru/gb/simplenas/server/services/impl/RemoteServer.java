package ru.gb.simplenas.server.services.impl;

import com.sun.istack.internal.NotNull;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.services.impl.NasMsgInboundHandler;
import ru.gb.simplenas.server.services.Authentificator;
import ru.gb.simplenas.server.services.ServerPropertyManager;
import ru.gb.simplenas.server.services.Server;
import ru.gb.simplenas.server.services.ServerFileManager;

import java.util.*;

import static ru.gb.simplenas.common.CommonData.DEBUG;
import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.server.SFactory.getProperyManager;
import static ru.gb.simplenas.server.SFactory.getServerFileManager;

public class RemoteServer implements Server
{
    private static RemoteServer instance;
    private final static Map<String, RemoteManipulator> CHANNELS = new HashMap<>();
    private Thread consoleReader;
    private boolean serverGettingOff;
    private Channel channelOfChannelFuture;
    public static final String CMD_EXIT = "exit";
    private final ServerFileManager fileNamager;
    private final int publicPort;

    private static Authentificator authentificator;

    private static final Logger LOGGER = LogManager.getLogger(RemoteServer.class.getName());


    private RemoteServer ()
    {
        ServerPropertyManager serverPropertyManager = getProperyManager();
        publicPort = serverPropertyManager.getPublicPort();
        fileNamager = getServerFileManager(
                serverPropertyManager.getCloudName(),
                serverPropertyManager.getWelcomeFolders(), //< папки, которые должны быть в папке у нового пользователя.
                serverPropertyManager.getWelcomeFiles());  //< файлы, которые должны быть в папке у нового пользователя.

        authentificator = new JdbcAuthentificationProvider();

        if (consoleReader == null)
        {
            consoleReader = new Thread (this::runConsoleReader);
            consoleReader.start();
        }
        LOGGER.debug("создан RemoteServer");
    }

    public static Server getInstance()
    {
        if (instance == null)
        {
            instance = new RemoteServer();
            if (authentificator != null && authentificator.isReady())
            {
                instance.run(); //< Здесь поток main переходит на обслуживание только метода run(), а всю
            }                   //  работу по обслуживанию клиентов выполняют потоки пулов.
        }
        return instance;
    }

    private void run()
    {
        LOGGER.info("run(): start");
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
                    protected void initChannel (SocketChannel socketChannel) throws Exception //< … мы его инициализируем в этом методе
                    {
                        LOGGER.trace("\t{}.initChannel (SocketChannel "+ socketChannel.toString()+") start");
                        // ChannelPipeline (конвейер канала) будет передавать данные на обработку всем обработчикам в том порядке,
                        // в котором они были добавлены. Каждому последующему обработчику передаются данные, уже обработанные в
                        // предыдущем.
                        // Первому обработчику на конвейере всегда данные предоставляются в виде ByteBuf. Это умолчальный тип
                        // буфера в Netty для данной ситуации.
                        socketChannel.pipeline().addLast (
                                    //new StringDecoder(), new StringEncoder(),
                                    //An encoder which serializes a Java object into a ByteBuf.
                                    new ObjectEncoder(),
                                    //A decoder which deserializes the received ByteBufs into Java objects.
                                    //new ObjectDecoder(ClassResolvers.cacheDisabled (null)),
                                    //new ObjectDecoder(MAX_OBJECT_SIZE, ClassResolvers.cacheDisabled (null)), //< A decoder which deserializes the received ByteBufs into Java objects.
                                    new ObjectDecoder (Integer.MAX_VALUE, ClassResolvers.weakCachingConcurrentResolver(null)), //< то же самое, но конкурентно и с небольшим кэшированием
                                    new NasMsgInboundHandler (new RemoteManipulator(fileNamager, socketChannel))
                                    //new TestInboundHandler (new RemoteManipulator (sC))
                                    );
                        LOGGER.trace("initChannel() end");

                    }
                })
                //.option (ChannelOption.SO_BACKLOG, 128)
                //.childOption (ChannelOption.SO_KEEPALIVE, true)
                ;

    // ChannelFuture.sync() запускает исполнение.
            ChannelFuture cfuture = sbts.bind(publicPort).sync();

            LOGGER.debug("run(): ChannelFuture "+ cfuture.toString());
            lnprint("\n\t\t*** Ready to getting clients ("+publicPort+"). ***\n");
            channelOfChannelFuture = cfuture.channel();

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
        catch (Exception e){e.printStackTrace();}
        finally
        {   // В конце работы сервера освобождаем ресурсы и потоки, которыми пользовались циклы событий (иначе
            //  программа не завершиться):
            groupParent.shutdownGracefully();
            groupChild.shutdownGracefully();
            LOGGER.info("run(): end");
            instance = null;
        }
    }

//Run-метод потока consoleReader.
    private void runConsoleReader()
    {
        LOGGER.trace("runConsoleReader(): start");
        Scanner scanner = new Scanner(System.in);
        String msg;
        while (!serverGettingOff)
        if (scanner.hasNext())
        {
            msg = scanner.nextLine().trim();
            if (!msg.isEmpty())
            if (msg.equalsIgnoreCase (CMD_EXIT)) //< теперь сервер можно закрыть руками.
            {
                serverGettingOff = true;
                LOGGER.info("получена команда CMD_EXIT");
                onCmdServerExit();
            }
            else LOGGER.error("runConsoleReader(): unsupported command detected: "+ msg);
        }//while
        scanner.close();
        consoleReader = null;
        LOGGER.trace("runConsoleReader(): end");
    }

//--------------------гетеры и сетеры ---------------------------------------------------------------------------*/

//---------------------------------------------------------------------------------------------------------------*/

    @Override public boolean validateOnLogin (@NotNull String login, @NotNull String password)
    {
        return authentificator.authenticate (login, password);
    }

    @Override public boolean clientsListAdd (RemoteManipulator manipulator, String userName)
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

    @Override public void clientsListRemove (RemoteManipulator manipulator, String userName)
    {
        if (DEBUG) lnprint("RemoteServer.clientsListRemove(): call.");
        //CHANNELS.remove (userName);
        if (userName != null && CHANNELS.get(userName) == manipulator)
        {
            CHANNELS.remove (userName);
            if (DEBUG) lnprint("RemoteServer.clientsListRemove(): клиент <"+userName+"> удалён.");
        }
    }

    private void closeAllClientConnections()
    {
        Set<Map.Entry<String, RemoteManipulator>> entries = CHANNELS.entrySet();

        for (Map.Entry<String, RemoteManipulator> e : entries)
        {
            RemoteManipulator manipulator = e.getValue();
            manipulator.startExitRequest(null);
        }
    }

//Обработчик команды CMD_EXIT, введённой в консоли.
    private void onCmdServerExit()
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

        if (authentificator != null)
        {
            authentificator.close();
            authentificator = null;
        }
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




