package devs.experimentalframe;

import devs.Port;
import devs.ScheduledDevsModel;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;
import devs.utils.Schedule;

public class LogBaseTwoCalculatorModel extends ScheduledDevsModel<LongSimTime, Void> {

  public static final String modelIdentifier = "logBaseTwoCalculatorModel";
  public static final Port<Integer> numberIn = new Port<>("numberIn");
  public static final Port<String> wordIn = new Port<>("wordIn");
  public static final Port<Integer> numberOut = new Port<>("numberOut");
  public static final Port<String> wordOut = new Port<>("wordOut");

  public LogBaseTwoCalculatorModel() {
    super(null, modelIdentifier, new Schedule<LongSimTime>());
  }

  @Override
  public void internalStateTransitionFunction(LongSimTime currentTime) {
    clearPendingOutput();

  }

  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, Bag bag) {
    simulator.getContext().getLog().info("Generating roots at " + currentTime);
    for (PortValue<?> pv : bag.getPortValueList()) {
      if (pv.getPortIdentifier().equals(numberIn.getPortIdentifier())) {
        int number = numberIn.getValue(pv);
        double log = 0.0;
        if (number != 1) {
          log = Math.log(number) / Math.log(2.0);
        }
        System.out.println("Log for " + number + " is " + log);
        Integer outValue = (int) Math.round(log);
        PortValue<?> outPortValue = new PortValue<Integer>(outValue, numberOut.getPortIdentifier());
        schedule.add(currentTime, outPortValue);
      } else if (pv.getPortIdentifier().equals(wordIn.getPortIdentifier())) {
        String word = wordIn.getValue(pv);
        String outWord = switch (word) {
          case "One" -> "Zero";
          case "Two" -> "One";
          case "Four" -> "Two";
          case "Eight" -> "Three";
          default -> "N/A";
        };
        System.out.println("Log for word " + word + " is " + outWord);
        PortValue<?> outPortValue = new PortValue<String>(outWord, wordOut.getPortIdentifier());
        schedule.add(currentTime, outPortValue);
      } else {
        throw new IllegalArgumentException(
            "LogBaseTwoCalculatorModel did not expect port value with identifier"
                + pv.getPortIdentifier());
      }
    }

  }

  @Override
  public void confluentStateTransitionFunction(LongSimTime currentTime, Bag bag) {
    externalStateTransitionFunction(currentTime, bag);
    internalStateTransitionFunction(currentTime);

  }

}
