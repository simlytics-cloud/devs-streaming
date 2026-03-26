package devs.observation.mongodb;

import devs.PDEVSModel;
import devs.PDevsSimulator;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import devs.msg.mutability.Immutable;
import devs.observation.DevsObservationMessage;
import devs.observation.Observation;
import devs.observation.ObservationUtils;
import devs.observation.PekkoReceptionistListingResponse;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MongoObserverModel extends PDEVSModel<LongSimTime, Void> {
    private ActorRef<DevsObservationMessage> mongoActor;
    private final List<Observation<LongSimTime, Object>> pendingObservations = new java.util.ArrayList<>();
    private final String runId;
    private final String branchId;
    private final String separator;
    private LongSimTime currentTime = LongSimTime.create(0L);

    public MongoObserverModel(String identifier, String runId, String branchId) {
        this(identifier, runId, branchId, "_");
    }

    public MongoObserverModel(String identifier, String runId, String branchId, String separator) {
        super(null, identifier);
        this.runId = runId;
        this.branchId = branchId;
        this.separator = separator;
    }

    @Override
    public void initialize(PDevsSimulator<LongSimTime, Void, ?> simulator) {
        super.initialize(simulator);
        simulator.getContext().getSystem().receptionist().tell(
            Receptionist.subscribe(MongoObservationActor.MONGO_OBSERVATION_KEY, simulator.getListingResponseAdapter())
        );
    }

    @Override
    protected void processReceptionistListing(PekkoReceptionistListingResponse receptionistListing) {
        Receptionist.Listing listing = receptionistListing.getListing();
        Set<ActorRef<DevsObservationMessage>> instances = listing.getServiceInstances(MongoObservationActor.MONGO_OBSERVATION_KEY);
        if (instances != null && !instances.isEmpty()) {
            this.mongoActor = instances.iterator().next();
            for (Observation<LongSimTime, Object> obs : pendingObservations) {
                this.mongoActor.tell(obs);
            }
            pendingObservations.clear();
        }
    }

    @Override
    public void internalStateTransitionFunction() {
        // No internal transitions
    }

    @Override
    public void externalStateTransitionFunction(LongSimTime elapsedTime, List<PortValue<?>> inputs) {
        currentTime = currentTime.plus(elapsedTime);
        for (PortValue<?> input : inputs) {
            Observation<LongSimTime, Object> observation = buildObservation(input);
            if (observation != null) {
                if (mongoActor != null) {
                    mongoActor.tell(observation);
                } else {
                    pendingObservations.add(observation);
                }
            }
        }
    }

    /**
     * Builds an Observation object from an incoming PortValue.
     * Subclasses can override this to provide custom mapping or metadata.
     *
     * @param input the incoming PortValue
     * @return an Observation object or null if it should be ignored
     */
    protected Observation<LongSimTime, Object> buildObservation(PortValue<?> input) {
        String producerId = ObservationUtils.extractProducerId(input.getPortName(), separator, modelIdentifier);

        // Validation that the producer is correctly extracted (e.g., if it contains a separator)
        if (input.getPortName().contains(separator)) {
            assert !producerId.equals(modelIdentifier) : "Producer ID should be extracted from port name";
        }

        return Observation.<LongSimTime, Object>builder()
                .runId(runId)
                .branchId(branchId)
                .time(currentTime)
                .producerModel(producerId)
                .observationType(input.getValue().getClass().getSimpleName())
                .payload(input.getValue())
                .build();
    }

    @Override
    public void confluentStateTransitionFunction(List<PortValue<?>> inputs) {
        externalStateTransitionFunction(LongSimTime.create(0L), inputs);
    }

    @Override
    public LongSimTime timeAdvanceFunction() {
        return LongSimTime.buildMaxValue();
    }

    @Override
    public List<PortValue<?>> outputFunction() {
        return Collections.emptyList();
    }
}
