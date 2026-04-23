package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import net.i2p.data.Base64;
import net.i2p.data.DataHelper;
import net.i2p.data.Hash;
import net.i2p.i2ptunnel.I2PTunnelClientBase;
import net.i2p.i2ptunnel.I2PTunnelConnectClient;
import net.i2p.i2ptunnel.I2PTunnelHTTPClient;
import net.i2p.i2ptunnel.I2PTunnelHTTPClientBase;
import net.i2p.i2ptunnel.SSLClientUtil;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.router.RouterContext;
import net.i2p.i2ptunnel.socks.I2PSOCKSTunnel;
import net.i2p.util.ConvertToHash;
import net.i2p.util.PasswordManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;

public class ClientTunnelCreator {
    private static final String OPT = TunnelController.PFX_OPTION;
    private static final String PROP_STREAMING_CONNECT_DELAY = "i2p.streaming.connectDelay";
    private static final String PROP_STREAMING_MAX_WINDOW_SIZE = "i2p.streaming.maxWindowSize";
    private static final String PROP_REDUCE_ON_IDLE = "i2cp.reduceOnIdle";
    private static final String PROP_CLOSE_ON_IDLE = "i2cp.closeOnIdle";
    private static final String PROP_NEW_DEST_ON_RESUME = "i2cp.newDestOnResume";
    private static final String PROP_PERSISTENT_CLIENT_KEY = "persistentClientKey";
    private static final String PROP_DELAY_OPEN = "i2cp.delayOpen";
    private static final String PROP_REDUCE_QUANTITY = "i2cp.reduceQuantity";
    private static final String PROP_REDUCE_IDLE_TIME = "i2cp.reduceIdleTime";
    private static final String PROP_CLOSE_IDLE_TIME = "i2cp.closeIdleTime";

    private final RouterContext _context;
    private final TunnelControllerGroup _group;
    private final TunnelRequestParser _parser;
    private final TunnelSupport _support;

    public ClientTunnelCreator(RouterContext context, TunnelControllerGroup group, TunnelRequestParser parser, TunnelSupport support) {
        _context = context;
        _group = group;
        _parser = parser;
        _support = support;
    }

    public List<String> create(Map<String, Object> inParams, String type) throws IOException {
        boolean persistentClientKey = _parser.getPersistentClientKey(inParams, type);
        Properties config = new Properties();
        _support.setCommon(config, inParams, type);
        _support.setTunnelClientEndpointOptions(config, inParams, type);
        setTunnelDestinationOptions(config, inParams, type);
        _support.setCustomOptions(config, inParams);
        setTunnelProxyOptions(config, inParams, type);
        setTunnelManagementOptions(config, inParams, type);
        setTunnelFilteringOptions(config, inParams, type);
        setTunnelAddressLookupOptions(config, inParams, type);
        setTunnelAuthenticationOptions(config, inParams, type);
        _support.setTunnelLengthOptions(config, inParams);
        _support.setTunnelQuantityOptions(config, inParams);
        _support.setTunnelCryptographyOptions(config, inParams);
        finalizeClientConfig(config, type);

        TunnelController controller = new TunnelController(config, "", persistentClientKey);
        _group.addController(controller);
        _group.saveConfig(controller);
        if (controller.getStartOnLoad())
            controller.startTunnelBackground();
        return controller.clearMessages();
    }

