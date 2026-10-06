package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.CoupledModelFactory;
import devs.PDevsCouplings;
import devs.SimulatorProvider;
import devs.iso.DevsMessage;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ChildBindingTest {
  private static final ActorTestKit testKit = ActorTestKit.create();
  private static final EnhancedClockContext clock =
      new EnhancedClockContext("shared", Rational.of(10));

  @AfterAll
  static void shutdown() {
    testKit.shutdownTestKit();
  }

  private TimeMapping<LongSimTime> mapping() {
    return new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.of(1, 1000), Rational.of(9)), Rational.ONE);
  }

  @Test
  void nativeBindingPreservesAffineMappingAndRejectsWrongNativeType() {
    ChildBinding<LongSimTime> binding = ChildBinding.nativeActor("native",
        testKit.<DevsMessage>createTestProbe().ref(), LongSimTime.class, mapping());
    QuantizedSimTime canonical = QuantizedSimTime.of(Rational.of(10017, 1000));
    assertEquals(LongSimTime.create(17), binding.exact(canonical));
    assertEquals(canonical, binding.toCanonical(LongSimTime.create(17)));
    assertThrows(ClassCastException.class, () -> binding.toCanonical(DoubleSimTime.create(17.0)));
    assertFalse(binding.isCoordinator());
    assertNull(binding.clock());
    assertDoesNotThrow(() -> binding.validateClock(clock));
    assertEquals(QuantizedSimTime.infinity(), binding.toCanonical(LongSimTime.create(Long.MAX_VALUE)));
  }

  @Test
  void deliveryAdaptationKeepsTheNativeTypeAndEffectiveTime() {
    ChildBinding<LongSimTime> binding = ChildBinding.nativeActor("native",
        testKit.<DevsMessage>createTestProbe().ref(), LongSimTime.class, mapping());
    QuantizedSimTime source = QuantizedSimTime.of(Rational.of(20003, 2000));
    assertThrows(ArithmeticException.class, () -> binding.exact(source));
    assertEquals(LongSimTime.create(2), binding.adapt(source));
    assertEquals(QuantizedSimTime.of(Rational.of(5001, 500)),
        binding.toCanonical(binding.adapt(source)));
    assertThrows(IllegalArgumentException.class, () -> binding.adapt(QuantizedSimTime.infinity()));
  }

  @Test
  void directActorsAndWithActorRetainTheBoundary() {
    ActorRef<DevsMessage> first = testKit.<DevsMessage>createTestProbe().ref();
    ActorRef<DevsMessage> second = testKit.<DevsMessage>createTestProbe().ref();
    ChildBinding<LongSimTime> binding = ChildBinding.nativeActor("native", first,
        LongSimTime.class, mapping());
    QuantizedSimTime start = QuantizedSimTime.of(Rational.of(10));
    assertSame(first, binding.spawn(null, start));
    assertSame(second, binding.withActor(second).spawn(null, start));
    assertSame(first, binding.spawn(null, start));
    assertEquals(binding.exact(start), binding.withActor(second).exact(start));
    assertThrows(ArithmeticException.class,
        () -> binding.spawn(null, QuantizedSimTime.of(Rational.of(20001, 2000))));
  }

  @Test
  void coordinatorBindingUsesCanonicalTimeAndValidatesBothClockFields() {
    ActorRef<DevsMessage> actor = testKit.<DevsMessage>createTestProbe().ref();
    ChildBinding<QuantizedSimTime> binding = ChildBinding.coordinatorActor("nested", actor, clock);
    QuantizedSimTime start = QuantizedSimTime.of(Rational.of(31, 3));
    assertTrue(binding.isCoordinator());
    assertEquals(clock, binding.clock());
    assertEquals(start, binding.exact(start));
    assertEquals(start, binding.adapt(start));
    assertEquals(start, binding.toCanonical(start));
    assertSame(actor, binding.spawn(null, start));
    assertDoesNotThrow(() -> binding.validateClock(new EnhancedClockContext("shared", Rational.of(10))));
    assertThrows(IllegalArgumentException.class,
        () -> binding.validateClock(new EnhancedClockContext("other", Rational.of(10))));
    assertThrows(IllegalArgumentException.class,
        () -> binding.validateClock(new EnhancedClockContext("shared", Rational.ZERO)));
    assertThrows(IllegalArgumentException.class, () -> binding.validateClock(null));
    assertThrows(NullPointerException.class, () -> ChildBinding.coordinatorActor("nested", actor, null));
  }

  @Test
  void allCoupledFactoriesAreRejectedAsNativeProviders() {
    CoupledModelFactory<LongSimTime> legacy = new CoupledModelFactory<>("legacy", List.of(),
        PDevsCouplings.builder("legacy").build());
    assertThrows(IllegalArgumentException.class,
        () -> ChildBinding.nativeProvider(legacy, LongSimTime.class, mapping()));
    CoupledModelFactory<QuantizedSimTime> legacyCanonical = new CoupledModelFactory<>("legacy",
        List.of(), PDevsCouplings.builder("legacy").build());
    assertThrows(IllegalArgumentException.class, () -> ChildBinding.coordinator(legacyCanonical));
    CoupledModelFactory<QuantizedSimTime> enhanced = CoupledModelFactory.enhanced("nested",
        List.of(), PDevsCouplings.builder("nested").build(),
        new CoordinatorExecutionConfig(clock));
    assertEquals(clock, ChildBinding.coordinator(enhanced).clock());
    NativeTimeCodec<QuantizedSimTime> codec = new NativeTimeCodec<>() {
      public QuantizedSimTime decode(QuantizedSimTime value) { return value; }
      public QuantizedSimTime encode(QuantizedSimTime value) { return value; }
      public QuantizedSimTime nextRepresentable(QuantizedSimTime value, Rational origin,
          Rational quantum) { return value; }
    };
    assertThrows(IllegalArgumentException.class, () -> ChildBinding.nativeProvider(enhanced,
        QuantizedSimTime.class, new TimeMapping<>(codec,
            TimeSemantics.of(Rational.ONE, Rational.ZERO))));
  }
}