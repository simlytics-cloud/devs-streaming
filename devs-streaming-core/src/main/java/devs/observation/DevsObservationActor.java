/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
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

import java.io.OutputStream;
import java.io.PrintStream;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import devs.utils.DevsObjectMapper;

/**
 * Utility actor that serializes observation messages and writes them to an output stream.
 */
public class DevsObservationActor extends AbstractBehavior<DevsObservationMessage> {

  private final PrintStream printStream;
  private final ObjectMapper objectMapper;
  private final String runId;

  /**
   * Factory for building observation writer behaviors with a fixed output stream and run id.
   */
  public static class DevsObservationActorFactory {

    protected final OutputStream outputStream;
    protected final String runId;

    /**
     * Constructs a DevsObservationActorFactory with the provided output stream and run identifier.
     *
     * @param outputStream the output stream where DEVS observation messages will be written
     * @param runId        the identifier associated with the current simulation run
     */
    public DevsObservationActorFactory(OutputStream outputStream, String runId) {
      this.outputStream = outputStream;
      this.runId = runId;
    }

    /**
     * Creates a new behavior for the DevsObservationActor to handle and process {@link DevsObservationMessage}
     * instances. This behavior enables serialization of DEVS observation messages and directs them to the
     * specified output stream with an associated run identifier.
     *
     * @return a {@link Behavior} instance configured to process {@link DevsObservationMessage} objects
     * using a DevsObservationActor.
     */
    public Behavior<DevsObservationMessage> createObservationBehavior() {
      return DevsObservationActor.create(outputStream, runId);
    }

  }

  /**
   * Creates a behavior for a DevsObservationActor.
   *
   * @param outputStream the OutputStream where observation messages will be written
   * @param runId        the unique identifier for this simulation run
   * @return a behavior instance for the DevsObservationActor
   */
  public static Behavior<DevsObservationMessage> create(OutputStream outputStream, String runId) {
    return Behaviors.setup(context -> {
      return new DevsObservationActor(context, outputStream, runId);
    });
  }


  /**
   * Creates a DevsObservationActor.
   *
   * @param context      the actor context
   * @param outputStream to OutputStream where messages will be observed
   * @param runId        the unique identifier for this simulation run
   */
  public DevsObservationActor(ActorContext<DevsObservationMessage> context, OutputStream outputStream,
                              String runId) {
    super(context);
    this.printStream = new PrintStream(outputStream);
    this.objectMapper = DevsObjectMapper.buildObjectMapper();
    this.runId = runId;
    objectMapper.registerModule(new Jdk8Module());
  }

  @Override
  public Receive<DevsObservationMessage> createReceive() {
    ReceiveBuilder<DevsObservationMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsObservationMessage.class, this::onObservationMessage);
    return builder.build();
  }

  /**
   * Handles observation messages by either shutting down or emitting a serialized line of JSON.
   *
   * @param devsObservationMessage observation message to process
   * @return updated actor behavior
   * @throws JsonProcessingException if there is an error during message serialization
   */
  protected Behavior<DevsObservationMessage> onObservationMessage(DevsObservationMessage devsObservationMessage)
      throws JsonProcessingException {
    if (devsObservationMessage instanceof StopLogger) {
      printStream.flush();
      printStream.close();
      return Behaviors.stopped();
    } else {
      String output = objectMapper.writeValueAsString(devsObservationMessage);
      printStream.println(output);
      return Behaviors.same();
    }
  }
}