    private void setTunnelDestinationOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!_parser.requiresTargetDestination(type))
            return;

        String targetDestination = _parser.getTargetDestination(inParams);
        if (targetDestination == null || targetDestination.trim().isEmpty())
            throw new IllegalArgumentException("TargetDestination is required for " + type);
        config.setProperty(TunnelController.PROP_DEST, targetDestination.trim());
    }

    private void setTunnelManagementOptions(Properties config, Map<String, Object> inParams, String type) {
        boolean persistentClientKey = _parser.getPersistentClientKey(inParams, type);
        Integer reduceCount = _parser.getReduceCount(inParams);
        Integer reduceTime = _parser.getReduceTime(inParams);
        Integer closeTime = _parser.getCloseTime(inParams);

        config.setProperty(OPT + PROP_STREAMING_CONNECT_DELAY, _parser.getConnectDelay(inParams) ? "500" : "0");

        String profile = _parser.getProfile(inParams);
        if ("interactive".equals(profile))
            config.setProperty(OPT + PROP_STREAMING_MAX_WINDOW_SIZE, "16");
        else
            config.remove(OPT + PROP_STREAMING_MAX_WINDOW_SIZE);

        config.setProperty(OPT + PROP_DELAY_OPEN, Boolean.toString(_parser.getConfiguredDelayOpen(inParams, type)));
        config.setProperty(OPT + PROP_REDUCE_ON_IDLE, Boolean.toString(_parser.getReduce(inParams)));
        config.setProperty(OPT + PROP_CLOSE_ON_IDLE, Boolean.toString(_parser.getClose(inParams)));

        int newDestMode = _parser.getNewDestMode(inParams, type);
        config.setProperty(OPT + PROP_NEW_DEST_ON_RESUME, Boolean.toString(newDestMode == 1));
        config.setProperty(OPT + PROP_PERSISTENT_CLIENT_KEY, Boolean.toString(newDestMode == 2));

        if (reduceCount != null)
            config.setProperty(OPT + PROP_REDUCE_QUANTITY, Integer.toString(reduceCount));

        if (reduceTime != null)
            config.setProperty(OPT + PROP_REDUCE_IDLE_TIME, Integer.toString(reduceTime * 60 * 1000));

        if (closeTime != null)
            config.setProperty(OPT + PROP_CLOSE_IDLE_TIME, Integer.toString(closeTime * 60 * 1000));

        String privKeyFile = _parser.getPrivKeyFile(inParams);
        if (privKeyFile != null && persistentClientKey)
            config.setProperty(TunnelController.PROP_FILE, privKeyFile);
        else if (persistentClientKey)
            config.setProperty(TunnelController.PROP_FILE, _support.getDefaultPrivateKeyFile());
        else
            config.remove(TunnelController.PROP_FILE);
    }

    private void setTunnelProxyOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!_parser.isProxyClientType(type))
            return;

        boolean isHTTPClient = TunnelController.TYPE_HTTP_CLIENT.equals(type);
        String proxyList = _parser.getProxyList(inParams);
        if (proxyList != null)
            config.setProperty(TunnelController.PROP_PROXIES, proxyList);

        config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_USE_OUTPROXY_PLUGIN,
                           Boolean.toString(_parser.getUseOutproxyPlugin(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_USER_AGENT,
                           Boolean.toString(isHTTPClient && _parser.getAllowUserAgent(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_REFERER,
                           Boolean.toString(isHTTPClient && _parser.getAllowReferer(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_ACCEPT,
                           Boolean.toString(isHTTPClient && _parser.getAllowAccept(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_INTERNAL_SSL,
                           Boolean.toString(isHTTPClient && _parser.getAllowInternalSSL(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_SSL_SET, Boolean.toString(isHTTPClient));

        if (_parser.supportsOutproxyType(type)) {
            String outproxyType = _parser.getOutproxyType(inParams);
            if (outproxyType != null)
                config.setProperty(OPT + I2PSOCKSTunnel.PROP_OUTPROXY_TYPE, outproxyType);
        }

        if (_parser.supportsSSLProxies(type)) {
            String sslProxies = _parser.getSSLProxies(inParams);
            if (sslProxies != null)
                config.setProperty(OPT + I2PTunnelHTTPClient.PROP_SSL_OUTPROXIES,
                                   sslProxies.trim().replace(" ", ","));
        }
    }

    private void setTunnelFilteringOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!_parser.supportsHTTPFiltering(type))
            return;

        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_USER_AGENT,
                           Boolean.toString(_parser.getAllowUserAgent(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_REFERER,
                           Boolean.toString(_parser.getAllowReferer(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_ACCEPT,
                           Boolean.toString(_parser.getAllowAccept(inParams)));
        config.setProperty(OPT + I2PTunnelHTTPClient.PROP_INTERNAL_SSL,
                           Boolean.toString(_parser.getAllowInternalSSL(inParams)));
    }

    private void setTunnelAddressLookupOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!_parser.supportsHTTPAddressLookup(type))
            return;

        String jumpList = _parser.getJumpList(inParams);
        if (jumpList != null)
            config.setProperty(OPT + I2PTunnelHTTPClient.PROP_JUMP_SERVERS,
                               jumpList.trim().replace("\r\n", ",").replace("\n", ",").replace(" ", ","));
    }

    private void setTunnelAuthenticationOptions(Properties config, Map<String, Object> inParams, String type) {
        if (!_parser.isProxyClientType(type))
            return;

        if (inParams.containsKey("ProxyAuth")) {
            boolean proxyAuth = _parser.getProxyAuth(inParams);
            config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_AUTH, _parser.getProxyAuthType(type, proxyAuth));

            if (proxyAuth) {
                String proxyUsername = _parser.getProxyUsername(inParams);
                String proxyPassword = _parser.getProxyPassword(inParams);

                if (proxyUsername == null || proxyPassword == null)
                    throw new IllegalArgumentException("ProxyUsername and ProxyPassword are required when ProxyAuth is enabled");

                if (TunnelController.TYPE_SOCKS.equals(type) || TunnelController.TYPE_SOCKS_IRC.equals(type)) {
                    config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_PREFIX + proxyUsername +
                                       I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_SHA256_SUFFIX,
                                       PasswordManager.sha256Hex(I2PSOCKSTunnel.AUTH_REALM, proxyUsername, proxyPassword));
                } else {
                    String realm = TunnelController.TYPE_HTTP_CLIENT.equals(type) ?
                                   I2PTunnelHTTPClient.AUTH_REALM : I2PTunnelConnectClient.AUTH_REALM;
                    config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_PREFIX + proxyUsername +
                                       I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_SUFFIX,
                                       PasswordManager.md5Hex(realm, proxyUsername, proxyPassword));
                    config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_PREFIX + proxyUsername +
                                       I2PTunnelHTTPClientBase.PROP_PROXY_DIGEST_SHA256_SUFFIX,
                                       PasswordManager.sha256Hex(realm, proxyUsername, proxyPassword));
                }
            }
        }

        config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_OUTPROXY_AUTH,
                           Boolean.toString(_parser.getOutproxyAuth(inParams)));

        String outproxyUsername = _parser.getOutproxyUsername(inParams);
        if (outproxyUsername != null)
            config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_OUTPROXY_USER, outproxyUsername);

        String outproxyPassword = _parser.getOutproxyPassword(inParams);
        if (outproxyPassword != null)
            config.setProperty(OPT + I2PTunnelHTTPClientBase.PROP_OUTPROXY_PW, outproxyPassword);
    }

    private void finalizeClientConfig(Properties config, String type) {
        if (Boolean.parseBoolean(config.getProperty(OPT + PROP_PERSISTENT_CLIENT_KEY)))
            ensurePersistentClientOptions(config);
        if ((TunnelController.TYPE_STD_CLIENT.equals(type) || TunnelController.TYPE_IRC_CLIENT.equals(type)) &&
            Boolean.parseBoolean(config.getProperty(OPT + I2PTunnelClientBase.PROP_USE_SSL))) {
            ensureClientSSLKeyStore(config);
        }
    }

    private void ensureClientSSLKeyStore(Properties config) {
        try {
            SSLClientUtil.verifyKeyStore(config, OPT, buildClientCertificateAltNames(config));
        } catch (IOException ioe) {
            try {
                SSLClientUtil.verifyKeyStore(config, OPT);
            } catch (IOException ignored) {
                // Mirror the app/UI behavior.
            }
        } catch (IllegalArgumentException iae) {
            try {
                SSLClientUtil.verifyKeyStore(config, OPT);
            } catch (IOException ignored) {
                // See note above.
            }
        }
    }

    private Set<String> buildClientCertificateAltNames(Properties config) {
        Set<String> altNames = new HashSet<>(4);
        String intfc = config.getProperty(TunnelController.PROP_INTFC);
        if (intfc != null && !intfc.equals("0.0.0.0") && !intfc.equals("::") &&
            !intfc.equals("0:0:0:0:0:0:0:0")) {
            altNames.add(intfc);
        }
        String tgts = config.getProperty(TunnelController.PROP_DEST);
        if (tgts != null) {
            if (intfc != null)
                altNames.add(intfc);
            String[] hosts = DataHelper.split(tgts, "[ ,]");
            for (String h : hosts) {
                int colon = h.indexOf(':');
                if (colon >= 0)
                    h = h.substring(0, colon);
                altNames.add(h);
                if (!h.endsWith(".b32.i2p")) {
                    Hash hash = ConvertToHash.getHash(h);
                    if (hash != null)
                        altNames.add(hash.toBase32());
                }
            }
        }
        return altNames;
    }

    private void ensurePersistentClientOptions(Properties config) {
        String p = OPT + "inbound.randomKey";
        if (!config.containsKey(p)) {
            byte[] rk = new byte[32];
            _context.random().nextBytes(rk);
            config.setProperty(p, Base64.encode(rk));
            p = OPT + "outbound.randomKey";
            _context.random().nextBytes(rk);
            config.setProperty(p, Base64.encode(rk));
        }

        String leaseSetType = config.getProperty(OPT + "i2cp.leaseSetType", "0");
        String encTypes = config.getProperty(OPT + "i2cp.leaseSetEncType", "4,0");
        _support.ensureLeaseSetKeys(config, encTypes, leaseSetType);

    }
}
