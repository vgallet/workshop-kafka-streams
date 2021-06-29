# Workshop KafkaStreams

You work for a financial institution and we're going to use [Kafka Streams](https://kafka.apache.org/documentation/streams/) to work with financial operations.

## Start the Apache Kafka Cluster

The docker-compose file contains an Apache Kafka cluster, a SchemaRegistry and a conole AKHQ.

Run the command

```
docker-compose up -d
```

Once all containers are up, go to the AKHQ console: [http://localhost:8080](http://localhost:8080/ui).

Go to the topics view, you should see three empty topics `user`, `bank-transfer` and `user-balance`.

## The setup

### User

All financial operations are done by between users. Users are simply represented with a name and a location.
You can take a look at the file [users.csv](java-producer/src/main/resources/users.csv).

```
Kylian;Metz
Rayan;Brest
Noémie;Lille
...
```

All the users are contained is the topic `user` and send using an [Apache Avro](https://avro.apache.org/) producer. 
An user is represented with Apache Avro schema: [user.avsc](java-producer/src/main/avro/user.avsc).

```
{"namespace": "bank.transfer.avro",
    "type": "record",
    "name": "User",
    "fields": [
        {"name": "name", "type": "string"},
        {"name": "city", "type": "string"}
    ]
}
```

### Bank Transfer

A bank transfer is an operation when an user, a credit send an amount of money to another user, a debtor.
This operation take place in a location at a date.

A bank transfer is represented with Apache Avro schema: [banktransfer.avsc](java-producer/src/main/avro/banktransfer.avsc).

```
{"namespace": "bank.transfer.avro",
    "type": "record",
    "name": "BankTransfer",
    "fields": [
        {"name": "amount", "type": "double"},
        {"name": "debtor", "type": "string"},
        {"name": "credit", "type": "string"},
        {"name": "date", "type": "string"},
        {"name": "location", "type": "string"}
    ]
}
```

### Produces the data

Go to the `java-producer` directory and run the command

```
mvn clean compile exec:java
```

It runs the class `BankTransferProducer` and populated the topics `user`, `bank-transfer`. Take at look at those topics in [AKHQ](http://localhost:8080/ui/docker-kafka-server/topic).

## Kafka Streams Application

Go to `kafka-streams` project, open the class `KafkaStreamsApplication`.

Take a look at the method `createTopolgoy`. This method creates the topology of the stream application.
Currently, it only contains a stream of the `banktransfer` topic.

```java
    KStream<String, BankTransfer> bankTransferKStream = builder.stream(
            BANK_TRANSFER_TOPIC,
            Consumed.with(Serdes.String(), bankTransferSerde)
    );
```

### TODO 01 - Debug and test

In some cases, you may need to see what's in your stream. You can use the `print` operation to do it.

> Calling print() is the same as calling foreach((key, value) -> System.out.println(key + ", " + value))

This operation is mainly for debugging/testing purposes, you should not use it in production.

Go to the `TODO 01` and complete it. Once done, run the application:

```
mvn clean compile exec:java
```

### TODO 02 - Alert on big amount

The business team wants you to alert the users a bank transfert is done with an amount higher than 15000€.

Go to the `TODO 02` and apply the filter operation in order to retain only the operations with an amount higher than 15000€.
Then, push the result to the topic `alert-huge-amount`.

### TODO 03 - Alert on too much operations

If a user is doing a lot of operations in a short amount of time, it can be a potential fraud.

We have to count how many operations a user is doing in a time window of 3 seconds.

Using the `groupByKey` operation and the `windowedBy` operation, compute if an user is doing more than 2 operations in a time window of 3 seconds, push the result to the topic `alert-too-much-operations`.

### TODO 04 - Alert on different location

As you can see a bank transfer is done in a location and an user has also a specified location.
The business team wants to alert the user is a bank transfer is done in a different location.

Create a `Ktable` from the `user` topic and join it with the `bankTransferKStream` Kstream using the `bankTransferWithUserJoiner` joiner.
You will be able to compare the two locations. If the two locations are different, publish the event to the topic `alert-different-location`.


### TODO 05 - Compute user balance

### TODO 06 - Interactive queries

### TODO 07 - Unit Testing