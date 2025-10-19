package example.generator;

import java.util.ArrayList;

import devs.PDEVSModel;
import devs.Port;
import devs.ScheduledDevsModel;
import devs.msg.Bag;
import devs.msg.time.LongSimTime;
import devs.utils.Schedule;

public class ScheduledGeneratorModel 
    extends PDEVSModel<LongSimTime, ScheduledGeneratorModelState> 
    implements ScheduledDevsModel<LongSimTime, ScheduledGeneratorModelState> {
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
  protected final Schedule<LongSimTime> schedule = new Schedule<>();


  /**
   * Constructs a new instance of the GeneratorModel with the given initial state.
   *
   * @param initialState the initial state of the GeneratorModel. This determines the starting state
   *                     of the model, either 0 or 1, and sets up the behavior of the periodic state
   *                     transitions.
   */
  public ScheduledGeneratorModel(int initialState) {
    super(new ScheduledGeneratorModelState(initialState), identifier);
    schedule.add(LongSimTime.create(1L), new FlipState());
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
  public void scheduledInternalStateTransitionFunction(LongSimTime currentTime) {
    ArrayList<Object> events = new ArrayList<>(schedule.get(currentTime));
    for (Object event: events) {
      if (event instanceof FlipState flipState) {
        if (modelState.getiState() == 0) {
          this.modelState.setiState(1);
        } else {
          this.modelState.setiState(0);
        }
        schedule.scheduleOutput(modelState.getiState(), generatorOutputPort, currentTime);
        schedule.add(LongSimTime.create(currentTime.getT() + 1), new FlipState());
      }
    }

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
  public void scheduledConfluentStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

  }


  @Override
  public Schedule<LongSimTime> getSchedule() {
    return schedule;
  }




  
}
