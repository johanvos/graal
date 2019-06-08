/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.graal.llvm;

import static com.oracle.svm.core.util.VMError.unimplemented;
import static org.graalvm.compiler.core.llvm.LLVMUtils.getVal;

import org.bytedeco.javacpp.LLVM.LLVMContextRef;
import org.bytedeco.javacpp.LLVM.LLVMValueRef;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.llvm.LLVMGenerationResult;
import org.graalvm.compiler.core.llvm.LLVMGenerator;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMKindTool;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.Platform;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

public class SubstrateLLVMGenerator extends LLVMGenerator implements SubstrateLIRGenerator {
    private final boolean isEntryPoint;
    private LLVMValueRef savedThreadPointer;

    SubstrateLLVMGenerator(Providers providers, LLVMGenerationResult generationResult, ResolvedJavaMethod method, LLVMContextRef context, int debugLevel) {
        super(providers, generationResult, method, new SubstrateLLVMIRBuilder(SubstrateUtil.uniqueShortName(method), context, shouldTrackPointers(method)),
                        new LLVMKindTool(context), debugLevel);
        /*
         * Called from native code so the workaround must not be applied here.
         */
        boolean applyAArchReturnTypeWorkaround = Platform.includedIn(Platform.AArch64.class) &&
                        !(method.getName().contains("GetDoubleField") ||
                                        method.getName().contains("GetFloatField") ||
                                        method.getName().contains("GetStaticDoubleField") ||
                                        method.getName().contains("CallStaticDoubleMethod") ||
                                        method.getName().contains("CallStaticFloatMethod") ||
                                        method.getName().contains("CallFloatMethod") ||
                                        method.getName().contains("CallDoubleMethod") ||
                                        method.getName().contains("GetStaticFloatField"));
        LLVMGenerator.applyAArchReturnFPReturnTypeWorkaround.set(applyAArchReturnTypeWorkaround);
        this.isEntryPoint = ((SharedMethod) method).isEntryPoint();
    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        if (javaKind != JavaKind.Void) {
            LLVMValueRef retVal = getVal(input);
            if (LLVMGenerator.applyAArchReturnFPReturnTypeWorkaround.get() && javaKind == JavaKind.Double) {
                retVal = builder.buildBitcast(retVal, builder.longType());
                emitFunctionEpilogue();
                builder.buildRet(retVal);
                return;
            } else if (LLVMGenerator.applyAArchReturnFPReturnTypeWorkaround.get() && javaKind == JavaKind.Float) {
                retVal = builder.buildBitcast(retVal, builder.intType());
                retVal = builder.buildZExt(retVal, Long.SIZE);
                retVal = builder.buildBitcast(retVal, builder.longType());
                emitFunctionEpilogue();
                builder.buildRet(retVal);
                return;
            }
        }
        super.emitReturn(javaKind, input);
    }

    private static boolean shouldTrackPointers(ResolvedJavaMethod method) {
        return !GuardedAnnotationAccess.isAnnotationPresent(method, Uninterruptible.class);
    }

    @Override
    public void emitVerificationMarker(Object marker) {
        /*
         * No-op, for now we do not have any verification of the LLVM IR that requires the markers.
         */
    }

    @Override
    public void emitFarReturn(AllocatableValue result, Value sp, Value setjmpBuffer) {
        /* Exception unwinding is handled by libunwind */
        throw unimplemented();
    }

    @Override
    public void emitDeadEnd() {
        emitPrintf("Dead end");
        builder.buildUnreachable();
    }

    @Override
    protected ResolvedJavaMethod findForeignCallTarget(ForeignCallDescriptor descriptor) {
        return ((SnippetRuntime.SubstrateForeignCallDescriptor) descriptor).findMethod(getMetaAccess());
    }

    @Override
    protected CallingConvention.Type getForeignCallCallingConvention(ForeignCallLinkage linkage) {
        return ((SubstrateCallingConvention) linkage.getOutgoingCallingConvention()).getType();
    }

    @Override
    public String getFunctionName(ResolvedJavaMethod method) {
        return SubstrateUtil.uniqueShortName(method);
    }

    @Override
    protected JavaKind getTypeKind(ResolvedJavaType type) {
        return ((HostedType) type).getStorageKind();
    }

    @Override
    public SubstrateRegisterConfig getRegisterConfig() {
        return (SubstrateRegisterConfig) super.getRegisterConfig();
    }

    @Override
    protected void emitFunctionPrologue() {
        if (SubstrateOptions.MultiThreaded.getValue() && isEntryPoint) {
            savedThreadPointer = builder.buildInlineGetRegister(getRegisterConfig().getThreadRegister().name);
        }
    }

    @Override
    protected void emitFunctionEpilogue() {
        if (SubstrateOptions.MultiThreaded.getValue() && isEntryPoint) {
            builder.buildInlineSetRegister(getRegisterConfig().getThreadRegister().name, savedThreadPointer);
        }
    }
}
