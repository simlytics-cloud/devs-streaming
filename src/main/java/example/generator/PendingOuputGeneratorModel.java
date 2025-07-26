package example.generator;

import java.util.ArrayList;
import java.util.List;

import devs.PDEVSModel;
import devs.PendingOutput;
import devs.Port;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;

public class PendingOuputGeneratorModel 
    extends PDEVSModel<LongSimTime, PendingOutputGeneratorModelState> 
    implements PendingOutput<LongSimTime, PendingOutputGeneratorModelState> {
public static String identifier = "generator";


  /**
   * Represents the output port of the GeneratorModel, used for emitting the current state of the
   * model during its execution. The port is identified with the name "OUTPUT" and is associated
   * with the data type {@link Integer}.
   * <p>
   * This port is primarily utilized to convey the periodic state transitions of the GeneratorModel
   * (0 or 1). It is designed to output the value of the model's current state whenever the output
   * function is invoked.
   * <p>
   * The data shared through this port is encapsulated in instances of PortValue.
   */
  public static final Port<Integer> generatorOutputPort = new Port<>("OUTPUT", Integer.class);

  /**
   * Constructs a new instance of the GeneratorModel with the given initial state.
   *
   * @param initialState the initial state of the GeneratorModel. This determines the starting state
   *                     of the model, either 0 or 1, and sets up the behavior of the periodic state
   *                     transitions.
   */
  public PendingOuputGeneratorModel(int initialState) {
    super(new PendingOutputGeneratorModelState(initialState), identifier);
  }

  /**
   * Defines the internal state transition logic for the GeneratorModel. This method alternates the
   * model's internal state between 0 and 1.
   *
   * @param currentTime the current simulation time. This parameter is typically provided by the
   *                    simulation environment and represents the point in simulated time at which
   *                    the transition is occurring.
   */
  @Override
  public void pendingInternalStateTransitionFunction(LongSimTime currentTime) {
    if (modelState.getiState() == 0) {
      this.modelState.setiState(1);
    } else {
      this.modelState.setiState(0);
    }
    modelState.getPendingIStateOutput().add(modelState.getiState());
  }

  /**
   * Defines the external state transition logic for the model. This method processes external
   * inputs received by the model at a specific simulation time and modifies the model's state
   * accordingly.
   *
   * @param currentTime the current simulation time at which the external state transition occurs.
   *                    It is typically provided by the simulation environment.
   * @param inputs      a bag of inputs received at the current simulation time. These inputs are
   *                    processed to influence the model's state during this transition.
   */
  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

  }

  /**
   * Handles the confluent state transition logic for the model. This method is invoked when both
   * internal and external state transitions are scheduled to occur simultaneously. It allows the
   * model to resolve and handle such scenarios by defining appropriate logic for state updates.
   *
   * @param currentTime the current simulation time at which the confluent state transition occurs.
   *                    Typically provided by the simulation environment.
   * @param inputs      a bag of inputs received at the current simulation time. These inputs are
   *                    processed in conjunction with the internal transition logic.
   */
  @Override
  public void pendingConfluentStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

  }

  /**
   * Determines the next time advance value of the model based on its current state and simulation
   * time. This method computes the time duration until the next event or state transition in the
   * model.
   *
   * @param currentTime the current simulation time provided by the simulation environment.
   *                    Represents the point in simulated time at which the time advance is being
   *                    calculated.
   * @return a LongSimTime instance representing the time until the next state transition. If the
   * model's state is 1, the same current time is returned. Otherwise, it returns the current time
   * incremented by 1.
   */
  @Override
  public LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
    if (modelState.getiState() == 1) {
      return LongSimTime.create(0L);
    } else {
      return LongSimTime.create(1L);
    }
  }

  @Override
  public void clearPendingOutput() {
    modelState.getPendingIStateOutput().clear();
  }

  @Override
  public List<PortValue<?>> getPendingOutput() {
    return new ArrayList<>(List.of(generatorOutputPort.createPortValue(modelState.getiState())));
  }

  @Override
  public boolean hasPendingOutput() {
    return !modelState.getPendingIStateOutput().isEmpty();
  }

  
}
