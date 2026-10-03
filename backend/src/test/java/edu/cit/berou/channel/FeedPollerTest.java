package edu.cit.berou.channel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class FeedPollerTest {

    @Test
    void resumesPollingWhenMarketplaceFeedCursorMovesBackwards() {
        TiangeClient tiangeClient = mock(TiangeClient.class);
        FeedEventProcessor processor = mock(FeedEventProcessor.class);
        ChannelEventRepository eventRepository = mock(ChannelEventRepository.class);
        ChannelProperties properties = mock(ChannelProperties.class);
        when(eventRepository.maxSeq()).thenReturn(466L);
        when(properties.feedPageSize()).thenReturn(50);
        when(tiangeClient.getFeed(466L, 50)).thenReturn(new TiangeMessages.FeedResponse(List.of(), 0));
        TiangeMessages.FeedEvent event = new TiangeMessages.FeedEvent(
                1L, "event-1", "ORDER_PLACED", "order-1", null, null, List.of(), null, null, null);
        when(tiangeClient.getFeed(0L, 50)).thenReturn(new TiangeMessages.FeedResponse(List.of(event), 1));
        when(processor.process(event)).thenReturn(true);

        FeedPoller poller = new FeedPoller(tiangeClient, processor, eventRepository, properties);
        poller.poll();
        poller.poll();

        verify(tiangeClient).getFeed(466L, 50);
        verify(tiangeClient).getFeed(0L, 50);
        verify(processor).process(event);
    }

    @Test
    void doesNotAdvanceCursorPastAnEventThatFailedToProcess() {
        TiangeClient tiangeClient = mock(TiangeClient.class);
        FeedEventProcessor processor = mock(FeedEventProcessor.class);
        ChannelEventRepository eventRepository = mock(ChannelEventRepository.class);
        ChannelProperties properties = mock(ChannelProperties.class);
        when(eventRepository.maxSeq()).thenReturn(0L);
        when(properties.feedPageSize()).thenReturn(50);
        TiangeMessages.FeedEvent event = new TiangeMessages.FeedEvent(
                1L, "event-1", "ORDER_PLACED", "order-1", null, null, List.of(), null, null, null);
        when(tiangeClient.getFeed(0L, 50)).thenReturn(new TiangeMessages.FeedResponse(List.of(event), 1));
        when(processor.process(event)).thenReturn(false);

        FeedPoller poller = new FeedPoller(tiangeClient, processor, eventRepository, properties);
        poller.poll();
        poller.poll();

        verify(tiangeClient, org.mockito.Mockito.times(2)).getFeed(0L, 50);
    }
}
