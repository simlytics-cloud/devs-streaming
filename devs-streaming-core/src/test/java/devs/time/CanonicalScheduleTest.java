package devs.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devs.SimulatorProvider;
import devs.iso.DevsMessage;
import devs.iso.PortValue;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.LongSimTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.junit.jupiter.api.Test;

class CanonicalScheduleTest {
  @Test
  void decimalOnlyTimeNormalizesExactly() {
    assertEquals(DecimalSimTime.of(new BigDecimal("1.25")),
        DecimalSimTime.of(new BigDecimal("1.2500")));
  }

  @Test
  void queuesLongDeliveryAtAdaptedEffectiveTime() {
    SimulatorProvider<LongSimTime> provider = new SimulatorProvider<>() {
      @Override
      public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context,
          LongSimTime initialTime) {
        return null;
      }

      @Override
      public String getModelIdentifier() {
        return "long-child";
      }
    };
    var binding = ChildBinding.nativeProvider(provider, LongSimTime.class,
        new DecimalTimeMapping<>(new LongTimeCodec(),
            TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO)));
    var schedule = new CanonicalSchedule(
        new CoordinatorExecutionConfig(new EnhancedClockContext("clock", BigDecimal.ZERO)),
        Map.<String, ChildBinding<?>>of("long-child", binding), List.of("long-child"));
    DecimalSimTime source = DecimalSimTime.of(new BigDecimal("10000.0131"));
    DecimalSimTime effective = DecimalSimTime.of(new BigDecimal("10001"));
    var interaction = new InteractionKey("source", "output", "long-child", "input");

    PendingDelivery delivery = schedule.enqueue(interaction,
        PortValue.builder().value("payload").portName("input").build(), source);
    schedule.update("long-child", LongSimTime.create(20000),
        DecimalSimTime.of(BigDecimal.ZERO));

    assertEquals(source, delivery.sourceTime());
    assertEquals(effective, delivery.effectiveTime());
    assertEquals(10001, ((LongSimTime) delivery.nativeTime()).getT());
    assertEquals(effective, schedule.next());
    assertEquals(List.of(), schedule.drain(source));
    assertEquals(List.of(delivery), schedule.drain(effective));
  }
}
