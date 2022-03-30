package ru.gb.simplenas.common.services;

import io.netty.channel.ChannelHandlerContext;
import ru.gb.simplenas.common.structs.NasMsg;

import java.io.IOException;

public interface Manipulator {

/** Поскольку этот метод вызывается <u>только</u> из NasMsgInboundHandler.channelRead0(),
*    то мы с чистой совестью можем пробрасывать IOException, если не собираемся его обрабатывать.<p>
*    см. {@link ru.gb.simplenas.common.services.impl.NasMsgInboundHandler}  */
    void handle (ChannelHandlerContext ctx, NasMsg nm) throws IOException;

    void onChannelActive (ChannelHandlerContext ctx);

    void onChannelInactive (ChannelHandlerContext ctx);

    void onExceptionCaught (ChannelHandlerContext ctx, Throwable cause);

    void startExitRequest ();
}
