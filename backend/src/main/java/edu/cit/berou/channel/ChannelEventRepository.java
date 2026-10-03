package edu.cit.berou.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ChannelEventRepository extends JpaRepository<ChannelEvent, String> {

    @Query("select coalesce(max(e.seq), 0) from ChannelEvent e")
    long maxSeq();
}
