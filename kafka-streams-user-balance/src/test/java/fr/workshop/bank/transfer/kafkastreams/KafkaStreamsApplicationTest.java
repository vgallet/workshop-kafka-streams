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

import static fr.workshop.bank.transfer.kafkastreams.KafkaStreamsApplicationUserBalance.ALERT_HUGE_AMOUNT_TOPIC;
import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsApplicationTest {

    private static final String SCHEMA_REGISTRY_SCOPE = KafkaStreamsApplicationTest.class.getName();
    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;

    @Test
    public void should_filter_huge_amount() {

        final Topology topology = KafkaStreamsApplicationUserBalance.createTopology(MOCK_SCHEMA_REGISTRY_URL);
        final Properties streamsConfiguration = KafkaStreamsApplicationUserBalance.configuration();
        streamsConfiguration.put(SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);

        Serde<BankTransfer> bankTransferSerde = new SpecificAvroSerde<>();
        Map<String, String> config = Map.of(SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);
        bankTransferSerde.configure(config, false);

        try (final TopologyTestDriver topologyTestDriver = new TopologyTestDriver(topology, streamsConfiguration)) {

            final TestInputTopic<String, BankTransfer> input = topologyTestDriver
                    .createInputTopic(KafkaStreamsApplicationUserBalance.BANK_TRANSFER_TOPIC,
                            Serdes.String().serializer(),
                            bankTransferSerde.serializer());

            final TestOutputTopic<String, BankTransfer> outputTopic = topologyTestDriver.createOutputTopic(ALERT_HUGE_AMOUNT_TOPIC,
                    Serdes.String().deserializer(),
                    bankTransferSerde.deserializer());

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

            input.pipeInput("User", firstBankTransfer);
            input.pipeInput("User", secondBankTransfer);


            assertThat(outputTopic.getQueueSize()).isEqualTo(1);
            BankTransfer expectedBankTransfer = outputTopic.readValue();
            assertThat(firstBankTransfer).isEqualTo(expectedBankTransfer);

        }
    }

    @AfterEach
    public void after() {
        MockSchemaRegistry.dropScope(SCHEMA_REGISTRY_SCOPE);
    }
}
