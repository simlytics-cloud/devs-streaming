package example.generator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import devs.iso.time.LongSimTime;
import devs.msg.state.ScheduleState;
import devs.utils.Schedule;
import java.util.ArrayList;
import java.util.List;

public class ScheduledGeneratorModelState extends ScheduleState<LongSimTime> {

    private Integer iState;

    @JsonCreator
    public ScheduledGeneratorModelState(
        @JsonProperty("currentTime") LongSimTime currentTime,
        @JsonProperty("schedule") Schedule<LongSimTime> schedule,
        @JsonProperty("iState") int iState
    ) {
        super(currentTime, schedule);
        this.iState = iState;
    }

    public ScheduledGeneratorModelState(Integer iState) {
        super(LongSimTime.create(0L));
        this.iState = iState;
    }
    public Integer getiState() {
        return iState;
    }
    public void setiState(Integer iState) {
        this.iState = iState;
    }


}
