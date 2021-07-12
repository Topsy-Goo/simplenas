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
import ru.gb.simplenas.server.services.ServerFileManager;
import ru.gb.simplenas.server.services.ServerPropertyManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import static ru.gb.simplenas.common.CommonData.DEBUG;
import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.server.SFactory.getProperyManager;
import static ru.gb.simplenas.server.SFactory.getServerFileManager;

public class RemoteServer implements Server {
    public static final String CMD_EXIT = "exit";
    private final static Map<String, RemoteManipulator> CHANNELS = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger(RemoteServer.class.getName());
    private static RemoteServer instance;
    private final ServerFileManager fileNamager;
    private final int publicPort;
    private Thread consoleReader;
    private boolean serverGettingOff;
    private Channel channelOfChannelFuture;


    private RemoteServer () {
        ServerPropertyManager serverPropertyManager = getProperyManager();
        publicPort = serverPropertyManager.getPublicPort();
        fileNamager = getServerFileManager(
                            serverPropertyManager.getCloudName(),
                            serverPropertyManager.getWelcomeFolders(),
                            serverPropertyManager.getWelcomeFiles());
        LOGGER.debug("создан RemoteServer");
    }

    public static Server getInstance () {
        if (instance == null) {
            instance = new RemoteServer();
            instance.run();
        }
        return instance;
    }

    private void run () {
        LOGGER.info("run(): start");
        EventLoopGroup groupParent = new NioEventLoopGroup(1);
        EventLoopGroup groupChild = new NioEventLoopGroup();
        try {
            ServerBootstrap sbts = new ServerBootstrap();
            sbts.group(groupParent, groupChild).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel (SocketChannel socketChannel) throws Exception {
                    LOGGER.trace("\t{}.initChannel (SocketChannel " + socketChannel.toString() + ") start");
                    socketChannel.pipeline().addLast(
                                                new ObjectEncoder(),
                                                new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.weakCachingConcurrentResolver(null)),
                                                new NasMsgInboundHandler(new RemoteManipulator(fileNamager, socketChannel)));
                    LOGGER.trace("initChannel() end");

                }
            });
            ChannelFuture cfuture = sbts.bind(publicPort).sync();

            lnprint("\n\t\t*** Ready to getting clients (" + publicPort + "). ***\n");
            channelOfChannelFuture = cfuture.channel();
            if (consoleReader == null) {
                consoleReader = new Thread(this::runConsoleReader);
                consoleReader.start();
            }
            cfuture.channel().closeFuture().sync();
        }
        catch (Exception e) {e.printStackTrace();}
        finally {
            groupParent.shutdownGracefully();
            groupChild.shutdownGracefully();
            LOGGER.info("run(): end");
            instance = null;
        }
    }

    private void runConsoleReader () {
        LOGGER.trace("runConsoleReader(): start");
        Scanner scanner = new Scanner(System.in);
        String msg;
        while (!serverGettingOff) {
            if (scanner.hasNext()) {
                msg = scanner.nextLine().trim();
                if (!msg.isEmpty()) {
                    if (msg.equalsIgnoreCase(CMD_EXIT)) {
                        serverGettingOff = true;
                        LOGGER.info("получена команда CMD_EXIT");
                        onCmdServerExit();
                    }
                    else { LOGGER.error("runConsoleReader(): unsupported command detected: " + msg); }
                }
            }
        }
        scanner.close();
        consoleReader = null;
        LOGGER.trace("runConsoleReader(): end");
    }

    //---------------------------------------------------------------------------------------------------------------*/

    @Override public boolean clientsListAdd (RemoteManipulator manipulator, String userName) {
        boolean ok = false;
        if (!CHANNELS.containsKey(userName)) {
            CHANNELS.put(userName, manipulator);
            ok = true;
            LOGGER.trace("clientsListAdd(): клиент <" + userName + "> добавлен.");
        }
        return ok;
    }

    @Override public void clientsListRemove (RemoteManipulator manipulator, String userName) {
        if (DEBUG) { lnprint("RemoteServer.clientsListRemove(): call."); }
        if (userName != null && CHANNELS.get(userName) == manipulator) {
            CHANNELS.remove(userName);
            if (DEBUG) { lnprint("RemoteServer.clientsListRemove(): клиент <" + userName + "> удалён."); }
        }
    }

    private void closeAllClientConnections () {
        Set<Map.Entry<String, RemoteManipulator>> entries = CHANNELS.entrySet();

        for (Map.Entry<String, RemoteManipulator> e : entries) {
            RemoteManipulator manipulator = e.getValue();
            manipulator.startExitRequest(null);
        }
    }

    private void onCmdServerExit () {
        LOGGER.trace("onCmdServerExit(): start");
        serverGettingOff = true;

        closeAllClientConnections();
        CHANNELS.clear();

        Channel c = channelOfChannelFuture;
        if (channelOfChannelFuture != null && channelOfChannelFuture.isOpen()) {
            channelOfChannelFuture.disconnect();
        }
        channelOfChannelFuture = null;

        LOGGER.trace("onCmdServerExit(): end");
    }

}




