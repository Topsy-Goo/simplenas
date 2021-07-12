package ru.gb.simplenas.common.services.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.simplenas.common.services.Manipulator;
import ru.gb.simplenas.common.structs.NasMsg;


public class NasMsgInboundHandler extends SimpleChannelInboundHandler<NasMsg> {
    private static final Logger LOGGER = LogManager.getLogger(NasMsgInboundHandler.class.getName());
    private final Manipulator manipulator;

    public NasMsgInboundHandler (Manipulator manipulator) {
        this.manipulator = manipulator;
    }


    @Override protected void channelRead0 (ChannelHandlerContext ctx, NasMsg nm) throws Exception {
        if (nm != null) { manipulator.handle(ctx, nm); }
    }

    //---------------------------------------------------------------------------------------------------------------*/

    @Override public void channelActive (ChannelHandlerContext ctx) throws Exception {
        manipulator.onChannelActive(ctx);
    }

    @Override public void channelInactive (ChannelHandlerContext ctx) throws Exception {
        manipulator.onChannelInactive(ctx);
    }

    @Override public void exceptionCaught (ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        manipulator.onExceptionCaught(ctx, cause);
        ctx.close();
    }

}
