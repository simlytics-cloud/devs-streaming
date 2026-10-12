package devs.time;
import static org.junit.jupiter.api.Assertions.*; import java.math.BigDecimal; import java.util.Optional; import org.junit.jupiter.api.Test;
class TimeSemanticsTest {
 @Test void rejectsNonpositiveUnits(){ assertThrows(IllegalArgumentException.class,()->TimeSemantics.of(BigDecimal.ZERO,BigDecimal.ZERO)); }
 @Test void decimalFactsNormalizeAndRetainGrid(){ var s=TimeSemantics.of(new BigDecimal(".0010"),new BigDecimal("-1.5"),Optional.of(TimeGranularity.of(new BigDecimal(".001")))); assertEquals(new BigDecimal(".001"),s.secondsPerUnit()); assertEquals(new BigDecimal("-1.5"),s.origin()); assertEquals(new BigDecimal(".001"),s.timeGranularity().orElseThrow().spacing()); }
 @Test void rejectsNullFacts(){ assertThrows(NullPointerException.class,()->TimeSemantics.of((BigDecimal)null,BigDecimal.ZERO)); } }
