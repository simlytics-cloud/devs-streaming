package devs.time;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal; import org.junit.jupiter.api.Test;
class TimeGranularityTest {
 @Test void positiveNormalizedSpacingHasValueSemantics() { var value=TimeGranularity.of(new BigDecimal(".001")); var equal=new TimeGranularity(new BigDecimal(".0010")); assertEquals(new BigDecimal(".001"),value.spacing()); assertEquals(value,equal); }
 @Test void rejectsNullAndNonpositiveSpacing() { assertThrows(NullPointerException.class,()->TimeGranularity.of((BigDecimal)null)); assertThrows(IllegalArgumentException.class,()->TimeGranularity.of(BigDecimal.ZERO)); } }
