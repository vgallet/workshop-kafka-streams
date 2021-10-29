package fr.workshop.bank.transfer.kafkastreams;

import bank.transfer.avro.BankTransfer;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static fr.workshop.bank.transfer.kafkastreams.KafkaStreamsApplicationAlertBigAmount.ALERT_HUGE_AMOUNT_TOPIC;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsApplicationAlertBigAmountTest {

    private static final String SCHEMA_REGISTRY_SCOPE = KafkaStreamsApplicationAlertBigAmountTest.class.getName();
    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;

    @Test
    public void should_filter_huge_amount() {

        final Topology topology = KafkaStreamsApplicationAlertBigAmount.createTopology(MOCK_SCHEMA_REGISTRY_URL);
        final Properties streamsConfiguration = KafkaStreamsApplicationAlertBigAmount.configuration();
        streamsConfiguration.put(SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);

        Serde<BankTransfer> bankTransferSerde = new SpecificAvroSerde<>();
        Map<String, String> config = Map.of(SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);
        bankTransferSerde.configure(config, false);

        // TODO 03
        try (final TopologyTestDriver topologyTestDriver = new TopologyTestDriver(topology, streamsConfiguration)) {

            BankTransfer firstBankTransfer = BankTransfer.newBuilder()
                .setAmount(16_000d)
                .setCredit("Credit")
                .setDebtor("Debit")
                .setLocation("Location")
                .setDate("date")
            .build();

            BankTransfer secondBankTransfer = BankTransfer.newBuilder()
                .setAmount(12d)
                .setCredit("Credit")
                .setDebtor("Debit")
                .setLocation("Location")
                .setDate("date")
            .build();

        }
    }

    @AfterEach
    public void after() {
        MockSchemaRegistry.dropScope(SCHEMA_REGISTRY_SCOPE);
    }
}
