package com.lts.nio;

import com.lts.nio.channel.ChannelInitializer;
import com.lts.nio.codec.Decoder;
import com.lts.nio.codec.Encoder;
import com.lts.nio.config.NioServerConfig;

import java.net.InetSocketAddress;

/**
 * @author Robert HG (254963746@qq.com) on 2/3/16.
 */
public class NioServerTest {

    public static void main(String[] args) {

        NioServerConfig serverConfig = new NioServerConfig();
        serverConfig.setBacklog(65536);
        serverConfig.setReuseAddress(true);
        serverConfig.setTcpNoDelay(true);

        NioServer server = new NioServer(serverConfig, new EventHandler(), new ChannelInitializer() {
            @Override
            protected Decoder getDecoder() {
                return CodecFactory.getDecoder();
            }

            @Override
            protected Encoder getEncoder() {
                return CodecFactory.getEncoder();
            }
        });

        server.bind(new InetSocketAddress(8221));

        while (true) {

            try {
                Thread.sleep(100000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


}
