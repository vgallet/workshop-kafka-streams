package fr.workshop.bank.transfer.kafkastreams;

import bank.transfer.avro.BankTransfer;
import bank.transfer.avro.BankTransferWithUser;
import bank.transfer.avro.User;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class KafkaStreamsApplicationDifferentLocation {

    public static final String USERS_TOPIC = "user";
    public static final String ALERT_DIFFERENT_LOCATION_TOPIC = "alert-different-location";
    public static final String BANK_TRANSFER_TOPIC = "bank-transfer";
    public static final String BANK_TRANSFER_USER_TOPIC = "bank-transfer-user";
    public static final String APPLICATION_SERVER = "localhost:8090";

    public static void main(String[] args) throws Exception {
        System.out.println(">>> Starting the KafkaStreamsApplicationDifferentLocation Application");

        final Properties settings = configuration();

        Topology topology = createTopology("http://schema-registry:8081");
        System.out.println(topology.describe());

        final KafkaStreams streams = new KafkaStreams(topology, settings);
        streams.setGlobalStateRestoreListener(new ConsoleGlobalRestoreListerner());

        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("<<< Stopping the KafkaStreamsApplicationDifferentLocation Application");
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

        final Serde<BankTransferWithUser> bankTransferUserSerde = new SpecificAvroSerde<>();
        bankTransferUserSerde.configure(serdeConfig, false);

        final StreamsBuilder builder = new StreamsBuilder();

        // BankTransfer Stream
        KStream<String, BankTransfer> bankTransferKStream = builder.stream(
                BANK_TRANSFER_TOPIC,
                Consumed.with(Serdes.String(), bankTransferSerde)
        );

        // TODO 05
        KTable < String, User > userTable = builder.table(USERS_TOPIC, Consumed.with(Serdes.String(), userSerde));

        ValueJoiner<BankTransfer, User, BankTransferWithUser> bankTransferWithUserJoiner = (bankTransfer, user) -> {
            BankTransferWithUser bankTransferWithUser = new BankTransferWithUser();
            bankTransferWithUser.setBanktransferAmount(bankTransfer.getAmount());
            bankTransferWithUser.setBanktransferCredit(bankTransfer.getCredit());
            bankTransferWithUser.setBanktransferDate(bankTransfer.getDate());
            bankTransferWithUser.setBanktransferDebtor(bankTransfer.getDebtor());
            bankTransferWithUser.setBanktransferLocation(bankTransfer.getLocation());
            bankTransferWithUser.setUserName(user.getName());
            bankTransferWithUser.setUserCity(user.getCity());
            return bankTransferWithUser;
        };

        bankTransferKStream
            .join(userTable, bankTransferWithUserJoiner)
            .through(BANK_TRANSFER_USER_TOPIC, Produced.with(Serdes.String(), bankTransferUserSerde))
            .filter((key, value) -> !value.getBanktransferLocation().equals(value.getUserCity()))
            .to(ALERT_DIFFERENT_LOCATION_TOPIC, Produced.with(Serdes.String(), bankTransferUserSerde));


        return builder.build();
    }

}
