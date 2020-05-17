package com.justin.distribute.election.zab.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class PropertiesUtil {

    private PropertiesUtil() {}

    public static final PropertiesUtil getInstance() {
        return new PropertiesUtil();
    }

    public static Properties getProParms() {
        return PropertiesUtil.getInstance().getProParms("/zab/zab.properties");
    }

    public static Map<Integer, String> getNodesAddress() {
        Map<Integer, String> nodesAddress = new HashMap<>();
        for (int i=1; ; i++) {
            String address = getClusterNodesAddress(i);
            if (address == null || address.equals("")) {
                break;
            }
            nodesAddress.put(i, address);
        }
        return nodesAddress;
    }

    public static String getClusterNodesAddress(final int id) {
        return getProParms().getProperty("node."+id);
    }

    public static Integer getNodeId() {
        return Integer.parseInt(getProParms().getProperty("node.id"));
    }

    public static String getLogDir() {
        return getDataDir() + "log_" + getNodeId();
    }

    public static String getCommittedDir() {
        return getDataDir() + "committed_" + getNodeId();
    }

    public static String getDataDir() {
        return getProParms().getProperty("node.data.dir");
    }

    private Properties getProParms(String propertiesName) {
        InputStream is = getClass().getResourceAsStream(propertiesName);
        Properties prop = new Properties();
        try {
            prop.load(is);
        } catch (IOException e1) {
            e1.printStackTrace();
        }finally {
            if(is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return prop;
    }
}
