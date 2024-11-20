package devs.proxy;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.ActorAttributes;
import org.apache.pekko.stream.Supervision;
import org.apache.pekko.stream.javadsl.Sink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.typesafe.config.Config;

import devs.msg.DevsMessage;
import devs.msg.InitSimMessage;
import devs.msg.time.SimTime;
import devs.utils.DevsObjectMapper;



/**
 * This actor serves as a local proxy for a remote DEVS simulator. It will have
 * a local  coordinator.
 * It get the identity of that coordinator from the initial InitSimMessage.
 * It passes all received DevsMessags to the Kafka producer.
 * All messages received from the consumer go to the parent coordinator.
 */
public class KafkaLocalProxy<T extends SimTime> extends KafkaDevsStreamProxy<T> {

  public static record ProxyProperties(
    String componentName, 
    String producerTopic,
    Config kafkaProducerConfig, 
    String consumerTopic, 
    Config kafkaConsumerConfig
  ){}

  public static <TT extends SimTime> Behavior<DevsMessage> create(
    ProxyProperties props
  ) {
    return Behaviors.setup(context -> new KafkaLocalProxy<TT>(context, props));
  }

  protected final Consumer.DrainingControl<Done> control;
  protected final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
  protected Optional<ActorRef<DevsMessage>> localParentCoordinator;

  public KafkaLocalProxy(ActorContext<DevsMessage> context,
      ProxyProperties props) {
    super(context, props.componentName(), props.producerTopic(), props.kafkaProducerConfig());

    ConsumerSettings<String, String> consumerSettings = ConsumerSettings
        .create(props.kafkaConsumerConfig(), new StringDeserializer(),
            new StringDeserializer())
        .withGroupId(java.util.UUID.randomUUID().toString());

    // Using a Kafka consumer from the Alpakka Kafka project because this consumer
    // does a better job of managing
    // threads. For example, the Java Kafka consumer uses an infinite loop to poll
    // for data
    // consuming an entire thread for this purpose
    // The planSource consumer does not auto commit and subscribes to the
    // webLvcTopic
    // The consumer's auto.offset.reset property is set to earliest so it always
    // reads all data
    this.control = Consumer.plainSource(consumerSettings, Subscriptions.topics(props.consumerTopic()))
        .map(record -> {
          System.out.println("Kafka received record: " + record.value());
          processRecord(record);
          return NotUsed.notUsed();
        })
        // This supervisor strategy will drop the current record being processed in the
        // event of an
        // error and will continue consuming with the next message
        .withAttributes(ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()))
        // This statement enables logging of messages in the previous step of the stream
        .log("LopConsumerLog")
        // Connect to a sink to continuously run the stream and a materializer that
        // gives a control
        // to shut down the stream on command.
        .toMat(Sink.ignore(), Consumer::createDrainingControl)
        .run(getContext().getSystem());
  }

  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsMessage.class, this::onDevsMessage);
    return builder.build();
  }

  @Override
  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    // Set the local coordinator
    if (devsMessage instanceof InitSimMessage initSimMessage) {
      this.localParentCoordinator = Optional.of(initSimMessage.getParent());
    }
    // Then pass the message to super to be sent to Kafka
    super.onDevsMessage(devsMessage);
    return Behaviors.same();
  }

  /**
   * Process a kafka record, convert it to a DevsMessage, and send it to the local
   * parent
   * 
   * @param record
   */
  private void processRecord(ConsumerRecord<String, String> record) {
    DevsMessage devsMessage = null;
    try {
      devsMessage = objectMapper.readValue(record.value(), DevsMessage.class);
    } catch (JsonProcessingException e) {
      System.err.println("Could not deserialize JSON record " + record.value());
      e.printStackTrace();
      System.exit(1);
    }
    if (localParentCoordinator.isPresent()) {
      localParentCoordinator.get().tell(devsMessage);
    } else {
      System.err
          .println("ERROR: Recived the following DevsMessage from Kafka before learning the identity of the local \n" +
              " parent coordinator via an InitSimMessage: " + record.value());
      System.exit(1);
    }
  }

}
