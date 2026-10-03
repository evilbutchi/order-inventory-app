package edu.cit.berou.channel;


final class ChannelInternalEvents {

    private ChannelInternalEvents() {
    }

    record DecisionReady(Long channelOrderId, String decision, String reason) {
    }

    record CancellationHandled(Long channelOrderId) {
    }

    record ResolutionReady(Long channelOrderId, String status) {
    }
}
