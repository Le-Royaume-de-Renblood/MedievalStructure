package fr.renblood.medievalstructures.integration;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public final class NpcShopkeeperIntegration {
    private static final String API_CLASS = "fr.renblood.npcshopkeeper.api.NpcShopkeeperAPI";
    private static final String REQUEST_CLASS = "fr.renblood.npcshopkeeper.api.NpcShopkeeperAPI$InnCustomerRequest";

    private NpcShopkeeperIntegration() {
    }

    public static boolean hasRoute(String routeId) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod("hasCommercialRoute", String.class);
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
            Class<?> api = Class.forName(API_CLASS);
            Class<?> requestClass = Class.forName(REQUEST_CLASS);
            Constructor<?> constructor = requestClass.getConstructor(String.class, String.class, String.class, long.class, String.class);
            Object request = constructor.newInstance(innName, itemId, itemDisplayName, price, type);
            Method method = api.getMethod("spawnInnCustomer", ServerLevel.class, String.class, requestClass);
            Object result = method.invoke(null, level, routeId, request);
            return result == null ? "UNAVAILABLE" : result.toString();
        } catch (ReflectiveOperationException ignored) {
            return "UNAVAILABLE";
        }
    }

    public static int clearInnCustomers(ServerLevel level, String innName) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method method = api.getMethod("clearInnCustomers", ServerLevel.class, String.class);
            Object result = method.invoke(null, level, innName);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }
}
