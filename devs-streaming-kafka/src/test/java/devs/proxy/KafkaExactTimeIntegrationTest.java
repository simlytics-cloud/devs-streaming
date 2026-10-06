package devs.proxy;

import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import devs.PDEVSModel;
import devs.PDevsSimulator;
import devs.iso.*;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.time.Rational;
import devs.utils.ConfigUtils;
import devs.utils.DevsObjectMapper;
import devs.utils.KafkaIntegrationSupport;
import devs.utils.KafkaUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt in with -Ddevs.kafka.integration=true and a running local broker at localhost:9092. */
@EnabledIfSystemProperty(named = "devs.kafka.integration", matches = "true")
class KafkaExactTimeIntegrationTest {
  private static final Duration WAIT = Duration.ofSeconds(20);
  private Config config;
  private AdminClient admin;
  private ActorTestKit kit;
  private String topic;
  private String runId;

  @BeforeAll
  static void requireBroker() throws Exception {
    KafkaIntegrationSupport.requireBroker();
  }

  @BeforeEach
  void setUp() throws Exception {
    config = ConfigFactory.load().getConfig("kafka.properties");
    topic = "exact-time-" + UUID.randomUUID();
    runId = "exact-run-" + UUID.randomUUID();
    admin = KafkaUtils.createAdminClient(config);
    admin.createTopics(List.of(new NewTopic(topic, 4, (short) 1))).all().get(20, TimeUnit.SECONDS);
    kit = ActorTestKit.create();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (kit != null) {
      kit.shutdownTestKit();
    }
    if (admin != null) {
      try {
        admin.deleteTopics(List.of(topic)).all().get(20, TimeUnit.SECONDS);
      } finally {
        admin.close();
      }
    }
  }

  @Test
  void decimalNativeSimulatorCompletesInitOutputTransitionAndTerminationCycle() throws Exception {
    TimeEndpointPolicy policy = TimeEndpointPolicy.decimalNative();
    DecimalSimTime initial = DecimalSimTime.of(new BigDecimal("0.12345678901234567890123456789"));
    DecimalSimTime next = DecimalSimTime.of(new BigDecimal("0.22345678901234567890123456789"));
    var parent = kit.<DevsMessage>createTestProbe();
    var model = new DecimalModel();
    var simulator = kit.spawn(PDevsSimulator.create(model, initial));
    ActorRef<DevsMessage> proxy = connect("model", simulator, parent, policy);

    var init = init(initial, "model");
    proxy.tell(new SimulationInitMessage<>(init, parent.getRef()));
    var initialized = parent.expectMessageClass(NextInternalTimeReport.class, WAIT);
    assertEquals(initial, initialized.getEventTime());
    assertEquals(next, initialized.getNextInternalTime());

    var request = RequestOutput.<DecimalSimTime>builder().eventTime(next).simulationRunId(runId)
        .messageId("request").senderId("parent").receiverId("model").build();
    proxy.tell(request);
    var output = parent.expectMessageClass(OutputReport.class, WAIT);
    assertEquals(next, output.getEventTime());
    assertEquals(next, output.getNextInternalTime());
    assertEquals(List.of(PortValue.builder().value(0).portName("OUT").build()),
        output.getPayload().getOutputs());

    var execute = transition(next, "model", List.of());
    proxy.tell(execute);
    var complete = parent.expectMessageClass(TransitionComplete.class, WAIT);
    assertEquals(next, complete.getEventTime());
    assertEquals(DecimalSimTime.infinity(), complete.getNextInternalTime());

    var stop = stop(next, "model");
    proxy.tell(stop);
    var terminated = parent.expectMessageClass(ModelTerminated.class, WAIT);
    assertEquals(runId, terminated.getSimulationRunId());
    parent.expectNoMessage(Duration.ofMillis(500));

    assertRecords(readRecords(8), policy, "model",
        List.of(init, request, execute, stop), List.of(initialized, output, complete, terminated));
  }

