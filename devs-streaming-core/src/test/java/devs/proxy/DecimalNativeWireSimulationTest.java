package devs.proxy;

import static org.junit.jupiter.api.Assertions.*;

import devs.PDEVSModel;
import devs.PDevsSimulator;
import devs.iso.*;
import devs.iso.time.DecimalSimTime;
import java.math.BigDecimal;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.junit.jupiter.api.Test;

class DecimalNativeWireSimulationTest {
  private static DecimalSimTime decimal(String value) {
    return DecimalSimTime.of(new BigDecimal(value));
  }

  private static final class DecimalModel extends PDEVSModel<DecimalSimTime, Integer> {
    DecimalModel() { super(0, "model"); }
    public void internalStateTransitionFunction() { modelState++; }
    public void externalStateTransitionFunction(DecimalSimTime elapsed, List<PortValue<?>> inputs) { modelState++; }
    public void confluentStateTransitionFunction(List<PortValue<?>> inputs) { modelState++; }
    public DecimalSimTime timeAdvanceFunction() { return modelState == 0 ? decimal("0.1") : DecimalSimTime.infinity(); }
    public List<PortValue<?>> outputFunction() { return List.of(PortValue.builder().value(modelState).portName("out").build()); }
  }

  @Test
  void realDecimalSimulatorCompletesExactJsonRequestReportTransitionCycle() throws Exception {
    DevsJsonBinding binding = new DevsJsonBinding(TimeEndpointPolicy.decimalNative());
    TestInbox<DevsMessage> parent = TestInbox.create();
    BehaviorTestKit<DevsMessage> simulator = BehaviorTestKit.create(PDevsSimulator.create(new DecimalModel(), decimal("0")));
    SimulationInit<?> init = (SimulationInit<?>) binding.decode(binding.encode(SimulationInit.<DecimalSimTime>builder()
        .eventTime(decimal("0")).simulationRunId("run").messageId("init").senderId("parent").receiverId("model").build()));
    simulator.run(new SimulationInitMessage<>(init, parent.getRef()));
    NextInternalTimeReport<?> next = (NextInternalTimeReport<?>) roundTrip(binding, parent.receiveMessage());
    assertEquals(decimal("0.1"), next.getNextInternalTime());
    simulator.run(binding.decode(binding.encode(RequestOutput.<DecimalSimTime>builder().eventTime(decimal("0.1"))
        .simulationRunId("run").messageId("request").senderId("parent").receiverId("model").build())));
    OutputReport<?> output = (OutputReport<?>) roundTrip(binding, parent.receiveMessage());
    assertEquals(decimal("0.1"), output.getEventTime());
    assertEquals(0, output.getPayload().getOutputs().getFirst().getValue());
    simulator.run(binding.decode(binding.encode(ExecuteTransition.<DecimalSimTime>builder().eventTime(decimal("0.1"))
        .payload(ExecuteTransitionPayload.builder().build()).simulationRunId("run").messageId("transition")
        .senderId("parent").receiverId("model").build())));
    TransitionComplete<?> complete = (TransitionComplete<?>) roundTrip(binding, parent.receiveMessage());
    assertEquals(decimal("0.1"), complete.getEventTime());
    assertSame(DecimalSimTime.infinity(), complete.getNextInternalTime());
  }

  private DevsMessage roundTrip(DevsJsonBinding binding, DevsMessage message) throws Exception {
    return binding.decode(binding.encode(message));
  }
}