package devs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import devs.msg.DevsMessage;
import devs.msg.log.DevsLogMessage;
import devs.msg.time.SimTime;
import devs.utils.ModelUtils;

public class CoupledModelFactory<T extends SimTime> {

	protected final String modelIdentifier;
	protected final List<PDEVSModel<T, ?>> devsModels;
	protected final List<CoupledModelFactory<T>> coupledModelFactories;
	protected final PDevsCouplings couplings;
	protected final List<String> loggingModels = new ArrayList<>();
	protected Optional<DevsLoggerFactory> devsLoggerFactoryOption = Optional.empty();
	
	public CoupledModelFactory(String modelIdentifier, List<PDEVSModel<T, ?>> devsModels, 
			List<CoupledModelFactory<T>> coupledModelFactories, PDevsCouplings couplings) {
		this.modelIdentifier = modelIdentifier;
		this.devsModels = devsModels;
		this.coupledModelFactories = coupledModelFactories;
		this.couplings = couplings;
	}

	public void addDevsLoggerFactor(DevsLoggerFactory devsLoggerFactory) {
		devsLoggerFactoryOption = Optional.of(devsLoggerFactory);
	}

	public void addLoggingModels(List<String> modelIds) {
		loggingModels.addAll(modelIds);
	}

	public void addLoggingModel(String modelIds) {
		loggingModels.add(modelIds);
	}

	public List<String> getLoggingModels() {
		return loggingModels;
	}
	
    public Behavior<DevsMessage> create(String parentIdentifier, T initialTime) {
    	return Behaviors.setup(context -> {
			ActorRef<DevsLogMessage> loggingActor = null;
			if (devsLoggerFactoryOption.isPresent()) {
				loggingActor = context.spawn(devsLoggerFactoryOption.get().createDevsLogMessageBehaior(), "loggingActor");
			}
	        Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
			for (PDEVSModel<T, ?> devsModel: devsModels) {
				ActorRef<DevsMessage> atomicModelRef;

				if (devsLoggerFactoryOption.isPresent() && loggingActor != null && loggingModels.contains(devsModel.getModelIdentifier())) {
					atomicModelRef = context.spawn(StateLoggingSimulator.create(
						devsModel, initialTime, loggingActor), ModelUtils.toLegalActorName(devsModel.getModelIdentifier()));
				} else {
					atomicModelRef = context.spawn(PDevsSimulator.create(
						devsModel, initialTime), ModelUtils.toLegalActorName(devsModel.getModelIdentifier()));
				}
	           context.watch(atomicModelRef);
	           modelSimulators.put(devsModel.getModelIdentifier(), atomicModelRef);
			}
	        
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
