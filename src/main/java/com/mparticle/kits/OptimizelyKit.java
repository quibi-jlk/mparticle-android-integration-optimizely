package com.mparticle.kits;


import android.content.Context;
import android.support.annotation.Nullable;

import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.UserAttributeListener;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.identity.MParticleUser;
import com.mparticle.internal.Logger;
import com.mparticle.internal.MPUtility;
import com.optimizely.ab.android.sdk.OptimizelyClient;
import com.optimizely.ab.android.sdk.OptimizelyManager;
import com.optimizely.ab.android.sdk.OptimizelyStartListener;
import com.optimizely.ab.config.Variation;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class OptimizelyKit extends KitIntegration implements KitIntegration.EventListener, KitIntegration.CommerceListener, OptimizelyStartListener {
    private static boolean providedClient = false;
    private static OptimizelyClient mOptimizelyClient;
    private static List<OptimizelyClientListener> mStartListeners = new ArrayList<>();

    protected final Queue<OptimizelyEvent> mEventQueue = new LinkedList<>();
    final static String USER_ID_FIELD_KEY = "userIdField";
    final static String EVENT_INTERVAL = "eventInterval";
    final static String DATAFILE_INTERVAL = "datafileInterval";
    final static String PROJECT_ID = "projectId";

    final static String USER_ID_CUSTOMER_ID_VALUE = "customerId";
    final static String USER_ID_EMAIL_VALUE = "email";
    final static String USER_ID_MPID_VALUE = "mpid";
    final static String USER_ID_DAS_VALUE = "deviceApplicationStamp";

    final public static String OPTIMIZELY_VALUE_KEY = "Optimizely.Value";
    final public static String OPTIMIZELY_EVENT_NAME = "Optimizely.EventName";
    final public static String OPTIMIZELY_USER_ID = "Optimizely.UserId";

    @Override
    public String getName() {
        return "Optimizely";
    }

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> map, Context context) throws IllegalArgumentException {

        String sdkKey = map.get(PROJECT_ID);
        Long eventInterval = tryParse(map.get(EVENT_INTERVAL));
        Long datafileDownloadInterval = tryParse(map.get(DATAFILE_INTERVAL));

        if (!providedClient && (mOptimizelyClient == null || !mOptimizelyClient.isValid())) {
            OptimizelyManager.Builder builder = OptimizelyManager.builder()
                    .withSDKKey(sdkKey);
            if (eventInterval != null) {
                builder.withEventDispatchInterval(eventInterval);
            }
            if (datafileDownloadInterval != null) {
                builder.withDatafileDownloadInterval(datafileDownloadInterval);
            }
            OptimizelyManager optimizelyManager = builder.build(context);

            optimizelyManager.initialize(context, null, this);
        }
        return null;
    }

    @Nullable
    public static OptimizelyClient getOptimizelyClient() {
        return mOptimizelyClient;
    }

    /**
     * Add a single use callback for Optimizely Client startup. Your listener will be automatically
     * removed after it is invoked. To remove it earlier, simply null our your reference
     * @param startListener
     */
    public static void getOptimizelyClient(OptimizelyClientListener startListener) {
        if (mOptimizelyClient != null) {
            startListener.onOptimizelyClientAvailable(mOptimizelyClient);
        } else {
            mStartListeners.add(startListener);
        }
    }

    public static void setOptimizelyClient(OptimizelyClient optimizelyClient) {
        mOptimizelyClient = optimizelyClient;
        providedClient = optimizelyClient != null;
    }

    @Override
    public List<ReportingMessage> setOptOut(boolean b) {
        return null;
    }

    @Override
    public List<ReportingMessage> leaveBreadcrumb(String s) {
        return null;
    }

    @Override
    public List<ReportingMessage> logError(String s, Map<String, String> map) {
        return null;
    }

    @Override
    public List<ReportingMessage> logException(Exception e, Map<String, String> map, String s) {
        return null;
    }

    @Override
    public List<ReportingMessage> logEvent(MPEvent mpEvent) {
        MParticleUser user = getCurrentUser();
        String customUserId = null;
        String valueString = null;
        if (mpEvent.getCustomFlags() != null) {
            List<String> valueList = mpEvent.getCustomFlags().get(OPTIMIZELY_VALUE_KEY);
            if (valueList != null && valueList.size() > 0) {
                valueString = valueList.get(0);
            }
            List<String> userIdList = mpEvent.getCustomFlags().get(OPTIMIZELY_USER_ID);
            if (userIdList != null && userIdList.size() > 0) {
                customUserId = userIdList.get(0);
            }
        }
        final String optimizelyCustomUserId = customUserId;
        final String optimizelyValueString = valueString;
        boolean eventCreated = getOptimizelyEvent(mpEvent, user, new OptimizelyEventCallback() {
            @Override
            public void onOptimizelyEventCreated(OptimizelyEvent optimizelyEvent) {
                if (!MPUtility.isEmpty(optimizelyValueString)) {
                    try {
                        Double value = Double.parseDouble(optimizelyValueString);
                        optimizelyEvent.addEventAttribute("value", value);
                        Logger.debug(String.format("Applying custom value: \"%s\" to Optimizely Event based on customFlag", String.valueOf(value)));
                    } catch (NumberFormatException ex) {
                        Logger.error(String.format("Unable to log Optimizely Value \"%s\", failed to parse as a Double", optimizelyValueString));
                    }
                }
                if (!MPUtility.isEmpty(optimizelyCustomUserId)) {
                    optimizelyEvent.userId = optimizelyCustomUserId;
                    Logger.debug(String.format("Applying custom userId: \"%s\" to Optimizely Event based on customFlag", optimizelyCustomUserId));
                }
                logOptimizelyEvent(optimizelyEvent);
            }
        });
        if (!eventCreated) {
            return null;
        } else {
            return Collections.singletonList(ReportingMessage.fromEvent(this, mpEvent));
        }

    }


    @Override
    public List<ReportingMessage> logScreen(String s, Map<String, String> map) {
        return null;
    }

    @Override
    public List<ReportingMessage> logLtvIncrease(BigDecimal bigDecimal, BigDecimal bigDecimal1, String s, Map<String, String> map) {
        return null;
    }

    @Override
    public List<ReportingMessage> logEvent(final CommerceEvent commerceEvent) {
        MParticleUser user = getCurrentUser();
        String customEventName = null;
        String customUserId = null;
        if (commerceEvent.getCustomFlags() != null) {
            List<String> eventNameFlags = commerceEvent.getCustomFlags().get(OPTIMIZELY_EVENT_NAME);
            List<String>userIdFlags = commerceEvent.getCustomFlags().get(OPTIMIZELY_USER_ID);
            if (eventNameFlags != null) {
                customEventName = eventNameFlags.get(0);
            }
            if (userIdFlags != null) {
                customUserId = userIdFlags.get(0);
            }
        }

        List<MPEvent> events = CommerceEventUtils.expand(commerceEvent);
        final String optimizelyCustomEventName = customEventName;
        final String optimizelyCustomUserId = customUserId;
        for (final MPEvent event: events) {
            getOptimizelyEvent(event, user, new OptimizelyEventCallback() {
                @Override
                public void onOptimizelyEventCreated(OptimizelyEvent optimizelyEvent) {
                    //If the event is a Purchase or Refund expanded event
                    if (commerceEvent.getProductAction() != null && event.getEventName().equals(String.format(CommerceEventUtils.PLUSONE_NAME, commerceEvent.getProductAction()))) {
                        //parse and apply the "revenue"
                        String totalAmountString = event.getInfo().get(CommerceEventUtils.Constants.ATT_TOTAL);
                        if (!MPUtility.isEmpty(totalAmountString)) {
                            try {
                                Double totalAmount = Double.valueOf(totalAmountString);
                                if (totalAmount != null) {
                                    Integer revenueInCents = Double.valueOf(totalAmount * 100).intValue();
                                    optimizelyEvent.eventAttributes.put("revenue", revenueInCents);
                                    Logger.debug(String.format("Applying revenue: \"%s\" to Optimizely Event based on transactionAttributes", revenueInCents));
                                }
                            } catch (NumberFormatException ex) {
                                Logger.error("Unable to parse Revenue value");
                            }
                        }
                        //And apply the custom name, if there is one
                        if (optimizelyCustomEventName != null) {
                            optimizelyEvent.eventName = optimizelyCustomEventName;
                            Logger.debug(String.format("Applying custom eventName: \"%s\" to Optimizely Event based on customFlag", optimizelyCustomEventName));
                        }
                    }
                    //Apply customId, if there is one, to all expanded events
                    if (optimizelyCustomUserId != null) {
                        optimizelyEvent.userId = optimizelyCustomUserId;
                        Logger.debug(String.format("Applying custom userId: \"%s\" to Optimizely Event based on customFlag", optimizelyCustomUserId));
                    }
                    logOptimizelyEvent(optimizelyEvent);
                }
            });
        }
        return Collections.singletonList(ReportingMessage.fromEvent(this, commerceEvent));
    }

    @Override
    protected void onKitDestroy() {
        super.onKitDestroy();
        mOptimizelyClient = null;
        mStartListeners = null;
    }

    @Override
    public void onStart(OptimizelyClient optimizelyClient) {
        //check providedClient, so we don't override a client that the was set explicitly
        if (!providedClient && optimizelyClient != null && optimizelyClient.isValid()) {
            mOptimizelyClient = optimizelyClient;
            if (mStartListeners != null) {
                for (OptimizelyClientListener listener : mStartListeners) {
                    try {
                        if (listener != null) {
                            listener.onOptimizelyClientAvailable(mOptimizelyClient);
                        }
                    } catch (Exception e) {

                    }
                }
            }
            mStartListeners.clear();
            replayQueue();
        }
    }

    void logOptimizelyEvent(OptimizelyEvent trackEvent) {
        if (mOptimizelyClient != null && mOptimizelyClient.isValid()) {
            if (trackEvent.eventAttributes == null) {
                mOptimizelyClient.track(trackEvent.eventName, trackEvent.userId, trackEvent.userAttributes);
            } else {
                mOptimizelyClient.track(trackEvent.eventName, trackEvent.userId, trackEvent.userAttributes, trackEvent.eventAttributes);
            }
        } else {
            queueEvent(trackEvent);
        }
    }

    String getUserId(MParticleUser user) {
        String userId = null;
        if (user != null) {
            String userIdField = getSettings().get(USER_ID_FIELD_KEY);
            if (USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(userIdField)) {
                userId = user.getUserIdentities().get(MParticle.IdentityType.CustomerId);
            } else if (USER_ID_EMAIL_VALUE.equalsIgnoreCase(userIdField)) {
                userId = user.getUserIdentities().get(MParticle.IdentityType.Email);
            } else if (USER_ID_MPID_VALUE.equalsIgnoreCase(userIdField)) {
                userId = Long.toString(user.getId());
            } else if (USER_ID_DAS_VALUE.equalsIgnoreCase(userIdField)) {
                userId = MParticle.getInstance().Identity().getDeviceApplicationStamp();
            }
        }
        if (userId == null) {
            userId = MParticle.getInstance().Identity().getDeviceApplicationStamp();
            Logger.debug("Optimizely userId not found, applying DAS as userId by default");
        }
        return userId;
    }

    private Boolean getOptimizelyEvent(final MPEvent mpEvent, final MParticleUser user, final OptimizelyEventCallback onEventCreated) {
        if (!MPUtility.isEmpty(getUserId(user))) {
            com.mparticle.UserAttributeListener listener = new com.mparticle.UserAttributeListener() {
                @Override
                public void onUserAttributesReceived(@Nullable Map<String, String> userAttributes, @Nullable Map<String, List<String>> userAttributeLists, @Nullable Long aLong) {
                    Map<String, String> attributes = new HashMap<>();
                    for (Map.Entry<String, String> entry : userAttributes.entrySet()) {
                        attributes.put(entry.getKey(), entry.getValue());
                    }


                    OptimizelyEvent event = new OptimizelyEvent();
                    String eventName = mpEvent.getEventName();
                    String userId = getUserId(user);

                    event.eventName = eventName;
                    event.userId = userId;
                    event.userAttributes = attributes;
                    if (mpEvent.getCustomAttributes() != null) {
                        event.eventAttributes = new HashMap<String, Object>(mpEvent.getInfo());
                    }
                    onEventCreated.onOptimizelyEventCreated(event);
                }
            };
            if (user != null) {
                user.getUserAttributes(listener);
            } else {
                listener.onUserAttributesReceived(new HashMap<String, String>(), new HashMap<String, List<String>>(), null);
            }
            return true;
        } else {
            return false;
        }
    }

    private void queueEvent(OptimizelyEvent event) {
        mEventQueue.offer(event);
        if (mEventQueue.size() > 10) {
            mEventQueue.remove();
        }
    }

    private void replayQueue() {
        while (!mEventQueue.isEmpty()) {
            logOptimizelyEvent(mEventQueue.poll());
        }
    }



    private Long tryParse(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }

    class OptimizelyEvent {
        String eventName;
        String userId;
        Map<String, String> userAttributes;
        Map<String, Object> eventAttributes;

        void addEventAttribute(String key, Object value) {
            if (eventAttributes == null) {
                eventAttributes = new HashMap<>();
            }
            eventAttributes.put(key, value);
        }
    }

    public interface OptimizelyClientListener {
        void onOptimizelyClientAvailable(OptimizelyClient optimizelyClient);
    }

    private interface OptimizelyEventCallback {
        void onOptimizelyEventCreated(OptimizelyEvent event);
    }
}
