/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2022 Guardsquare NV
 */

package proguard.classfile.verify

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify
import proguard.classfile.AccessConstants
import proguard.classfile.ClassConstants
import proguard.classfile.ClassPool
import proguard.classfile.Clazz
import proguard.classfile.Member
import proguard.classfile.Method
import proguard.classfile.ProgramClass
import proguard.classfile.ProgramMember
import proguard.classfile.VersionConstants
import proguard.classfile.attribute.Attribute
import proguard.classfile.attribute.CodeAttribute
import proguard.classfile.attribute.preverification.MoreZeroFrame
import proguard.classfile.attribute.preverification.StackMapFrame
import proguard.classfile.attribute.preverification.StackMapTableAttribute
import proguard.classfile.attribute.preverification.VerificationType
import proguard.classfile.attribute.preverification.visitor.StackMapFrameVisitor
import proguard.classfile.attribute.preverification.visitor.VerificationTypeVisitor
import proguard.classfile.attribute.visitor.AllAttributeVisitor
import proguard.classfile.attribute.visitor.AttributeVisitor
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.io.ProgramClassWriter
import proguard.classfile.visitor.AllMethodVisitor
import proguard.classfile.visitor.MemberVisitor
import proguard.preverify.CodePreverifier
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.JavaSource
import java.io.DataOutputStream
import java.io.FileOutputStream

class TestCodePreverifier : FreeSpec({

    "Given a stackmap entry with a backwards branch" - {
        // Start building the class.
        val  programClass=  ClassBuilder(
            VersionConstants.CLASS_VERSION_1_8,
            AccessConstants.PUBLIC,
            "TestFilterInputStream",
            "java/io/FilterInputStream"
        ) // Add the main method.
            .addMethod(
                AccessConstants.PUBLIC or AccessConstants.STATIC,
                "main",
                "([Ljava/lang/String;)V",
                50,  // Compose the equivalent of this java code:
                //     System.out.println("Hello, world!");

                ClassBuilder.CodeBuilder { code ->
                    code.getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
                        .ldc("Hello")
                        .invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
                        .return_()
                }) // We don't need to preverify simple code that doesn't have
            // special control flow. It works fine without a stack map
            // table attribute.
            // Retrieve the final class.
            .addField(AccessConstants.PRIVATE, "httpURLConnection", "Ljava/net/HttpURLConnection;")

            .addMethod(
                AccessConstants.PUBLIC,
                ClassConstants.METHOD_NAME_INIT,
                "(Ljava/net/HttpURLConnection;)V",
                50,

                /**
                 *

                 *  com.google.android.gms.internal.ads.zzaqz(java.net.HttpURLConnection);
        *     descriptor: (Ljava/net/HttpURLConnection;)V
        *     flags: (0x0000)
        *     Code:
        *       stack=2, locals=3, args_size=2
        *          0: aload_1
        *          1: invokevirtual #11                 // Method java/net/HttpURLConnection.getInputStream:()Ljava/io/InputStream;
        *          4: astore_2
        *          5: aload_0
        *          6: aload_2
        *          7: invokespecial #7                  // Method java/io/FilterInputStream."<init>":(Ljava/io/InputStream;)V
        *         10: aload_0
        *         11: aload_1
        *         12: putfield      #6                  // Field zza:Ljava/net/HttpURLConnection;
        *         15: return
        *         16: pop
        *         17: aload_1
        *         18: invokevirtual #10                 // Method java/net/HttpURLConnection.getErrorStream:()Ljava/io/InputStream;
        *         21: astore_2
        *         22: goto          5
        *       Exception table:
        *          from    to  target type
        *              0     5    16   Class java/io/IOException
        *       LineNumberTable:
        *         line 1: 0
        *         line 3: 7
        *         line 2: 16
        *       StackMapTable: number_of_entries = 2
        *         frame_type = 252 /* append */
        *           offset_delta = 5
        *           locals = [ class java/io/InputStream ]
        *         frame_type = 255 /* full_frame */
        *           offset_delta = 10
        *           locals = [ class com/google/android/gms/internal/ads/zzaqz, class java/net/HttpURLConnection ]
        *           stack = [ class java/io/IOException ]
        *
        */
                { code ->
                    val tryStart = code.createLabel()
                    val tryEnd = code.createLabel()
//                    val catchLable = code.createLabel()

                    code
                        .label(tryStart)
                        .aload_1()
                        .invokevirtual("java/net/HttpURLConnection", "getInputStream", "()Ljava/io/InputStream;")
                        .astore_2()
                        .label(tryEnd)
                        .aload_0()
                        .aload_2()
                        .invokespecial("java/io/FilterInputStream", "<init>", "(Ljava/io/InputStream;)V")
                        .aload_0()
                        .aload_1()
                        .putfield("TestFilterInputStream", "httpURLConnection", "Ljava/net/HttpURLConnection;")
                        .return_()
                        .catch_(tryStart, tryEnd, "java/io/IOException", null)
                        .pop()
                        .aload_1()
                        .invokevirtual("java/net/HttpURLConnection", "getErrorStream", "()Ljava/io/InputStream;")
                        .astore_2()
                        .goto_(tryEnd)
                }
            )
            .programClass;
        if(true){
            val dataOutputStream = DataOutputStream(FileOutputStream("/home/xc/Downloads/ReportAttachements/PG-keep/Test/TestFilterInputStream.class"))
            programClass.accept(ProgramClassWriter(dataOutputStream))
            dataOutputStream.close()
        }

        programClass.accept(
            AllMethodVisitor(
                AllAttributeVisitor(
                    CodePreverifier(false),
                ),
            ),
        )

        if(true)
        {
            val dataOutputStream = DataOutputStream(FileOutputStream("/home/xc/Downloads/ReportAttachements/PG-keep/Test/Test1/TestFilterInputStream.class"))
            programClass.accept(ProgramClassWriter(dataOutputStream))
            dataOutputStream.close()
        }

        val programClassPool = ClassPool(programClass)

        "Then the local variable should be correct" {
            programClassPool.toString()
        }
    }


    /**
     * goto
     *
     *
     * aload 0
     * invoke
     *
     *
     * exception 177 179 175
     *
     */



})
