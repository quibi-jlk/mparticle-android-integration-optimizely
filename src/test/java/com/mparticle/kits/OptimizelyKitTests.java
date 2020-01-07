package com.mparticle.kits;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.UserAttributeListener;
import com.mparticle.commerce.Cart;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Impression;
import com.mparticle.commerce.Product;
import com.mparticle.commerce.Promotion;
import com.mparticle.commerce.TransactionAttributes;
import com.mparticle.consent.ConsentState;
import com.mparticle.identity.IdentityApi;
import com.mparticle.identity.MParticleUser;
import com.mparticle.mock.MockContext;
import com.mparticle.mock.MockKitConfiguration;
import com.mparticle.testutils.RandomUtils;
import com.mparticle.testutils.TestingUtils;
import com.optimizely.ab.android.sdk.OptimizelyClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class OptimizelyKitTests {
    RandomUtils randomUtils = new RandomUtils();

    private KitIntegration getKit() {
        return new OptimizelyKit();
    }

    @Before
    public void before() {
        MParticle mockMParticle = Mockito.mock(MParticle.class);
        IdentityApi mockIdentityApi = Mockito.mock(IdentityApi.class);
        MParticleUser mockUser = new EmptyMParticleUser();
        Mockito.when(mockMParticle.Identity()).thenReturn(mockIdentityApi);
        Mockito.when(mockMParticle.getEnvironment()).thenReturn(MParticle.Environment.Development);
        Mockito.when(mockIdentityApi.getCurrentUser()).thenReturn(mockUser);
        MParticle.setInstance(mockMParticle);
    }

    @Test
    public void testGetName() throws Exception {
        String name = getKit().getName();
        assertTrue(name != null && name.length() > 0);
    }

    /**
     * Kit *should* throw an exception when they're initialized with the wrong settings.
     */
    @Test
    public void testOnKitCreate() throws Exception {
        OptimizelyKit optimizelyKit = new OptimizelyKit();
        OptimizelyClient client = Mockito.mock(OptimizelyClient.class);
        Mockito.when(client.isValid()).thenReturn(true);
        OptimizelyKit.setOptimizelyClient(client);

        Map<String, String> minimalSettings = new HashMap<>();
        Exception e = null;
        minimalSettings.put("projectId", "2234");
        try {
            optimizelyKit.onKitCreate(minimalSettings, new MockContext());
        } catch (Exception ex) {
            e = ex;
        }
        assertNull(e);

        Map<String, String> badSettings = new HashMap<>();
        badSettings.put("projectId", "2234");
        badSettings.put(OptimizelyKit.DATAFILE_INTERVAL, "foo");
        badSettings.put(OptimizelyKit.EVENT_INTERVAL, "bar");
        try {
            optimizelyKit.onKitCreate(badSettings, new MockContext());
        } catch (Exception ex) {
            e = ex;
        }
        assertNull(e);

        Map<String, String> goodSettings = new HashMap<>();
        goodSettings.put("projectId", "2234");
        goodSettings.put(OptimizelyKit.DATAFILE_INTERVAL, "3");
        goodSettings.put(OptimizelyKit.EVENT_INTERVAL, "5");
        try {
            optimizelyKit.onKitCreate(goodSettings, new MockContext());
        } catch (Exception ex) {
            e = ex;
        }
        assertNull(e);
    }

    /**
     * Test that the correct value for userId is being set, based on the settings value. default userId
     * will be null
     */
    @Test
    public void testUserId() throws JSONException {
        Product product1 = new Product.Builder("product1", "1234", 0.0).build();
        Product product2 = new Product.Builder("product2", "9876", 1.0).build();
        Product product3 = new Product.Builder("product3", "3333", 300.0).build();
        final CommerceEvent commerceEvent = new CommerceEvent.Builder(Product.ADD_TO_CART, product1)
                .addProduct(product2)
                .addProduct(product3)
                .build();


        final Long mpid = new Random().nextLong();
        String customerId = randomUtils.getAlphaNumericString(20);
        String email = randomUtils.getAlphaNumericString(10);

        final Map<MParticle.IdentityType, String> identities = new HashMap<>();
        identities.put(MParticle.IdentityType.CustomerId, customerId);
        identities.put(MParticle.IdentityType.Email, email);

        MParticleUser user = new EmptyMParticleUser() {
            @NonNull
            @Override
            public Map<MParticle.IdentityType, String> getUserIdentities() {
                return identities;
            }

            @NonNull
            @Override
            public long getId() {
                return mpid;
            }
        };

        Mockito.when(MParticle.getInstance().Identity().getCurrentUser()).thenReturn(user);

        final Mutable<String> expectedUserId = new Mutable<String>("");
        final Mutable<Integer> count = new Mutable<>(0);
        final Mutable<JSONObject> settings = new Mutable<JSONObject>(new JSONObject());
        OptimizelyKit optimizelyKit = new OptimizelyKit() {
            @Override
            void logOptimizelyEvent(OptimizelyEvent trackEvent) {
                count.value++;
                assertEquals(expectedUserId.value, trackEvent.userId);
            }

            @Override
            public KitConfiguration getConfiguration() {
                try {
                    return MockKitConfiguration.createKitConfiguration(new JSONObject()
                            .put("id", MParticle.ServiceProviders.OPTIMIZELY)
                            .put("as", settings.value));
                } catch (JSONException e) {
                    return null;
                }
            }
        };

        Integer expectedEventCount = CommerceEventUtils.expand(commerceEvent).size();

        settings.value = new JSONObject().put(OptimizelyKit.USER_ID_FIELD_KEY, OptimizelyKit.USER_ID_EMAIL_VALUE);
        expectedUserId.value = email;
        optimizelyKit.logEvent(commerceEvent);
        assertEquals(expectedEventCount, count.value);
        count.value = 0;

        settings.value = new JSONObject().put(OptimizelyKit.USER_ID_FIELD_KEY, OptimizelyKit.USER_ID_CUSTOMER_ID_VALUE);
        expectedUserId.value = customerId;
        optimizelyKit.logEvent(commerceEvent);
        assertEquals(expectedEventCount, count.value);
        count.value = 0;

        settings.value = new JSONObject().put(OptimizelyKit.USER_ID_FIELD_KEY, OptimizelyKit.USER_ID_MPID_VALUE);
        expectedUserId.value = String.valueOf(mpid);
        optimizelyKit.logEvent(commerceEvent);
        assertEquals(expectedEventCount, count.value);
        count.value = 0;

        String das = UUID.randomUUID().toString();
        Mockito.when(MParticle.getInstance().Identity().getDeviceApplicationStamp()).thenReturn(das);
        settings.value = new JSONObject().put(OptimizelyKit.USER_ID_FIELD_KEY, OptimizelyKit.USER_ID_DAS_VALUE);
        expectedUserId.value = das;
        optimizelyKit.logEvent(commerceEvent);
        assertEquals(expectedEventCount, count.value);
        count.value = 0;

        //test default, should be das
        settings.value = new JSONObject();
        expectedUserId.value = das;
        optimizelyKit.logEvent(commerceEvent);
        //Don't log events if there is no userId type present
        assertEquals(3, count.value.longValue());
        count.value = 0;


        Mockito.when(MParticle.getInstance().Identity().getCurrentUser()).thenReturn(null);
        //test default when no user is present, default to das
        settings.value = new JSONObject();
        expectedUserId.value = das;
        optimizelyKit.logEvent(new MPEvent.Builder("an event", MParticle.EventType.Location).build());
        optimizelyKit.logEvent(commerceEvent);
        //log events with das if there is no userId type present
        assertEquals(4, count.value.longValue());

    }

    /**
     * Test that when the Client is explicitly set via the static getClient() method, the Kit will not
     * override it, even if a client request is in progress
     */
    @Test
    public void testClientSetClientNotOverriden() {
        OptimizelyClient optimizelyClient = Mockito.mock(OptimizelyClient.class);
        OptimizelyKit.setOptimizelyClient(optimizelyClient);

        OptimizelyClient rejectedClient = Mockito.mock(OptimizelyClient.class);
        Mockito.when(rejectedClient.isValid()).thenReturn(true);
        OptimizelyKit optimizelyKit = new OptimizelyKit();
        optimizelyKit.onStart(rejectedClient);

        assertTrue(optimizelyClient == OptimizelyKit.getOptimizelyClient());

        OptimizelyKit.setOptimizelyClient(null);

        optimizelyKit.onStart(rejectedClient);

        assertTrue(rejectedClient == OptimizelyKit.getOptimizelyClient());
    }

    @Test
    public void testLogEventNoCustom() {
        MPEvent event = new MPEvent.Builder("An event", MParticle.EventType.Location).build();
        final Mutable<Boolean> called = new Mutable<>(false);
        OptimizelyKit optimizelyKit = new MockOptimizelyKit() {
            @Override
            void logOptimizelyEvent(OptimizelyEvent trackEvent) {
                if (called.value) {
                    fail("multiple events, expecting 1");
                }
                assertEquals(trackEvent.eventName, "An event");
                assertNull(trackEvent.eventAttributes);
                called.value = true;
            }
        };
        optimizelyKit.logEvent(event);
        assertTrue(called.value);
    }


    /**
     * test that all Product custom attributes are properly translated into Optimizely event attributes
     * test that default event name is correct
     */
    @Test
    public void testCommerceEventNoCustom() {
        final Map<String, String> userAttributes = randomUtils.getRandomAttributes(4);
        MParticleUser user = new EmptyMParticleUser() {
            @Nullable
            @Override
            public Map<String, Object> getUserAttributes(@Nullable UserAttributeListener userAttributeListener) {
                userAttributeListener.onUserAttributesReceived(userAttributes, new HashMap<String, List<String>>(), 1L);
                return null;
            }
        };
        Mockito.when(MParticle.getInstance().Identity().getCurrentUser()).thenReturn(user);


        Product product1 = new Product.Builder("product1", "1234", 0.0).build();
        Product product2 = new Product.Builder("product2", "9876", 1.0).build();
        Product product3 = new Product.Builder("product3", "3333", 300.0).build();

        final CommerceEvent commerceEvent = new CommerceEvent.Builder(Product.ADD_TO_CART, product1)
        .addProduct(product2)
        .addProduct(product3)
        .build();

        int expectedCount = CommerceEventUtils.expand(commerceEvent).size();

        final Mutable<Integer> count = new Mutable<>(0);
        OptimizelyKit optimizelyKit = new MockOptimizelyKit() {
            @Override
            void logOptimizelyEvent(OptimizelyEvent trackEvent) {
                count.value++;
                assertEquals(String.format("eCommerce - %s - Item", commerceEvent.getProductAction()), trackEvent.eventName);
                assertEquals(userAttributes, trackEvent.userAttributes);
            }
        };
        optimizelyKit.logEvent(commerceEvent);
        assertEquals(expectedCount, count.value.longValue());
    }

    /**
     * test that the "revenue" reserved keyword is being populated when a CommerceEvent has revenue
     */
    @Test
    public void testCommerceEventRevenue() {
        final Map<String, String> userAttributes = randomUtils.getRandomAttributes(4);
        MParticleUser user = new EmptyMParticleUser() {
            @Nullable
            @Override
            public Map<String, Object> getUserAttributes(@Nullable UserAttributeListener userAttributeListener) {
                userAttributeListener.onUserAttributesReceived(userAttributes, new HashMap<String, List<String>>(), 1L);
                return null;
            }
        };
        Mockito.when(MParticle.getInstance().Identity().getCurrentUser()).thenReturn(user);

        Map<String, String> customAttributes = randomUtils.getRandomAttributes(3);
        customAttributes.put(OptimizelyKit.OPTIMIZELY_EVENT_NAME, "myCustomName1");

        Map<String, String> customAttributes1 = randomUtils.getRandomAttributes(5);
        customAttributes1.put(OptimizelyKit.OPTIMIZELY_EVENT_NAME, "myCustomName2");

        Product product1 = new Product.Builder("product1", "1234", 0.0).customAttributes(customAttributes).build();
        Product product2 = new Product.Builder("product2", "9876", 1.0).customAttributes(customAttributes1).build();
        Product product3 = new Product.Builder("product3", "3333", 300.0).build();
        TransactionAttributes transactionAttributes = new TransactionAttributes("999").setRevenue(45.5);
        final CommerceEvent commerceEvent = new CommerceEvent.Builder(Product.PURCHASE, product1)
                .transactionAttributes(transactionAttributes)
                .addProduct(product2)
                .addProduct(product3)
                .build();

        final Mutable<Integer> count = new Mutable<>(0);
        final Mutable<Boolean> revenueEventFound = new Mutable<>(false);
        OptimizelyKit optimizelyKit = new MockOptimizelyKit() {
            @Override
            void logOptimizelyEvent(OptimizelyEvent trackEvent) {
                count.value++;
                if (String.format(CommerceEventUtils.PLUSONE_NAME, commerceEvent.getProductAction()).equals(trackEvent.eventName)) {
                    //make sure it is our revenue * 100 (we our dollars, they are cents)
                    assertEquals((Integer)(trackEvent.eventAttributes.get("revenue")), 4550, 0.0);
                    assertFalse(revenueEventFound.value);
                    revenueEventFound.value = true;
                } else {
                    assertEquals(String.format("eCommerce - %s - Item", commerceEvent.getProductAction()), trackEvent.eventName);
                }
                assertEquals(userAttributes, trackEvent.userAttributes);
            }
        };
        optimizelyKit.logEvent(commerceEvent);
        assertEquals(CommerceEventUtils.expand(commerceEvent).size(), count.value.longValue());
        assertTrue(revenueEventFound.value);
    }

    /**
     * Ensure that the custom name flag is being applied when it should be
     */
    @Test
    public void testCommerceEventProductCustomName() {
        final String eventName = "custom name";
        Product product1 = new Product.Builder("product1", "1234", 0.0).build();
        TransactionAttributes transactionAttributes = new TransactionAttributes("999").setRevenue(20.5);

        final CommerceEvent commerceEvent = new CommerceEvent.Builder(Product.PURCHASE, product1)
                .transactionAttributes(transactionAttributes)
                .addCustomFlag(OptimizelyKit.OPTIMIZELY_EVENT_NAME, eventName)
                .build();

        final Mutable<Integer> count = new Mutable<>(0);
        final Mutable<Boolean> customNameFound = new Mutable<>(false);
        OptimizelyKit optimizelyKit = new MockOptimizelyKit() {
            @Override
            void logOptimizelyEvent(OptimizelyEvent trackEvent) {
                count.value++;
                if (trackEvent.eventName.equals(eventName)) {
                    assertEquals((Integer)(trackEvent.eventAttributes.get("revenue")), 2050, 0);
                    assertFalse(customNameFound.value);
                    customNameFound.value = true;
                }
            }
        };

        optimizelyKit.logEvent(commerceEvent);
        assertEquals(CommerceEventUtils.expand(commerceEvent).size(), count.value.longValue());
        assertTrue(customNameFound.value);
    }

    @Test
    public void testCustomValue() {
        MPEvent randomEvent = new MPEvent.Builder(TestingUtils.getInstance().getRandomMPEventRich())
                .addCustomFlag(OptimizelyKit.OPTIMIZELY_VALUE_KEY, "40.1")
                .build();
        final Mutable<Boolean> eventFound = new Mutable<>(false);
        OptimizelyKit optimizelyKit = new MockOptimizelyKit() {
            @Override
            void logOptimizelyEvent(OptimizelyEvent trackEvent) {
                assertFalse(eventFound.value);
                eventFound.value = true;
                assertEquals((Double)trackEvent.eventAttributes.get("value"), 40.1, 0.0);
            }
        };

        optimizelyKit.logEvent(randomEvent);
        assertTrue(eventFound.value);
    }

    /**
     * Make sure the queueing is working. When the OptimizelyClient is not present, we should be queueing
     * events. The queue should be emptied when the OptimizelyClient becomes available
     */
    @Test
    public void testQueueWorking() {
        final Map<String, String> userAttributes = randomUtils.getRandomAttributes(4);
        MParticleUser user = new EmptyMParticleUser() {
            @Nullable
            @Override
            public Map<String, Object> getUserAttributes(@Nullable UserAttributeListener userAttributeListener) {
                userAttributeListener.onUserAttributesReceived(userAttributes, new HashMap<String, List<String>>(), 1L);
                return null;
            }
        };
        Mockito.when(MParticle.getInstance().Identity().getCurrentUser()).thenReturn(user);


        Product product1 = new Product.Builder("product1", "1234", 0.0).build();
        Product product2 = new Product.Builder("product2", "9876", 1.0).build();
        Product product3 = new Product.Builder("product3", "3333", 300.0).build();
        final CommerceEvent commerceEvent = new CommerceEvent.Builder(Product.ADD_TO_CART, product1)
                .build();

        CommerceEvent commerceEvent1 = new CommerceEvent.Builder(Product.CLICK, product2)
                .addProduct(product3)
                .build();

        OptimizelyKit optimizelyKit = new MockOptimizelyKit();
        optimizelyKit.logEvent(commerceEvent);
        optimizelyKit.logEvent(commerceEvent1);

        int expectedEvents = CommerceEventUtils.expand(commerceEvent).size() + CommerceEventUtils.expand(commerceEvent1).size();

        assertEquals(expectedEvents, optimizelyKit.mEventQueue.size());

        OptimizelyClient optimizelyClient = Mockito.mock(OptimizelyClient.class);
        Mockito.when(optimizelyClient.isValid()).thenReturn(false);

        optimizelyKit.onStart(optimizelyClient);

        //Events should NOT be dequeued if the OptimizelyClient is not valid
        assertEquals(expectedEvents, optimizelyKit.mEventQueue.size());
        int count = invocationCount(optimizelyClient, "track");
        assertEquals(0, count);

        Mockito.when(optimizelyClient.isValid()).thenReturn(true);
        optimizelyKit.onStart(optimizelyClient);
        verify(optimizelyClient, times(expectedEvents)).track(Mockito.anyString(), Mockito.anyString(), Mockito.any(Map.class), Mockito.any(Map.class));
        count = invocationCount(optimizelyClient, "track");
        assertEquals(expectedEvents, count);
        assertEquals(0, optimizelyKit.mEventQueue.size());
    }

    @Test
    public void testCustomUserId() {
        MPEvent event = new MPEvent.Builder("Event Name", MParticle.EventType.Search).addCustomFlag(OptimizelyKit.OPTIMIZELY_USER_ID, "44").build();

        final Mutable<Integer> count = new Mutable<>(0);
        OptimizelyKit optimizelyKit = new MockOptimizelyKit() {
            @Override
            String getUserId(MParticleUser user) {
                return "should not be this userId";
            }

            @Override
            void logOptimizelyEvent(OptimizelyEvent trackEvent) {
                count.value++;
                assertEquals("44", trackEvent.userId);
            }
        };

        optimizelyKit.logEvent(event);
        assertEquals(1, count.value.intValue());
        count.value = 0;

        Product product = new Product.Builder("My Product", "12345", 1.0).build();
        Product product1 = new Product.Builder("My Other Product", "2356", 2.0).build();
        Impression impression = new Impression("name", product).addProduct(product1);
        TransactionAttributes transactionAttributes = new TransactionAttributes("123").setRevenue(43.2);
        CommerceEvent commerceEvent = new CommerceEvent.Builder(Product.DETAIL, product)
                .addProduct(product1)
                .addImpression(impression)
                .addCustomFlag(OptimizelyKit.OPTIMIZELY_USER_ID, "44")
                .transactionAttributes(transactionAttributes)
//                .addPromotion(promotion)
                .build();

        optimizelyKit.logEvent(commerceEvent);
        assertEquals(4, count.value.intValue());
    }

    private int invocationCount(Object object, String methodName) {
        Collection<Invocation> invocationList = Mockito.mockingDetails(object).getInvocations();

        int invocationCount = 0;
        for (Invocation invocation : invocationList) {
            if (invocation.getMethod().getName().equals(methodName)) {
                invocationCount++;
            }
        }
        return invocationCount;
    }

    class MockOptimizelyKit extends OptimizelyKit {

        @Override
        public KitConfiguration getConfiguration() {
            try {
                return MockKitConfiguration.createKitConfiguration(new JSONObject()
                        .put("id", MParticle.ServiceProviders.OPTIMIZELY)
                        .put("as", new JSONObject().put(OptimizelyKit.USER_ID_FIELD_KEY, OptimizelyKit.USER_ID_MPID_VALUE)));
            } catch (JSONException e) {
                return null;
            }
        }

    }

    public static class Mutable<T> {
        public T value;
        public Mutable(T value) {
            this.value = value;
        }
    }

    class EmptyMParticleUser implements MParticleUser {

        @NonNull
        @Override
        public long getId() {
            return 1L;
        }

        @NonNull
        @Override
        public Cart getCart() {
            return null;
        }

        @NonNull
        @Override
        public Map<String, Object> getUserAttributes() {
            return new HashMap<>();
        }

        @Nullable
        @Override
        public Map<String, Object> getUserAttributes(@Nullable UserAttributeListener userAttributeListener) {
            userAttributeListener.onUserAttributesReceived(new HashMap<String, String>(), new HashMap<String, List<String>>(), getId());
            return null;
        }

        @Override
        public boolean setUserAttributes(@NonNull Map<String, Object> map) {
            return false;
        }

        @NonNull
        @Override
        public Map<MParticle.IdentityType, String> getUserIdentities() {
            return null;
        }

        @Override
        public boolean setUserAttribute(@NonNull String s, @NonNull Object o) {
            return false;
        }

        @Override
        public boolean setUserAttributeList(@NonNull String s, @NonNull Object o) {
            return false;
        }

        @Override
        public boolean incrementUserAttribute(@NonNull String s, int i) {
            return false;
        }

        @Override
        public boolean removeUserAttribute(@NonNull String s) {
            return false;
        }

        @Override
        public boolean setUserTag(@NonNull String s) {
            return false;
        }

        @NonNull
        @Override
        public ConsentState getConsentState() {
            return null;
        }

        @Override
        public void setConsentState(@Nullable ConsentState consentState) {

        }

        @Override
        public boolean isLoggedIn() {
            return false;
        }

        @Override
        public long getFirstSeenTime() {
            return 0;
        }

        @Override
        public long getLastSeenTime() {
            return 0;
        }
    }
}