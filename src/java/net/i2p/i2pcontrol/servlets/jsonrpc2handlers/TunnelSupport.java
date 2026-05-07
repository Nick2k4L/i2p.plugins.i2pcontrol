package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import net.i2p.client.I2PClient;
import net.i2p.crypto.EncType;
import net.i2p.crypto.KeyGenerator;
import net.i2p.crypto.KeyPair;
import net.i2p.crypto.SigType;
import net.i2p.data.DataHelper;
import net.i2p.data.SimpleDataStructure;
import net.i2p.i2ptunnel.I2PTunnelClientBase;
import net.i2p.i2ptunnel.I2PTunnelHTTPClient;
import net.i2p.i2ptunnel.I2PTunnelHTTPClientBase;
import net.i2p.i2ptunnel.I2PTunnelIRCClient;
import net.i2p.i2ptunnel.TunnelController;
import net.i2p.i2ptunnel.TunnelControllerGroup;
import net.i2p.i2ptunnel.socks.I2PSOCKSTunnel;
import net.i2p.router.RouterContext;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class TunnelSupport {
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
    private static final String SHARED_CLIENT_NICKNAME = "shared clients";

    private static final String[] NO_SHOW_OPTS = {
        "inbound.length", "outbound.length", "inbound.lengthVariance", "outbound.lengthVariance",
        "inbound.backupQuantity", "outbound.backupQuantity", "inbound.quantity", "outbound.quantity",
        "inbound.nickname", "outbound.nickname", PROP_STREAMING_CONNECT_DELAY, PROP_STREAMING_MAX_WINDOW_SIZE,
        I2PTunnelIRCClient.PROP_DCC
    };
    private static final String[] BOOLEAN_CLIENT_OPTS = {
        PROP_REDUCE_ON_IDLE, PROP_CLOSE_ON_IDLE, PROP_NEW_DEST_ON_RESUME, PROP_PERSISTENT_CLIENT_KEY,
        PROP_DELAY_OPEN, I2PTunnelClientBase.PROP_USE_SSL
    };
    private static final String[] BOOLEAN_PROXY_OPTS = {
        I2PTunnelHTTPClientBase.PROP_OUTPROXY_AUTH,
        I2PTunnelHTTPClientBase.PROP_USE_OUTPROXY_PLUGIN,
        I2PTunnelHTTPClient.PROP_USER_AGENT,
        I2PTunnelHTTPClient.PROP_REFERER,
        I2PTunnelHTTPClient.PROP_ACCEPT,
        I2PTunnelHTTPClient.PROP_INTERNAL_SSL,
        I2PTunnelHTTPClient.PROP_SSL_SET
    };
    private static final String[] OTHER_CLIENT_OPTS = {
        PROP_REDUCE_IDLE_TIME, PROP_REDUCE_QUANTITY, PROP_CLOSE_IDLE_TIME,
        I2PTunnelHTTPClientBase.PROP_OUTPROXY_USER, I2PTunnelHTTPClientBase.PROP_OUTPROXY_PW,
        I2PSOCKSTunnel.PROP_OUTPROXY_TYPE,
        I2PTunnelHTTPClient.PROP_JUMP_SERVERS,
        I2PTunnelHTTPClientBase.PROP_AUTH,
        I2PClient.PROP_SIGTYPE,
        I2PTunnelHTTPClient.PROP_SSL_OUTPROXIES,
        "inbound.randomKey", "outbound.randomKey", "i2cp.leaseSetSigningPrivateKey",
        "i2cp.leaseSetPrivateKey", "i2cp.leaseSetEncType"
    };
    private static final String[] OTHER_PROXY_OPTS = {
        "proxyUsername", "proxyPassword"
    };
    private static final Set<String> NO_SHOW_SET = new HashSet<>(128);
    private static final Set<String> NON_PROXY_NO_SHOW_SET = new HashSet<>(4);
    static {
        NO_SHOW_SET.addAll(Arrays.asList(NO_SHOW_OPTS));
        NO_SHOW_SET.addAll(Arrays.asList(BOOLEAN_CLIENT_OPTS));
        NO_SHOW_SET.addAll(Arrays.asList(BOOLEAN_PROXY_OPTS));
        NO_SHOW_SET.addAll(Arrays.asList(OTHER_CLIENT_OPTS));
        NON_PROXY_NO_SHOW_SET.addAll(Arrays.asList(OTHER_PROXY_OPTS));
    }

    private final RouterContext _context;
    private final TunnelControllerGroup _group;
    private final TunnelRequestParser _parser;

    public TunnelSupport(RouterContext context, TunnelControllerGroup group, TunnelRequestParser parser) {
        _context = context;
        _group = group;
        _parser = parser;
    }

    public void setCommon(Properties config, Map<String, Object> inParams, String type, boolean edit) {

        String name = _parser.getName(inParams, edit).trim();
        if (_parser.getNewName(inParams) != null)
            name = _parser.getNewName(inParams).trim();

        config.setProperty(TunnelController.PROP_TYPE, type);
        config.setProperty(TunnelController.PROP_NAME, name);
        config.setProperty(TunnelController.PROP_LISTEN_PORT, Integer.toString(_parser.getPort(inParams)));
        config.setProperty(TunnelController.PROP_START, Boolean.toString(_parser.getStartOnLoad(inParams)));

        if (TunnelController.TYPE_HTTP_SERVER.equals(type))
            config.setProperty(TunnelController.PROP_TARGET_PORT, Integer.toString(_parser.getPort(inParams)));

        String description = _parser.getDescription(inParams);
        if (description != null) {
            config.setProperty(TunnelController.PROP_DESCR, description);
        }
        else {
            config.remove(TunnelController.PROP_DESCR);
        }
    }

    public void setTunnelClientEndpointOptions(Properties config, Map<String, Object> inParams, String type, boolean edit) {
        String name = _parser.getName(inParams, edit).trim();
        if (_parser.getNewName(inParams) != null)
            name = _parser.getNewName(inParams).trim();

        boolean sharedClient = _parser.getSharedClient(inParams, type);
        if (TunnelController.TYPE_STREAMR_CLIENT.equals(type) || TunnelController.TYPE_HTTP_SERVER.equals(type)) {
            String targetHost = _parser.getTargetHost(inParams);
            config.setProperty(TunnelController.PROP_TARGET_HOST, targetHost != null ? targetHost : "127.0.0.1");
        } else {
            String reachableBy = _parser.getReachableBy(inParams);
            config.setProperty(TunnelController.PROP_INTFC, reachableBy != null ? reachableBy : "127.0.0.1");
        }

        config.setProperty(TunnelController.PROP_SHARED, Boolean.toString(sharedClient));

        String nickname = sharedClient ? SHARED_CLIENT_NICKNAME : name;
        config.setProperty(OPT + "inbound.nickname", nickname);
        config.setProperty(OPT + "outbound.nickname", nickname);

        config.setProperty(OPT + I2PTunnelClientBase.PROP_USE_SSL,
                           Boolean.toString(_parser.getConfiguredUseSSL(inParams, type)));

        if (_parser.supportsDCC(type)) {
            boolean dcc = _parser.getDCC(inParams);
            config.setProperty(OPT + I2PTunnelIRCClient.PROP_DCC, Boolean.toString(dcc));
            if (dcc) {
                config.setProperty(OPT + TunnelController.PROP_MAX_CONNS_MIN, "3");
                config.setProperty(OPT + TunnelController.PROP_MAX_CONNS_HOUR, "10");
                config.setProperty(OPT + TunnelController.PROP_MAX_TOTAL_CONNS_MIN, "5");
                config.setProperty(OPT + TunnelController.PROP_MAX_TOTAL_CONNS_HOUR, "25");
            }
        }
    }

    public void setTunnelLengthOptions(Properties config, Map<String, Object> inParams) {
        Integer tunnelLength = _parser.getTunnelLength(inParams);
        Integer tunnelVariance = _parser.getTunnelVariance(inParams);

        if (tunnelLength != null)
            setTunnelQuantity(config, "length", tunnelLength, tunnelLength);

        if (tunnelVariance != null)
            setTunnelQuantity(config, "lengthVariance", tunnelVariance, tunnelVariance);
    }

    public void setTunnelQuantityOptions(Properties config, Map<String, Object> inParams) {
        Integer tunnelQuantity = _parser.getTunnelQuantity(inParams);
        Integer backupQuantity = _parser.getBackupQuantity(inParams);

        if (tunnelQuantity != null)
            setTunnelQuantity(config, "quantity", tunnelQuantity, tunnelQuantity);

        if (backupQuantity != null)
            setTunnelQuantity(config, "backupQuantity", backupQuantity, backupQuantity);
    }

    public void setTunnelCryptographyOptions(Properties config, Map<String, Object> inParams) {
        config.setProperty(OPT + I2PClient.PROP_SIGTYPE, _parser.getNormalizedSigType(inParams));

        String encType = _parser.getEncType(inParams);
        if (encType != null)
            config.setProperty(OPT + "i2cp.leaseSetEncType", encType);
    }

    public void setCustomOptions(Properties config, Map<String, Object> inParams) {
        String customOptions = _parser.getCustomOptions(inParams);
        if (customOptions != null)
            addCustomOptions(config, customOptions);
    }

    public String getDefaultPrivateKeyFile() {
        int tunnel = _group == null ? 999 : _group.getControllers().size();
        String rv = "i2ptunnel" + tunnel + "-privKeys.dat";
        int i = 0;
        while ((new File(_context.getConfigDir(), rv)).exists()) {
            rv = "i2ptunnel" + tunnel + '.' + (++i) + "-privKeys.dat";
        }
        return rv;
    }

    public void ensureLeaseSetKeys(Properties config, String encTypes, String leaseSetType) {
        String signingKeyProp = OPT + "i2cp.leaseSetSigningPrivateKey";
        if ("0".equals(encTypes) && "0".equals(leaseSetType)) {
            if (!config.containsKey(signingKeyProp)) {
                SigType sigType = SigType.parseSigType(
                        config.getProperty(
                                OPT + I2PClient.PROP_SIGTYPE,
                                Integer.toString(TunnelController.PREFERRED_SIGTYPE.getCode())
                        )
                );
                if (sigType != null) {
                    try {
                        SimpleDataStructure[] keys = KeyGenerator.getInstance().generateSigningKeys(sigType);
                        config.setProperty(signingKeyProp, sigType.name() + ':' + keys[1].toBase64());
                    } catch (GeneralSecurityException gse) {
                        // Leave unset if we can't generate it.
                    }
                }
            }
        }

        String privateKeyProp = OPT + "i2cp.leaseSetPrivateKey";
        String existingKeys = config.getProperty(privateKeyProp);
        if (existingKeys != null && !existingKeys.isEmpty() && !existingKeys.contains(":")) {
            existingKeys = "ELGAMAL_2048:" + existingKeys;
            config.setProperty(privateKeyProp, existingKeys);
        }

        for (String encType : DataHelper.split(encTypes, ",")) {
            EncType parsed = EncType.parseEncType(encType);
            if (parsed == null || !parsed.isAvailable())
                continue;

            String typeName = parsed.toString();
            existingKeys = config.getProperty(privateKeyProp, "");
            if (!existingKeys.contains(typeName + ':')) {
                KeyPair keys = KeyGenerator.getInstance().generatePKIKeys(parsed);
                String newKey = typeName + ':' + keys.getPrivate().toBase64();
                if (!existingKeys.isEmpty())
                    config.setProperty(privateKeyProp, existingKeys + ',' + newKey);
                else
                    config.setProperty(privateKeyProp, newKey);
            }
        }
    }


    private void setTunnelQuantity(Properties config, String name, int inbound, int outbound) {
        config.setProperty(OPT + "inbound." + name, Integer.toString(inbound));
        config.setProperty(OPT + "outbound." + name, Integer.toString(outbound));
    }

    private void addCustomOptions(Properties config, String customOptions) {
        String type = config.getProperty(TunnelController.PROP_TYPE);
        for (String token : customOptions.split("[,\\s]+")) {
            if (token.trim().isEmpty())
                continue;
            int equals = token.indexOf('=');
            String key = equals >= 0 ? token.substring(0, equals).trim() : token.trim();
            String value = equals >= 0 ? token.substring(equals + 1).trim() : "";
            if (NO_SHOW_SET.contains(key))
                continue;
            if (!TunnelController.TYPE_HTTP_CLIENT.equals(type) &&
                !TunnelController.TYPE_CONNECT.equals(type) &&
                NON_PROXY_NO_SHOW_SET.contains(key)) {
                continue;
            }
            if (!key.isEmpty())
                config.setProperty(OPT + key, value);
        }
    }

    public TunnelController findTunnelControllerByName(String name) {
        for (TunnelController controller : _group.getControllers()) {
            if (controller.getName().equals(name))
                return controller;
        }
        return null;
    }


}
