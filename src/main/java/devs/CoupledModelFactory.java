package devs;

import devs.msg.DevsMessage;
import devs.msg.log.DevsLogMessage;
import devs.msg.time.SimTime;
import devs.proxy.KafkaLocalProxy;
import devs.utils.ModelUtils;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import java.util.*;

public class CoupledModelFactory<T extends SimTime> {

  protected final String modelIdentifier;
  protected final List<PDEVSModel<T, ?>> devsModels;
  protected final List<CoupledModelFactory<T>> coupledModelFactories;
  protected final PDevsCouplings couplings;
  protected final List<String> loggingModels = new ArrayList<>();
  protected List<KafkaLocalProxy.ProxyProperties> proxyModels = new ArrayList<>();
  protected Optional<ActorRef<DevsLogMessage>> devsLoggerOption = Optional.empty();

  public CoupledModelFactory(String modelIdentifier, List<PDEVSModel<T, ?>> devsModels,
      List<CoupledModelFactory<T>> coupledModelFactories, PDevsCouplings couplings) {
    this.modelIdentifier = modelIdentifier;
    this.devsModels = devsModels;
    this.coupledModelFactories = coupledModelFactories;
    this.couplings = couplings;
  }

  public CoupledModelFactory(String modelIdentifier, List<PDEVSModel<T, ?>> devsModels,
      List<CoupledModelFactory<T>> coupledModelFactories, List<KafkaLocalProxy.ProxyProperties> proxyModels, PDevsCouplings couplings) {
    this.modelIdentifier = modelIdentifier;
    this.devsModels = devsModels;
    this.coupledModelFactories = coupledModelFactories;
    this.proxyModels = proxyModels;
    this.couplings = couplings;
  }

  public void addDevsLogger(ActorRef<DevsLogMessage> devsLoggger) {
    devsLoggerOption = Optional.of(devsLoggger);
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
      Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
      for (PDEVSModel<T, ?> devsModel: devsModels) {
        ActorRef<DevsMessage> atomicModelRef;

        if (devsLoggerOption.isPresent() && loggingModels.contains(devsModel.getModelIdentifier())) {
          atomicModelRef = context.spawn(StateLoggingSimulator.create(
              devsModel, initialTime, devsLoggerOption.get()), ModelUtils.toLegalActorName(devsModel.getModelIdentifier()));
        } else {
          atomicModelRef = context.spawn(PDevsSimulator.create(
              devsModel, initialTime), ModelUtils.toLegalActorName(devsModel.getModelIdentifier()));
        }
        context.watch(atomicModelRef);
        modelSimulators.put(devsModel.getModelIdentifier(), atomicModelRef);
      }

      for (KafkaLocalProxy.ProxyProperties proxy: proxyModels) {
        ActorRef<DevsMessage> kafkaLocalProxy = context.spawn(KafkaLocalProxy.create(proxy), ModelUtils.toLegalActorName(proxy.componentName()));
        modelSimulators.put(proxy.componentName(), kafkaLocalProxy);
      }

      for (CoupledModelFactory<T> factory: coupledModelFactories) {
        if (devsLoggerOption.isPresent()) {
          factory.addDevsLogger(devsLoggerOption.get());
          factory.addLoggingModels(loggingModels);
        }
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
