/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and
 * DEVS Streaming Framework Java contributors.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package devs.observation;

import devs.PDEVSModel;
import devs.PDevsSimulator;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Backend-neutral observation model that forwards observations to discovered sink actors.
 */
public class ObservationModel extends PDEVSModel<LongSimTime, Void> {

  private final List<Observation<LongSimTime, Object>> pendingObservations = new ArrayList<>();
  private final Set<ActorRef<DevsObservationMessage>> observationSinks = new LinkedHashSet<>();
  private final String runId;
  private final String branchId;
  private final String separator;
  private LongSimTime currentTime = LongSimTime.create(0L);

  /**
   * Creates an observation router that uses the default producer separator.
   *
   * @param identifier model identifier for this observation router
   * @param runId run identifier to attach to emitted observations
   * @param branchId branch identifier to attach to emitted observations
   */
  public ObservationModel(String identifier, String runId, String branchId) {
    this(identifier, runId, branchId, "_");
  }

  /**
   * Creates an observation router for a run and branch.
   *
   * @param identifier model identifier for this observation router
   * @param runId run identifier to attach to emitted observations
   * @param branchId branch identifier to attach to emitted observations
   * @param separator separator used to extract producer identifiers from port names
   */
  public ObservationModel(String identifier, String runId, String branchId, String separator) {
    super(null, identifier);
    this.runId = runId;
    this.branchId = branchId;
    this.separator = separator;
  }

  /**
   * Subscribes to the shared observation sink service key during model initialization.
   *
   * @param simulator simulator that provides access to the Pekko actor system
   */
  @Override
  public void initialize(PDevsSimulator<LongSimTime, Void, ?> simulator) {
    super.initialize(simulator);
    simulator.getContext().getSystem().receptionist().tell(
        Receptionist.subscribe(ObservationSinkKeys.OBSERVATION_SINK_KEY,
            simulator.getListingResponseAdapter())
    );
  }

  @Override
  protected void processReceptionistListing(PekkoReceptionistListingResponse receptionistListing) {
    Receptionist.Listing listing = receptionistListing.getListing();
    Set<ActorRef<DevsObservationMessage>> instances =
        listing.getServiceInstances(ObservationSinkKeys.OBSERVATION_SINK_KEY);
    this.observationSinks.clear();
    if (instances != null) {
      this.observationSinks.addAll(instances);
    }
    flushPendingObservations();
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
        if (observationSinks.isEmpty()) {
          pendingObservations.add(observation);
        } else {
          tellObservationSinks(observation);
        }
      }
    }
  }

  /**
   * Builds an observation message from an incoming port value.
   *
   * @param input incoming port value emitted by an observed model
   * @return observation message to forward to registered sinks
   */
  protected Observation<LongSimTime, Object> buildObservation(PortValue<?> input) {
    String producerId = ObservationUtils.extractProducerId(input.getPortName(), separator,
        modelIdentifier);

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

  private void flushPendingObservations() {
    if (observationSinks.isEmpty() || pendingObservations.isEmpty()) {
      return;
    }
    for (Observation<LongSimTime, Object> observation : pendingObservations) {
      tellObservationSinks(observation);
    }
    pendingObservations.clear();
  }

  private void tellObservationSinks(Observation<LongSimTime, Object> observation) {
    for (ActorRef<DevsObservationMessage> observationSink : observationSinks) {
      observationSink.tell(observation);
    }
  }
}