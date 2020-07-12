/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // SQ: EventLoop: new 完就有 Selector 了，但没有 eventLoop 线程

        // SQ: NioServerSocketChannel 运行在 bossGroup 的 eventLoop 中，用于处理 OP_ACCEPT 事件；
        //      通过在 Pipeline 末尾添加 ServerBootstrapAcceptor 实现将 accept 到的 NioSocketChannel 注册到 workerGroup 上；
        //  NioSocketChannel 运行在 workerGroup 的 eventLoop 中，用于处理 OP_READ 事件；

        // SQ: bossGroup 线程数设置为1就够用了，每个 bind 的端口只会生成一个 NioServerSocketChannel
        //  并 register 到一个 NioEventLoop(1个线程) ，线程数多了没用；
        //  要 bind 更多端口时才考虑增加 bossGroup 线程数；
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(new EchoServerHandler());
                 }
             });

            // SQ: bind 方法中完成:
            //  1. 实例化 NioServerSocketChannel，设置各初始化参数，在 Pipeline 上添加各种 Handler 等；
            //         1.1. 向 Pipeline 末尾添加 ServerBootstrapAcceptor（用于将 NioSocketChannel 注册到 workerGroup 的 NioEventLoop上）；
            //  2. 将 NioServerSocketChannel 注册到 bossGroup NioEventLoop（jdk Channel 注册到 Selector 上）；
            //  3. 执行 NioServerSocketChannel 的 bind 方法 (jdk Channel 开始监听端口）；
            ChannelFuture f = b.bind(PORT)
                .sync();
            System.out.println("bind sync finished.");

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
