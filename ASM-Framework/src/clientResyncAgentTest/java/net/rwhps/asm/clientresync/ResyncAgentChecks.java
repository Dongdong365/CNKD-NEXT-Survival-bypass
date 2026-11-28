package net.rwhps.asm.clientresync;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Collection;

/** Run in a fresh JVM with the packaged agent and the local reference engine. */
public final class ResyncAgentChecks {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = ResyncAgentChecks.class.getClassLoader();
        Class<?> net = Class.forName(ResyncTransformer.NET.replace('/', '.'), false, loader);
        Class<?> loop = Class.forName(ResyncTransformer.LOOP.replace('/', '.'), false, loader);
        // Resolve the exact methods, not the unrelated Android-only method signatures
        // omitted from the local desktop engine library.
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        lookup.findVirtual(net, "a", MethodType.methodType(void.class,
                Class.forName(ResyncTransformer.PACKET.replace('/', '.'), false, loader)));
        lookup.findVirtual(loop, "b", MethodType.methodType(void.class, float.class, int.class));
        check(ResyncTransformer.bothHooksApplied(loader), "missing one or both real engine hooks");

        ResyncTransformer transformer = new ResyncTransformer();
        byte[] netClass = ResyncTransformer.classResource(loader, ResyncTransformer.NET);
        byte[] mutated = netClass.clone();
        mutated[mutated.length - 1] ^= 1;
        check(transformer.transform(loader, ResyncTransformer.NET, null, null, mutated) == null,
                "changed engine class must not be patched");
        check(transformer.transform(loader, ResyncTransformer.LOOP, loop, null, new byte[0]) == null,
                "must not retransform a running client");

        long start = 100;
        ResyncGate.WaitLimit limit = new ResyncGate.WaitLimit(start);
        check(limit.decide(start, false) == ResyncGate.Decision.WAIT, "missing roster should hold simulation");
        check(limit.decide(start + 1, true) == ResyncGate.Decision.READY, "ready roster should release immediately");
        check(limit.decide(start + ResyncGate.MAX_WAIT_NANOS - 1, false) == ResyncGate.Decision.WAIT,
                "missing roster must wait until its deadline");
        check(limit.decide(start + ResyncGate.MAX_WAIT_NANOS, false) == ResyncGate.Decision.TIMEOUT,
                "permanently missing roster must not freeze the client forever");
        check(!ResyncGate.shouldHold(new Object()), "ordinary frames must keep native behavior");
        checkRosterGate(loader, net, loop);
        System.out.println("Client resync agent checks passed: real engine class verification, version guard and roster gate");
    }

    @SuppressWarnings("unchecked") // The obfuscated engine stores c objects in a raw queue.
    private static void checkRosterGate(ClassLoader loader, Class<?> net, Class<?> loop) throws Exception {
        // Instantiate the real desktop objects without the game window, ticker or any socket.
        loader.loadClass("android.os.Looper").getMethod("a").invoke(null);
        Class<?> engine = loader.loadClass(ResyncTransformer.ENGINE.replace('/', '.'));
        Class<?> team = loader.loadClass(ResyncTransformer.TEAM.replace('/', '.'));
        Class<?> player = loader.loadClass("com.corrodinggames.rts.game.e");
        Object context = loader.loadClass("android.content.ServerContext").getConstructor().newInstance();
        Object game = loop.getConstructor(loader.loadClass("android.content.Context")).newInstance(context);
        Object network = net.getConstructor().newInstance();
        Field initialConnection = net.getDeclaredField("aL");
        initialConnection.setAccessible(true);
        Object source = initialConnection.get(network); // unconnected, constructor-created c
        ((Collection<Object>) net.getField("aM").get(network)).add(source);
        net.getField("B").setBoolean(network, true);
        net.getField("aW").setBoolean(network, true);
        engine.getField("bX").set(game, network);
        engine.getField("bL").set(game, loader.loadClass("com.corrodinggames.rts.game.b.b").getConstructor().newInstance());

        Object local = player.getConstructor(int.class, boolean.class, String.class).newInstance(0, true, "local");
        Object remote = player.getConstructor(int.class, boolean.class, String.class).newInstance(1, true, "remote");
        team.getField("W").setInt(remote, 30);
        team.getField("X").setLong(remote, System.currentTimeMillis());
        net.getField("z").set(network, local);
        engine.getField("bs").set(game, local);
        Field slots = team.getDeclaredField("as");
        slots.setAccessible(true);
        Object[] roster = (Object[]) slots.get(null);
        Field counter = net.getField("ar");

        Object token = ResyncGate.begin(network, game);
        check(token != null, "expected the active client to capture its roster");
        roster[1] = null;
        complete(counter, network, token);
        check(ResyncGate.shouldHold(game), "the missing remote slot must hold simulation");
        check(roster[1] == null, "the gate must not create a roster entry");
        complete(counter, network, ResyncGate.begin(network, game));
        check(ResyncGate.shouldHold(game), "a second resync must retain the original roster expectation");
        roster[1] = remote;
        check(!ResyncGate.shouldHold(game), "restoring the same slot must release immediately");

        token = ResyncGate.begin(network, game);
        ResyncGate.loaded(false, token);
        check(!ResyncGate.shouldHold(game), "failed load must not arm the gate");
        ResyncGate.loaded(true, ResyncGate.begin(network, game));
        check(!ResyncGate.shouldHold(game), "uncommitted load must not arm the gate");

        boolean wasObserveOnly = ResyncGate.observeOnly;
        ResyncGate.observeOnly = true;
        token = ResyncGate.begin(network, game);
        roster[1] = null;
        complete(counter, network, token);
        check(!ResyncGate.shouldHold(game), "observe mode must not pause simulation");
        roster[1] = remote;
        check(!ResyncGate.shouldHold(game), "observe mode must clear the ready state");
        ResyncGate.observeOnly = wasObserveOnly;

        token = ResyncGate.begin(network, game);
        roster[1] = null;
        complete(counter, network, token);
        Field closing = source.getClass().getDeclaredField("b");
        closing.setAccessible(true);
        closing.setBoolean(source, true);
        check(!ResyncGate.shouldHold(game), "closing the original connection must release without a fake reconnect");
        check(ResyncGate.begin(network, game) == null, "must not arm on a closing session");
    }

    private static void complete(Field counter, Object network, Object token) throws IllegalAccessException {
        check(token != null, "load must have captured an established roster");
        ResyncGate.loaded(true, token);
        counter.setInt(network, counter.getInt(network) + 1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
