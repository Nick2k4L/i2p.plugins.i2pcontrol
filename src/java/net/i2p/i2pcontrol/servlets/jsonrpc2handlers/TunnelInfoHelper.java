package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.i2p.data.Hash;
import net.i2p.data.TunnelId;
import net.i2p.router.RouterContext;
import net.i2p.router.TunnelInfo;
import net.i2p.router.tunnel.HopConfig;
import net.i2p.router.tunnel.pool.TunnelPool;

final class TunnelInfoHelper {
    private static final String ROLE_INBOUND_GATEWAY = "inbound gateway";
    private static final String ROLE_OUTBOUND_ENDPOINT = "outbound endpoint";
    private static final String ROLE_PARTICIPANT = "participant";

    private final RouterContext _context;

    TunnelInfoHelper(RouterContext context) {
        _context = context;
    }

    int getParticipatingCount() {
        return _context.tunnelManager().getParticipatingCount();
    }

    List<Map<String, Object>> getParticipatingInfo() {
        List<Map<String, Object>> participatingTunnels = new ArrayList<>();
        int inactiveCount = 0;
        long now = _context.clock().now();

        List<HopConfig> hopConfigs = _context.tunnelDispatcher().listParticipatingTunnels();
        for (HopConfig config : hopConfigs) {
            if (config.getProcessedMessagesCount() <= 0) {
                inactiveCount++;
                continue;
            }
            participatingTunnels.add(extractParticipatingTunnel(config, now));
        }

        Map<String, Object> inactiveInfo = new HashMap<>();
        inactiveInfo.put("inactiveCount", inactiveCount);
        participatingTunnels.add(inactiveInfo);
        return participatingTunnels;
    }

    Map<String, Object> getParticipatingLifetimeBandwidth() {
        long processed = getParticipatingLifetimeProcessed();
        Map<String, Object> lifetime = new HashMap<>();
        lifetime.put("KB", processed);
        return lifetime;
    }

    int getExploratoryInboundCount() {
        return _context.tunnelManager().getFreeTunnelCount();
    }

    int getExploratoryOutboundCount() {
        return _context.tunnelManager().getOutboundTunnelCount();
    }

    List<Map<String, Object>> getExploratoryInfo() {
        TunnelPool inboundExploratory = _context.tunnelManager().getInboundExploratoryPool();
        TunnelPool outboundExploratory = _context.tunnelManager().getOutboundExploratoryPool();
        List<Map<String, Object>> exploratoryTunnels = new ArrayList<>();
        exploratoryTunnels.addAll(extractTunnelPool(inboundExploratory));
        exploratoryTunnels.addAll(extractTunnelPool(outboundExploratory));
        return exploratoryTunnels;
    }

    Map<String, Object> getExploratoryLifetimeBandwidth() {
        TunnelPool inboundExploratory = _context.tunnelManager().getInboundExploratoryPool();
        TunnelPool outboundExploratory = _context.tunnelManager().getOutboundExploratoryPool();
        return extractLifetimeBandwidth(inboundExploratory, outboundExploratory);
    }

    int getClientInboundCount() {
        return _context.tunnelManager().getInboundClientTunnelCount();
    }

    int getClientOutboundCount() {
        return _context.tunnelManager().getOutboundClientTunnelCount();
    }

    List<Map<String, Object>> getClientInfo() {
        List<Map<String, Object>> clientTunnels = extractClientTunnels(true);
        clientTunnels.addAll(extractClientTunnels(false));
        return clientTunnels;
    }

    Map<String, Object> getClientLifetimeBandwidth() {
        long inboundProcessed = 0;
        long outboundProcessed = 0;
        for (TunnelPool pool : _context.tunnelManager().getInboundClientPools().values()) {
            inboundProcessed += pool.getLifetimeProcessed();
        }
        for (TunnelPool pool : _context.tunnelManager().getOutboundClientPools().values()) {
            outboundProcessed += pool.getLifetimeProcessed();
        }
        return extractLifetimeBandwidth(inboundProcessed, outboundProcessed);
    }

    List<Map<String, Object>> getClientInboundList() {
        return extractClientTunnels(true);
    }

    List<Map<String, Object>> getClientOutboundList() {
        return extractClientTunnels(false);
    }

    private List<Map<String, Object>> extractClientTunnels(boolean inbound) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<Hash, TunnelPool> pools = inbound ? _context.tunnelManager().getInboundClientPools()
                                              : _context.tunnelManager().getOutboundClientPools();
        List<TunnelPool> sortedPools = new ArrayList<>(pools.values());
        sortedPools.sort((left, right) -> getTunnelPoolName(left).compareTo(getTunnelPoolName(right)));

