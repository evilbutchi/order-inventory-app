package edu.cit.berou.channel;

import edu.cit.berou.InstanceIdentity;
import org.springframework.stereotype.Component;

@Component
class ChannelAdminImpl implements ChannelAdmin {

    private final InstanceIdentity instanceIdentity;
    private final ChannelEventRepository eventRepository;
    private final ChannelOrderRepository channelOrderRepository;

    ChannelAdminImpl(InstanceIdentity instanceIdentity, ChannelEventRepository eventRepository,
                     ChannelOrderRepository channelOrderRepository) {
        this.instanceIdentity = instanceIdentity;
        this.eventRepository = eventRepository;
        this.channelOrderRepository = channelOrderRepository;
    }

    @Override
    public ChannelSnapshot status() {
        var all = channelOrderRepository.findAll();
        long accepted = all.stream().filter(o -> ChannelOrder.ACCEPTED.equals(o.getStatus())).count();
        long rejected = all.stream().filter(o -> ChannelOrder.REJECTED.equals(o.getStatus())).count();
        long backordered = all.stream().filter(o -> ChannelOrder.BACKORDERED.equals(o.getStatus())).count();
        long cancelled = all.stream().filter(o -> ChannelOrder.CANCELLED.equals(o.getStatus())).count();
        return new ChannelSnapshot(instanceIdentity.instanceId(), instanceIdentity.startedAt(),
                instanceIdentity.uptimeSeconds(), eventRepository.maxSeq(), accepted, rejected, backordered, cancelled);
    }
}
