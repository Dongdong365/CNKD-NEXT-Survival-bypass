/*
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.dependent.redirections.game

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

/** Only adjusts the native survival HQ verdict. The engine still owns waves, victory and defeat. */
internal object CooperativeSurvivalVerdictTransformer : ClassFileTransformer {
    internal const val MISSION = "com/corrodinggames/rts/gameFramework/n/f"
    internal const val PATCH_MARKER = "cnkdCooperativeUnitVerdict"
    internal const val BRIDGE = "com/corrodinggames/rts/gameFramework/j/HessSurvivalResyncBinding"
    private const val ENGINE = "com/corrodinggames/rts/gameFramework/l"
    private const val UNIT = "com/corrodinggames/rts/game/units/am"
    private const val COMMAND_CENTER = "com/corrodinggames/rts/game/units/d/e"
    private const val TEAM = "com/corrodinggames/rts/game/n"
    private const val UI = "com/corrodinggames/rts/gameFramework/f/g"

    private val patchedClasses = object : ClassValue<Boolean>() {
        override fun computeValue(type: Class<*>): Boolean = try {
            type.getDeclaredField(PATCH_MARKER).type == Boolean::class.javaPrimitiveType
        } catch (_: NoSuchFieldException) {
            false
        }
    }

    fun isPatched(type: Class<*>): Boolean = patchedClasses.get(type)

    override fun transform(
        loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray
    ): ByteArray? {
        if (className != MISSION || classBeingRedefined != null || loader == null) return null
        return try {
            patch(classfileBuffer)
        } catch (error: RuntimeException) {
            System.err.println("[Headless] Falling back to native survival HQ verdict: ${error.message}")
            null
        }
    }

    internal fun patch(bytes: ByteArray): ByteArray? {
        val reader = ClassReader(bytes)
        if (reader.className != MISSION) return null
        val node = ClassNode().also { reader.accept(it, 0) }
        if (node.fields.any { it.name == PATCH_MARKER }) return null
        val method = node.methods.singleOrNull { it.name == "c" && it.desc == "(F)V" }
            ?: error("missing native mission update")
        val code = method.instructions.toArray().filter { it.opcode >= 0 }
        val jump = findNativeDefeatJump(code) ?: error("native survival HQ branch did not match")

        // Stack before IFEQ: [nativeDefeat]. Replace it with the bridge result and leave
        // the engine's original branch target/frames and all other control flow intact.
        method.instructions.insertBefore(jump, InsnList().apply {
            add(VarInsnNode(ALOAD, 0))
            add(MethodInsnNode(INVOKESTATIC, BRIDGE, "shouldDefeat", "(ZLjava/lang/Object;)Z", false))
        })
        node.fields.add(FieldNode(ACC_PUBLIC or ACC_STATIC or ACC_FINAL or ACC_SYNTHETIC, PATCH_MARKER, "Z", null, 1))
        return ClassWriter(reader, ClassWriter.COMPUTE_MAXS).also { node.accept(it) }.toByteArray()
    }

    private fun findNativeDefeatJump(code: List<AbstractInsnNode>): JumpInsnNode? {
        // Locate the exact tail: if (noHQ) { this.m = true; engine.bS.H(); } return.
        if (code.size < 9) return null
        val tail = code.takeLast(9)
        val jump = tail[1] as? JumpInsnNode ?: return null
        val defeat = tail[7] as? MethodInsnNode ?: return null
        if (tail[0].opcode != ILOAD || jump.opcode != IFEQ || tail[2].opcode != ALOAD ||
            (tail[2] as? VarInsnNode)?.`var` != 0 || tail[3].opcode != ICONST_1 ||
            !tail[4].field(PUTFIELD, MISSION, "m", "Z") || tail[5].opcode != ALOAD ||
            !tail[6].field(GETFIELD, ENGINE, "bS", "L$UI;") ||
            defeat.opcode != INVOKEVIRTUAL || defeat.owner != UI || defeat.name != "H" || defeat.desc != "()V" ||
            tail[8].opcode != RETURN || generateSequence(jump.label.next) { it.next }.firstOrNull { it.opcode >= 0 } !== tail[8]
        ) return null

        // Confirm the scan just above it really belongs to an active native survival
        // mission and looks for HQs on the local player, not a generic win/lose check.
        val before = code.dropLast(9)
        val gate = before.indices.lastOrNull { i ->
            before[i].field(GETFIELD, MISSION, "k", "Z") &&
                before.getOrNull(i + 1).jumpsTo(IFEQ, jump) &&
                before.getOrNull(i + 3)?.field(GETFIELD, MISSION, "m", "Z") == true &&
                before.getOrNull(i + 4).jumpsTo(IFNE, jump)
        } ?: return null
        val scan = before.drop(gate + 5)
        return jump.takeIf {
            scan.any { it is TypeInsnNode && it.opcode == INSTANCEOF && it.desc == COMMAND_CENTER } &&
                scan.any { it.field(GETFIELD, UNIT, "bP", "Z") } &&
                scan.any { it.field(GETFIELD, ENGINE, "bs", "L$TEAM;") }
        }
    }

    private fun AbstractInsnNode.field(op: Int, owner: String, name: String, desc: String): Boolean =
        this is FieldInsnNode && opcode == op && this.owner == owner && this.name == name && this.desc == desc

    private fun AbstractInsnNode?.jumpsTo(op: Int, other: JumpInsnNode): Boolean =
        this is JumpInsnNode && opcode == op && label === other.label
}
