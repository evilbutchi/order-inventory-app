package edu.cit.berou.channel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ChannelOrderRepository extends JpaRepository<ChannelOrder, Long> {

    Optional<ChannelOrder> findByTiangeOrderId(String tiangeOrderId);

    
    @Query("select distinct co from ChannelOrder co join co.items i "
            + "where co.status = 'BACKORDERED' and co.pendingResolution is null "
            + "and i.productId = :productId")
    List<ChannelOrder> findBackorderedFor(String productId);

    
    @Query("select co from ChannelOrder co where "
            + "(co.status in ('ACCEPTED', 'REJECTED', 'BACKORDERED') and co.decisionSentAt is null) or "
            + "(co.status = 'CANCELLED' and co.cancellationConfirmedAt is null) or "
            + "(co.pendingResolution is not null and co.resolvedAt is null)")
    List<ChannelOrder> findPendingNotifications();
}