/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and
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

package devs.iso;

import devs.iso.time.SimTime;
import devs.time.EnhancedClockContext;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Represents a message used to initialize a simulation within the DEVS framework.
 * <p>
 * This message is passed to an actor to initiate the simulation process. It encapsulates the
 * initialization details along with a reference to the parent actor for communication.
 *
 * @param <T> the type of simulation time associated with the initialization.
 */
public class SimulationInitMessage<T extends SimTime> implements DevsMessage {

  private final ActorRef<DevsMessage> parent;
  private final SimulationInit<T> simulationInit;
  private final EnhancedClockContext enhancedClock;

  /**
   * Constructs an InitSimMessage instance to initialize a simulation.
   *
   * @param simulationInit the initialization configuration for the simulation.
   * @param parent  the reference to the parent actor for communication.
   */
  public SimulationInitMessage(SimulationInit<T> simulationInit, ActorRef<DevsMessage> parent) {
    this(simulationInit, parent, null);
  }

  /**
   * Constructs a local initialization wrapper with optional enhanced clock context.
   * The context is local-only; transport validation rejects wrappers that carry it.
   *
   * @param simulationInit the simulation initialization configuration
   * @param parent the parent actor used as the return path
   * @param enhancedClock the enhanced clock context, or {@code null} for legacy initialization
   */
  public SimulationInitMessage(SimulationInit<T> simulationInit, ActorRef<DevsMessage> parent,
      EnhancedClockContext enhancedClock) {
    this.parent = parent;
    this.simulationInit = simulationInit;
    this.enhancedClock = enhancedClock;
  }

  public ActorRef<DevsMessage> getParent() {
    return parent;
  }

  public SimulationInit<T> getSimulationInit() {
    return simulationInit;
  }

  /** @return the local enhanced clock context, or empty for legacy initialization */
  public Optional<EnhancedClockContext> getEnhancedClock() {
    return Optional.ofNullable(enhancedClock);
  }
}
