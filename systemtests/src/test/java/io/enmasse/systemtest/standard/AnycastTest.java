/*
 * Copyright 2016-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.systemtest.standard;

import io.enmasse.systemtest.Destination;
import io.enmasse.systemtest.ability.ITestBaseStandard;
import io.enmasse.systemtest.amqp.AmqpClient;
import io.enmasse.systemtest.bases.TestBaseWithShared;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AnycastTest extends TestBaseWithShared implements ITestBaseStandard {

    public static void runAnycastTest(Destination dest, AmqpClient... clients) throws InterruptedException, TimeoutException, IOException, ExecutionException {
        if (clients.length == 0) {
            throw new IllegalStateException("Clients are required for this test");
        }
        List<String> msgs = new ArrayList<>();
        for (int i = 0; i < clients.length; i++) {
            msgs.add("message-anycast-" + i);
        }
        List<Future<List<Message>>> received = new ArrayList<>();
        for (AmqpClient client : clients) {
            received.add(client.recvMessages(dest.getAddress(), 1));
        }
        Future<Integer> sendResult = clients[0].sendMessages(dest.getAddress(), msgs);
        assertThat("Wrong count of messages sent", sendResult.get(1, TimeUnit.MINUTES), is(msgs.size()));
        for (int i = 0; i < received.size(); i++) {
            assertThat("Wrong count of messages received: receiver" + i,
                    received.get(i).get(1, TimeUnit.MINUTES).size(), is(1));
        }
    }

    @Test
    void testMultipleReceivers() throws Exception {
        Destination dest = Destination.anycast("anycastMultipleReceivers");
        setAddresses(dest);
        AmqpClient client1 = amqpClientFactory.createQueueClient();
        AmqpClient client2 = amqpClientFactory.createQueueClient();
        AmqpClient client3 = amqpClientFactory.createQueueClient();

        runAnycastTest(dest, client1, client2, client3);
    }

    @Test
    void testRestApi() throws Exception {
        Destination a1 = Destination.anycast("anycastRest1");
        Destination a2 = Destination.anycast("anycastRest2");

        runRestApiTest(sharedAddressSpace, a1, a2);
    }

    @Test
    void testScaleRouterAutomatically() throws Exception {
        //deploy addresses
        ArrayList<Destination> dest = new ArrayList<>();
        int destCount = 210;
        for (int i = 0; i < destCount; i++) {
            dest.add(Destination.anycast("medium-anycast-" + i, "standard-medium-anycast"));//router credit = 0.01 => 210 * 0.01 = 2.1 pods
        }
        setAddresses(dest.toArray(new Destination[0]));
//        TODO once getAddressPlanConfig() method will be implemented
//        double requiredCredit = getAddressPlanConfig(DestinationPlan.STANDARD_SMALL_ANYCAST.plan()).getRequiredCreditFromResource("router");
//        int replicasCount = (int) (destCount * requiredCredit);
//        waitForBrokerReplicas(sharedAddressSpace, dest.get(0), replicasCount);

        waitForRouterReplicas(sharedAddressSpace, 3);

        //simple send/receive
        AmqpClient client1 = amqpClientFactory.createQueueClient();
        AmqpClient client2 = amqpClientFactory.createQueueClient();
        for (int i = 0; i < destCount; i = i + 5) {
            runAnycastTest(dest.get(i), client1, client2);
        }

        //remove part of destinations
        int removeCount = 120;
        deleteAddresses(dest.subList(0, removeCount).toArray(new Destination[0])); //router credit =>2.1-1.2 => max(2, 0.90 pods + dummy-address in special case)
        waitForRouterReplicas(sharedAddressSpace, 2);

        //simple send/receive
        for (int i = removeCount; i < destCount; i = i + 3) {
            runAnycastTest(dest.get(i), client1, client2);
        }

        //remove all destinations
        setAddresses();
        waitForRouterReplicas(sharedAddressSpace, 2);
    }
}
