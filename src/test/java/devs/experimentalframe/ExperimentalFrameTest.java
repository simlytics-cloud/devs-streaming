package devs.experimentalframe;

import devs.CoupledModelFactory;
import devs.PDEVSModel;
import devs.PDevsCouplings;
import devs.RootCoordinator;
import devs.msg.DevsMessage;
import devs.msg.InitSim;
import devs.msg.time.LongSimTime;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExperimentalFrameTest {

  protected final CoupledModelFactory<LongSimTime> coupledModelFactory;


  public ExperimentalFrameTest() {

    PowerOfTwoGenerator generator = new PowerOfTwoGenerator();
    LogBaseTwoCalculatorModel logCalculator = new LogBaseTwoCalculatorModel();
    TestAcceptor testAcceptor = new TestAcceptor();
    List<PDEVSModel<LongSimTime, ?>> devsModels = Arrays.asList(generator, logCalculator, testAcceptor);

    PDevsCouplings couplings = new PDevsCouplings(Collections.emptyList(), Collections.singletonList(new TestOutputCouplingHandler()));
    coupledModelFactory = new CoupledModelFactory<>("experimentalFrameTest", devsModels,
        Collections.emptyList(), couplings);
  }

  @Test
  @DisplayName("Test coupled model in experimental frame")
  protected void testCoupledModel() throws InterruptedException {
    executeExperimentalFrame(LongSimTime.builder().t(0L).build(), LongSimTime.builder().t(8L).build());
  }

  protected void executeExperimentalFrame(LongSimTime startTime, LongSimTime endTime) throws InterruptedException {
    ActorTestKit testKit = ActorTestKit.create();
    ActorRef<DevsMessage> testFrame = testKit.spawn(coupledModelFactory.create("root", startTime), "experimentalFrameTest");
    ActorRef<DevsMessage> rootCoordinator = testKit.spawn(RootCoordinator.create(
        endTime, testFrame), "root");
    rootCoordinator.tell(InitSim.builder().time(startTime).build());
    TestProbe<DevsMessage> testProbe = testKit.createTestProbe();
    testProbe.expectTerminated(rootCoordinator, Duration.ofSeconds(10));
    //Thread.sleep(10 * 1000);
    testKit.shutdownTestKit();
  }

}
