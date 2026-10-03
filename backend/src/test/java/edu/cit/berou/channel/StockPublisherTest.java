package edu.cit.berou.channel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class StockPublisherTest {

    @Test
    void publishesEveryQueuedValueInOrder() {
        TiangeClient tiangeClient = mock(TiangeClient.class);
        StockUpdateRepository updates = mock(StockUpdateRepository.class);
        ChannelOrderRepository orders = mock(ChannelOrderRepository.class);
        StockUpdate first = new StockUpdate("P100", 4, Instant.now());
        StockUpdate second = new StockUpdate("P100", 3, Instant.now());
        when(orders.findPendingNotifications()).thenReturn(List.of());
        Iterator<Optional<StockUpdate>> queued = List.of(
                Optional.of(first), Optional.of(second), Optional.<StockUpdate>empty()).iterator();
        when(updates.findFirstByOrderByIdAsc()).thenAnswer(invocation -> queued.next());

        StockPublisher publisher = new StockPublisher(
                tiangeClient, updates, orders, new NotificationGuard(), transactionManager());

        assertTrue(publisher.flushDirty());

        InOrder order = inOrder(tiangeClient, updates);
        order.verify(tiangeClient).putStock(List.of(new TiangeMessages.StockEntry("P100", 4)));
        order.verify(updates).delete(first);
        order.verify(tiangeClient).putStock(List.of(new TiangeMessages.StockEntry("P100", 3)));
        order.verify(updates).delete(second);
    }

    @Test
    void leavesQueuedStockUntouchedWhileAnOrderNotificationIsPending() {
        TiangeClient tiangeClient = mock(TiangeClient.class);
        StockUpdateRepository updates = mock(StockUpdateRepository.class);
        ChannelOrderRepository orders = mock(ChannelOrderRepository.class);
        when(orders.findPendingNotifications()).thenReturn(List.of(mock(ChannelOrder.class)));

        StockPublisher publisher = new StockPublisher(
                tiangeClient, updates, orders, new NotificationGuard(), transactionManager());

        assertFalse(publisher.flushDirty());
        verify(updates, never()).findFirstByOrderByIdAsc();
        verify(tiangeClient, never()).putStock(org.mockito.ArgumentMatchers.anyList());
    }

    private static PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
