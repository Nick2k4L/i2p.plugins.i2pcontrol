package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import net.i2p.client.I2PClient;
import net.i2p.crypto.SigType;
import net.i2p.data.Base32;
import net.i2p.data.Base64;
import net.i2p.data.DataHelper;
import net.i2p.i2ptunnel.I2PTunnelHTTPServer;
import net.i2p.i2ptunnel.I2PTunnelServer;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.router.RouterContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class ServiceTunnelCreator {
    private static final String OPT = TunnelController.PFX_OPTION;
    private static final String PROP_STREAMING_CONNECT_DELAY = "i2p.streaming.connectDelay";
    private static final String PROP_REDUCE_ON_IDLE = "i2cp.reduceOnIdle";
    private static final String PROP_REDUCE_QUANTITY = "i2cp.reduceQuantity";
    private static final String PROP_REDUCE_IDLE_TIME = "i2cp.reduceIdleTime";
    private static final String PROP_MAX_STREAMS = "i2p.streaming.maxConcurrentStreams";
    private static final String PROP_MAX_CONNS_MIN = "i2p.streaming.maxConnsPerMinute";
    private static final String PROP_MAX_CONNS_HOUR = "i2p.streaming.maxConnsPerHour";
    private static final String PROP_MAX_CONNS_DAY = "i2p.streaming.maxConnsPerDay";
    private static final String PROP_MAX_TOTAL_CONNS_MIN = "i2p.streaming.maxTotalConnsPerMinute";
    private static final String PROP_MAX_TOTAL_CONNS_HOUR = "i2p.streaming.maxTotalConnsPerHour";
    private static final String PROP_MAX_TOTAL_CONNS_DAY = "i2p.streaming.maxTotalConnsPerDay";
    private static final String PROP_ENABLE_ACCESS_LIST = "i2cp.enableAccessList";
    private static final String PROP_ENABLE_BLACKLIST = "i2cp.enableBlackList";

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

    private static final int DEFAULT_MAX_CONNS_MIN = 30;
    private static final int DEFAULT_MAX_CONNS_HOUR = 80;
    private static final int DEFAULT_MAX_CONNS_DAY = 200;
    private static final int DEFAULT_MAX_TOTAL_CONNS_MIN = 50;
    private static final int DEFAULT_MAX_TOTAL_CONNS_HOUR = 0;
    private static final int DEFAULT_MAX_TOTAL_CONNS_DAY = 0;

    private static final int DEFAULT_MAX_STREAMS = 30;

    private final RouterContext _context;
    private final TunnelControllerGroup _group;
    private final TunnelRequestParser _parser;
    private final TunnelSupport _support;

    public ServiceTunnelCreator(RouterContext context, TunnelControllerGroup group, TunnelRequestParser parser, TunnelSupport support) {
        _context = context;
        _group = group;
        _parser = parser;
        _support = support;
    }

    public List<String> create(Map<String, Object> inParams, String type) throws IOException {
        Properties config = new Properties();
        setConfiguration(config, inParams, type, false);
        finalizeServiceConfig(config, type);

        TunnelController controller = new TunnelController(config, "");
        _group.addController(controller);
        _group.saveConfig(controller);

        if (controller.getStartOnLoad())
            controller.startTunnelBackground();
        return controller.clearMessages();
    }

    public void edit(Map<String, Object> inParams, String name) throws IOException {
        TunnelController controller = _support.findTunnelControllerByName(name);
        if (controller == null)
            throw new IllegalArgumentException("Tunnel with name '" + name + "' not found");
        Properties config = controller.getConfig("");
        setConfiguration(config, inParams, controller.getType(), true);
        finalizeServiceConfig(config, controller.getType());

        controller.setConfig(config, "");
        _group.saveConfig(controller);

        controller.clearMessages();
    }

    private void setConfiguration(Properties config, Map<String, Object> inParams, String type, boolean edit) {
        setServiceCommon(config, inParams, type, edit);
        setServiceEndpointOptions(config, inParams, type, edit);
        _support.setCustomOptions(config, inParams);
        _support.setTunnelLengthOptions(config, inParams);
        _support.setTunnelQuantityOptions(config, inParams);
        _support.setTunnelCryptographyOptions(config, inParams);
        setEncryptLeaseSetOptions(config, inParams);
        setPosts(config, inParams, type);
        setConcurrentConnections(config, inParams);
        setInboundConnections(config, inParams);
        setProfile(config, inParams);
        setReduceTunnelQuantityIdle(config, inParams);
        setReduce(config, inParams);
        setServerAccessOptions(config, inParams, type);
        setRestrictedAccessList(config, inParams);
    }

    private void setServiceCommon(Properties config, Map<String, Object> inParams, String type, boolean edit) {
        String name = _parser.getName(inParams, edit).trim();
        if (_parser.getNewName(inParams) != null)
            name = _parser.getNewName(inParams).trim();

        config.setProperty(TunnelController.PROP_TYPE, type);
        config.setProperty(TunnelController.PROP_NAME, name);
        config.setProperty(TunnelController.PROP_START, Boolean.toString(_parser.getStartOnLoad(inParams)));

        if (type.equals(TunnelController.TYPE_STD_CLIENT)) {
            config.setProperty(OPT + PROP_STREAMING_CONNECT_DELAY,
                    _parser.getConnectDelay(inParams) ? "500" : "0");
        }
        // explicitly set to zero for everything else
        else {
            config.setProperty(OPT + PROP_STREAMING_CONNECT_DELAY, "0");
        }
        config.setProperty(OPT + "inbound.nickname", name);
        config.setProperty(OPT + "outbound.nickname", name);

        String description = _parser.getDescription(inParams);
        if (description != null) {
            config.setProperty(TunnelController.PROP_DESCR, description);
        }
        else {
            config.remove(TunnelController.PROP_DESCR);
        }
    }

    private void setServiceEndpointOptions(Properties config, Map<String, Object> inParams, String type, boolean edit) {
        String privKeyFile = _parser.getPrivKeyFile(inParams);
        if (privKeyFile != null && !privKeyFile.trim().isEmpty())
            config.setProperty(TunnelController.PROP_FILE, privKeyFile.trim());
        else if (!edit || inParams.containsKey("PrivKeyFile") || config.getProperty(TunnelController.PROP_FILE) == null)
            config.setProperty(TunnelController.PROP_FILE, _support.getDefaultPrivateKeyFile());

        Integer targetPort = _parser.getTargetPort(inParams);
        if (targetPort == null)
            targetPort = _parser.getPort(inParams);
        config.setProperty(TunnelController.PROP_TARGET_PORT, Integer.toString(targetPort));

        if (!TunnelController.TYPE_STREAMR_SERVER.equals(type)) {
            String targetHost = _parser.getTargetHost(inParams);
            if (targetHost != null && !targetHost.trim().isEmpty())
                config.setProperty(TunnelController.PROP_TARGET_HOST, targetHost.trim());
            else
                config.setProperty(TunnelController.PROP_TARGET_HOST, "127.0.0.1");
        }

        config.setProperty(OPT + I2PTunnelServer.PROP_USE_SSL,
                           Boolean.toString(_parser.getUseSSL(inParams)));

        if (TunnelController.TYPE_HTTP_BIDIR_SERVER.equals(type)) {
            config.setProperty(TunnelController.PROP_LISTEN_PORT, Integer.toString(_parser.getPort(inParams)));
            String reachableBy = _parser.getReachableBy(inParams);
            if (reachableBy != null && !reachableBy.trim().isEmpty()) {
                config.setProperty(TunnelController.PROP_INTFC, reachableBy.trim());
            } else {
                String targetHost = _parser.getTargetHost(inParams);
                if (targetHost != null && !targetHost.trim().isEmpty())
                    config.setProperty(TunnelController.PROP_INTFC, targetHost.trim());
                else
                    config.setProperty(TunnelController.PROP_INTFC, "");
            }
        } else if (TunnelController.TYPE_STREAMR_SERVER.equals(type)) {
            String reachableBy = _parser.getReachableBy(inParams);
            if (reachableBy != null && !reachableBy.trim().isEmpty())
                config.setProperty(TunnelController.PROP_INTFC, reachableBy.trim());
            else
                config.setProperty(TunnelController.PROP_INTFC, "");
        }

        if (TunnelController.TYPE_HTTP_SERVER.equals(type) || TunnelController.TYPE_HTTP_BIDIR_SERVER.equals(type)) {
            String websiteHostname = _parser.getWebsiteHostname(inParams);
            if (websiteHostname != null && !websiteHostname.trim().isEmpty())
                config.setProperty(TunnelController.PROP_SPOOFED_HOST, websiteHostname.trim());
            else
                config.remove(TunnelController.PROP_SPOOFED_HOST);
        }

        if (!TunnelController.TYPE_HTTP_SERVER.equals(type) && !TunnelController.TYPE_STREAMR_SERVER.equals(type))
            config.setProperty(TunnelController.OPT_BUNDLE_REPLY, "true");
    }

    private void setPosts(Properties config, Map<String, Object> inParams, String type) {
        if (!TunnelController.TYPE_HTTP_SERVER.equals(type) &&
            !TunnelController.TYPE_HTTP_BIDIR_SERVER.equals(type)) {
            return;
        }

        Integer postLimitPeriod = _parser.getPostLimitPeriod(inParams);
        Integer postBanTime = _parser.getPostBanTime(inParams);
        Integer totalBanTime = _parser.getTotalBanTime(inParams);
        Integer perClientPeriod = _parser.getPerClientPeriod(inParams);
        Integer totalPeriod = _parser.getTotalPeriod(inParams);

        if (postLimitPeriod != null)
            config.setProperty(OPT + I2PTunnelHTTPServer.OPT_POST_WINDOW, Integer.toString(postLimitPeriod * 60));
        if (postBanTime != null)
            config.setProperty(OPT + I2PTunnelHTTPServer.OPT_POST_BAN_TIME, Integer.toString(postBanTime * 60));
        if (totalBanTime != null)
            config.setProperty(OPT + I2PTunnelHTTPServer.OPT_POST_TOTAL_BAN_TIME, Integer.toString(totalBanTime * 60));
        if (totalPeriod != null)
            config.setProperty(OPT + I2PTunnelHTTPServer.OPT_POST_TOTAL_MAX, Integer.toString(totalPeriod));
        if (perClientPeriod != null)
            config.setProperty(OPT + I2PTunnelHTTPServer.OPT_POST_MAX, Integer.toString(perClientPeriod));
    }

    private void setConcurrentConnections(Properties config, Map<String, Object> inParams) {
        Integer maxConcurrentConns = _parser.getMaxConcurrentConns(inParams);
        config.setProperty(OPT + TunnelController.PROP_LIMITS_SET, Boolean.TRUE.toString());
        config.setProperty(OPT + PROP_MAX_STREAMS,
                           maxConcurrentConns != null ? Integer.toString(maxConcurrentConns) : Integer.toString(DEFAULT_MAX_STREAMS));
    }

    private void setInboundConnections(Properties config, Map<String, Object> inParams) {
        Integer clientPerMinute = _parser.getClientPerMinute(inParams);
        Integer clientPerHour = _parser.getClientPerHour(inParams);
        Integer clientPerDay = _parser.getClientPerDay(inParams);

        Integer totalInPerMinute = _parser.getTotalInPerMinute(inParams);
        Integer totalInPerHour = _parser.getTotalInPerHour(inParams);
        Integer totalInPerDay = _parser.getTotalInPerDay(inParams);

        config.setProperty(OPT + PROP_MAX_CONNS_MIN,
                           clientPerMinute != null ? Integer.toString(clientPerMinute) : Integer.toString(DEFAULT_MAX_CONNS_MIN));
        config.setProperty(OPT + PROP_MAX_CONNS_HOUR,
                           clientPerHour != null ? Integer.toString(clientPerHour) : Integer.toString(DEFAULT_MAX_CONNS_HOUR));
        config.setProperty(OPT + PROP_MAX_CONNS_DAY,
                           clientPerDay != null ? Integer.toString(clientPerDay) : Integer.toString(DEFAULT_MAX_CONNS_DAY));

        config.setProperty(OPT + PROP_MAX_TOTAL_CONNS_MIN,
                           totalInPerMinute != null ? Integer.toString(totalInPerMinute) : Integer.toString(DEFAULT_MAX_TOTAL_CONNS_MIN));
        config.setProperty(OPT + PROP_MAX_TOTAL_CONNS_HOUR,
                           totalInPerHour != null ? Integer.toString(totalInPerHour) : Integer.toString(DEFAULT_MAX_TOTAL_CONNS_HOUR));
        config.setProperty(OPT + PROP_MAX_TOTAL_CONNS_DAY,
                           totalInPerDay != null ? Integer.toString(totalInPerDay) : Integer.toString(DEFAULT_MAX_TOTAL_CONNS_DAY));
    }

    // TODO: Remember this is only for STANDARD CLIENT
    private void setProfile(Properties config, Map<String, Object> inParams) {
        String profile = _parser.getProfile(inParams);
        if ("interactive".equals(profile))
            config.setProperty(OPT + "i2p.streaming.maxWindowSize", "16");
        else
            config.remove(OPT + "i2p.streaming.maxWindowSize");
    }

    private void setReduceTunnelQuantityIdle(Properties config, Map<String, Object> inParams) {
        Integer reduceCount = _parser.getReduceCount(inParams);
        Integer reduceTime = _parser.getReduceTime(inParams);
        if (reduceCount != null)
            config.setProperty(OPT + PROP_REDUCE_QUANTITY, Integer.toString(reduceCount));
        if (reduceTime != null)
            config.setProperty(OPT + PROP_REDUCE_IDLE_TIME, Integer.toString(reduceTime * 60 * 1000));
    }

    private void setReduce(Properties config, Map<String, Object> inParams) {
        config.setProperty(OPT + PROP_REDUCE_ON_IDLE, Boolean.toString(_parser.getReduce(inParams)));
    }

    private void setServerAccessOptions(Properties config, Map<String, Object> inParams, String type) {
        boolean multiHoming = _parser.getMultiHoming(inParams);
        boolean isHTTPServer = TunnelController.TYPE_HTTP_SERVER.equals(type) ||
                               TunnelController.TYPE_HTTP_BIDIR_SERVER.equals(type);

        config.setProperty(OPT + I2PTunnelHTTPServer.OPT_REJECT_INPROXY,
                           Boolean.toString(isHTTPServer && _parser.getBlockAccessInProxies(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPServer.OPT_REJECT_USER_AGENTS,
                           Boolean.toString(isHTTPServer && _parser.getBlockUserAgents(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPServer.OPT_REJECT_REFERER,
                           Boolean.toString(isHTTPServer && _parser.getBlockReferers(inParams)));

        if (isHTTPServer && _parser.getUserAgents(inParams) != null) {
            config.setProperty(OPT + I2PTunnelHTTPServer.OPT_USER_AGENTS,
                               Objects.toString(_parser.getUserAgents(inParams)));
        }
        else {
            config.remove(OPT + I2PTunnelHTTPServer.OPT_USER_AGENTS);
        }

        config.setProperty(OPT + "shouldBundleReplyInfo", Boolean.toString(multiHoming));
        config.setProperty(OPT + I2PTunnelServer.PROP_UNIQUE_LOCAL,
                           Boolean.toString(_parser.getUniqueLocalAddressPerClient(inParams)));
    }

    private void setRestrictedAccessList(Properties config, Map<String, Object> inParams) {
        String accessMode = _parser.getAccessOption(inParams);
        String filePathFilter = _parser.getFilePathFilter(inParams);
        String accessList = _parser.getAccessList(inParams);
        config.setProperty(OPT + PROP_ENABLE_ACCESS_LIST, Boolean.toString("allow".equals(accessMode)));
        config.setProperty(OPT + PROP_ENABLE_BLACKLIST, Boolean.toString("deny".equals(accessMode)));

        if (accessList != null){
            setAccessList(accessList, config);
        }
        else {
            config.remove(OPT + "i2cp.accessList");
        }

        if (filePathFilter != null) {
            config.setProperty(TunnelController.PROP_FILTER, filePathFilter);
        }  else {
            config.remove(TunnelController.PROP_FILTER);
        }
    }

    private void setEncryptLeaseSetOptions(Properties config, Map<String, Object> inParams) {
        int encryptMode = _parser.getEncryptLeaseSetMode(inParams);
        if (encryptMode < 0)
            return;

        if (encryptMode >= ENCRYPT_LEASE_SET_BLINDED)
            validateBlindedSigType(config);

        config.setProperty(OPT + "i2cp.encryptLeaseSet", Boolean.toString(encryptMode == ENCRYPT_LEASE_SET_AES));

        String optionalLookup = _parser.getOptionalLookup(inParams);
        if (optionalLookup != null) {
            optionalLookup = optionalLookup.trim();
            if (!optionalLookup.isEmpty())
                config.setProperty(OPT + "i2cp.leaseSetSecret", Base64.encode(DataHelper.getUTF8(optionalLookup)));
            else
                config.remove(OPT + "i2cp.leaseSetSecret");
        }

        switch (encryptMode) {
            case ENCRYPT_LEASE_SET_DISABLE:
                config.remove(OPT + "i2cp.leaseSetSecret");
                config.remove(OPT + "i2cp.leaseSetType");
                config.remove(OPT + "i2cp.leaseSetAuthType");
                config.remove(OPT + "i2cp.leaseSetKey");
                config.remove(OPT + "i2cp.leaseSetPrivKey");
                break;
            case ENCRYPT_LEASE_SET_AES:
                addLeaseSetPrivKey(config, false);
                config.remove(OPT + "i2cp.leaseSetSecret");
                config.remove(OPT + "i2cp.leaseSetAuthType");
                break;
            case ENCRYPT_LEASE_SET_BLINDED:
                config.put(OPT + "i2cp.leaseSetType", "5");
                config.remove(OPT + "i2cp.leaseSetSecret");
                config.remove(OPT + "i2cp.leaseSetAuthType");
                config.remove(OPT + "i2cp.leaseSetKey");
                config.remove(OPT + "i2cp.leaseSetPrivKey");
                break;
            case ENCRYPT_LEASE_SET_BLINDED_LOOKUP:
                config.put(OPT + "i2cp.leaseSetType", "5");
                config.remove(OPT + "i2cp.leaseSetAuthType");
                config.remove(OPT + "i2cp.leaseSetKey");
                config.remove(OPT + "i2cp.leaseSetPrivKey");
                break;
            case ENCRYPT_LEASE_SET_PSK_PER_USER:
            case ENCRYPT_LEASE_SET_PSK:
                addLeaseSetPrivKey(config, true);
                config.remove(OPT + "i2cp.leaseSetSecret");
                config.put(OPT + "i2cp.leaseSetAuthType", "2");
                break;
            case ENCRYPT_LEASE_SET_PSK_LOOKUP_PER_USER:
            case ENCRYPT_LEASE_SET_PSK_LOOKUP:
                addLeaseSetPrivKey(config, true);
                config.put(OPT + "i2cp.leaseSetAuthType", "2");
                break;
            case ENCRYPT_LEASE_SET_DH_PER_USER:
                addLeaseSetPrivKey(config, true);
                config.remove(OPT + "i2cp.leaseSetSecret");
                config.put(OPT + "i2cp.leaseSetAuthType", "1");
                break;
            case ENCRYPT_LEASE_SET_DH_LOOKUP_PER_USER:
                addLeaseSetPrivKey(config, true);
                config.put(OPT + "i2cp.leaseSetAuthType", "1");
                break;
        }
    }

    private void finalizeServiceConfig(Properties config, String type) {
        String p = OPT + "inbound.randomKey";
        if (!config.containsKey(p)) {
            byte[] rk = new byte[32];
            _context.random().nextBytes(rk);
            config.setProperty(p, Base64.encode(rk));
            p = OPT + "outbound.randomKey";
            _context.random().nextBytes(rk);
            config.setProperty(p, Base64.encode(rk));
        }

        String encTypes = getEffectiveLeaseSetEncTypes(config, type);
        String leaseSetType = config.getProperty(OPT + "i2cp.leaseSetType", "0");
        config.setProperty(OPT + "i2cp.leaseSetEncType", encTypes);
        _support.ensureLeaseSetKeys(config, encTypes, leaseSetType);

    }

    private String getEffectiveLeaseSetEncTypes(Properties config, String type) {
        String encTypes = config.getProperty(OPT + "i2cp.leaseSetEncType");
        if (encTypes != null) {
            encTypes = encTypes.trim();
            if (!encTypes.isEmpty())
                return encTypes;
        }
        if (TunnelController.TYPE_HTTP_SERVER.equals(type) ||
            TunnelController.TYPE_STREAMR_SERVER.equals(type)) {
            return "6,4";
        }
        if (TunnelController.TYPE_IRC_SERVER.equals(type))
            return "4";
        return "4,0";
    }

    private void validateBlindedSigType(Properties config) {
        SigType sigType = SigType.parseSigType(config.getProperty(OPT + I2PClient.PROP_SIGTYPE,
                                                                  Integer.toString(TunnelController.PREFERRED_SIGTYPE.getCode())));
        if (sigType != SigType.EdDSA_SHA512_Ed25519 &&
            sigType != SigType.RedDSA_SHA512_Ed25519) {
            throw new IllegalArgumentException("EncryptLeaseSet requires Ed25519 or RedDSA when blinded");
        }
    }

    private void addLeaseSetPrivKey(Properties config, boolean isBlinded) {
        String opt = OPT + "i2cp.leaseSetKey";
        String blindedOpt = OPT + "i2cp.leaseSetPrivKey";
        String encoded = config.getProperty(opt);
        if (encoded == null) {
            byte[] data = new byte[32];
            _context.random().nextBytes(data);
            encoded = Base64.encode(data);
            config.setProperty(opt, encoded);
        }
        if (isBlinded) {
            config.setProperty(blindedOpt, encoded);
            config.put(OPT + "i2cp.leaseSetType", "5");
        } else {
            config.remove(blindedOpt);
            config.remove(OPT + "i2cp.leaseSetType");
        }
    }

    private void setAccessList(String val, Properties config) {
        if (val != null) {
            val = val.trim();
            if (!val.isEmpty()) {
                val = val.replace("\r\n", ",").replace("\n", ",").replace(" ", ",");
                String[] vals = DataHelper.split(val, ",");
                StringBuilder buf = new StringBuilder(val.length());
                for (int i = 0; i < vals.length; i++) {
                    String v = vals[i];
                    int len = v.length();
                    if (len == 0)
                        continue;
                    if (len == 60 && v.endsWith(".b32.i2p")) {
                        byte[] b = Base32.decode(v.substring(0, 52));
                        if (b != null)
                            v = Base64.encode(b);
                    }
                    buf.append(v);
                    if (i != vals.length - 1)
                        buf.append(',');
                }
                if (!buf.isEmpty()) {
                    config.setProperty(OPT + "i2cp.accessList", buf.toString());
                    return;
                }
            }
        }
        config.remove(OPT + "i2cp.accessList");
    }
}
