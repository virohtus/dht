package com.virohtus.dht.core.engine.store.network;

import com.virohtus.dht.core.DhtNodeManager;
import com.virohtus.dht.core.action.Action;
import com.virohtus.dht.core.action.TransportableAction;
import com.virohtus.dht.core.engine.action.network.*;
import com.virohtus.dht.core.engine.action.peer.PeerDisconnected;
import com.virohtus.dht.core.engine.store.Store;
import com.virohtus.dht.core.engine.store.peer.PeerStore;
import com.virohtus.dht.core.network.FingerTable;
import com.virohtus.dht.core.network.Keyspace;
import com.virohtus.dht.core.network.Network;
import com.virohtus.dht.core.network.Node;
import com.virohtus.dht.core.network.peer.Peer;
import com.virohtus.dht.core.network.peer.PeerNotFoundException;
import com.virohtus.dht.core.transport.protocol.DhtProtocol;
import com.virohtus.dht.core.util.Resolvable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class NetworkStore implements Store {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkStore.class);
    private final DhtNodeManager dhtNodeManager;
    private final PeerStore peerStore;
    private final Resolvable<Network> networkResolvable;

    public NetworkStore(DhtNodeManager dhtNodeManager, PeerStore peerStore) {
        this.dhtNodeManager = dhtNodeManager;
        this.peerStore = peerStore;
        this.networkResolvable = new Resolvable<>(DhtProtocol.NETWORK_TIMEOUT);
    }

    public void joinNetwork(SocketAddress socketAddress) throws IOException, InterruptedException, TimeoutException {
        Peer peer = peerStore.createPeer(socketAddress);

        Node node = dhtNodeManager.getNode();
        JoinNetworkResponse response;
        synchronized (node) {
            response = peer.sendRequest(new JoinNetworkRequest(node),
                    JoinNetworkResponse.class).get();
        }

        Node successorNode = response.getNode();
        FingerTable successorFingerTable = successorNode.getFingerTable();
        synchronized (node) {
            node.setKeyspace(successorFingerTable.getPredecessor().getKeyspace());
            node.getFingerTable().addSuccessor(response.getNode());

            if (successorFingerTable.hasSuccessors() &&
                    successorFingerTable.getImmediateSuccessor().getNodeIdentity().equals(node.getNodeIdentity())) {
                node.getFingerTable().setPredecessor(successorNode);
            }
        }
        LOG.info("successfully joined network");
    }

    public Network getNetwork() throws InterruptedException, TimeoutException, PeerNotFoundException, IOException {
        Node node = dhtNodeManager.getNode();
        synchronized (node) {
            GetNetwork getNetwork = new GetNetwork(node);
            if (!node.getFingerTable().hasSuccessors()) {
                return getNetwork.getNetwork();
            }
            Peer successor = peerStore.getPeer(node.getFingerTable().getImmediateSuccessor());
            successor.send(getNetwork.serialize());
        }
        return networkResolvable.get();
    }

    @Override
    public void onAction(Action action) {
        if(action instanceof TransportableAction) {
            TransportableAction transportableAction = (TransportableAction)action;
            switch (transportableAction.getType()) {
                case DhtProtocol.JOIN_NETWORK_REQUEST:
                    handleJoinNetworkRequest((JoinNetworkRequest)transportableAction);
                    break;
                case DhtProtocol.GET_NODE_IDENTITY_REQUEST:
                    handleGetNodeIdentityRequest((GetNodeIdentityRequest)transportableAction);
                    break;
                case DhtProtocol.GET_NETWORK:
                    handleGetNetwork((GetNetwork)transportableAction);
                    break;
                case DhtProtocol.GET_NODE_REQUEST:
                    handleGetNodeRequest((GetNodeRequest)transportableAction);
                    break;
            }
        }

        if(action instanceof PeerDisconnected) {
            handlePeerDisconnected((PeerDisconnected)action);
        }
    }

    private void handlePeerDisconnected(PeerDisconnected peerDisconnected) {
        Peer peer = peerDisconnected.getPeer();
        try {
            Node node = dhtNodeManager.getNode();
            synchronized (node) {
                FingerTable fingerTable = node.getFingerTable();
                fingerTable.removeSuccessor(peer.getNodeIdentity());
                if (fingerTable.getPredecessor() != null && fingerTable.getPredecessor().getNodeIdentity().equals(peer.getNodeIdentity())) {
                    Node predecessor = fingerTable.getPredecessor();
                    fingerTable.setPredecessor(null);
                    node.getKeyspace().merge(predecessor.getKeyspace());
                }
            }
        } catch (Exception e) {
            LOG.error("error during network cleanup for peer: " + peer.getId() + " " + e);
        }
    }

    private void handleJoinNetworkRequest(JoinNetworkRequest request) {
        if(!request.hasSourcePeer()) {
            LOG.error("received JoinNetworkRequest from null peer!");
            return;
        }
        Node node = dhtNodeManager.getNode();
        synchronized (node) {
            Node predecessor = request.getNode();
            FingerTable fingerTable = node.getFingerTable();
            fingerTable.setPredecessor(predecessor);
            Keyspace lowersplit = node.getKeyspace().split();
            if (predecessor.getKeyspace().isDefaultKeyspace()) {
                predecessor.setKeyspace(lowersplit);
            } else {
                predecessor.getKeyspace().merge(lowersplit);
            }

            if (!fingerTable.hasSuccessors()) {
                fingerTable.addSuccessor(predecessor);
            }
            try {
                request.getSourcePeer().send(new JoinNetworkResponse(request.getRequestId(), node).serialize());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleGetNodeIdentityRequest(GetNodeIdentityRequest request) {
        if(!request.hasSourcePeer()) {
            LOG.error("received GetNodeIdentityRequest from null peer!");
            return;
        }
        try {
            Node node = dhtNodeManager.getNode();
            synchronized (node) {
                GetNodeIdentityResponse response = new GetNodeIdentityResponse(request.getRequestId(), node.getNodeIdentity());
                request.getSourcePeer().send(response.serialize());
            }
        } catch (IOException e) {
            LOG.error("failed to send GetNodeIdentityResponse to peer: " + request.getSourcePeer().getId());
        }
    }

    private void handleGetNodeRequest(GetNodeRequest request) {
        Node node = dhtNodeManager.getNode();
        synchronized (node) {
            try {
                request.getSourcePeer().send(new GetNodeResponse(request.getRequestId(), node).serialize());
            } catch (IOException e) {
                LOG.error("failed to send GetNodeResponse!", e);
            }
        }
    }

    private void handleGetNetwork(GetNetwork getNetwork) {
        List<Node> nodes = getNetwork.getNetwork().getNodes();
        if(nodes.isEmpty()) {
            LOG.warn("received GetNetwork request, but nobody is participating!");
            return;
        }
        if(nodes.get(0).getNodeIdentity().equals(dhtNodeManager.getNode().getNodeIdentity())) {
            networkResolvable.resolve(getNetwork.getNetwork());
            return;
        }
        Node node = dhtNodeManager.getNode();
        synchronized (node) {
            if (!node.getFingerTable().hasSuccessors()) {
                LOG.warn("received GetNetwork request but we have nowhere to go!");
                return;
            }
            try {
                Peer successor = peerStore.getPeer(node.getFingerTable().getImmediateSuccessor());
                getNetwork.getNetwork().addNode(node);
                successor.send(getNetwork.serialize());
            } catch (Exception e) {
                LOG.error("could not forward GetNetwork to immediate successor!");
            }
        }
    }
}