  @Test
  void rationalRecorderPreservesOneThirdAndInfinityAndIsolatesRuns() throws Exception {
    TimeEndpointPolicy policy = TimeEndpointPolicy.recorder(Set.of(QuantizedSimTime.class));
    QuantizedSimTime zero = QuantizedSimTime.of(Rational.ZERO);
    QuantizedSimTime third = QuantizedSimTime.of(Rational.of(1, 3));
    var parent = kit.<DevsMessage>createTestProbe();
    var recorder = new RationalRecorder();
    var simulator = kit.spawn(PDevsSimulator.create(recorder, zero));
    var proxy = connect("recorder", simulator, parent, policy);
    var otherRun = kit.<DevsMessage>createTestProbe();
    String runB = runId + "-B";
    var receiverB = kit.spawn(KafkaReceiver.create(otherRun.getRef(), otherRun.getRef(), "recorder",
        runB, config, topic, policy));

    var init = init(zero, "recorder");
    proxy.tell(new SimulationInitMessage<>(init, parent.getRef()));
    var initialized = parent.expectMessageClass(NextInternalTimeReport.class, WAIT);
    assertEquals(zero, initialized.getEventTime());
    assertEquals(QuantizedSimTime.infinity(), initialized.getNextInternalTime());

    var crossRun = ExecuteTransition.<QuantizedSimTime>builder()
        .eventTime(QuantizedSimTime.of(Rational.of(1, 7)))
        .payload(ExecuteTransitionPayload.builder().addInputs(
            PortValue.builder().value(-1).portName("IN").build()).build())
        .simulationRunId(runB).messageId("cross-run").senderId("parent").receiverId("recorder").build();
    var publisherB = new KafkaMessagePublisher("recorder", runB, "recorder", topic, config);
    try {
      publisherB.publish(0, new DevsJsonBinding(policy).encode(crossRun));
      otherRun.expectMessage(WAIT, crossRun);
      assertNull(recorder.recorded.poll(1, TimeUnit.SECONDS), "Run B must not reach recorder A");

      var inputs = List.<PortValue<?>>of(PortValue.builder().value(23).portName("IN").build());
      var execute = transition(third, "recorder", inputs);
      proxy.tell(execute);
      var complete = parent.expectMessageClass(TransitionComplete.class, WAIT);
      assertEquals(third, complete.getEventTime());
      assertEquals(QuantizedSimTime.infinity(), complete.getNextInternalTime());
      assertEquals(new Recording(third, inputs), recorder.recorded.poll(20, TimeUnit.SECONDS));
      assertNull(recorder.recorded.poll(1, TimeUnit.SECONDS), "Recorder must receive only its own run");
      otherRun.expectNoMessage(Duration.ofMillis(500));

      var stop = stop(third, "recorder");
      proxy.tell(stop);
      var terminated = parent.expectMessageClass(ModelTerminated.class, WAIT);
      parent.expectNoMessage(Duration.ofMillis(500));

      var stopB = SimulationTerminate.<QuantizedSimTime>builder().eventTime(third)
          .simulationRunId(runB).messageId("stop-B").senderId("parent").receiverId("recorder").build();
      publisherB.publish(1, new DevsJsonBinding(policy).encode(stopB));
      otherRun.expectMessage(WAIT, stopB);
      otherRun.expectTerminated(receiverB, WAIT);

      var all = readRecords(8);
      var own = all.stream().filter(r -> r.key().equals(runId)).toList();
      assertRecords(own, policy, "recorder", List.of(init, execute, stop),
          List.of(initialized, complete, terminated));
      var foreign = all.stream().filter(r -> r.key().equals(runB)).toList();
      assertEquals(2, foreign.size());
      assertEquals(crossRun, new DevsJsonBinding(policy).decode(foreign.getFirst().value()));
      assertEquals(stopB, new DevsJsonBinding(policy).decode(foreign.getLast().value()));
      assertEquals(foreign.getFirst().partition(), foreign.getLast().partition());
      assertHeaders(foreign.getFirst(), runB, "recorder", 0);
      assertHeaders(foreign.getLast(), runB, "recorder", 1);
    } finally {
      publisherB.close();
    }
  }

  private ActorRef<DevsMessage> connect(String model, ActorRef<DevsMessage> simulator,
      TestProbe<DevsMessage> parent, TimeEndpointPolicy policy) {
    var proxy = kit.spawn(KafkaDevsStreamProxy.create(model, runId, topic, config, policy));
    var replies = kit.spawn(KafkaDevsStreamProxy.create("parent", runId, topic, config, policy));
    kit.spawn(KafkaReceiver.create(simulator, replies, model, runId, config, topic, policy));
    kit.spawn(KafkaReceiver.create(parent.getRef(), parent.getRef(), "parent", runId, config, topic, policy));
    return proxy;
  }

  private <T extends SimTime> SimulationInit<T> init(T time, String model) {
    return SimulationInit.<T>builder().eventTime(time).simulationRunId(runId).messageId("init")
        .senderId("parent").receiverId(model).build();
  }

  private <T extends SimTime> ExecuteTransition<T> transition(T time, String model,
      List<PortValue<?>> inputs) {
    return ExecuteTransition.<T>builder().eventTime(time)
        .payload(ExecuteTransitionPayload.builder().addAllInputs(inputs).build())
        .simulationRunId(runId).messageId("execute").senderId("parent").receiverId(model).build();
  }

