package net.rwhps.asm.clientresync;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Client game-thread state, separate from the engine's pause, disconnect and result flags. */
public final class ResyncGate {
    static final long MAX_WAIT_NANOS = TimeUnit.SECONDS.toNanos(3);
    static volatile boolean observeOnly;
    private static final ThreadLocal<Window> PENDING = new ThreadLocal<>();
    private static final ClassValue<EngineAccess> ACCESS = new ClassValue<EngineAccess>() {
        @Override
        protected EngineAccess computeValue(Class<?> netClass) {
            try {
                return new EngineAccess(netClass);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Engine bindings unavailable", e);
            }
        }
    };

    private ResyncGate() {}

    public static Object begin(Object net, Object game) {
        // On this profile packet handling, the save loader and the simulation gate run in
        // order on the same game thread. Keep the baseline off the gate until load succeeds.
        Window previous = PENDING.get();
        PENDING.remove();
        try {
            if (!ResyncTransformer.bothHooksApplied(net.getClass().getClassLoader())) return null;
            EngineAccess access = ACCESS.get(net.getClass());
            Object source = access.soleConnection(net);
            if (!access.sameSession(game, net, source) || access.map.get(game) == null) return null;
            int counter = access.resyncCounter.getInt(net);
            if (previous != null && previous.sameSession(game, net, source)) {
                // Further snapshots must not replace the old roster with a partial one or
                // extend an already-running wait indefinitely.
                return new Window(previous, counter);
            }
            Object local = access.networkPlayer.get(net);
            if (local == null) return null;
            int localSlot = access.slot.getInt(local);
            List<Slot> expected = access.capture(localSlot);
            if (!access.localBindingReady(game, net, localSlot) || expected.stream().noneMatch(s -> !s.remote)) {
                ClientResyncAgent.log("not armed: no established in-game human roster");
                return null;
            }
            return new Window(access, game, net, source, counter, localSlot, expected);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            ClientResyncAgent.log("not armed: " + error.getClass().getSimpleName());
            return null;
        }
    }

    public static void loaded(boolean success, Object token) {
        if (token == null) return;
        Window window = (Window) token;
        if (!success) {
            ClientResyncAgent.log("not armed: native snapshot load failed");
            return;
        }
        if (window.limit == null) window.limit = new WaitLimit(System.nanoTime());
        PENDING.set(window);
        ClientResyncAgent.log("loaded candidate=" + window.expected.size() + " localSlot=" + window.localSlot);
    }

