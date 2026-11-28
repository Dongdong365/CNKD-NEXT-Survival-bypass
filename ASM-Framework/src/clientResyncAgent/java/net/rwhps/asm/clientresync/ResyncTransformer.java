package net.rwhps.asm.clientresync;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Changes two call sites. All original branch targets, handlers, and stack-map frames remain valid. */
final class ResyncTransformer implements ClassFileTransformer {
    static final String GAME = "com/corrodinggames/rts/";
    static final String LOOP = GAME + "game/i";
    static final String ENGINE = GAME + "gameFramework/l";
    static final String TEAM = GAME + "game/n";
    static final String NET = GAME + "gameFramework/j/ad";
    static final String PACKET = GAME + "gameFramework/j/au";
    static final String CONNECTION = GAME + "gameFramework/j/c";
    static final String SAVE = GAME + "gameFramework/y";
    static final String GATE = "net/rwhps/asm/clientresync/ResyncGate";
    static final String SAVE_DESC = "(L" + GAME + "gameFramework/j/k;ZZZ)Z";
    static final String PACKET_DESC = "(L" + PACKET + ";)V";
    private static final String[][] PROFILE = {
        {NET, "d43a99a0359e459bdd0090c0b62be5c0694b4aa84492df5b3ca13f80f9654140"},
        {LOOP, "38e14172a23de1124160045c5c984c0b3b0b453a1f25ff99ade03cab9fe86256"},
        {ENGINE, "cb1f53e6afb593d030dd8b28e6a48dc43789c22497df2dfa044df15f69c9528e"},
        {TEAM, "b64315419db9daf76b813d8d4b294ecb41e75076c8357102e8885fe4bd4c74de"},
        {PACKET, "431850469cbbb0ec57cc94b8893040d03540acd6b194bbfb357a9e068c51e87d"},
        {CONNECTION, "d195c138af38498ff696c70da48af727d35c912cba605bd3141138d4931d2fa6"},
        {SAVE, "5000200ce1e3927b12ec590bb4d401f50cfe102f2d11cb6952f76d3fc34b608b"},
    };
    private static final Map<ClassLoader, Integer> APPLIED = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ClassLoader, Boolean> compatible = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (loader == null || redefined != null || !(NET.equals(name) || LOOP.equals(name))) return null;
        try {
            if (!verifyProfile(loader)) return null;
            if (!checksum(bytes).equals(NET.equals(name) ? PROFILE[0][1] : PROFILE[1][1])) {
                ClientResyncAgent.log("skipped changed class " + name);
                return null;
            }
            byte[] patched = patch(name, bytes);
            synchronized (APPLIED) {
                int bit = NET.equals(name) ? 1 : 2;
                APPLIED.put(loader, APPLIED.getOrDefault(loader, 0) | bit);
            }
            ClientResyncAgent.log("patched " + name);
            return patched;
        } catch (IOException | ClassNotFoundException | RuntimeException | LinkageError error) {
            compatible.put(loader, false);
            ClientResyncAgent.log("not applied to " + name + ": " + error.getClass().getSimpleName());
            return null;
        }
    }

    static boolean bothHooksApplied(ClassLoader loader) {
        return APPLIED.getOrDefault(loader, 0) == 3;
    }

    private boolean verifyProfile(ClassLoader loader) throws IOException, ClassNotFoundException {
        Boolean cached = compatible.get(loader);
        if (cached != null) return cached;
        if (loader.loadClass(ResyncGate.class.getName()) != ResyncGate.class) {
            compatible.put(loader, false);
            ClientResyncAgent.log("disabled: game class loader cannot use this agent's bridge");
            return false;
        }
        for (String[] entry : PROFILE) {
            if (!entry[1].equals(checksum(classResource(loader, entry[0])))) {
                compatible.put(loader, false);
                ClientResyncAgent.log("disabled: unsupported client bytecode for " + entry[0]);
                return false;
            }
        }
        compatible.put(loader, true);
        return true;
    }

    static byte[] patch(String name, byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        if (!name.equals(node.name)) throw new IllegalArgumentException("Wrong engine class");
        if (NET.equals(name)) {
            patchLoad(method(node, "a", PACKET_DESC));
        } else if (LOOP.equals(name)) {
            patchLoop(method(node, "b", "(FI)V"));
        } else {
            throw new IllegalArgumentException("Unexpected class " + name);
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void patchLoad(MethodNode method) {
        MethodInsnNode load = uniqueCall(method, SAVE, "a", SAVE_DESC);
        int token = method.maxLocals++;
        // The loader arguments are already on the stack. They are left untouched. This
        // invocation is the accepted, non-debug packet-35 branch of ad.a(au).
        InsnList before = new InsnList();
        before.add(new VarInsnNode(Opcodes.ALOAD, 0)); // ad
        before.add(new VarInsnNode(Opcodes.ALOAD, 2)); // l.B(), the running game
        before.add(hook("begin", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
        before.add(new VarInsnNode(Opcodes.ASTORE, token));
        method.instructions.insertBefore(load, before);

        InsnList after = new InsnList();
        after.add(new InsnNode(Opcodes.DUP)); // preserve the original boolean result
        after.add(new VarInsnNode(Opcodes.ALOAD, token));
        after.add(hook("loaded", "(ZLjava/lang/Object;)V"));
        method.instructions.insert(load, after);
    }

    private static void patchLoop(MethodNode method) {
        MethodInsnNode networkFrame = uniqueCall(method, NET, "a", "(F)V");
        // The virtual i.a(true) predicate immediately after network processing branches
        // around the entire lockstep simulation (including catch-up), not around rendering.
        AbstractInsnNode game = nextInstruction(networkFrame);
        AbstractInsnNode flag = nextInstruction(game);
        AbstractInsnNode pause = nextInstruction(flag);
        if (!(game instanceof VarInsnNode) || game.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) game).var != 0
                || flag.getOpcode() != Opcodes.ICONST_1 || !callMatches(pause, LOOP, "a", "(Z)Z")
                || nextInstruction(pause).getOpcode() != Opcodes.IFNE) {
            throw new IllegalStateException("Simulation pause anchor changed");
        }
        InsnList gate = new InsnList();
        gate.add(new VarInsnNode(Opcodes.ALOAD, 0));
        gate.add(hook("shouldHold", "(Ljava/lang/Object;)Z"));
        gate.add(new InsnNode(Opcodes.IOR)); // never clear the original pause decision
        method.instructions.insert(pause, gate);
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing engine method " + name + desc));
    }

    private static MethodInsnNode uniqueCall(MethodNode method, String owner, String name, String desc) {
        MethodInsnNode match = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!callMatches(instruction, owner, name, desc)) continue;
            if (match != null) throw new IllegalStateException("Ambiguous engine anchor " + owner + '.' + name);
            match = (MethodInsnNode) instruction;
        }
        if (match == null) throw new IllegalStateException("Missing engine anchor " + owner + '.' + name);
        return match;
    }

    private static boolean callMatches(AbstractInsnNode instruction, String owner, String name, String desc) {
        if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return owner.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc);
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        if (next == null) throw new IllegalStateException("Truncated engine method");
        return next;
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, name, desc, false);
    }

    static byte[] classResource(ClassLoader loader, String name) throws IOException {
        try (InputStream in = loader.getResourceAsStream(name + ".class")) {
            if (in == null) throw new IOException("Missing engine class " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static String checksum(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
