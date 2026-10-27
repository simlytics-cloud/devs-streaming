package devs.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import devs.SimulatorProvider;
import devs.iso.DevsMessage;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.LongSimTime;
import java.math.BigDecimal;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.junit.jupiter.api.Test;

class ChildBindingTest {
  @Test
  void decimalOnlyTimeNormalizesExactly() {
    assertEquals(DecimalSimTime.of(new BigDecimal("1.25")),
        DecimalSimTime.of(new BigDecimal("1.2500")));
  }

  @Test
  void adaptsLongDeliveryUpwardWhileExactConversionRemainsStrict() {
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
    DecimalSimTime source = DecimalSimTime.of(new BigDecimal("10000.0131"));

    LongSimTime adapted = binding.adapt(source);
    assertEquals(10001, adapted.getT());
    assertEquals(DecimalSimTime.of(new BigDecimal("10001")), binding.toCanonical(adapted));
    assertThrows(ArithmeticException.class, () -> binding.exact(source));
  }
}
