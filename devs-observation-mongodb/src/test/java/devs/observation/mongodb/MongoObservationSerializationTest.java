package devs.observation.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.observation.Observation;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MongoObservationSerializationTest {
    private final ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();

    static List<SimTime> times() {
        return List.of(LongSimTime.create(9007199254740993L), DoubleSimTime.create(0.25),
                DecimalSimTime.of(new BigDecimal("0.12345678901234567890123456789")),
                DecimalSimTime.of(new BigDecimal("1E+1000")),
                DecimalSimTime.of(new BigDecimal("1E-1000")),
                DecimalSimTime.of(new BigDecimal("1.000")),
                QuantizedSimTime.of(Rational.of(1, 3)), QuantizedSimTime.of(Rational.of(1, 60)),
                QuantizedSimTime.of(Rational.of(new BigInteger("123456789012345678901234567890"),
                        BigInteger.valueOf(7))),
                DecimalSimTime.infinity(), QuantizedSimTime.infinity());
    }

    static Observation<SimTime, String> observation(SimTime time, String id) {
        return Observation.<SimTime, String>builder()._id(id).runId("run").branchId("branch")
                .time(time).producerModel("producer").observationType("ExactTime")
                .payload("unchanged").build();
    }

    @Test
    void actorConversionPreservesExactTimesAndEnvelopeThroughBsonJsonReadback() throws Exception {
        int index = 0;
        for (SimTime time : times()) {
            Observation<SimTime, String> observation = observation(time, "id-" + index++);
            Document document = MongoObservationActor.toDocument(mapper, observation);
            assertNoDecimal128(document);
            JsonNode expected = mapper.readTree(mapper.writeValueAsString(observation));
            JsonNode readback = mapper.readTree(document.toJson());
            assertEquals(expected, readback);
            Observation<?, ?> restored = mapper.readValue(document.toJson(), Observation.class);
            assertEquals(time.getClass(), restored.getTime().getClass());
            assertEquals(0, time.compareTo(restored.getTime()));
            assertEquals(observation._id(), restored._id());
            assertEquals("unchanged", restored.getPayload());
            Document storedTime = document.get("time", Document.class);
            if (time instanceof DecimalSimTime decimal) {
                assertEquals("decimal", storedTime.getString("timeType"));
                if (decimal.isInfinite()) {
                    assertEquals(Map.of("timeType", "decimal", "infinity", "positive"), storedTime);
                } else {
                    assertEquals(decimal.value().toString(), storedTime.getString("value"));
                }
            } else if (time instanceof QuantizedSimTime rational) {
                assertEquals("rational", storedTime.getString("timeType"));
                if (rational.isInfinite()) {
                    assertEquals(Map.of("timeType", "rational", "infinity", "positive"), storedTime);
                } else {
                    assertInstanceOf(String.class, storedTime.get("numerator"));
                    assertInstanceOf(String.class, storedTime.get("denominator"));
                }
            }
        }
    }

    @Test
    void historicalLongAndDoubleDocumentsKeepTheirLayoutAndConcreteTypes() throws Exception {
        for (String json : List.of(
                "{\"timeType\":\"devs.iso.time.LongSimTime\",\"t\":9007199254740993}",
                "{\"timeType\":\"devs.iso.time.DoubleSimTime\",\"t\":0.25}")) {
            SimTime time = mapper.readValue(json, SimTime.class);
            Document document = MongoObservationActor.toDocument(mapper, observation(time, "historical"));
            assertEquals(mapper.readTree(json), mapper.readTree(document.toJson()).get("time"));
            assertNoDecimal128(document);
            Observation<?, ?> restored = mapper.readValue(document.toJson(), Observation.class);
            assertEquals(time.getClass(), restored.getTime().getClass());
            assertEquals(0, time.compareTo(restored.getTime()));
        }
    }

    private static void assertNoDecimal128(Object value) {
        assertFalse(value instanceof Decimal128);
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(MongoObservationSerializationTest::assertNoDecimal128);
        } else if (value instanceof Iterable<?> values) {
            values.forEach(MongoObservationSerializationTest::assertNoDecimal128);
        }
    }
}