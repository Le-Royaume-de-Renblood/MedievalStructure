package fr.renblood.medievalstructures.integration;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public final class NpcShopkeeperIntegration {
    private static final String API_CLASS = "fr.renblood.npcshopkeeper.api.NpcShopkeeperAPI";
    private static final String REQUEST_CLASS = "fr.renblood.npcshopkeeper.api.NpcShopkeeperAPI$InnCustomerRequest";

    // Resolve optional API signatures once. Route presence and spawn capacity remain live queries.
    private static final class InnBridge {
        static final Class<?> API = type(API_CLASS), REQUEST = type(REQUEST_CLASS);
        static final Method HAS_ROUTE = method("hasCommercialRoute", String.class);
        static final Method CLEAR = method("clearInnCustomers", ServerLevel.class, String.class);
        static final Method SPAWN = REQUEST == null ? null : method("spawnInnCustomer", ServerLevel.class, String.class, REQUEST);
        static final Constructor<?> REQUEST_CONSTRUCTOR = constructor();
        private static Class<?> type(String name) { try{return Class.forName(name);}catch(ClassNotFoundException absent){return null;} }
        private static Method method(String name,Class<?>... args) { if(API==null)return null;try{return API.getMethod(name,args);}catch(ReflectiveOperationException absent){return null;} }
        private static Constructor<?> constructor() { if(REQUEST==null)return null;try{return REQUEST.getConstructor(String.class,String.class,String.class,long.class,String.class);}catch(ReflectiveOperationException absent){return null;} }
    }

    private NpcShopkeeperIntegration() {
    }

    public static boolean hasRoute(String routeId) {
        try {
            Method method = InnBridge.HAS_ROUTE;
            if(method==null)return false;
            Object result = method.invoke(null, routeId);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static List<String> getRouteSummaries() {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod("getCommercialRouteSummaries");
            Object result = method.invoke(null);
            if (result instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    public static List<String> getRouteSelectors() {
        return invokeStringList("getInnMenuRouteSelectors");
    }

    public static String resolveRouteId(String selector) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod("resolveInnMenuRouteId", String.class);
            Object result = method.invoke(null, selector);
            return result instanceof String value ? value : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static List<String> getTradeItemIds() {
        return invokeStringList("getTradeItemIds");
    }

    public static long[] suggestTradeReferenceRange(ServerPlayer player, String itemId) {
        try {
            Method method = Class.forName(API_CLASS).getMethod("suggestTradeReferenceRange", ServerPlayer.class, String.class);
            Object result = method.invoke(null, player, itemId);
            return result instanceof long[] range ? range : new long[]{0L, 0L};
        } catch (ReflectiveOperationException ignored) {
            return new long[]{0L, 0L};
        }
    }

    public static boolean createTradePriceReference(ServerPlayer player, String itemId, int min, int max,
                                                     boolean sendToBackend) {
        try {
            Method method = Class.forName(API_CLASS).getMethod("createTradePriceReference", ServerPlayer.class,
                    String.class, int.class, int.class, boolean.class);
            return Boolean.TRUE.equals(method.invoke(null, player, itemId, min, max, sendToBackend));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static List<String> invokeStringList(String methodName) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod(methodName);
            Object result = method.invoke(null);
            if (result instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    public static String trySpawnInnCustomer(ServerLevel level, String routeId, String innName, String itemId,
                                           String itemDisplayName, long price, String type) {
        try {
            Method method = InnBridge.SPAWN;
            Constructor<?> constructor = InnBridge.REQUEST_CONSTRUCTOR;
            if(method==null||constructor==null)return "UNAVAILABLE";
            Object request = constructor.newInstance(innName, itemId, itemDisplayName, price, type);
            Object result = method.invoke(null, level, routeId, request);
            return result == null ? "UNAVAILABLE" : result.toString();
        } catch (ReflectiveOperationException ignored) {
            return "UNAVAILABLE";
        }
    }

    public static int clearInnCustomers(ServerLevel level, String innName) {
        try {
            Method method = InnBridge.CLEAR;
            if(method==null)return 0;
            Object result = method.invoke(null, level, innName);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }
}
