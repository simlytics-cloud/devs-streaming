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

package devs;

import devs.iso.log.RunIdMessage;
import devs.iso.log.StopLogger;
import java.io.OutputStream;
import java.io.PrintStream;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import devs.iso.log.DevsLogMessage;
import devs.utils.DevsObjectMapper;

/**
 * A utility actor to serialize log messages and write them to an ouput stream.
 */
public class DevsLoggingActor extends AbstractBehavior<DevsLogMessage> {

  private final PrintStream printStream;
  private final ObjectMapper objectMapper;
  private final String runId;

  /**
   * Factory class for creating instances of a DevsLoggingActor Behavior. This factory aids in
   * creating actor behaviors that serialize DEVS log messages and write them to a specified output
   * stream with an associated run identifier.
   */
  public static class DevsLoggingActorFactory {

    protected final OutputStream outputStream;
    protected final String runId;

    /**
     * Constructs a DevsLoggingActorFactory with the provided output stream and run identifier.
     *
     * @param outputStream the output stream where DEVS log messages will be written
     * @param runId        the identifier associated with the current simulation run
     */
    public DevsLoggingActorFactory(OutputStream outputStream, String runId) {
      this.outputStream = outputStream;
      this.runId = runId;
    }

    /**
     * Creates a new behavior for the DevsLoggingActor to handle and process {@link DevsLogMessage}
     * instances. This behavior enables serialization of DEVS log messages and directs them to the
     * specified output stream with an associated run identifier.
     *
     * @return a {@link Behavior} instance configured to process {@link DevsLogMessage} objects
     * using a DevsLoggingActor.
     */
    public Behavior<DevsLogMessage> createDevsLogMessageBehavior() {
      return DevsLoggingActor.create(outputStream, runId);
    }

  }

  /**
   * Creates a behavior for a DevsLoggingActor.
   *
   * @param outputStream the OutputStream where log messages will be written
   * @param runId        the unique identifier for this simulation run
   * @return a behavior instance for the DevsLoggingActor
   */
  public static Behavior<DevsLogMessage> create(OutputStream outputStream, String runId) {
    return Behaviors.setup(context -> {
      context.getSystem().receptionist()
          .tell(Receptionist.register(StateLoggingSimulator.stateLoggerKey, context.getSelf()));
      return new DevsLoggingActor(context, outputStream, runId);
    });
  }


  /**
   * Creates a DevsLoggingActor.
   *
   * @param context      the actor context
   * @param outputStream to OutputStream where messages will be logged
   * @param runId        the unique identifier for this simulation run
   */
  public DevsLoggingActor(ActorContext<DevsLogMessage> context, OutputStream outputStream,
      String runId) {
    super(context);
    this.printStream = new PrintStream(outputStream);
    this.objectMapper = DevsObjectMapper.buildObjectMapper();
    this.runId = runId;
    objectMapper.registerModule(new Jdk8Module());
  }

  @Override
  public Receive<DevsLogMessage> createReceive() {
    ReceiveBuilder<DevsLogMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsLogMessage.class, this::onDevsLogMessage);
    return builder.build();
  }

  /**
   * Handles a DevsLogMessage by either stopping the logger when a StopLogger message is received or
   * serializing and printing the log message.
   *
   * @param devsLogMessage the logging message to process; it may be either a message to stop
   *                       logging or another log message to handle
   * @return the updated behavior of the actor; it returns {@code Behaviors.stopped()} for a
   * StopLogger message or {@code Behaviors.same()} for other messages
   * @throws JsonProcessingException if there is an error during message serialization
   */
  protected Behavior<DevsLogMessage> onDevsLogMessage(DevsLogMessage devsLogMessage)
      throws JsonProcessingException {
    if (devsLogMessage instanceof StopLogger) {
      printStream.flush();
      printStream.close();
      return Behaviors.stopped();
    } else {
      RunIdMessage runIdMessage =
          RunIdMessage.builder().runId(runId).devsLogMessage(devsLogMessage).build();
      String output = objectMapper.writeValueAsString(runIdMessage);
      printStream.println(output);
      return Behaviors.same();
    }
  }
}
