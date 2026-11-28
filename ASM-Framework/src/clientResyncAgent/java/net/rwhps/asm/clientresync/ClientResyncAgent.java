package net.rwhps.asm.clientresync;

import java.lang.instrument.Instrumentation;

/** Standalone agent: no headless server, runtime attach, or on-disk engine changes. */
public final class ClientResyncAgent {
    private ClientResyncAgent() {}

    public static void premain(String options, Instrumentation instrumentation) {
        boolean observe = "observe".equals(options);
        if (options != null && !options.isEmpty() && !observe) {
            log("disabled: expected no options or =observe");
            return;
        }
        ResyncGate.observeOnly = observe;
        instrumentation.addTransformer(new ResyncTransformer());
        log("installed mode=" + (observe ? "observe" : "gate") + "; waiting for a supported client engine");
    }

    static void log(String message) {
        System.err.println("[ClientResync] " + message);
    }
}
