package ru.gb.simplenas.common.services.impl;

import io.netty.channel.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.structs.NasMsg;

/** Этот класс используется и клиентом, и сервером, но в конструктор передаются разные манипуляторы.  */
public class NasMsgInboundHandler extends SimpleChannelInboundHandler<NasMsg>// ChannelInboundHandlerAdapter //
{
    private static final Logger LOGGER = LogManager.getLogger(NasMsgInboundHandler.class.getName());
    private final Manipulator manipulator;

    public NasMsgInboundHandler (Manipulator manipulator) {
        this.manipulator = manipulator;
    }

    //«SimpleChannelInboundHandler автоматически освобождает ресурсы…»
    @Override protected void channelRead0 (ChannelHandlerContext ctx, NasMsg nm) throws Exception {
        if (nm != null) manipulator.handle(ctx, nm);
    }
//---------------------- методы жизненного циклла хэндлера ----------------------------

    @Override public void handlerAdded (ChannelHandlerContext ctx) {
        //(Здесь можно проводить НЕпродолжительную инициализацию.)
    }

    @Override public void handlerRemoved (ChannelHandlerContext ctx) {
        //(Здесь можно проводить НЕпродолжительную ДЕинициализацию.)
    }
//--------------------------------------------------------------------------------------

    //срабатывает, когда клиент подключается
    @Override public void channelActive (ChannelHandlerContext ctx) throws Exception {
        //super.channelActive(ctx);
        manipulator.onChannelActive(ctx);
    }

    //срабатывает, когда клиент отключается
    @Override public void channelInactive (ChannelHandlerContext ctx) throws Exception {
        //super.channelInactive(ctx);
        manipulator.onChannelInactive(ctx);
    }

    //здесь мы узнаём об исключениях, которые взникли в процессе обработки посылки.
    @Override public void exceptionCaught (ChannelHandlerContext ctx, Throwable cause) throws Exception {

        //super.exceptionCaught(ctx,cause);
        cause.printStackTrace();
        manipulator.onExceptionCaught(ctx, cause);
        ctx.close();  //< (опционально) разрываем соединение с клиентом
    }
}
