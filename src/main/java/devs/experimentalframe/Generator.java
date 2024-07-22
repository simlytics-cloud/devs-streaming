package devs.experimentalframe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import devs.PDEVSModel;
import devs.Port;
import devs.ScheduledDevsModel;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.DoubleSimTime;
import devs.msg.time.LongSimTime;
import devs.msg.time.SimTime;
import devs.utils.Schedule;



public abstract class Generator<T extends SimTime> extends ScheduledDevsModel<T, Void> {
	


	public static final String modelIdentifier = "Generator";
	final protected Map<String, Port<?>> ports;
	
	public Generator(String modelIdentifier, Schedule<T> schedule) {
		super(null, modelIdentifier, schedule);
		this.ports = buildPorts();
	}

	
	protected abstract Map<String, Port<?>> buildPorts();


	@Override
	public void internalStateTransitionFunction(T currentTime) {
		clearPendingOutput();
		
	}

	@Override
	public void externalStateTransitionFunction(T currentTime, Bag bag) {
	    // No external events
	    throw new IllegalArgumentException("Generator does not expect external events.  \n" +
	            "Got event with port identifier of " + bag.getPortValueList().get(0).getPortIdentifier());
		
	}

	@Override
	public void confluentStateTransitionFunction(T currentTime, Bag bag) {
		externalStateTransitionFunction(currentTime, bag); // Will throw an error.  No external events expected
		internalStateTransitionFunction(currentTime);
	}


	public Map<String, Port<?>> getPorts() {
		return ports;
	}



}
