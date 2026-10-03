package edu.cit.berou.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
class FeedPoller {

    private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);

    private final TiangeClient tiangeClient;
    private final FeedEventProcessor processor;
    private final ChannelEventRepository eventRepository;
    private final int pageSize;
    private Long cursor;

    FeedPoller(TiangeClient tiangeClient, FeedEventProcessor processor,
              ChannelEventRepository eventRepository, ChannelProperties props) {
        this.tiangeClient = tiangeClient;
        this.processor = processor;
        this.eventRepository = eventRepository;
        this.pageSize = props.feedPageSize();
    }

    @Scheduled(fixedDelayString = "${channel.feed-poll-ms:500}", initialDelayString = "${channel.feed-poll-ms:500}")
    public void poll() {
        try {
            if (cursor == null) {
                cursor = eventRepository.maxSeq();
            }

            for (int guard = 0; guard < 20; guard++) {
                TiangeMessages.FeedResponse page = tiangeClient.getFeed(cursor, pageSize);

                if (page.nextCursor() < cursor) {
                    log.warn("Tiangge feed cursor moved backwards from {} to {}; resuming from the marketplace cursor",
                            cursor, page.nextCursor());
                    cursor = page.nextCursor();
                }

                if (page.events() == null || page.events().isEmpty()) {
                    return;
                }
                for (TiangeMessages.FeedEvent event : page.events()) {
                    if (!processor.process(event)) {
                        return;
                    }
                    cursor = Math.max(cursor, event.seq());
                }
                if (page.events().size() < pageSize) {
                    return;
                }
            }
        } catch (ChannelException e) {
            log.warn("Feed poll failed: {}", e.getMessage());
        }
    }
}
