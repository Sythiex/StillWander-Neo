package com.cinecraft.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;

/** Reads the pinned mod binaries without initializing their Minecraft entry points. */
final class OptionalApiSignatureTest {
    @Test
    void freecamStateIsPublicStaticAndBoolean() throws Exception {
        method(read("net/xolt/freecam/Freecam"), "isEnabled", "()Z", true);
    }

    @Test
    void replayProbeUsesTheSessionHandlerRatherThanTheRecorder() throws Exception {
        ClassNode replay = read("com/replaymod/replay/ReplayModReplay");
        assertTrue(replay.fields.stream().anyMatch(f -> f.name.equals("instance")
                && f.desc.equals("Lcom/replaymod/replay/ReplayModReplay;")
                && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)));
        method(replay, "getReplayHandler", "()Lcom/replaymod/replay/ReplayHandler;", false);
    }

    @Test
    void dynamicFpsRefreshAcceptsItsActivityFlag() throws Exception {
        method(read("dynamic_fps/impl/DynamicFPSMod"), "onStatusChanged", "(Z)V", true);
    }

    private static void method(ClassNode owner, String name, String descriptor, boolean isStatic) {
        assertTrue(owner.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)
                && (m.access & Opcodes.ACC_PUBLIC) != 0 && ((m.access & Opcodes.ACC_STATIC) != 0) == isStatic),
                owner.name + "." + name + descriptor);
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = OptionalApiSignatureTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }
}
