package devs;

import java.util.ArrayList;
import java.util.List;
import devs.msg.time.SimTime;

public abstract class LoggingSimulatorProvider<T extends SimTime> 
    implements SimulatorProvider<T> {
    
    protected List<String> loggingModels = new ArrayList<>();

    public List<String> getLoggingModels() {
        return loggingModels;
    }

    public void setLoggingModels(List<String> loggingModels) {
        this.loggingModels = loggingModels;
    }
  
}
