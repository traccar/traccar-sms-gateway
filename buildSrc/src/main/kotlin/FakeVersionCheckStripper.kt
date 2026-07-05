import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

// Empties the Fossify Commons methods that show its "fake version" anti-fork dialog,
// at AGP's ASM stage (before R8, so inlining can't defeat it).
abstract class FakeVersionCheckStripper :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    private companion object {
        val TARGETS = mapOf(
            "org.fossify.commons.extensions.ActivityKt" to
                ("showModdedAppWarning" to "(Lorg/fossify/commons/activities/BaseSimpleActivity;)V"),
            "org.fossify.commons.compose.extensions.ActivityExtensionsKt" to
                ("fakeVersionCheck" to "(Landroid/content/Context;Lkotlin/jvm/functions/Function0;)V"),
        )
    }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor = object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
        private val target = TARGETS[classContext.currentClassData.className]

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            if (target != null && name == target.first && descriptor == target.second) {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                mv.visitCode()
                mv.visitInsn(Opcodes.RETURN)
                mv.visitMaxs(0, 0)
                mv.visitEnd()
                return null
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions)
        }
    }

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className in TARGETS
}
