package com.virohtus.dht.core.engine.action.network;

import com.virohtus.dht.core.action.ResponseAction;
import com.virohtus.dht.core.network.Node;
import com.virohtus.dht.core.transport.io.DhtInputStream;
import com.virohtus.dht.core.transport.io.DhtOutputStream;
import com.virohtus.dht.core.transport.protocol.DhtEvent;
import com.virohtus.dht.core.transport.protocol.DhtProtocol;

import java.io.IOException;

public class JoinNetworkResponse extends ResponseAction {

    private Node node;

    public JoinNetworkResponse(String requestId, Node node) {
        super(requestId);
        this.node = node;
    }

    public JoinNetworkResponse(DhtEvent dhtEvent) throws IOException {
        super(dhtEvent);
    }

    public Node getNode() {
        return node;
    }

    @Override
    public int getType() {
        return DhtProtocol.JOIN_NETWORK_RESPONSE;
    }

    @Override
    public void toWire(DhtOutputStream outputStream) throws IOException {
        super.toWire(outputStream);
        node.toWire(outputStream);
    }

    @Override
    public void fromWire(DhtInputStream inputStream) throws IOException {
        super.fromWire(inputStream);
        node = new Node(inputStream);
    }
}
