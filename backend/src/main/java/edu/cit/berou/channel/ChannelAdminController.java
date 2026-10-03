package edu.cit.berou.channel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
class ChannelAdminController {

    private final ChannelAdmin channelAdmin;

    ChannelAdminController(ChannelAdmin channelAdmin) {
        this.channelAdmin = channelAdmin;
    }

    @GetMapping("/api/channel/status")
    public ChannelSnapshot status() {
        return channelAdmin.status();
    }
}
