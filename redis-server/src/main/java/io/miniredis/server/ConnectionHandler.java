package io.miniredis.server;

import io.miniredis.core.CommandContext;
import io.miniredis.protocol.RespProtocolException;
import io.miniredis.protocol.RespValue;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public final class ConnectionHandler extends SimpleChannelInboundHandler<RespValue.Array> {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    private final ServerState state;

    private NettySession session;
    private ScheduledFuture<?> stallTimer;
    private boolean errorSent;

    public ConnectionHandler(ServerState state) {
        this.state = state;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        session = new NettySession(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancelStallTimer();
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RespValue.Array msg) {
        if (errorSent) return;
        if (session.isCloseRequested()) return;

        long now = state.clock().currentTimeMillis();
        CommandContext cmdCtx = new CommandContext(
                state.store(), session, state.propagator(), state.config(), now);

        RespValue reply;
        try {
            reply = state.dispatcher().dispatch(cmdCtx, msg);
        } catch (Throwable t) {
            log.warn("Command failed", t);
            reply = new RespValue.Error("ERR internal error");
        }
        ctx.write(reply);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
        if (session != null && session.isCloseRequested() && !errorSent) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        boolean writable = ctx.channel().isWritable();
        ctx.channel().config().setAutoRead(writable);

        cancelStallTimer();
        if (!writable && !errorSent) {
            stallTimer = ctx.executor().schedule(() -> {
                log.warn("Closing stalled client {}", ctx.channel().remoteAddress());
                ctx.close();
            }, state.stallTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (errorSent) { ctx.close(); return; }

        Throwable root = (cause instanceof DecoderException && cause.getCause() != null)
                ? cause.getCause() : cause;

        if (root instanceof RespProtocolException) {
            errorSent = true;
            ctx.channel().config().setAutoRead(false);
            ctx.writeAndFlush(new RespValue.Error("ERR " + root.getMessage()))
                    .addListener(ChannelFutureListener.CLOSE);
        } else {
            log.debug("Connection closed: {}", root.toString());
            ctx.close();
        }
    }

    private void cancelStallTimer() {
        if (stallTimer != null) { stallTimer.cancel(false); stallTimer = null; }
    }
}