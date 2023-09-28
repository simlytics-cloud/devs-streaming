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

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import devs.msg.log.DevsLogMessage;
import devs.utils.DevsObjectMapper;
import java.io.OutputStream;
import java.io.PrintStream;

public class DevsLoggingActor extends AbstractBehavior<DevsLogMessage> {

  private final PrintStream printStream;
  private final ObjectMapper objectMapper;

  public static Behavior<DevsLogMessage> create(OutputStream outputStream) {
    return Behaviors.setup(context -> new DevsLoggingActor(context, outputStream));
  }


  public DevsLoggingActor(ActorContext<DevsLogMessage> context, OutputStream outputStream) {
    super(context);
    this.printStream = new PrintStream(outputStream);
    this.objectMapper = DevsObjectMapper.buildObjectMapper();
    ;
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
    String output = objectMapper.writeValueAsString(devsLogMessage);
    printStream.println(output);
    return Behaviors.same();
  }
}
