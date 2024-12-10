/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import devs.msg.log.DevsLogMessage;
import devs.msg.log.RunIdMessage;
import devs.utils.DevsObjectMapper;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.io.OutputStream;
import java.io.PrintStream;

public class DevsLoggingActor extends AbstractBehavior<DevsLogMessage> {

  private final PrintStream printStream;
  private final ObjectMapper objectMapper;
  private final String runId;

  public static class DevsLoggingActorFactory {

    protected final OutputStream outputStream;
    protected final String runId;

    public DevsLoggingActorFactory(OutputStream outputStream, String runId) {
      this.outputStream = outputStream;
      this.runId = runId;
    }

    public Behavior<DevsLogMessage> createDevsLogMessageBehaior() {
      return DevsLoggingActor.create(outputStream, runId);
    }

  }

  public static Behavior<DevsLogMessage> create(OutputStream outputStream, String runId) {
    return Behaviors.setup(context -> new DevsLoggingActor(context, outputStream, runId));
  }


  public DevsLoggingActor(ActorContext<DevsLogMessage> context, OutputStream outputStream, String runId) {
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

  protected Behavior<DevsLogMessage> onDevsLogMessage(DevsLogMessage devsLogMessage)
      throws JsonProcessingException {
    RunIdMessage runIdMessage = RunIdMessage.builder().runId(runId).devsLogMessage(devsLogMessage).build();
    String output = objectMapper.writeValueAsString(runIdMessage);
    printStream.println(output);
    return Behaviors.same();
  }
}
