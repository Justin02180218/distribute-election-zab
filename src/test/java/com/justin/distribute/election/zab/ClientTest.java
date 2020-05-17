package com.justin.distribute.election.zab;

import com.justin.distribute.election.zab.client.Client;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class ClientTest {
    private Client client;

    @Before
    public void setUp() {
        client = Client.getInstance();
    }

    @After
    public void tearDown() {
//        Client.shutdown();
    }

    @Test
    public void testKVPut() {
        String key = "election";
        String value = "ZAB";
        boolean flag = client.put(key, value);
        System.out.println("Put-------====>flag: " + flag);
    }

    @Test
    public void testKVGet() {
        String key = "election";
        String value = client.get(key);
        System.out.println("Get-------====>value: " + value);
    }
}
