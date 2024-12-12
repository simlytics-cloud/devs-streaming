package devs.experimentalframe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devs.Port;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;

public class TestAcceptor extends Acceptor<LongSimTime, Integer> {

  public TestAcceptor() {
    super(0);
  }

  public static Port<Integer> acceptNumber = new Port<>("acceptNumber");
  public static Port<String> acceptWord = new Port<>("acceptWord");

  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, Bag bag) {
    // simulator.getContext().getLog().info("Validating output at " + currentTime);
    for (PortValue<?> pv : bag.getPortValueList()) {
      if ("acceptNumber".equals(pv.getPortIdentifier())) {
        double d = acceptNumber.getValue(pv);
        System.out.println("Got number " + d + " at " + currentTime);
        double expectedValue = 0.0;
        if (currentTime.getT() != 1) {
          expectedValue = Math.log(currentTime.getT().doubleValue()) / Math.log(2.0);
        }
        assertEquals(expectedValue, d, 0.0000001);
      } else if ("acceptWord".equals(pv.getPortIdentifier())) {
        String word = acceptWord.getValue(pv);
        System.out.println("Got word " + word + " at " + currentTime);
        String expectedWord = switch (currentTime.getT().intValue()) {
          case 1 -> "Zero";
          case 2 -> "One";
          case 4 -> "Two";
          case 8 -> "Three";
          default -> "N/A";
        };
        assert (word.equals(expectedWord));
      } else {
        throw new IllegalArgumentException(
            "Test acceptor did not expect port value with identifier " + pv.getPortIdentifier());
      }
    }

  }

}
