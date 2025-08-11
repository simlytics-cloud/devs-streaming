/*
 * MCA Acquire Sensors Library Copyright (C) 2025 simlytics.cloud LLC and
 * MCA Acquire Sensors Library contributors.  All rights reserved.
 *
 * This software is provided to the US Army by simlytics.cloud, LLC
 * to use and to create non-commercial content.  Any commercial use is
 * prohibited without permission.
 *
 *  The U.S. Government has Government Purpose Rights (GPR) to the software.
 *
 *  DISTRIBUTION STATEMENT C:
 *  Distribution is authorized to U.S. Government Agencies and their contractors.
 *  Other requests for this document shall be referred to US Army DEVCOM - Soldier Center
 *
 * Unless required by applicable law or agreed to in writing, software is distributed on an
 */

package devs.utils;

import devs.msg.DevsExternalMessage;
import devs.msg.DevsMessage;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.BoundedSourceQueue;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import scala.util.Try;


public abstract class HttpServiceActor<T1, T2> extends AbstractBehavior<HttpServiceActor.Command> {
  
  public interface Command {

  }

  public interface Response extends DevsExternalMessage {

  }

  public record ErrorResponse(String error, String requestId) implements Response {

  }

  public record SucceffulResponse<T2>(T2 responseData,
                                      String requestId) implements Response {

  }

  public record Request<T1>(String requestData,
                            ActorRef<DevsMessage> requester,
                            String requestId) implements Command {

  }

  public record SingleHttpResponse<T1>(HttpResponse response,
                                   Request<T1> request) implements
      Command {

  }

  public record SingleHttpFailure<T1>(String error,
                                  Request<T1> request) implements
      Command {

  }

  protected final BoundedSourceQueue<Pair<HttpRequest, Request<T1>>> sourceQueue;

  public HttpServiceActor(ActorContext<Command> context, String acquireUrl) {
    super(context);
    Flow<Pair<HttpRequest, Request<T1>>, Pair<Try<HttpResponse>, Request<T1>>, NotUsed> httpFlow = Http.get(
        context.getSystem()).superPool();

    sourceQueue =
        Source.<Pair<HttpRequest, Request<T1>>>queue(1000)
            .via(httpFlow)
            .to(Sink.foreachAsync(100, response ->
                CompletableFuture.runAsync(() -> {
                  if (response.first().isSuccess()) {
                    HttpResponse httpResponse = response.first().get();
                    context.getSelf()
                        .tell(new SingleHttpResponse<T1>(httpResponse, response.second()));
                  } else {
                    context.getSelf().tell(
                        new SingleHttpFailure<T1>(response.first().failed().get().getMessage(),
                            response.second()));
                  }
                }, context.getSystem().executionContext())))
            .run(context.getSystem());
  }

  @Override
  public Receive<Command> createReceive() {
    ReceiveBuilder<Command> builder = newReceiveBuilder();
    builder.onMessage(Request.class, this::onRequest);
    builder.onMessage(SingleHttpResponse.class, this::onSingleHttpResponse);
    builder.onMessage(SingleHttpFailure.class, this::onSingleHttpFailure);
    return builder.build();
  }

  protected abstract Behavior<Command> onRequest(Request<T1> request);
  protected abstract Behavior<Command> onSingleHttpResponse(SingleHttpResponse<T1> singleHttpResponse);
  protected abstract Behavior<Command> onSingleHttpFailure(SingleHttpFailure<T1> singleHttpFailure);
}
