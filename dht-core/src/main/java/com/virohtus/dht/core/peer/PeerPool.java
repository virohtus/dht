package com.virohtus.dht.core.peer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PeerPool {

    private final Map<String, Peer> pool = new HashMap<>();

    public Peer getPeer(String peerId) throws PeerNotFoundException {
        synchronized (pool) {
            if (pool.containsKey(peerId)) {
                return pool.get(peerId);
            }
            throw new PeerNotFoundException(peerId);
        }
    }

    public void addPeer(Peer peer) {
        synchronized (pool) {
            pool.put(peer.getPeerId(), peer);
        }
    }

    public Peer removePeer(String peerId) throws PeerNotFoundException {
        synchronized (pool) {
            if(pool.containsKey(peerId)) {
                return pool.remove(peerId);
            }
            throw new PeerNotFoundException(peerId);
        }
    }

    public Set<Peer> listPeers() {
        synchronized (pool) {
            return new HashSet<>(pool.values());
        }
    }
}
