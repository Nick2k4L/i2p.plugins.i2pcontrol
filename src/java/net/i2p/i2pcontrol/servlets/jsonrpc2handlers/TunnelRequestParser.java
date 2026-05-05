package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;
import net.i2p.crypto.SigType;
import net.i2p.i2ptunnel.I2PTunnelHTTPClientBase;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import java.util.Map;

public class TunnelRequestParser {
    private static final int ENCRYPT_LEASE_SET_DISABLE = 0;
    private static final int ENCRYPT_LEASE_SET_AES = 1;
    private static final int ENCRYPT_LEASE_SET_BLINDED = 2;
    private static final int ENCRYPT_LEASE_SET_BLINDED_LOOKUP = 3;
    private static final int ENCRYPT_LEASE_SET_PSK = 4;
    private static final int ENCRYPT_LEASE_SET_PSK_LOOKUP = 5;
    private static final int ENCRYPT_LEASE_SET_PSK_PER_USER = 6;
    private static final int ENCRYPT_LEASE_SET_PSK_LOOKUP_PER_USER = 7;
    private static final int ENCRYPT_LEASE_SET_DH_PER_USER = 8;
    private static final int ENCRYPT_LEASE_SET_DH_LOOKUP_PER_USER = 9;

    private final TunnelControllerGroup _group;

    public TunnelRequestParser(TunnelControllerGroup group) {
        _group = group;
    }

    public String getType(Map<String, Object> inParams) {
        String type = (String) inParams.get("Type");
        if (type == null || type.trim().isEmpty())
            throw new IllegalArgumentException("Type is required");
        return type.trim();
    }

    public String getName(Map<String, Object> inParams, boolean edit) {
        String name = (String) inParams.get("Name");
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Name is required");
        if (!edit) {
            if (findTunnelControllerByName(name.trim()) != null)
                throw new IllegalArgumentException("tunnel " + name.trim() + " already exists");
        }
        return name;
    }

    public String getNewName(Map<String, Object> inParams) {
       String newName = (String) inParams.get("NewName");
        if (findTunnelControllerByName(newName.trim()) != null)
            throw new IllegalArgumentException("tunnel " + newName.trim() + " already exists");
        return newName;
    }

    public int getPort(Map<String, Object> inParams) {
        Object portObj = inParams.get("Port");
        if (portObj == null)
            throw new IllegalArgumentException("Port is required");
        int port = ((Number) portObj).intValue();
        if (port <= 0 || port > 65535)
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        return port;
    }

