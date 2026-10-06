package devs.proxy;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.*;
import devs.iso.time.*;
import devs.time.*;
import java.math.BigDecimal;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevsJsonBindingTest {
  private SimulationInit<SimTime> init(SimTime value) {
    return SimulationInit.<SimTime>builder().eventTime(value).simulationRunId("run")
        .messageId("init").senderId("parent").receiverId("model").build();
  }

  @Test
  void markerAgreementIsRequiredEvenForNumericAndTimestampFreeMessages() throws Exception {
    var legacy = new DevsJsonBinding(TimeEndpointPolicy.legacy());
    var exact = new DevsJsonBinding(TimeEndpointPolicy.recorder(Set.of(LongSimTime.class, DecimalSimTime.class, QuantizedSimTime.class)));
    for (DevsMessage message : new DevsMessage[]{init(LongSimTime.create(1)), ModelTerminated.builder()
        .simulationRunId("run").messageId("id").senderId("model").receiverId("parent").build()}) {
      String old = legacy.encode(message);
      String modern = exact.encode(message);
      assertFalse(old.contains("wireProfile"));
      assertEquals(message, legacy.decode(old));
      assertEquals(message, exact.decode(modern));
      assertThrows(Exception.class, () -> exact.decode(old));
      assertThrows(Exception.class, () -> legacy.decode(modern));
      assertThrows(Exception.class, () -> exact.decode(modern.replace("devs-exact-time-v1", "unknown")));
    }
  }

  @Test
  void rejectsUnknownMessageTypesAndReservedContextBeforeDynamicResolution() throws Exception {
    var binding = new DevsJsonBinding(TimeEndpointPolicy.decimalNative());
    String json = binding.encode(init(DecimalSimTime.of(BigDecimal.ONE)));
    for (String type : new String[]{"java.lang.Runtime", "devs.iso.SimulationInit", "MonitoringMessage", "unknown"}) {
      Exception failure = assertThrows(Exception.class, () -> binding.decode(json.replace("SimulationInit", type)));
      assertEquals("profile-mismatch", DevsJsonBinding.diagnostic(failure));
    }
    for (String reserved : new String[]{"enhancedClock", "clockContext", "clockId", "compositionOrigin", "parent"}) {
      assertThrows(Exception.class, () -> binding.decode("{\"" + reserved + "\":{}," + json.substring(1)));
    }
    assertEquals(init(DecimalSimTime.of(BigDecimal.ONE)), binding.decode("{\"extra\":\"" + "x".repeat(9000) + "\"," + json.substring(1)));
    Exception invalid = assertThrows(Exception.class, () -> binding.decode(json.replace("\"value\":\"1\"", "\"value\":\"01\"")));
    assertTrue(invalid.getMessage().contains("eventTime"));
    assertEquals("malformed-time", DevsJsonBinding.diagnostic(invalid));
  }

  @Test
  void nativePolicyPreservesLatticeAndRepresentabilityRatherThanAdapting() throws Exception {
    var mapping = new TimeMapping<>(new DecimalTimeCodec(), TimeSemantics.of(Rational.ONE, Rational.ZERO,
        Optional.of(TimeGranularity.of(Rational.of(1, 10)))));
    var nativeBinding = new DevsJsonBinding(TimeEndpointPolicy.nativeEndpoint(DecimalSimTime.class, mapping));
    var tenth = init(DecimalSimTime.of(new BigDecimal("0.1")));
    assertEquals(tenth, nativeBinding.decode(nativeBinding.encode(tenth)));
    assertThrows(IllegalArgumentException.class, () -> nativeBinding.encode(init(DecimalSimTime.of(new BigDecimal("0.15")))));
    assertThrows(IllegalArgumentException.class, () -> nativeBinding.encode(init(QuantizedSimTime.of(Rational.of(1, 3)))));
    assertThrows(ArithmeticException.class, () -> new DecimalTimeCodec().encode(QuantizedSimTime.of(Rational.of(1, 3))));
    assertThrows(IllegalArgumentException.class, () -> TimeEndpointPolicy.nativeEndpoint(QuantizedSimTime.class,
        new TimeMapping<>(new NativeTimeCodec<QuantizedSimTime>() {
          public QuantizedSimTime decode(QuantizedSimTime value) { return value; }
          public QuantizedSimTime encode(QuantizedSimTime value) { return value; }
          public QuantizedSimTime nextRepresentable(QuantizedSimTime value, Rational origin, Rational quantum) { return value; }
        }, TimeSemantics.of(Rational.ONE, Rational.ZERO))));

  }

  @Test
  void declaredGridChecksEveryReportTimeInBothTransportDirections() throws Exception {
    var mapping = new TimeMapping<>(new DecimalTimeCodec(), TimeSemantics.of(Rational.ONE, Rational.ZERO,
        Optional.of(TimeGranularity.of(Rational.of(1, 1000)))));
    var restricted = new DevsJsonBinding(TimeEndpointPolicy.nativeEndpoint(DecimalSimTime.class, mapping));
    var unrestricted = new DevsJsonBinding(TimeEndpointPolicy.decimalNative());
    DecimalSimTime onGrid = DecimalSimTime.of(new BigDecimal("0.001"));
    DecimalSimTime offGrid = DecimalSimTime.of(new BigDecimal("0.0005"));
    for (boolean badEvent : new boolean[]{false, true}) {
      DecimalSimTime event = badEvent ? offGrid : onGrid;
      DecimalSimTime next = badEvent ? onGrid : offGrid;
      for (DevsMessage report : new DevsMessage[]{
          NextInternalTimeReport.<DecimalSimTime>builder().eventTime(event)
              .simulationRunId("run").messageId("next").senderId("model").receiverId("parent").nextInternalTime(next).build(),
          OutputReport.<DecimalSimTime>builder().eventTime(event).payload(OutputReportPayload.builder().build())
              .simulationRunId("run").messageId("output").senderId("model").receiverId("parent").nextInternalTime(next).build(),
          TransitionComplete.<DecimalSimTime>builder().eventTime(event)
              .simulationRunId("run").messageId("complete").senderId("model").receiverId("parent").nextInternalTime(next).build()}) {
        String field = badEvent ? "eventTime" : "nextInternalTime";
        assertTrue(assertThrows(IllegalArgumentException.class, () -> restricted.encode(report)).getMessage().contains(field));
        String json = unrestricted.encode(report);
        assertTrue(assertThrows(Exception.class, () -> restricted.decode(json)).getMessage().contains(field));
      }
    }
    var valid = init(onGrid);
    assertEquals(valid, restricted.decode(restricted.encode(valid)));
  }
}