package fr.workshop.bank.transfer.kafkastreams;

import bank.transfer.avro.BankTransfer;
import bank.transfer.avro.User;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class KafkaStreamsApplicationAlertBigAmount {

    public static final String ALERT_HUGE_AMOUNT_TOPIC = "alert-huge-amount";
    public static final String BANK_TRANSFER_TOPIC = "bank-transfer";
    public static final String APPLICATION_SERVER = "localhost:8090";

    public static void main(String[] args) {
        System.out.println(">>> Starting the KafkaStreamsApplicationAlertBigAmount Application");

        final Properties settings = configuration();

        Topology topology = createTopology("http://schema-registry:8081");
        System.out.println(topology.describe());

        final KafkaStreams streams = new KafkaStreams(topology, settings);
        streams.setGlobalStateRestoreListener(new ConsoleGlobalRestoreListerner());

        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("<<< Stopping the KafkaStreamsApplicationAlertBigAmount Application");
            streams.close();
        }));

    }

    public static Properties configuration() {
        final Properties settings = new Properties();
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG, UUID.randomUUID().toString());
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        settings.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        settings.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        settings.put(StreamsConfig.APPLICATION_SERVER_CONFIG, APPLICATION_SERVER);
        return settings;
    }

    public static Topology createTopology(String schemaRegistryUrl) {
        // Avro Configuration
        final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", schemaRegistryUrl);

        final Serde<User> userSerde = new SpecificAvroSerde<>();
        userSerde.configure(serdeConfig, false);

        final Serde<BankTransfer> bankTransferSerde = new SpecificAvroSerde<>();
        bankTransferSerde.configure(serdeConfig, false);

        final StreamsBuilder builder = new StreamsBuilder();

        // BankTransfer Stream
        KStream<String, BankTransfer> bankTransferKStream = builder.stream(
                BANK_TRANSFER_TOPIC,
                Consumed.with(Serdes.String(), bankTransferSerde)
        );

        // TODO 01 use the print operation with Printed.toSysOut() argument
//        bankTransferKStream.print(Printed.toSysOut());


        // TODO 02
        bankTransferKStream
            .filter((key, value) -> value.getAmount() > 15_000)
            .to(ALERT_HUGE_AMOUNT_TOPIC, Produced.valueSerde(bankTransferSerde));

        return builder.build();
    }

}
