package fr.workshop.bank.transfer.kafkastreams;

import bank.transfer.avro.*;
import fr.workshop.bank.transfer.UserBalanceServer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Duration;
import java.util.*;

import static org.apache.kafka.streams.kstream.Suppressed.BufferConfig.unbounded;

public class KafkaStreamsApplication {

    public static final String USERS_TOPIC = "user";
    public static final String ALERT_HUGE_AMOUNT_TOPIC = "alert-huge-amount";
    public static final String ALERT_TOO_MUCH_OPERATIONS_TOPIC = "alert-too-much-operations";
    public static final String ALERT_DIFFERENT_LOCATION_TOPIC = "alert-different-location";
    private static final String BANK_TRANSFER_TOPIC = "bank-transfer";
    private static final String BANK_TRANSFER_USER_TOPIC = "bank-transfer-user";
    private static final String USER_BALANCE_TOPIC = "user-balance";
    public static final String BALANCE_VIEW = "BALANCE_VIEW";
    public static final String APPLICATION_SERVER = "localhost:8090";

    public static void main(String[] args) throws Exception {
        System.out.println(">>> Starting the streams-app Application");

        final Properties settings = new Properties();
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG, UUID.randomUUID().toString());
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        settings.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        settings.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        settings.put(StreamsConfig.APPLICATION_SERVER_CONFIG, APPLICATION_SERVER);
        // TODO 06
        settings.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE);

        Topology topology = createTopology();
        System.out.println(topology.describe());

        final KafkaStreams streams = new KafkaStreams(topology, settings);
        streams.setGlobalStateRestoreListener(new ConsoleGlobalRestoreListerner());

        streams.start();

        UserBalanceServer server = new UserBalanceServer(streams);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("<<< Stopping the streams-app Application");
            streams.close();
            try {
                server.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

    }

    private static Topology createTopology() {
        // Avro Configuration
        final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", "http://schema-registry:8081");

        final Serde<User> userSerde = new SpecificAvroSerde<>();
        userSerde.configure(serdeConfig, false);

        final Serde<BankTransfer> bankTransferSerde = new SpecificAvroSerde<>();
        bankTransferSerde.configure(serdeConfig, false);

        final Serde<UserBalance> userBalanceSerde = new SpecificAvroSerde<>();
        userBalanceSerde.configure(serdeConfig, false);

        final Serde<UserOperation> userOperationSerde = new SpecificAvroSerde<>();
        userOperationSerde.configure(serdeConfig, false);

        final Serde<BankTransferWithUser> bankTransferUserSerde = new SpecificAvroSerde<>();
        bankTransferUserSerde.configure(serdeConfig, false);

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

        // TODO 03
        bankTransferKStream
            .groupByKey()
            .windowedBy(TimeWindows.of(Duration.ofMillis(1000)))
            .count()
            .filter((key, value) -> value >= 2)
            .suppress(Suppressed.untilWindowCloses(unbounded()))
            .toStream()
                .foreach(new ForeachAction<Windowed<String>, Long>() {
                    @Override
                    public void apply(Windowed<String> key, Long value) {
                        System.out.println(key + " count " + value);
                    }
                });
//            .to(ALERT_TOO_MUCH_OPERATIONS_TOPIC, Produced.with(timeWindowedSerdeFrom(String.class), Serdes.Long()));


        // TODO 04
        KTable<String, User> userTable = builder.table(USERS_TOPIC, Consumed.with(Serdes.String(), userSerde));

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
            .filter((key, value) -> value.getBanktransferLocation() != value.getUserCity())
            .to(ALERT_DIFFERENT_LOCATION_TOPIC, Produced.with(Serdes.String(), bankTransferUserSerde));


        // TODO 05
        Materialized<String, UserBalance, KeyValueStore<Bytes, byte[]>> balanceStore = Materialized.<String, UserBalance, KeyValueStore<Bytes, byte[]>>as(BALANCE_VIEW)
                .withKeySerde(Serdes.String())
                .withValueSerde(userBalanceSerde);

        bankTransferKStream
                .flatMap((KeyValueMapper<String, BankTransfer, Iterable<KeyValue<String, UserOperation>>>) (key, value) -> {
                    UserOperation creditOperation = new UserOperation(value.getCredit(), -value.getAmount());
                    UserOperation debtorOperation = new UserOperation(value.getDebtor(), value.getAmount());

                    return List.of(
                            KeyValue.pair(creditOperation.getClient().toString(), creditOperation),
                            KeyValue.pair(debtorOperation.getClient().toString(), debtorOperation)
                    );
                })
                .groupByKey(Grouped.with(Serdes.String(), userOperationSerde))
                .aggregate(
                        () -> null,
                        (key, value, currentBalance) -> {
                            if (currentBalance == null) {
                                return new UserBalance(value.getClient(), value.getAmount());
                            }

                            currentBalance.setAmount(currentBalance.getAmount() + value.getAmount());
                            return currentBalance;
                        }, balanceStore)
            .toStream()
            .to(USER_BALANCE_TOPIC, Produced.with(Serdes.String(), userBalanceSerde));

        return builder.build();
    }

}
