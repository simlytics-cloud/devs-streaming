package devs.experimentalframe;




import devs.Port;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class GeneratorTest {

  private final Generator<LongSimTime> generator;

  public GeneratorTest() {
    generator = new PowerOfTwoGenerator();
  }

  @Test
  @DisplayName("Test Generator for experimental frame.")
  void testGenerator() {

    // First scheduled event is for t1
    LongSimTime t0 = LongSimTime.builder().t(0L).build();
    LongSimTime t1 = LongSimTime.builder().t(1L).build();
    assert(generator.timeAdvanceFunction(t0).equals(t1));

    // For each power of two, generator should output an integer and the word
    for (int e = 0; e <= 3; e++) {
      long t = (long) Math.pow(2.0, e);

      System.out.println("Testing generator at t = " + t);
      // Time advance should go 1, 2, 4, 8 with powers of two
      LongSimTime simTime = LongSimTime.builder().t(t).build();
      assert(generator.timeAdvanceFunction(simTime).equals(simTime));
      // Test the output
      for (PortValue<?> pv: generator.outputFunction().getPortValueList()) {
        if ("number".equals(pv.getPortIdentifier())) {
          Port<Integer> integerPort = (Port<Integer>) generator.getPorts().get("number");
          Integer i = integerPort.getValue(pv);
          assert(i == t);
        } else if ("words".equals(pv.getPortIdentifier())) {
          Port<String> wordPort = (Port<String>) generator.getPorts().get("words");
          String word = wordPort.getValue(pv);
          String expectedWord = switch ((int) t) {
            case 1 -> "One";
            case 2 -> "Two";
            case 4 -> "Four";
            case 8 -> "Eight";
            default -> "N/A";
          };
          assert(word.equals(expectedWord));
        }
      }

      // Internal transition will clear outputs
      generator.internalStateTransitionFunction(simTime);
    }


  }


}