    public static boolean shouldHold(Object game) {
        Window window = PENDING.get();
        if (window == null) return false;
        try {
            if (window.game != game || !window.access.sameSession(game, window.net, window.source)) {
                return release("session_changed");
            }
            if (window.access.resyncCounter.getInt(window.net) != window.counter + 1) {
                // Native post-load processing did not complete, or a different native load
                // has since replaced the snapshot. Do not hold either path with stale state.
                return release("native_resync_not_committed");
            }
            Object[] roster = window.access.roster();
            boolean localReady = window.access.localBindingReady(game, window.net, window.localSlot, roster);
            List<Integer> missing = window.access.missing(window.expected, roster);
            switch (window.limit.decide(System.nanoTime(), localReady && missing.isEmpty())) {
                case READY:
                    return release("ready");
                case TIMEOUT:
                    return release("timeout localReady=" + localReady + " missingSlots=" + missing);
                default:
                    if (!window.reportedWait) {
                        window.reportedWait = true;
                        ClientResyncAgent.log((observeOnly ? "would_hold" : "holding") + " localReady=" + localReady
                                + " missingSlots=" + missing);
                    }
                    return !observeOnly;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return release("gate_error " + error.getClass().getSimpleName());
        }
    }

    private static boolean release(String reason) {
        PENDING.remove();
        ClientResyncAgent.log("released reason=" + reason);
        return false;
    }

    enum Decision { WAIT, READY, TIMEOUT }

    static final class WaitLimit {
        final long since;

        WaitLimit(long since) { this.since = since; }

        Decision decide(long now, boolean ready) {
            if (ready) return Decision.READY;
            return now - since >= MAX_WAIT_NANOS ? Decision.TIMEOUT : Decision.WAIT;
        }
    }

    private static final class Window {
        final EngineAccess access;
        final Object game, net, source;
        final int counter, localSlot;
        final List<Slot> expected;
        WaitLimit limit;
        boolean reportedWait;

        Window(EngineAccess access, Object game, Object net, Object source, int counter, int localSlot, List<Slot> expected) {
            this.access = access;
            this.game = game;
            this.net = net;
            this.source = source;
            this.counter = counter;
            this.localSlot = localSlot;
            this.expected = expected;
        }

        Window(Window previous, int counter) {
            this(previous.access, previous.game, previous.net, previous.source, counter, previous.localSlot, previous.expected);
            limit = previous.limit;
            reportedWait = previous.reportedWait;
        }

        boolean sameSession(Object game, Object net, Object source) {
            return this.game == game && this.net == net && this.source == source;
        }
    }

    private static final class Slot {
        final int index, alliance;
        final String name;
        final boolean remote;

        Slot(int index, int alliance, String name, boolean remote) {
            this.index = index;
            this.alliance = alliance;
            this.name = name;
            this.remote = remote;
        }
    }

    private static final class EngineAccess {
        private final Field networked, server, started, connections, closed, closing, gameNet, gamePlayer,
                networkPlayer, teams, slot, alliance, name, ai, surrendered, defeated, inactive, ping, pingReceived;
        final Field map, resyncCounter;

        EngineAccess(Class<?> net) throws ReflectiveOperationException {
            ClassLoader loader = net.getClassLoader();
            Class<?> game = type(loader, ResyncTransformer.ENGINE);
            Class<?> team = type(loader, ResyncTransformer.TEAM);
            Class<?> connection = type(loader, ResyncTransformer.CONNECTION);
            closed = field(connection, "a");
            closing = field(connection, "b");
            networked = field(net, "B");
            server = field(net, "C");
            started = field(net, "aW");
            connections = field(net, "aM");
            networkPlayer = field(net, "z");
            resyncCounter = field(net, "ar");
            gameNet = field(game, "bX");
            gamePlayer = field(game, "bs");
            map = field(game, "bL");
            teams = field(team, "as"); // backing array for n.k(int), not n.d()
            slot = field(team, "k");
            alliance = field(team, "r");
            name = field(team, "v");
            ai = field(team, "w");
            surrendered = field(team, "E");
            defeated = field(team, "G");
            inactive = field(team, "F");
            ping = field(team, "W");
            pingReceived = field(team, "X");
        }

        Object soleConnection(Object net) throws IllegalAccessException {
            Iterator<?> queue = ((Collection<?>) connections.get(net)).iterator();
            Object first = queue.hasNext() ? queue.next() : null;
            return queue.hasNext() ? null : first;
        }

        boolean sameSession(Object game, Object net, Object source) throws IllegalAccessException {
            return game != null && source != null && gameNet.get(game) == net && networked.getBoolean(net)
                    && !server.getBoolean(net) && started.getBoolean(net) && !closed.getBoolean(source)
                    && !closing.getBoolean(source) && soleConnection(net) == source;
        }

        Object[] roster() throws IllegalAccessException {
            return ((Object[]) teams.get(null)).clone();
        }

        List<Slot> capture(int local) throws IllegalAccessException {
            Object[] players = roster();
            List<Slot> expected = new ArrayList<>();
            for (int index = 0; index < players.length; index++) {
                Object team = players[index];
                boolean remote = index != local;
                if (!available(team, index, remote)) continue;
                String playerName = (String) name.get(team);
                if (playerName != null && !playerName.isEmpty()) {
                    expected.add(new Slot(index, alliance.getInt(team), playerName, remote));
                }
            }
            return expected;
        }

        boolean localBindingReady(Object game, Object net, int index) throws IllegalAccessException {
            return localBindingReady(game, net, index, roster());
        }

        boolean localBindingReady(Object game, Object net, int index, Object[] players) throws IllegalAccessException {
            Object local = networkPlayer.get(net);
            return index >= 0 && index < players.length && local != null && slot.getInt(local) == index
                    && players[index] != null && gamePlayer.get(game) == players[index];
        }

        List<Integer> missing(List<Slot> expected, Object[] players) throws IllegalAccessException {
            List<Integer> missing = new ArrayList<>();
            for (Slot s : expected) {
                Object team = s.index < players.length ? players[s.index] : null;
                if (!available(team, s.index, s.remote) || alliance.getInt(team) != s.alliance
                        || !Objects.equals(name.get(team), s.name)) {
                    missing.add(s.index);
                }
            }
            return missing;
        }

        private boolean available(Object team, int index, boolean remote) throws IllegalAccessException {
            if (team == null || slot.getInt(team) != index || alliance.getInt(team) < 0 || ai.getBoolean(team)
                    || surrendered.getBoolean(team) || defeated.getBoolean(team) || inactive.getBoolean(team)) return false;
            if (!remote) return true;
            // Network snapshot evidence, not a remote socket/ACK. W is the reported ping;
            // X is its local receive time; ignore unknown and stale remote status.
            long received = pingReceived.getLong(team);
            return ping.getInt(team) >= 0 && received > 0 && System.currentTimeMillis() - received <= 15_000;
        }

        private static Class<?> type(ClassLoader loader, String internalName) throws ClassNotFoundException {
            return Class.forName(internalName.replace('/', '.'), false, loader);
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}
