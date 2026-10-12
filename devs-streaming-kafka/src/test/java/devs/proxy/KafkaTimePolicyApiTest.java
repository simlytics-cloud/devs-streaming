package devs.proxy;

import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.SimTime;
import java.util.List;
import java.util.Set;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.junit.jupiter.api.Test;

/** Broker-free contract checks: creating a provider or deferred behavior opens no Kafka clients. */
class KafkaTimePolicyApiTest {
  private final Config config = ConfigFactory.empty();
  private final KafkaLocalProxy.ProxyProperties props = new KafkaLocalProxy.ProxyProperties(
      "run", "parent", "requests", "model", "reports", config);

  @Test
  void oldProviderConstructorsKeepLegacyPolicyEvenWithExactGenericTime() {
    var stream = new KafkaDevsStreamProxyProvider<DecimalSimTime>("model", "run", "requests", config);
    var local = new KafkaLocalProxy.KafkaProxySimulatorProvider<DecimalSimTime>(props);
    for (TimeEndpointPolicy policy : List.of(stream.getTimeEndpointPolicy(), local.getTimeEndpointPolicy())) {
      assertEquals(TimeWireProfile.LEGACY_NUMERIC_V1, policy.profile());
      assertEquals(TimeEndpointPolicy.Role.LEGACY, policy.role());
      assertEquals(TimeEndpointPolicy.legacy().acceptedTypes(), policy.acceptedTypes());
    }
    assertEquals("model", stream.getModelIdentifier());
    assertEquals("model", local.getModelIdentifier());
  }

  @Test
  void providersRetainExplicitPolicyWithoutInferringItFromGenericTime() {
    for (TimeEndpointPolicy policy : List.of(TimeEndpointPolicy.decimalNative(),
        TimeEndpointPolicy.decimalNative(), TimeEndpointPolicy.recorder(Set.of(DecimalSimTime.class)))) {
      var stream = new KafkaDevsStreamProxyProvider<SimTime>("model", "run", "requests", config, policy);
      var local = new KafkaLocalProxy.KafkaProxySimulatorProvider<SimTime>(props, policy);
      assertSame(policy, stream.getTimeEndpointPolicy());
      assertSame(policy, local.getTimeEndpointPolicy());
    }
    assertThrows(NullPointerException.class,
        () -> new KafkaDevsStreamProxyProvider<>("model", "run", "requests", config, null));
    assertThrows(NullPointerException.class,
        () -> new KafkaLocalProxy.KafkaProxySimulatorProvider<>(props, null));
  }

  @Test
  void adaptersExposeOldAndPolicyLastFactoriesAndConstructors() throws Exception {
    assertNotNull(KafkaDevsStreamProxy.create("model", "run", "requests", config));
    assertNotNull(KafkaDevsStreamProxy.create("model", "run", "requests", config,
        TimeEndpointPolicy.decimalNative()));
    assertNotNull(KafkaLocalProxy.create(props));
    assertNotNull(KafkaLocalProxy.create(props, TimeEndpointPolicy.decimalNative()));
    assertNotNull(KafkaReceiver.create(null, null, "model", "run", config, "requests"));
    assertNotNull(KafkaReceiver.create(null, null, "model", "run", config, "requests",
        TimeEndpointPolicy.decimalNative()));
    assertNotNull(KafkaDevsStreamProxy.class.getConstructor(ActorContext.class, String.class,
        String.class, String.class, Config.class));
    assertNotNull(KafkaDevsStreamProxy.class.getConstructor(ActorContext.class, String.class,
        String.class, String.class, Config.class, TimeEndpointPolicy.class));
    assertNotNull(KafkaLocalProxy.class.getConstructor(ActorContext.class, KafkaLocalProxy.ProxyProperties.class));
    assertNotNull(KafkaLocalProxy.class.getConstructor(ActorContext.class,
        KafkaLocalProxy.ProxyProperties.class, TimeEndpointPolicy.class));
    assertNotNull(KafkaReceiver.class.getConstructor(ActorContext.class, ActorRef.class, ActorRef.class,
        String.class, String.class, Config.class, String.class));
    assertNotNull(KafkaReceiver.class.getConstructor(ActorContext.class, ActorRef.class, ActorRef.class,
        String.class, String.class, Config.class, String.class, TimeEndpointPolicy.class));
  }
}
