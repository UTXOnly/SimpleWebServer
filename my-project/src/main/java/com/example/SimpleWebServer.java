package com.example;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import javax.management.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;


public class SimpleWebServer {
    private static final int PORT = 8000;

    public static void main(String[] args) throws IOException, NotCompliantMBeanException,
            InstanceAlreadyExistsException, MBeanRegistrationException, InterruptedException,
            MalformedObjectNameException, InstanceNotFoundException {
        // Create platform MBean server
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        // Register MBean
        SimpleMetric metric = new SimpleMetric();
        ObjectName metricName = new ObjectName("Foo:type=SimpleMetric");
        mbs.registerMBean(metric, metricName);

        // Create HTTP server on port 8000
        EventLoopGroup group = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(PORT))
                .childHandler(new HttpServerInitializer(mbs));

        // Start server
        ChannelFuture future = bootstrap.bind().sync();
        if (future.isSuccess()) {
            System.out.println("Server started on port " + PORT);
        } else {
            System.err.println("Server failed to start");
            future.cause().printStackTrace();
        }

        // Wait for user input before stopping
        System.out.println("Press enter to stop server.");
        System.in.read();

        // Stop server
        future.channel().closeFuture().sync();
        group.shutdownGracefully();
        mbs.unregisterMBean(metricName);
    }

    public interface SimpleMetricMBean {
        int getCount();
    }

    static class SimpleMetric implements SimpleMetricMBean {
        private int count;

        @Override
        public synchronized int getCount() {
            count++;
            return count;
        }
    }

static class MetricHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private MBeanServer mbs;

    public MetricHandler(MBeanServer mbs) {
        this.mbs = mbs;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.uri().equals("/metrics")) {
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.buffer());

            // Set response headers
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");

            // Build response body
            StringBuilder responseBody = new StringBuilder();
            try {
                ObjectName metricName = new ObjectName("Foo:type=SimpleMetric");
                MBeanInfo info = mbs.getMBeanInfo(metricName);
                MBeanAttributeInfo[] attributes = info.getAttributes();
                for (MBeanAttributeInfo attribute : attributes) {
                    if (attribute.isReadable()) {
                        String attributeName = attribute.getName();
                        Object attributeValue = null;
                        try {
                            attributeValue = mbs.getAttribute(metricName, attributeName);
                        } catch (InstanceNotFoundException e) {
                            // Handle the exception if the MBean instance is not found
                            responseBody.append("Error: MBean instance not found");
                        }
                        responseBody.append(attributeName)
                                .append(" ")
                                .append(attributeValue)
                                .append("\n");
                    }
                }
            } catch (MalformedObjectNameException e) {
                // Handle the exception if the object name is malformed
                responseBody.append("Error: Malformed object name");
            } catch (MBeanException | ReflectionException e) {
                // Handle other MBean-related exceptions
                responseBody.append("Error: ").append(e.getMessage());
            }

            ByteBuf buf = Unpooled.copiedBuffer(responseBody.toString(), CharsetUtil.UTF_8);
            response.content().writeBytes(buf);
            buf.release();

            // Set the content length in the response headers
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            // Send response
            ctx.writeAndFlush(response)
                    .addListener(ChannelFutureListener.CLOSE);

            // Release request
            releaseRequest(request);
        } else {
            // Handle other requests not supported
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static void releaseRequest(FullHttpRequest request) {
        if (request != null && request.refCnt() > 1) {
            request.release();
        }
    }
}

static class HttpServerInitializer extends ChannelInitializer<Channel> {
    private MBeanServer mbs;

    public HttpServerInitializer(MBeanServer mbs) {
        this.mbs = mbs;
    }

    @Override
    protected void initChannel(Channel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpRequestDecoder());
        pipeline.addLast(new HttpResponseEncoder());
        pipeline.addLast(new HttpObjectAggregator(512 * 1024));
        pipeline.addLast(new MetricHandler(mbs));
    }
}
}
