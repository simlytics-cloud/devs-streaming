package devs.utils;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Fail fast for opt-in integration tests; never starts a broker or other services. */
public final class KafkaIntegrationSupport {
  private KafkaIntegrationSupport() {}

  public static void requireBroker() throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", 9092), 2000);
    }
  }
}