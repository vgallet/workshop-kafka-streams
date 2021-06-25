package fr.workshop.bank.transfer.kafkastreams;

import bank.transfer.avro.BankTransfer;
import bank.transfer.avro.User;
import bank.transfer.avro.UserBalance;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class UserBalanceApplication {

    public static final String USERS_TOPIC = "user";
    private static final String BANK_TRANSFER_TOPIC = "bank-transfer";

    public static void main(String[] args) {
        System.out.println(">>> Starting the streams-app Application");

        final Properties settings = new Properties();
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-balance-streams-app-1");
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        settings.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        settings.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", "http://schema-registry:8081");

        final Serde<User> userSerde = new SpecificAvroSerde<>();
        userSerde.configure(serdeConfig, false);

        final Serde<BankTransfer> bankTransferSerde = new SpecificAvroSerde<>();
        bankTransferSerde.configure(serdeConfig, false);

        final Serde<UserBalance> userBalanceSerde = new SpecificAvroSerde<>();
        userBalanceSerde.configure(serdeConfig, false);

        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, BankTransfer> bankTransferKStream = builder.stream(
                BANK_TRANSFER_TOPIC,
                Consumed.with(Serdes.String(), bankTransferSerde)
        );

//        bankTransferKStream.print(Printed.toSysOut());

        bankTransferKStream.filter(new Predicate<String, BankTransfer>() {
            @Override
            public boolean test(String key, BankTransfer value) {
                return value.getAmount() > 19000;
            }
        }).print(Printed.toSysOut());

        Topology topology = builder.build();
        System.out.println(topology.describe());
        final KafkaStreams streams = new KafkaStreams(topology, settings);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("<<< Stopping the streams-app Application");
            streams.close();
        }));

        streams.start();
    }

    private static class Operation {
        String user;
        Double amount;

        public Operation(String user, Double amount) {
            this.user = user;
            this.amount = amount;
        }
    }
}
