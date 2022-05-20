# Workshop KafkaStreams

You work for a financial institution and you're going to use [Kafka Streams](https://kafka.apache.org/documentation/streams/) to work with financial operations.

## Start the Apache Kafka Cluster

The docker-compose file contains an Apache Kafka cluster, a Schema Registry component and a console AKHQ.

Run the command

```
docker-compose up -d
```

Once all containers are up, go to the AKHQ console: [http://localhost:8000](http://localhost:8000/ui).

Go to the topics view, you should see a few empty topics.

```
bank-transfer
user
user-balance
...
```

## The setup

### User

All financial operations are done between two users. Users are simply represented with a name and a location.
You can take a look at the file [users.csv](java-producer/src/main/resources/users.csv).

```
Kylian;Metz
Rayan;Brest
Noémie;Lille
...
```

A user is represented with an Apache Avro schema: [user.avsc](kafka-streams-avro-schema/src/main/avro/user.avsc).

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

A bank transfer is an operation when a user sent an amount of money to another user.
This operation takes place in a location at a date.

A bank transfer is represented with Apache Avro schema: [banktransfer.avsc](kafka-streams-avro-schema/src/main/avro/banktransfer.avsc).

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

All Avro schemas are located in the dependency ``. Run this command to install on your local repository this dependency:

```
mvn clean install
```

### Produces the data

Go to the `java-producer` directory and run the command

```
mvn clean compile exec:java
```

It runs the class `BankTransferProducer` and populated the topics `user`, `bank-transfer`. Take at look at those topics in [AKHQ](http://localhost:8000/ui/docker-kafka-server/topic).
You should see exactly 100 entries in the `user` topic and some transfer operations in the `bank-transfer` topic.


## Kafka Streams Application

Go to `kafka-streams-big-amount` project, open the class `KafkaStreamsApplicationAlertBigAmount`.

Take a look at the method `createTopology`. This method creates the topology of the stream application.
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

This operation **is mainly for debugging/testing purposes, you should not use it in production**.

Go to the `TODO 01` and complete it. Once done, run the application:

```
mvn clean compile exec:java
```

### TODO 02 - Alert on big amount

The business team wants you to alert the users a bank transfert is done with an amount higher than 15000€.

In the `kafka-streams-big-amount` project, go to the `TODO 02` and apply the `filter` operation in order to retain only the operations with an amount higher than 15000€.
Then, push the result to the topic `alert-huge-amount`.

Once done, go to AKHQ and inspect the content of the topic `alert-huge-amount`. You should only see bank transfert with an amount higher than 15000.

### TODO 03 - Unit Testing

It can be a pain in the neck to set up a working environment to test a topology. 
Using Docker and particularly TestContainers the work can be reduced, however you can unit test your topology using the library `kafka-streams-test-utils`.

In the `kafka-streams-big-amount` project, go to the test class `KafkaStreamsApplicationAlertBigAmountTest`, using the method `createInputTopic` on `topologyTestDriver` create an instance of `TestInputTopic` to produce the two instances of bankTransfer `firstBankTransfer` and `secondBankTransfer`.
Then, using the method `createOutputTopic` create an instance of `TestOutputTopic` to assert that the records produced are correctly filtered.

Run `mvn test` to ensure the test is passing.

### TODO 04 - Count the number of operations per user

If a user is doing a lot of operations in a short amount of time, it can be a potential fraud or a bot for example. The business team wants you to count how many operations a user is doing in a time window of 3 seconds.

In the project `kafka-streams-number-operations`, go to the `KafkaStreamsApplicationNumberOperations` class. 
Using the `groupByKey` operation and the `windowedBy` operation, compute if a user is doing at least 2 operations in a time window of 3 seconds, push the result to the topic `alert-too-many-operations`.

### TODO 05 - Alert on different location

As you can see a bank transfer is done with a location and a user has also a specified location.
The business team wants to alert the user when a bank transfer is done in a different location.

In the `kafka-streams-different-location` project, go to the class `KafkaStreamsApplicationDifferentLocation`.
Create a `Ktable` from the `user` topic and join it with the `bankTransferKStream` KStream using the `bankTransferWithUserJoiner` joiner.
You will be able to compare the two locations. If the two locations are different, publish the event to the topic `alert-different-location`.

### TODO 06 - Compute user balance

One important feature is to compute the balance for each user. The business team wants to know the exact balance for each user in real time.

In the `kafka-streams-user-balance` project, go to the class `KafkaStreamsApplicationUserBalance`.
Using the operator `flatMap`, split each `bankTransfert` into two `UserOperation`. One for the debtor and one for the credit.
Next, `groupBy` those operations and `aggregate` them to compute each user balance. Once done, store it into a `Materialized` view using the object `balanceStore`.
Finally, push the user balance into the `user-balance` topic.

### TODO 07 - Exactly once

It's really important to update the balance of our users debtor and credit in the same transaction in order to avoid problems.

To do that, simply update the settings of the application to use the semantics `exactly-once`.

### TODO 08 - Interactive queries

Go to the class `UserBalanceServer` and complete the method `userBalance` to query the local store `balanceStore` and return the balance of the user.

Then you can run a curl command to test it. For example:

```
curl http://localhost:9090/state/balance/Hugo
```
