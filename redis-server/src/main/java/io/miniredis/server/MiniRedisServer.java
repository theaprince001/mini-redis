package io.miniredis.server;

import io.miniredis.core.Clock;
import io.miniredis.core.Config;
import io.miniredis.core.MiniRedisStore;
import io.miniredis.core.Propagator;
import io.miniredis.core.SystemClock;
import io.miniredis.protocol.RespParser;
import io.miniredis.protocol.RespProtocolException;
import io.miniredis.protocol.RespValue;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class MiniRedisServer {

    private static final Logger log = LoggerFactory.getLogger(MiniRedisServer.class);

    public static final long DEFAULT_CLIENT_OUTBOUND_HARD_LIMIT = 32L * 1024 * 1024;
    public static final long DEFAULT_STALL_TIMEOUT_MS           = 30_000L;
    public static final int  DEFAULT_SO_SNDBUF                  = 0;
    public static final int  DEFAULT_MAX_QUERY_BUFFER           = 16 * 1024 * 1024;

    private static final int WATERMARK_LOW  = 64 * 1024;
    private static final int WATERMARK_HIGH = 512 * 1024;

    private final int requestedPort;
    private final long clientOutboundHardLimit;
    private final long stallTimeoutMs;
    private final int  soSndBuf;
    private final int  maxQueryBuffer;

    private final CommandDispatcher dispatcher;
    private final MiniRedisStore store;
    private final Propagator propagator;
    private final Config config;
    private final Clock clock;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private int boundPort;

    public MiniRedisServer(int port) {
        this(port, DEFAULT_CLIENT_OUTBOUND_HARD_LIMIT, DEFAULT_STALL_TIMEOUT_MS,
                DEFAULT_SO_SNDBUF, DEFAULT_MAX_QUERY_BUFFER);
    }

    public MiniRedisServer(int port, long clientOutboundHardLimit, long stallTimeoutMs) {
        this(port, clientOutboundHardLimit, stallTimeoutMs, DEFAULT_SO_SNDBUF,
                DEFAULT_MAX_QUERY_BUFFER);
    }

    public MiniRedisServer(int port, long clientOutboundHardLimit,
                           long stallTimeoutMs, int soSndBuf) {
        this(port, clientOutboundHardLimit, stallTimeoutMs, soSndBuf,
                DEFAULT_MAX_QUERY_BUFFER);
    }

    public MiniRedisServer(int port, long clientOutboundHardLimit,
                           long stallTimeoutMs, int soSndBuf, int maxQueryBuffer) {
        this.requestedPort = port;
        this.clientOutboundHardLimit = clientOutboundHardLimit;
        this.stallTimeoutMs = stallTimeoutMs;
        this.soSndBuf = soSndBuf;
        this.maxQueryBuffer = maxQueryBuffer;

        this.store = new MiniRedisStore();
        this.propagator = Propagator.NoOp.INSTANCE;
        this.config = new Config();
        this.clock = SystemClock.INSTANCE;

        this.dispatcher = new CommandDispatcher();
        Commands.registerAll(dispatcher);
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("mini-redis-boss"));
        workerGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("mini-redis-worker"));

        // Capture the worker thread so the store can enforce single-threaded access.
        workerGroup.next()
                .submit(() -> store.setOwnerThread(Thread.currentThread()))
                .syncUninterruptibly();

        final ServerState state = new ServerState(
                dispatcher, store, propagator, config, clock, stallTimeoutMs);

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast("respDecoder", new RespFrameDecoder(maxQueryBuffer));
                        p.addLast("outboundCounter",
                                new OutboundByteCounter(clientOutboundHardLimit));
                        p.addLast("respEncoder", new RespFrameEncoder());
                        p.addLast("connectionHandler", new ConnectionHandler(state));
                    }
                })
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(WATERMARK_LOW, WATERMARK_HIGH));

        if (soSndBuf > 0) {
            b.childOption(ChannelOption.SO_SNDBUF, soSndBuf);
        }

        ChannelFuture f = b.bind(requestedPort).sync();
        this.serverChannel = f.channel();
        this.boundPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        log.info("MiniRedis listening on port {}", boundPort);
    }

    public void stop() {
        try {
            if (serverChannel != null) serverChannel.close().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            Future<?> bossDone = null;
            Future<?> workerDone = null;
            if (bossGroup   != null) bossDone   = bossGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            if (workerGroup != null) workerDone = workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            try {
                if (bossDone   != null) bossDone.sync();
                if (workerDone != null) workerDone.sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public int port() { return boundPort; }

    public ChannelFuture closeFuture() { return serverChannel.closeFuture(); }

    public static void main(String[] args) throws Exception {
        int port = 6379;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        MiniRedisServer server = new MiniRedisServer(port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));
        server.closeFuture().sync();
    }

    static final class RespFrameDecoder extends ByteToMessageDecoder {

        private final int maxQueryBuffer;

        RespFrameDecoder(int maxQueryBuffer) { this.maxQueryBuffer = maxQueryBuffer; }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (true) {
                int before = in.readableBytes();
                RespValue v = RespParser.parseRequest(in);
                if (v == null) {
                    if (in.readableBytes() > maxQueryBuffer) {
                        throw new RespProtocolException(
                                "Protocol error: query buffer exceeded " + maxQueryBuffer);
                    }
                    return;
                }
                out.add(v);
                if (in.readableBytes() == before) {
                    throw new RespProtocolException("Protocol error: parser made no progress");
                }
            }
        }
    }

    static final class RespFrameEncoder extends MessageToByteEncoder<RespValue> {
        @Override
        protected void encode(ChannelHandlerContext ctx, RespValue msg, ByteBuf out) {
            io.miniredis.protocol.RespEncoder.encode(out, msg);
        }
    }
}