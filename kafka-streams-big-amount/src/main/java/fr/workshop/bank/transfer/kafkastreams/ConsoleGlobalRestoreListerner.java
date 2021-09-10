package fr.workshop.bank.transfer.kafkastreams;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.processor.StateRestoreListener;

public class ConsoleGlobalRestoreListerner implements StateRestoreListener {

    @Override
    public void onRestoreStart(final TopicPartition topicPartition,
                               final String storeName,
                               final long startingOffset,
                               final long endingOffset) {

        System.out.print("Started restoration of " + storeName + " partition " + topicPartition.partition());
        System.out.println(" total records to be restored " + (endingOffset - startingOffset));
    }

    @Override
    public void onBatchRestored(final TopicPartition topicPartition,
                                final String storeName,
                                final long batchEndOffset,
                                final long numRestored) {

        System.out.println("Restored batch " + numRestored + " for " + storeName + " partition " + topicPartition.partition());

    }

    @Override
    public void onRestoreEnd(final TopicPartition topicPartition,
                             final String storeName,
                             final long totalRestored) {

        System.out.println("Restoration complete for " + storeName + " partition " + topicPartition.partition());
    }
}