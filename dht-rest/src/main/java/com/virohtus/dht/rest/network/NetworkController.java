package com.virohtus.dht.rest.network;

import com.virohtus.dht.core.DhtNodeManager;
import com.virohtus.dht.core.network.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/network")
public class NetworkController {

    @Autowired private DhtNodeManager nodeManager;

    @RequestMapping(value = "", method = RequestMethod.GET, headers = {"Cache-Control=no-cache"})
    public Network getNetwork() {
        return nodeManager.getNetwork();
    }
}
