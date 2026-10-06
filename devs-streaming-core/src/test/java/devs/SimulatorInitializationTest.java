package devs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devs.iso.DevsMessage;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.time.LongSimTime;
import example.generator.GeneratorModel;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

class SimulatorInitializationTest {
  @Test
  void suppliedInitialTimeSetsElapsedTimeOrigin() {
    AtomicReference<PDevsSimulator<LongSimTime, ?, ?>> simulator = new AtomicReference<>();
    BehaviorTestKit<DevsMessage> kit = BehaviorTestKit.create(Behaviors.setup(context -> {
      var actor = new PDevsSimulator<>(new GeneratorModel(0, "generator"),
          LongSimTime.create(0), context);
      simulator.set(actor);
      return actor;
    }));
    kit.run(new SimulationInitMessage<>(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(100)).simulationRunId("run")
        .messageId("init").senderId("parent").receiverId("generator").build(),
        TestInbox.<DevsMessage>create().getRef()));
    assertEquals(LongSimTime.create(100), simulator.get().getTimeLast());
  }
}