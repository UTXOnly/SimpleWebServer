import java.io.IOException;
import java.io.OutputStream;
import com.sun.net.httpserver.*;
import javax.management.*;
import java.lang.management.*;
import java.net.InetSocketAddress;

public class SimpleWebServer {
    private static final int PORT = 8000;

    public static void main(String[] args) throws IOException, MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        // Create platform MBean server
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        // Register MBean
        SimpleMetric metric = new SimpleMetric();
        ObjectName metricName = new ObjectName("Foo:type=SimpleMetric");
        mbs.registerMBean(metric, metricName);

        // Create HTTP server on port 8000
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
        HttpContext context = httpServer.createContext("/metrics");
        context.setHandler(new MetricHandler(mbs));

        // Start server
        httpServer.start();
        System.out.println("Server started on port " + PORT);

        // Wait for user input before stopping
        System.out.println("Press enter to stop server.");
        System.in.read();

        // Stop server
        httpServer.stop(0);

        // Unregister MBean
        try {
            mbs.unregisterMBean(metricName);
        } catch (InstanceNotFoundException e) {
            // Handle the exception here, for example by logging an error message.
        }

    }

    static class SimpleMetric implements SimpleMetricMBean {
        private int count;

        @Override
        public synchronized int getCount() {
            count++;
            return count;
        }
    }

    public interface SimpleMetricMBean {
        int getCount();
    }

    static class MetricHandler implements HttpHandler {
        private MBeanServer mbs;

        public MetricHandler(MBeanServer mbs) {
            this.mbs = mbs;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Set response headers
            exchange.getResponseHeaders().set("Content-Type", "text/plain");

            // Build response body
            StringBuilder responseBody = new StringBuilder();

            try {
                ObjectName metricName = new ObjectName("Foo:type=SimpleMetric");
                MBeanInfo info = mbs.getMBeanInfo(metricName);
                MBeanAttributeInfo[] attributes = info.getAttributes();
                for (MBeanAttributeInfo attribute : attributes) {
                    if (attribute.isReadable()) {
                        String attributeName = attribute.getName();
                        Object attributeValue = mbs.getAttribute(metricName, attributeName);
                        responseBody.append(attributeName)
                                    .append(" ")
                                    .append(attributeValue)
                                    .append("\n");
                    }
                }
            } catch (Exception e) {
                responseBody.append("Error: ").append(e.getMessage());
            }

            // Send response headers and body
            exchange.sendResponseHeaders(200, responseBody.length());
            OutputStream output = exchange.getResponseBody();
            output.write(responseBody.toString().getBytes());
            output.close();
        }
    }
}