  private <T extends SimTime> SimulationTerminate<T> stop(T time, String model) {
    return SimulationTerminate.<T>builder().eventTime(time).simulationRunId(runId).messageId("stop")
        .senderId("parent").receiverId(model).build();
  }

  private List<ConsumerRecord<String, String>> readRecords(int count) {
    Properties properties = ConfigUtils.toProperties(config);
    properties.put("group.id", "raw-" + UUID.randomUUID());
    properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.put("auto.offset.reset", "earliest");
    properties.put("enable.auto.commit", "false");
    var records = new ArrayList<ConsumerRecord<String, String>>();
    try (var consumer = new KafkaConsumer<String, String>(properties)) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + WAIT.toNanos();
      while (records.size() < count && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(200)).forEach(records::add);
      }
    }
    assertEquals(count, records.size(), "Every request and response must be persisted");
    return records;
  }

  private void assertRecords(List<ConsumerRecord<String, String>> records, TimeEndpointPolicy policy,
      String model, List<? extends DevsSimMessage> requests, List<? extends DevsSimMessage> replies)
      throws Exception {
    assertEquals(requests.size() + replies.size(), records.size());
    int partition = records.getFirst().partition();
    long previousOffset = -1;
    int requestIndex = 0;
    int replyIndex = 0;
    var mapper = DevsObjectMapper.buildObjectMapper();
    var binding = new DevsJsonBinding(policy);
    for (var record : records) {
      assertEquals(runId, record.key(), "Kafka keys remain runId strings, not time keys");
      assertEquals(partition, record.partition(), "All records for a run share one partition");
      assertTrue(record.offset() > previousOffset, "Partition records remain FIFO");
      previousOffset = record.offset();
      assertEquals(DevsJsonBinding.EXACT_PROFILE, mapper.readTree(record.value()).get("wireProfile").textValue());
      var message = binding.decode(record.value());
      assertEquals(runId, message.getSimulationRunId());
      if (message.getReceiverId().equals(model)) {
        assertHeaders(record, runId, model, requestIndex);
        assertEquals(requests.get(requestIndex++), message);
      } else {
        assertEquals("parent", message.getReceiverId());
        assertHeaders(record, runId, "parent", replyIndex);
        assertEquals(replies.get(replyIndex++), message);
      }
    }
    assertEquals(requests.size(), requestIndex);
    assertEquals(replies.size(), replyIndex);
  }

  private static void assertHeaders(ConsumerRecord<String, String> record, String run,
      String receiver, long sequence) {
    assertEquals(3, record.headers().toArray().length, "Exact time must not add transport headers");
    assertEquals(run, header(record, "X-Run-Id"));
    assertEquals(receiver, header(record, "X-Receiver-Id"));
    assertEquals(Long.toString(sequence), header(record, "X-Sequence"));
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    var header = record.headers().lastHeader(name);
    assertNotNull(header, name);
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private static class DecimalModel extends PDEVSModel<DecimalSimTime, Integer> {
    DecimalModel() { super(0, "model"); }
    public void internalStateTransitionFunction() { modelState++; }
    public void externalStateTransitionFunction(DecimalSimTime elapsed, List<PortValue<?>> inputs) {
      throw new AssertionError("Only an internal transition is expected");
    }
    public void confluentStateTransitionFunction(List<PortValue<?>> inputs) {
      throw new AssertionError("Only an internal transition is expected");
    }
    public DecimalSimTime timeAdvanceFunction() {
      return modelState == 0 ? DecimalSimTime.of(new BigDecimal("0.1")) : DecimalSimTime.infinity();
    }
    public List<PortValue<?>> outputFunction() {
      return List.of(PortValue.builder().value(modelState).portName("OUT").build());
    }
  }

  private record Recording(QuantizedSimTime elapsed, List<PortValue<?>> inputs) {}

  private static class RationalRecorder extends PDEVSModel<QuantizedSimTime, Void> {
    final LinkedBlockingQueue<Recording> recorded = new LinkedBlockingQueue<>();
    RationalRecorder() { super(null, "recorder"); }
    public void internalStateTransitionFunction() {
      throw new AssertionError("A passive recorder must not execute internal transitions");
    }
    public void externalStateTransitionFunction(QuantizedSimTime elapsed, List<PortValue<?>> inputs) {
      recorded.add(new Recording(elapsed, List.copyOf(inputs)));
    }
    public void confluentStateTransitionFunction(List<PortValue<?>> inputs) {
      throw new AssertionError("A passive recorder must not execute confluent transitions");
    }
    public QuantizedSimTime timeAdvanceFunction() { return QuantizedSimTime.infinity(); }
    public List<PortValue<?>> outputFunction() { return List.of(); }
  }
}