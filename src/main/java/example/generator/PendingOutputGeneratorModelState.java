package example.generator;

import java.util.ArrayList;
import java.util.List;

public class PendingOutputGeneratorModelState {

    private Integer iState;
    private List<Integer> pendingIStateOutput;

    public PendingOutputGeneratorModelState(Integer iState) {
        this.iState = iState;
        this.pendingIStateOutput = new ArrayList<>();
    }
    public Integer getiState() {
        return iState;
    }
    public void setiState(Integer iState) {
        this.iState = iState;
    }
    public List<Integer> getPendingIStateOutput() {
        return pendingIStateOutput;
    }
    public void setPendingIStateOutput(List<Integer> pendingIStateOutput) {
        this.pendingIStateOutput = pendingIStateOutput;
    }


}
