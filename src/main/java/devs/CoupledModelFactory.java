package devs;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import devs.msg.DevsMessage;
import devs.msg.time.SimTime;
import devs.utils.ModelUtils;

public class CoupledModelFactory<T extends SimTime> {

	protected final String modelIdentifier;
	protected final List<PDEVSModel<T, ?>> devsModels;
	protected final List<CoupledModelFactory<T>> coupledModelFactories;
	protected final PDevsCouplings couplings;
	
	public CoupledModelFactory(String modelIdentifier, List<PDEVSModel<T, ?>> devsModels, 
			List<CoupledModelFactory<T>> coupledModelFactories, PDevsCouplings couplings) {
		this.modelIdentifier = modelIdentifier;
		this.devsModels = devsModels;
		this.coupledModelFactories = coupledModelFactories;
		this.couplings = couplings;
	}
	
    public Behavior<DevsMessage> create(String parentIdentifier, T initialTime) {
    	return Behaviors.setup(context -> {
	        Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
	        devsModels.stream().forEach(devsModel -> {
	            ActorRef<DevsMessage> atomicModelRef = context.spawn(PDevsSimulator.create(
	                devsModel, initialTime), ModelUtils.toLegalActorName(devsModel.getModelIdentifier()));
	           context.watch(atomicModelRef);
	           modelSimulators.put(devsModel.getModelIdentifier(), atomicModelRef);
	        });
	        
	        for (CoupledModelFactory<T> factory: coupledModelFactories) {
	        	ActorRef<DevsMessage> coupledModel = context.spawn(factory.create(modelIdentifier, initialTime), 
	        			ModelUtils.toLegalActorName(factory.getModelIdentifier()));
	        	context.watch(coupledModel);
	        	modelSimulators.put(factory.getModelIdentifier(), coupledModel);
	        }
	        
	        return new PDevsCoordinator<T>(modelIdentifier, parentIdentifier, modelSimulators, couplings, context);
    	});
    }

	public String getModelIdentifier() {
		return modelIdentifier;
	}
    

}
