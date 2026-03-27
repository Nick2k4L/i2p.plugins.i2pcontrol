package net.i2p.i2pcontrol.servlets.jsonrpc2handlers;

import net.i2p.data.DataHelper;
import net.i2p.data.Destination;
import net.i2p.router.RouterContext;
import net.i2p.util.OrderedProperties;
import net.i2p.util.SecureFileOutputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class AddressBookFiles {
    private static final String DEFAULT_PRIVATE_BOOK = "../privatehosts.txt";
    private static final String DEFAULT_PUBLISHED_BOOK = "../eepsite/docroot/hosts.txt";
    private static final String DEFAULT_SUBSCRIPTIONS = "subscriptions.txt";

    private final RouterContext _context;

    AddressBookFiles(RouterContext context) {
        _context = context;
    }

    File getAddressBookDir() {
        return new File(_context.getRouterDir(), "addressbook");
    }

    File getAddressBookConfigFile() {
        return new File(getAddressBookDir(), "config.txt");
    }

    Properties loadAddressBookConfig() throws IOException {
        Properties props = new OrderedProperties();
        File config = getAddressBookConfigFile();
        if (config.exists()) {
            DataHelper.loadProps(props, config);
        }
        if (props.getProperty("private_addressbook") == null) {
            props.setProperty("private_addressbook", DEFAULT_PRIVATE_BOOK);
        }
        String local = props.getProperty("master_addressbook");
        if (local != null) {
            props.setProperty("local_addressbook", local);
            props.remove("master_addressbook");
        }
        return props;
    }

    void storeAddressBookConfig(Properties props) throws IOException {
        DataHelper.storeProps(props, getAddressBookConfigFile());
    }

    File resolveConfiguredFile(String propertyName, String defaultValue) throws IOException {
        Properties props = loadAddressBookConfig();
        String configured = props.getProperty(propertyName, defaultValue);
        return new File(getAddressBookDir(), configured).getCanonicalFile();
    }

    File getPublishedAddressBook() throws IOException {
        return resolveConfiguredFile("published_addressbook", DEFAULT_PUBLISHED_BOOK);
    }

    File getSubscriptionsFile() throws IOException {
        return resolveConfiguredFile("subscriptions", DEFAULT_SUBSCRIPTIONS);
    }

    Properties loadPublishedEntries(File published) throws IOException {
        Properties props = new Properties();
        if (!published.exists()) {
            return props;
        }
        try (FileInputStream fis = new FileInputStream(published)) {
            props.load(fis);
        }
        return props;
    }

    boolean putPublishedEntry(File published, String hostname, Destination destination) throws IOException {
        Properties props = loadPublishedEntries(published);
        props.setProperty(hostname, destination.toBase64());
        return storePublishedEntries(published, props);
    }

    boolean removePublishedEntry(File published, String hostname) throws IOException {
        Properties props = loadPublishedEntries(published);
        Object removed = props.remove(hostname);
        if (removed == null) {
            return false;
        }
        return storePublishedEntries(published, props);
    }

    boolean storePublishedEntries(File published, Properties props) throws IOException {
        File parent = published.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(published)) {
            props.store(fos, null);
        }
        return true;
    }

    static void applyConfigUpdates(Properties props, Map<?, ?> updates) {
        for (Map.Entry<?, ?> entry : updates.entrySet()) {
            if (!(entry.getKey() instanceof String))
                continue;
            String key = ((String) entry.getKey()).trim();
            if (key.isEmpty())
                continue;
            Object value = entry.getValue();
            if (value == null) {
                props.remove(key);
            } else {
                props.setProperty(key, value.toString());
            }
        }
    }

    List<String> loadSubscriptions(File file) throws IOException {
        if (!file.exists()) {
            return Collections.emptyList();
        }
        List<String> rv = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = stripSubscriptionComments(line).trim();
                if (!line.isEmpty()) {
                    rv.add(line);
                }
            }
        }
        return rv;
    }

    void storeSubscriptions(File file, List<String> subscriptions) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create subscriptions parent directory");
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new SecureFileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String subscription : subscriptions) {
                writer.write(subscription);
                writer.newLine();
            }
        }
    }

    static String stripSubscriptionComments(String line) {
        if (line.startsWith(";")) {
            return "";
        }
        int hash = line.indexOf('#');
        if (hash >= 0) {
            return line.substring(0, hash);
        }
        return line;
    }

    static List<String> normalizeSubscriptions(Object raw) {
        if (raw instanceof List) {
            List<?> input = (List<?>) raw;
            List<String> rv = new ArrayList<>(input.size());
            for (Object item : input) {
                if (item == null)
                    continue;
                String line = stripSubscriptionComments(item.toString()).trim();
                if (!line.isEmpty()) {
                    rv.add(line);
                }
            }
            return rv;
        }
        if (raw instanceof String) {
            String[] lines = ((String) raw).split("\\r?\\n");
            List<String> rv = new ArrayList<>(lines.length);
            for (String line : lines) {
                line = stripSubscriptionComments(line).trim();
                if (!line.isEmpty()) {
                    rv.add(line);
                }
            }
            return rv;
        }
        return null;
    }

    static Map<String, String> toStringMap(Properties props) {
        Map<String, String> rv = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            rv.put((String) entry.getKey(), (String) entry.getValue());
        }
        return rv;
    }
}
