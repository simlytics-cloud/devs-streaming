package devs.experimentalframe;

import devs.Port;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;
import devs.utils.Schedule;

import java.util.HashMap;
import java.util.Map;

public class PowerOfTwoGenerator extends Generator<LongSimTime> {

  public final static String modelIdentifier = "powerOfTwoGenerator";

  public PowerOfTwoGenerator() {
    super(modelIdentifier, buildSchedule());

  }

  protected static Schedule<LongSimTime> buildSchedule() {
    LongSimTime t0 = LongSimTime.builder().t(0L).build();
    LongSimTime t1 = LongSimTime.builder().t(1L).build();
    LongSimTime t2 = LongSimTime.builder().t(2L).build();
    LongSimTime t4 = LongSimTime.builder().t(4L).build();
    LongSimTime t8 = LongSimTime.builder().t(8L).build();
    Schedule<LongSimTime> initialSchedule = new Schedule<>();

    initialSchedule.add(t1, new PortValue<Integer>(Integer.valueOf(1), "numbers"));
    initialSchedule.add(t1, new PortValue<String>("One", "words"));

    initialSchedule.add(t2, new PortValue<Integer>(Integer.valueOf(2), "numbers"));
    initialSchedule.add(t2, new PortValue<String>("Two", "words"));

    initialSchedule.add(t4, new PortValue<Integer>(Integer.valueOf(4), "numbers"));
    initialSchedule.add(t4, new PortValue<String>("Four", "words"));

    initialSchedule.add(t8, new PortValue<Integer>(Integer.valueOf(8), "numbers"));
    initialSchedule.add(t8, new PortValue<String>("Eight", "words"));

    return initialSchedule;
  }

  @Override
  protected Map<String, Port<?>> buildPorts() {
    Map<String, Port<?>> ports = new HashMap<>();
    ports.put("numbers", new Port<Integer>("numbers"));
    ports.put("words", new Port<String>("words"));
    return ports;
  }

}