        for (TunnelPool pool : sortedPools) {
            list.addAll(extractTunnelPool(pool));
        }
        return list;
    }

    private List<Map<String, Object>> extractTunnelPool(TunnelPool pool) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (pool == null) {
            return list;
        }

        List<TunnelInfo> tunnels = new ArrayList<>(pool.listTunnels());
        tunnels.sort(Comparator.comparingLong(TunnelInfo::getExpiration));

        Hash client = pool.getSettings().getDestination();
        for (TunnelInfo info : tunnels) {
            if (info.getExpiration() <= 0) {
                continue;
            }
            list.add(extractTunnelInfo(info, client));
        }
        return list;
    }

    private Map<String, Object> extractTunnelInfo(TunnelInfo info, Hash client) {
        Map<String, Object> record = new HashMap<>();
        boolean inbound = info.isInbound();
        int length = info.getLength();

        record.put("inbound", inbound);
        record.put("expiration", info.getExpiration());
        record.put("processedMessagesCount", info.getProcessedMessagesCount());

        if (client != null) {
            record.put("clientHash", client.toBase64());
        }

        if (length <= 0) {
            record.put("gateway", null);
            record.put("participants", Collections.emptyList());
            record.put("endpoint", null);
            return record;
        }

        if (length == 1) {
            Map<String, Object> localHop = extractTunnelHop(info, 0);
            if (inbound) {
                record.put("gateway", null);
                record.put("participants", Collections.emptyList());
                record.put("endpoint", localHop);
            } else {
                record.put("gateway", localHop);
                record.put("participants", Collections.emptyList());
                record.put("endpoint", null);
            }
            return record;
        }

        record.put("gateway", extractTunnelHop(info, 0));
        List<Map<String, Object>> participants = new ArrayList<>();
        for (int i = 1; i < length - 1; i++) {
            participants.add(extractTunnelHop(info, i));
        }
        record.put("participants", participants);
        record.put("endpoint", extractTunnelHop(info, length - 1));
        return record;
    }

    private Map<String, Object> extractTunnelHop(TunnelInfo info, int hop) {
        Map<String, Object> hopInfo = new HashMap<>();
        Hash peer = info.getPeer(hop);
        TunnelId id = info.isInbound() ? info.getReceiveTunnelId(hop) : info.getSendTunnelId(hop);
        boolean local = peer != null && peer.equals(_context.routerHash());

        hopInfo.put("peerHash", peer != null ? peer.toBase64() : null);
        hopInfo.put("local", local);
        hopInfo.put("tunnelId", id != null ? id.getTunnelId() : null);
        return hopInfo;
    }

    private Map<String, Object> extractParticipatingTunnel(HopConfig config, long now) {
        Map<String, Object> tunnelInfo = new HashMap<>();
        int processedMessagesCount = config.getProcessedMessagesCount();
        long expiresInMs = config.getExpiration() - now;
        int lifetime = (int) ((now - config.getCreation()) / 1000);
        if (lifetime <= 0) {
            lifetime = 1;
        } else if (lifetime > 10 * 60) {
            lifetime = 10 * 60;
        }
        long rateBps = (1024L * processedMessagesCount) / lifetime;

        Hash receiveFrom = config.getReceiveFrom();
        Hash sendTo = config.getSendTo();

        tunnelInfo.put("peerHashFrom", receiveFrom != null ? receiveFrom.toBase64() : null);
        tunnelInfo.put("peerHashTo", sendTo != null ? sendTo.toBase64() : null);
        tunnelInfo.put("receiveTunnelId", config.getReceiveTunnelId());
        tunnelInfo.put("sendTunnelId", config.getSendTunnelId());
        tunnelInfo.put("tunnelExpiration", config.getExpiration());
        tunnelInfo.put("expiresInMs", Math.max(0L, expiresInMs));
        tunnelInfo.put("processedMessagesCount", processedMessagesCount);
        tunnelInfo.put("rateBps", rateBps);
        tunnelInfo.put("role", getParticipationRole(receiveFrom, sendTo));
        return tunnelInfo;
    }

    private Map<String, Object> extractLifetimeBandwidth(TunnelPool inboundPool, TunnelPool outboundPool) {
        long inboundProcessed = inboundPool != null ? inboundPool.getLifetimeProcessed() : 0;
        long outboundProcessed = outboundPool != null ? outboundPool.getLifetimeProcessed() : 0;
        return extractLifetimeBandwidth(inboundProcessed, outboundProcessed);
    }

    private Map<String, Object> extractLifetimeBandwidth(long inboundProcessed, long outboundProcessed) {
        Map<String, Object> lifetime = new HashMap<>();
        lifetime.put("inboundKB", inboundProcessed);
        lifetime.put("outboundKB", outboundProcessed);
        return lifetime;
    }

    private long getParticipatingLifetimeProcessed() {
        long processed = 0;
        net.i2p.stat.RateStat rate = _context.statManager().getRate("tunnel.participatingMessageCount");
        if (rate != null) {
            net.i2p.stat.Rate tenMinuteRate = rate.getRate(10 * 60 * 1000);
            if (tenMinuteRate != null) {
                processed = (long) tenMinuteRate.getLifetimeTotalValue();
            }
        }

        for (HopConfig config : _context.tunnelDispatcher().listParticipatingTunnels()) {
            if (config.getProcessedMessagesCount() > 0) {
                processed += config.getRecentMessagesCount();
            }
        }
        return processed;
    }

    private String getTunnelPoolName(TunnelPool pool) {
        String nickname = pool.getSettings().getDestinationNickname();
        if (nickname != null && !nickname.isEmpty()) {
            return nickname;
        }
        Hash client = pool.getSettings().getDestination();
        return client != null ? client.toBase32() : "";
    }

    private String getParticipationRole(Hash receiveFrom, Hash sendTo) {
        if (sendTo == null) {
            return ROLE_OUTBOUND_ENDPOINT;
        }
        if (receiveFrom == null) {
            return ROLE_INBOUND_GATEWAY;
        }
        return ROLE_PARTICIPANT;
    }
}
