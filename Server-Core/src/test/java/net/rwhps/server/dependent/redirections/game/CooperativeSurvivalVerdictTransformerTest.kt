package net.rwhps.server.dependent.redirections.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.jar.JarFile

class CooperativeSurvivalVerdictTransformerTest {
    @Test
    fun `reference engine uses the bridge only for the native HQ defeat branch`() {
        val bytes = engineClass()
        val patched = checkNotNull(CooperativeSurvivalVerdictTransformer.patch(bytes))
        val node = ClassNode().also { ClassReader(patched).accept(it, 0) }
        assertTrue(node.fields.any { it.name == CooperativeSurvivalVerdictTransformer.PATCH_MARKER })
        val missionUpdate = node.methods.single { it.name == "c" && it.desc == "(F)V" }
        val bridge = missionUpdate.instructions.toArray().filterIsInstance<MethodInsnNode>()
            .single { it.owner == CooperativeSurvivalVerdictTransformer.BRIDGE }
        assertEquals(INVOKESTATIC, bridge.opcode)
        assertEquals("shouldDefeat", bridge.name)
        assertEquals("(ZLjava/lang/Object;)Z", bridge.desc)
        assertEquals(IFEQ, (bridge.next as JumpInsnNode).opcode)
        assertNull(CooperativeSurvivalVerdictTransformer.patch(patched))
    }

    @Test
    fun `changed bytecode falls back without rewriting the mission`() {
        val node = ClassNode().also { ClassReader(engineClass()).accept(it, 0) }
        val missionUpdate = node.methods.single { it.name == "c" && it.desc == "(F)V" }
        missionUpdate.instructions.toArray().filterIsInstance<MethodInsnNode>().last { it.name == "H" }.name = "oldH"
        val changed = ClassWriter(0).also { node.accept(it) }.toByteArray()
        assertNull(CooperativeSurvivalVerdictTransformer.transform(
            javaClass.classLoader, CooperativeSurvivalVerdictTransformer.MISSION, null, null, changed
        ))
    }

    private fun engineClass(): ByteArray {
        val jar = listOf(File("libs/game-lib.jar"), File("Server-Core/libs/game-lib.jar")).first { it.isFile }
        return JarFile(jar).use {
            it.getInputStream(it.getJarEntry(CooperativeSurvivalVerdictTransformer.MISSION + ".class")).use { input ->
                input.readBytes()
            }
        }
    }
}
