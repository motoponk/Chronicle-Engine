/*
 * Copyright 2016 higherfrequencytrading.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.IOTools;
import net.openhft.chronicle.engine.ThreadMonitoringTest;
import net.openhft.chronicle.engine.api.map.KeyValueStore;
import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import net.openhft.chronicle.engine.cfg.ChronicleMapCfg;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.wire.TextWire;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.YamlLogging;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(value = Parameterized.class)
public class TestInsertUpdateChronicleMapViewOnServer extends ThreadMonitoringTest {

    @NotNull
    public String connection = "RemoteSubscriptionTest.host.port";

    private AssetTree clientAssetTree;
    private VanillaAssetTree serverAssetTree;
    private ServerEndpoint serverEndpoint;
    private WireType wireType;

    public TestInsertUpdateChronicleMapViewOnServer(WireType wireType) {
        this.wireType = wireType;
    }

    @NotNull
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        @NotNull final List<Object[]> list = new ArrayList<>();
        list.add(new Object[]{WireType.BINARY});
        list.add(new Object[]{WireType.TEXT});
        return list;
    }

    @Before
    public void before() throws IOException {
        IOTools.deleteDirWithFiles("data");
        serverAssetTree = new VanillaAssetTree().forTesting();

        YamlLogging.setAll(false);

        connection = "TestInsertUpdateChronicleMapView.host.port";
        TCPRegistry.createServerSocketChannelFor(connection);
        serverEndpoint = new ServerEndpoint(connection, serverAssetTree, "cluster");

        serverAssetTree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore",
                VanillaMapView::new, KeyValueStore.class);

        serverAssetTree.root().addLeafRule(KeyValueStore.class, "use Chronicle Map", this::createMap);

        clientAssetTree = new VanillaAssetTree().forRemoteAccess(connection, wireType);

    }

    private <K, V> AuthenticatedKeyValueStore<K, V> createMap(RequestContext requestContext, Asset asset) {
        return new ChronicleMapKeyValueStore<>(createConfig(requestContext), asset);
    }

    private <K, V> ChronicleMapCfg<K, V> createConfig(RequestContext requestContext) {
        ChronicleMapCfg cfg = (ChronicleMapCfg) TextWire.from("!ChronicleMapCfg {\n" +
                "      entries: 10000,\n" +
                "      keyClass: !type String,\n" +
                "      valueClass: !type String,\n" +
                "      exampleKey: \"some_key\",\n" +
                "      exampleValue: \"some_value\",\n" +
                "      mapFileDataDirectory: data/mapData,\n" +
                "    }").readObject();

        cfg.name(requestContext.fullName());
        return cfg;
    }

    @Override
    public void preAfter() {
        clientAssetTree.close();
        Jvm.pause(100);
        serverEndpoint.close();
        if (serverEndpoint != null)
            serverEndpoint.close();
        serverAssetTree.close();

        IOTools.deleteDirWithFiles("data");
    }

    @Test
    public void testInsertFollowedByUpdate() throws InterruptedException {

        @NotNull final MapView<String, String> serverMap = serverAssetTree.acquireMap
                ("testInsertFollowedByUpdateServer?putReturnsNull=false",
                        String.class, String
                                .class);

        @NotNull final BlockingQueue<MapEvent> events = new ArrayBlockingQueue<>(5);
        clientAssetTree.registerSubscriber("testInsertFollowedByUpdateServer?putReturnsNull=false", MapEvent.class,
                events::add);

        {
            serverMap.put("hello", "world");
            final MapEvent event = events.poll(5, SECONDS);
            assertTrue(event instanceof InsertedEvent);
            // sometimes the event is duplicated
            events.clear();
        }
        {
            serverMap.put("hello", "world2");
            final MapEvent event = events.poll(5, SECONDS);
            assertTrue(event instanceof UpdatedEvent);
            assertNull(events.poll(500, MILLISECONDS));
        }
    }

    @Test
    public void testInsertFollowedByUpdateWhenPutReturnsNullTrue() throws InterruptedException {

        @NotNull final MapView<String, String> serverMap = serverAssetTree.acquireMap
                ("testInsertFollowedByUpdateWhenPutReturnsNullTrueServer?putReturnsNull=true",
                        String.class, String
                                .class);

        @NotNull final BlockingQueue<MapEvent> events = new ArrayBlockingQueue<>(1);
        clientAssetTree.registerSubscriber("testInsertFollowedByUpdateWhenPutReturnsNullTrueServer?putReturnsNull=true", MapEvent.class,
                events::add);

        Jvm.pause(500);

        {
            serverMap.put("hello", "world");
            final MapEvent event = events.poll(5, SECONDS);
            assertTrue(event instanceof InsertedEvent);
            assertNull(events.poll(500, MILLISECONDS));
        }
        {
            serverMap.put("hello", "world2");
            final MapEvent event = events.poll(5, SECONDS);
            assertTrue(event instanceof UpdatedEvent);
            assertNull(events.poll(500, MILLISECONDS));
        }
    }
}