    public boolean getShared(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("Shared"));
    }

    public boolean getStartOnLoad(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("StartOnLoad"));
    }

    public String getDescription(Map<String, Object> inParams) {
        return (String) inParams.get("Description");
    }

    public String getReachableBy(Map<String, Object> inParams) {
        return (String) inParams.get("ReachableBy");
    }

    public String getProfile(Map<String, Object> inParams) {
        return (String) inParams.get("Profile");
    }

    public boolean getConnectDelay(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("ConnectDelay"));
    }

    public String getSigType(Map<String, Object> inParams) {
        return (String) inParams.get("SigType");
    }

    public String getEncType(Map<String, Object> inParams) {
        String encType = (String) inParams.get("EncType");
        return encType != null ? encType : (String) inParams.get("Encrypted");
    }

    public String getCustomOptions(Map<String, Object> inParams) {
        return (String) inParams.get("CustomOptions");
    }

    public String getTargetDestination(Map<String, Object> inParams) {
        String destination = (String) inParams.get("TargetDestination");
        return destination != null ? destination : (String) inParams.get("Destination");
    }

    public String getTargetHost(Map<String, Object> inParams) {
        String targetHost = (String) inParams.get("TargetHost");
        return targetHost != null ? targetHost : (String) inParams.get("Host");
    }

    public Integer getTargetPort(Map<String, Object> inParams) {
        Object targetPortObj = inParams.get("TargetPort");
        return targetPortObj != null ? ((Number) targetPortObj).intValue() : null;
    }

    public String getWebsiteHostname(Map<String, Object> inParams) {
        String websiteHostname = (String) inParams.get("WebsiteHostname");
        return websiteHostname != null ? websiteHostname : (String) inParams.get("SpoofedHost");
    }

    public String getSSLProxies(Map<String, Object> inParams) {
        return (String) inParams.get("SSLProxies");
    }

    public String getJumpList(Map<String, Object> inParams) {
        return (String) inParams.get("JumpList");
    }

    public Integer getTunnelLength(Map<String, Object> inParams) {
        Object tunnelLengthObj = inParams.get("TunnelLength");
        return tunnelLengthObj != null ? ((Number) tunnelLengthObj).intValue() : null;
    }

    public Integer getTunnelVariance(Map<String, Object> inParams) {
        Object tunnelVarianceObj = inParams.get("TunnelVariance");
        return tunnelVarianceObj != null ? ((Number) tunnelVarianceObj).intValue() : null;
    }

    public Integer getTunnelQuantity(Map<String, Object> inParams) {
        Object tunnelQuantityObj = inParams.get("TunnelQuantity");
        return tunnelQuantityObj != null ? ((Number) tunnelQuantityObj).intValue() : null;
    }

    public Integer getBackupQuantity(Map<String, Object> inParams) {
        Object backupQuantityObj = inParams.get("TunnelBackupQuantity");
        return backupQuantityObj != null ? ((Number) backupQuantityObj).intValue() : null;
    }

    public boolean getDelayOpen(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("DelayOpen"));
    }

    public boolean getReduce(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("Reduce"));
    }

    public boolean getClose(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("Close"));
    }

    public boolean getUseSSL(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("UseSSL"));
    }

    public boolean getUseOutproxyPlugin(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("UseOutproxyPlugin"));
    }

    public boolean getProxyAuth(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("ProxyAuth"));
    }

    public boolean getOutproxyAuth(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("OutproxyAuth"));
    }

    public boolean getDCC(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("DCC")) || Boolean.TRUE.equals(inParams.get("EnableDCC"));
    }

    public boolean getAllowUserAgent(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowUserAgent"));
    }

    public boolean getAllowReferer(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowReferer"));
    }

    public boolean getAllowAccept(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowAccept"));
    }

    public boolean getAllowInternalSSL(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("AllowInternalSSL"));
    }

    public Integer getNewDest(Map<String, Object> inParams) {
        Object newDestObj = inParams.get("NewDest");
        return newDestObj != null ? ((Number) newDestObj).intValue() : null;
    }

    public boolean getAllowNewDestOnResume(Map<String, Object> inParams) {
        Integer newDest = getNewDest(inParams);
        return newDest != null && newDest == 1;
    }

    public boolean getPersistentClientKey(Map<String, Object> inParams) {
        Integer newDest = getNewDest(inParams);
        return (inParams.containsKey("PersistentClientKey") &&
                Boolean.TRUE.equals(inParams.get("PersistentClientKey"))) ||
               (newDest != null && newDest == 2);
    }

    public boolean getPersistentClientKey(Map<String, Object> inParams, String type) {
        if (!supportsPersistentClientKey(type))
            return false;
        return getPersistentClientKey(inParams);
    }

    public int getNewDestMode(Map<String, Object> inParams, String type) {
        if (getPersistentClientKey(inParams, type))
            return 2;
        if (!supportsPersistentClientKey(type))
            return 0;
        if (getAllowNewDestOnResume(inParams))
            return 1;
        return 0;
    }

    public String getProxyList(Map<String, Object> inParams) {
        return (String) inParams.get("ProxyList");
    }

    public String getOutproxyType(Map<String, Object> inParams) {
        return (String) inParams.get("OutproxyType");
    }

    public String getProxyUsername(Map<String, Object> inParams) {
        return (String) inParams.get("ProxyUsername");
    }

    public String getProxyPassword(Map<String, Object> inParams) {
        String proxyPassword = (String) inParams.get("ProxyPassword");
        return proxyPassword != null ? proxyPassword : (String) inParams.get("nofilter_proxyPassword");
    }

    public String getOutproxyUsername(Map<String, Object> inParams) {
        return (String) inParams.get("OutproxyUsername");
    }

    public String getOutproxyPassword(Map<String, Object> inParams) {
        String outproxyPassword = (String) inParams.get("OutproxyPassword");
        return outproxyPassword != null ? outproxyPassword : (String) inParams.get("nofilter_outproxyPassword");
    }

    public Integer getReduceCount(Map<String, Object> inParams) {
        Object reduceCountObj = inParams.get("ReduceCount");
        return reduceCountObj != null ? ((Number) reduceCountObj).intValue() : null;
    }

    public Integer getReduceTime(Map<String, Object> inParams) {
        Object reduceTimeObj = inParams.get("ReduceTime");
        return reduceTimeObj != null ? ((Number) reduceTimeObj).intValue() : null;
    }

    public Integer getCloseTime(Map<String, Object> inParams) {
        Object closeTimeObj = inParams.get("CloseTime");
        return closeTimeObj != null ? ((Number) closeTimeObj).intValue() : null;
    }

    public String getPrivKeyFile(Map<String, Object> inParams) {
        return (String) inParams.get("PrivKeyFile");
    }

    public boolean isProxyClientType(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type) ||
               TunnelController.TYPE_CONNECT.equals(type) ||
               TunnelController.TYPE_SOCKS.equals(type) ||
               TunnelController.TYPE_SOCKS_IRC.equals(type);
    }

    public boolean supportsPersistentClientKey(String type) {
        return !TunnelController.TYPE_HTTP_CLIENT.equals(type) &&
               !TunnelController.TYPE_CONNECT.equals(type) &&
               !TunnelController.TYPE_STREAMR_CLIENT.equals(type);
    }

    public boolean getSharedClient(Map<String, Object> inParams, String type) {
        if (TunnelController.TYPE_STREAMR_CLIENT.equals(type))
            return false;
        return getShared(inParams);
    }

    public boolean requiresTargetDestination(String type) {
        return TunnelController.TYPE_STD_CLIENT.equals(type) ||
               TunnelController.TYPE_IRC_CLIENT.equals(type) ||
               TunnelController.TYPE_STREAMR_CLIENT.equals(type);
    }

    public boolean supportsUseSSL(String type) {
        return TunnelController.TYPE_STD_CLIENT.equals(type) ||
               TunnelController.TYPE_IRC_CLIENT.equals(type);
    }

    public boolean supportsDCC(String type) {
        return TunnelController.TYPE_IRC_CLIENT.equals(type);
    }

    public boolean supportsHTTPFiltering(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type);
    }

    public boolean supportsHTTPAddressLookup(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type);
    }

    public boolean supportsOutproxyType(String type) {
        return TunnelController.TYPE_SOCKS.equals(type) ||
               TunnelController.TYPE_SOCKS_IRC.equals(type);
    }

    public boolean supportsSSLProxies(String type) {
        return TunnelController.TYPE_HTTP_CLIENT.equals(type);
    }

    public boolean getConfiguredUseSSL(Map<String, Object> inParams, String type) {
        return supportsUseSSL(type) && getUseSSL(inParams);
    }

    public boolean getConfiguredDelayOpen(Map<String, Object> inParams, String type) {
        if (TunnelController.TYPE_STREAMR_CLIENT.equals(type))
            return false;
        return getDelayOpen(inParams);
    }

    public String getEncryptLeaseSet(Map<String, Object> inParams) {
        return (String) inParams.get("EncryptLeaseSet");
    }

    public int getEncryptLeaseSetMode(Map<String, Object> inParams) {
        String encryptLeaseSet = getEncryptLeaseSet(inParams);
        if (encryptLeaseSet == null)
            return -1;

        encryptLeaseSet = encryptLeaseSet.trim();
        if (encryptLeaseSet.isEmpty())
            return -1;

        String mode = encryptLeaseSet.toLowerCase();
        switch (mode) {
            case "disable":
                return ENCRYPT_LEASE_SET_DISABLE;
            case "encrypted (aes)":
                return ENCRYPT_LEASE_SET_AES;
            case "blinded":
                return ENCRYPT_LEASE_SET_BLINDED;
            case "blinded with lookup password":
                return ENCRYPT_LEASE_SET_BLINDED_LOOKUP;
            case "encrypted (psk)":
                return ENCRYPT_LEASE_SET_PSK;
            case "encrypted with lookup password (psk)":
                return ENCRYPT_LEASE_SET_PSK_LOOKUP;
            case "encrypted with per-user key (psk)":
                return ENCRYPT_LEASE_SET_PSK_PER_USER;
            case "encrypted with lookup password and per-user key (psk)":
                return ENCRYPT_LEASE_SET_PSK_LOOKUP_PER_USER;
            case "encrypted with per-user key (dh)":
                return ENCRYPT_LEASE_SET_DH_PER_USER;
            case "encrypted with lookup password and per-user key (dh)":
                return ENCRYPT_LEASE_SET_DH_LOOKUP_PER_USER;
            default:
                throw new IllegalArgumentException("EncryptLeaseSet is not valid");
        }
    }

    public String getOptionalLookup(Map<String, Object> inParams) {
        return (String) inParams.get("OptionalLookup");
    }

    public boolean getBlockAccessInProxies(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("BlockAccessInProxies"));
    }

    public boolean getBlockUserAgents(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("BlockUserAgents"));
    }

    public String getUserAgents(Map<String, Object> inParams) {
        return (String) inParams.get("UserAgents");
    }

    public boolean getUniqueLocalAddressPerClient(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("UniqueLocalAddressPerClient"));
    }

    public boolean getBlockReferers(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("BlockReferers"));
    }

    public boolean getMultiHoming(Map<String, Object> inParams) {
        return Boolean.TRUE.equals(inParams.get("MultiHoming"));
    }

    public String getAccessOption(Map<String, Object> inParams) {
        return (String) inParams.get("AccessOption");
    }

    public String getAccessList(Map<String, Object> inParams) {
        return (String) inParams.get("AccessList");
    }

    public String getFilePathFilter(Map<String, Object> inParams) {
        return (String) inParams.get("FilterFilePath");
    }

    public Integer getMaxConcurrentConns(Map<String, Object> inParams) {
        Object maxConnectionsObj = inParams.get("MaxConcurrentConns");
        return maxConnectionsObj != null ? ((Number) maxConnectionsObj).intValue() : null;
    }

    public Integer getClientPerMinute(Map<String, Object> inParams) {
        Object clientPerMinuteObj = inParams.get("ClientPerMinute");
        return clientPerMinuteObj != null ? ((Number) clientPerMinuteObj).intValue() : null;
    }

    public Integer getClientPerHour(Map<String, Object> inParams) {
        Object clientPerHourObj = inParams.get("ClientPerHour");
        return clientPerHourObj != null ? ((Number) clientPerHourObj).intValue() : null;
    }

    public Integer getClientPerDay(Map<String, Object> inParams) {
        Object clientPerDayObj = inParams.get("ClientPerDay");
        return clientPerDayObj != null ? ((Number) clientPerDayObj).intValue() : null;
    }

    public Integer getTotalInPerMinute(Map<String, Object> inParams) {
        Object totalInPerMinuteObj = inParams.get("TotalInPerMinute");
        return totalInPerMinuteObj != null ? ((Number) totalInPerMinuteObj).intValue() : null;
    }

    public Integer getTotalInPerHour(Map<String, Object> inParams) {
        Object totalInPerHourObj = inParams.get("TotalInPerHour");
        return totalInPerHourObj != null ? ((Number) totalInPerHourObj).intValue() : null;
    }

    public Integer getTotalInPerDay(Map<String, Object> inParams) {
        Object totalInPerDayObj = inParams.get("TotalInPerDay");
        return totalInPerDayObj != null ? ((Number) totalInPerDayObj).intValue() : null;
    }

    public Integer getPostLimitPeriod(Map<String, Object> inParams) {
        Object postPeriodObj = inParams.get("PostLimit");
        return postPeriodObj != null ? ((Number) postPeriodObj).intValue() : null;
    }

    public Integer getPostBanTime(Map<String, Object> inParams) {
        Object postBanTimeObj = inParams.get("PostLimitTime");
        return postBanTimeObj != null ? ((Number) postBanTimeObj).intValue() : null;
    }

    public Integer getPerClientPeriod(Map<String, Object> inParams) {
        Object perClientPeriodObj = inParams.get("PerClientPeriod");
        return perClientPeriodObj != null ? ((Number) perClientPeriodObj).intValue() : null;
    }

    public Integer getTotalPeriod(Map<String, Object> inParams) {
        Object totalPeriodObj = inParams.get("TotalPeriod");
        return totalPeriodObj != null ? ((Number) totalPeriodObj).intValue() : null;
    }

    public Integer getTotalBanTime(Map<String, Object> inParams) {
        Object totalBanTimeObj = inParams.get("TotalBanTime");
        return totalBanTimeObj != null ? ((Number) totalBanTimeObj).intValue() : null;
    }

    public String getHostName(Map<String, Object> inParams) {
        return (String) inParams.get("HostName");
    }

    public String getNormalizedSigType(Map<String, Object> inParams) {
        String sigType = getSigType(inParams);
        SigType parsed = sigType != null ? SigType.parseSigType(sigType.trim()) : null;
        if (parsed == null || !parsed.isAvailable())
            parsed = TunnelController.PREFERRED_SIGTYPE;
        return Integer.toString(parsed.getCode());
    }

    public String getProxyAuthType(String type, boolean proxyAuth) {
        if (!proxyAuth)
            return "false";
        if (TunnelController.TYPE_SOCKS.equals(type) || TunnelController.TYPE_SOCKS_IRC.equals(type))
            return "true";
        return I2PTunnelHTTPClientBase.DIGEST_AUTH;
    }

    private TunnelController findTunnelControllerByName(String name) {
        if (_group == null)
            return null;
        for (TunnelController controller : _group.getControllers()) {
            if (controller.getName().equals(name))
                return controller;
        }
        return null;
    }
}
