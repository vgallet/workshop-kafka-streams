package fr.vga.workshop.bank.transfer;

import bank.transfer.avro.BankTransfer;
import bank.transfer.avro.User;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BankTransferProducer {

    public static final String USERS_TOPIC = "user";
    private static final String BANK_TRANSFER_TOPIC = "bank-transfer";

    public static void main(String[] args) throws IOException, URISyntaxException {
        final Properties settings = new Properties();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        settings.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://schema-registry:8081");

        final KafkaProducer<String, User> userProducer = new KafkaProducer<>(settings);
        final KafkaProducer<String, BankTransfer> bankTransferProducer = new KafkaProducer<>(settings);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Closing producers.");
            userProducer.close();
            bankTransferProducer.close();
        }));


        Path path = Paths.get(BankTransferProducer.class.getClassLoader().getResource("users.csv").toURI());
        List<String> rows = Files.readAllLines(path);

        List<User> users = rows.stream()
                .map(row -> {
                    String[] split = row.split(";");
                    return new User(split[0], split[1]);
                })
                .collect(Collectors.toList());

        users.stream()
            .map(user -> new ProducerRecord<>(USERS_TOPIC, user.getName().toString(), user))
            .forEach(record -> userProducer.send(record, (md, e) -> {
                if (e != null) {
                    System.out.println(e);
                } else {
                    System.out.println("User " + record.value());
                }
            }));

        Path bankTransferPath = Paths.get(BankTransferProducer.class.getClassLoader().getResource("banktransfer.csv").toURI());
        List<String> bankTransferRows = Files.readAllLines(bankTransferPath);

        bankTransferRows.stream()
                .map(row -> {
                    String[] split = row.split(";");
                    return BankTransfer.newBuilder()
                            .setAmount(Double.valueOf(split[2]))
                            .setCredit(split[0])
                            .setDebtor(split[1])
                            .setDate(new Date().toString())
                            .setLocation(split[3])
                            .build();
                }).forEach(bankTransfer -> {
            final ProducerRecord<String, BankTransfer> record = new ProducerRecord(BANK_TRANSFER_TOPIC, bankTransfer.getCredit(), bankTransfer);
            bankTransferProducer.send(record, (md, e) -> {
                if (e != null) {
                    System.out.println(e);
                } else {
                    System.out.println("Envoi " + bankTransfer.toString());
                }
            });
            try {
                Thread.sleep((long) Math.floor(Math.random()*(5000-500+1)+500));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}
