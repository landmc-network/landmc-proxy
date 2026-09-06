package pl.landmc.proxy.skin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls the public SkinsRestorer API by reflection.
 *
 * <p>Reflection rather than a compile-time dependency so the proxy is not pinned to one
 * SkinsRestorer release: the plugin is installed by whoever runs the network, and its API jar
 * has already changed its required Java version once. Everything here is a public API method,
 * and the calls happen off the event threads.
 *
 * <p>{@code findMethod} matches on name and parameter count rather than exact types because the
 * skin identifier is a SkinsRestorer type this project cannot name.
 */
final class SkinsRestorerApiBridge {

    private SkinsRestorerApiBridge() {
    }

    /**
     * Checks that every method this bridge calls exists on the installed SkinsRestorer.
     *
     * <p>Reflection fails at the moment of use, which for a skin change means in front of a
     * player, after an update nobody connected to the failure. Walking the API once at startup
     * turns that into a line in the log while somebody is still watching the console.
     *
     * @param playerClass the platform's player type, which the applier is looked up by
     * @throws ReflectiveOperationException when the installed version no longer matches
     */
    static void verify(Class<?> playerClass) throws ReflectiveOperationException {
        Object skinsRestorer = provider();
        Object skinStorage = invokeNoArgs(skinsRestorer, "getSkinStorage");
        Object playerStorage = invokeNoArgs(skinsRestorer, "getPlayerStorage");

        skinStorage.getClass().getMethod("findOrCreateSkinData", String.class);
        findMethod(playerStorage, "setSkinIdOfPlayer", 2);
        findMethod(skinApplier(skinsRestorer, playerClass), "applySkin", 1);
    }

    static Object provider() throws ReflectiveOperationException {
        Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
        return providerClass.getMethod("get").invoke(null);
    }

    static Object invokeNoArgs(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    static Optional<?> findOrCreateSkinData(Object skinStorage, String skinName)
            throws ReflectiveOperationException {
        Object result = skinStorage.getClass()
                .getMethod("findOrCreateSkinData", String.class)
                .invoke(skinStorage, skinName);
        if (result instanceof Optional<?> optional) {
            return optional;
        }
        throw new ReflectiveOperationException("SkinsRestorer returned an unsupported skin lookup result");
    }

    static void setPlayerSkin(Object playerStorage, UUID playerId, Object skinIdentifier)
            throws ReflectiveOperationException {
        Method method = findMethod(playerStorage, "setSkinIdOfPlayer", 2);
        invoke(method, playerStorage, playerId, skinIdentifier);
    }

    /**
     * Zdejmuje zapisanego skina, zeby gracz wrocil do swojego.
     *
     * <p>Szukane po nazwie i liczbie argumentow, jak reszta tego mostu: SkinsRestorer zmienia
     * sygnatury miedzy wersjami, a plugin, ktory przestaje sie uruchamiac po aktualizacji
     * cudzego jara, jest gorszy niz plugin bez jednej komendy.
     */
    static void removePlayerSkin(Object playerStorage, UUID playerId)
            throws ReflectiveOperationException {

        Method method = findMethod(playerStorage, "removeSkinIdOfPlayer", 1);
        invoke(method, playerStorage, playerId);
    }

    static Object skinApplier(Object skinsRestorer, Class<?> playerClass)
            throws ReflectiveOperationException {
        return skinsRestorer.getClass().getMethod("getSkinApplier", Class.class).invoke(skinsRestorer, playerClass);
    }

    static void applySkin(Object skinApplier, Object player) throws ReflectiveOperationException {
        Method method = findMethod(skinApplier, "applySkin", 1);
        invoke(method, skinApplier, player);
    }

    private static Method findMethod(Object target, String name, int parameterCount)
            throws NoSuchMethodException {
        return Arrays.stream(target.getClass().getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == parameterCount)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(
                        target.getClass().getName() + "." + name + "/" + parameterCount));
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        }
        catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            throw exception;
        }
    }
}
