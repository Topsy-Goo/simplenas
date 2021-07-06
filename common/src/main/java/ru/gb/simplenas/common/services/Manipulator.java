package ru.gb.simplenas.common.services;

import io.netty.channel.ChannelHandlerContext;
import ru.gb.simplenas.common.structs.NasMsg;

public interface Manipulator
{
//эти методы нужны серверу и клиенту
    void handle (ChannelHandlerContext ctx, NasMsg nm);
    void onChannelActive (ChannelHandlerContext ctx);
    void onChannelInactive (ChannelHandlerContext ctx);
    void onExceptionCaught (ChannelHandlerContext ctx, Throwable cause);
    void startExitRequest (NasMsg nm);

//эти методы нужны клиенту
    boolean startListRequest (NasMsg nm);
    boolean startLoad2LocalRequest  (String toLocalFolder, NasMsg nm);
    boolean startLoad2ServerRequest (String fromLocalFolder, NasMsg nm);
    boolean startSimpleRequest (NasMsg nm);

}
