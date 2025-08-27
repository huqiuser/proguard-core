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

    "Given a stackmap entry with an array type" - {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            JavaSource(
                "Test.java",
                """
                 public class Test {
                     public static void main(String[] args) {
                         Object[] test;
                         if (args.length > 0) {
                             test = new Test[0];
                         } else {
                             test = new Integer[0];
                         }
                 
                         System.out.println(test);
                     }
                 }
                """.trimIndent(),
            ),
            javacArguments = listOf("-source", "1.8", "-target", "1.8"),
        )

        "Then the local variable should be correct" {
            val verificationTypeVisitor = spyk(object : VerificationTypeVisitor {
                override fun visitAnyVerificationType(
                    clazz: Clazz,
                    method: Method,
                    codeAttribute: CodeAttribute,
                    offset: Int,
                    verificationType: VerificationType,
                ) {
                }
            })

            programClassPool.classesAccept(
                AllMethodVisitor(
                    AllAttributeVisitor(
                        CodePreverifier(false),
                    ),
                ),
            )

            programClassPool.classesAccept(
                "Test",
                AllMethodVisitor(
                    object : MemberVisitor, AttributeVisitor {
                        override fun visitAnyMember(clazz: Clazz, member: Member) {}

                        override fun visitProgramMember(programClass: ProgramClass, programMember: ProgramMember) {
                            programMember.attributesAccept(programClass, this)
                        }

                        override fun visitAnyAttribute(clazz: Clazz?, attribute: Attribute?) {}

                        override fun visitCodeAttribute(clazz: Clazz, method: Method, codeAttribute: CodeAttribute) {
                            codeAttribute.attributesAccept(
                                clazz,
                                method,
                                object : AttributeVisitor {
                                    override fun visitAnyAttribute(clazz: Clazz, attribute: Attribute) {
                                    }

                                    override fun visitStackMapTableAttribute(
                                        clazz: Clazz,
                                        method: Method,
                                        codeAttribute: CodeAttribute,
                                        stackMapTableAttribute: StackMapTableAttribute,
                                    ) {
                                        stackMapTableAttribute.stackMapFramesAccept(
                                            clazz,
                                            method,
                                            codeAttribute,
                                            object : StackMapFrameVisitor {
                                                override fun visitAnyStackMapFrame(
                                                    clazz: Clazz,
                                                    method: Method,
                                                    codeAttribute: CodeAttribute,
                                                    offset: Int,
                                                    stackMapFrame: StackMapFrame,
                                                ) {
                                                }

                                                override fun visitMoreZeroFrame(
                                                    clazz: Clazz,
                                                    method: Method,
                                                    codeAttribute: CodeAttribute,
                                                    offset: Int,
                                                    moreZeroFrame: MoreZeroFrame,
                                                ) {
                                                    moreZeroFrame.additionalVariablesAccept(
                                                        clazz,
                                                        method,
                                                        codeAttribute,
                                                        offset,
                                                        verificationTypeVisitor,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                ),
            )
            verify {
                val clazz = programClassPool.getClass("Test")
                verificationTypeVisitor.visitObjectType(
                    clazz,
                    clazz.findMethod("main", "([Ljava/lang/String;)V"),
                    ofType<CodeAttribute>(),
                    ofType<Int>(),
                    withArg {
                        clazz.getClassName(it.u2classIndex) shouldBe "[Ljava/lang/Object;"
                    },
                )
            }
        }
    }

    "Given a stackmap entry with a primitve array type" - {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            JavaSource(
                "Test.java",
                """
                 public class Test {
                     public static void main(String[] args) {
                         int[] test;
                         if (args.length > 0) {
                             test = null;
                         } else {
                             test = new int[0];
                         }
                         System.out.println(test);
                     }
                 }
                """.trimIndent(),
            ),
            javacArguments = listOf("-source", "1.8", "-target", "1.8"),
        )
        "Then the local variable should be correct" {
            val verificationTypeVisitor = spyk(object : VerificationTypeVisitor {
                override fun visitAnyVerificationType(
                    clazz: Clazz,
                    method: Method,
                    codeAttribute: CodeAttribute,
                    offset: Int,
                    verificationType: VerificationType,
                ) {
                }
            })

            programClassPool.classesAccept(
                AllMethodVisitor(
                    AllAttributeVisitor(
                        CodePreverifier(false),
                    ),
                ),
            )

            programClassPool.classesAccept(
                "Test",
                AllMethodVisitor(
                    object : MemberVisitor, AttributeVisitor {
                        override fun visitAnyMember(clazz: Clazz, member: Member) {}

                        override fun visitProgramMember(programClass: ProgramClass, programMember: ProgramMember) {
                            programMember.attributesAccept(programClass, this)
                        }

                        override fun visitAnyAttribute(clazz: Clazz?, attribute: Attribute?) {}

                        override fun visitCodeAttribute(clazz: Clazz, method: Method, codeAttribute: CodeAttribute) {
                            codeAttribute.attributesAccept(
                                clazz,
                                method,
                                object : AttributeVisitor {
                                    override fun visitAnyAttribute(clazz: Clazz, attribute: Attribute) {
                                    }

                                    override fun visitStackMapTableAttribute(
                                        clazz: Clazz,
                                        method: Method,
                                        codeAttribute: CodeAttribute,
                                        stackMapTableAttribute: StackMapTableAttribute,
                                    ) {
                                        stackMapTableAttribute.stackMapFramesAccept(
                                            clazz,
                                            method,
                                            codeAttribute,
                                            object : StackMapFrameVisitor {
                                                override fun visitAnyStackMapFrame(
                                                    clazz: Clazz,
                                                    method: Method,
                                                    codeAttribute: CodeAttribute,
                                                    offset: Int,
                                                    stackMapFrame: StackMapFrame,
                                                ) {
                                                }

                                                override fun visitMoreZeroFrame(
                                                    clazz: Clazz,
                                                    method: Method,
                                                    codeAttribute: CodeAttribute,
                                                    offset: Int,
                                                    moreZeroFrame: MoreZeroFrame,
                                                ) {
                                                    moreZeroFrame.additionalVariablesAccept(
                                                        clazz,
                                                        method,
                                                        codeAttribute,
                                                        offset,
                                                        verificationTypeVisitor,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                ),
            )
            verify {
                val clazz = programClassPool.getClass("Test")
                verificationTypeVisitor.visitObjectType(
                    clazz,
                    clazz.findMethod("main", "([Ljava/lang/String;)V"),
                    ofType<CodeAttribute>(),
                    ofType<Int>(),
                    withArg {
                        clazz.getClassName(it.u2classIndex) shouldBe "[I"
                    },
                )
            }
        }
    }

    "Given a stackmap entry with a backwards branch" - {
        // Start building the class.
        val  programClass=  ClassBuilder(
            VersionConstants.CLASS_VERSION_1_8,
            AccessConstants.PUBLIC,
            "Test",
            ClassConstants.NAME_JAVA_LANG_OBJECT
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

            .addMethod(
                AccessConstants.PUBLIC,
                ClassConstants.METHOD_NAME_INIT,
                ClassConstants.METHOD_TYPE_INIT,
                50,

                { code ->
                    val lable1 = code.createLabel()
                    val lable2 = code.createLabel()

                    code.goto_(lable1)
                        .label(lable2)
                        .aload_0()
                        .invokespecial(ClassConstants.NAME_JAVA_LANG_OBJECT, ClassConstants.METHOD_NAME_INIT, ClassConstants.METHOD_TYPE_INIT)
                        .return_()
                        .label(lable1)
                        .goto_(lable2)
                }
            )
            .programClass;
//        if(true){
//            val dataOutputStream = DataOutputStream(FileOutputStream("/home/xc/Downloads/ReportAttachements/PG-keep/Test/Test.class"))
//            programClass.accept(ProgramClassWriter(dataOutputStream))
//            dataOutputStream.close()
//        }

        programClass.accept(
            AllMethodVisitor(
                AllAttributeVisitor(
                    CodePreverifier(false),
                ),
            ),
        )

//        if(true)
//        {
//            val dataOutputStream = DataOutputStream(FileOutputStream("/home/xc/Downloads/ReportAttachements/PG-keep/Test/Test1/Test.class"))
//            programClass.accept(ProgramClassWriter(dataOutputStream))
//            dataOutputStream.close()
//        }

        val programClassPool = ClassPool(programClass)

        "Then the local variable should be correct" {
            programClassPool.toString()
        }
    }


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




})
