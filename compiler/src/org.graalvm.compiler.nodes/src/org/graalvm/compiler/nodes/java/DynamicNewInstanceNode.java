/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import java.lang.reflect.Modifier;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public class DynamicNewInstanceNode extends AbstractNewObjectNode implements Canonicalizable {
    public static final NodeClass<DynamicNewInstanceNode> TYPE = NodeClass.create(DynamicNewInstanceNode.class);

    @Input ValueNode clazz;

    public DynamicNewInstanceNode(ValueNode clazz, boolean fillContents) {
        this(TYPE, clazz, fillContents, null);
    }

    protected DynamicNewInstanceNode(NodeClass<? extends DynamicNewInstanceNode> c, ValueNode clazz, boolean fillContents, FrameState stateBefore) {
        super(c, StampFactory.objectNonNull(), fillContents, stateBefore);
        this.clazz = clazz;
        assert ((ObjectStamp) clazz.stamp(NodeView.DEFAULT)).nonNull();
    }

    public ValueNode getInstanceType() {
        return clazz;
    }

    public static boolean canConvertToNonDynamic(ValueNode clazz, CanonicalizerTool tool) {
        if (clazz.isConstant()) {
            ResolvedJavaType type = tool.getConstantReflection().asJavaType(clazz.asConstant());
            if (type != null && !throwsInstantiationException(type, tool.getMetaAccess()) && tool.getMetaAccessExtensionProvider().canConstantFoldDynamicAllocation(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (canConvertToNonDynamic(clazz, tool)) {
            ResolvedJavaType type = tool.getConstantReflection().asJavaType(clazz.asConstant());
            return new NewInstanceNode(type, fillContents(), stateBefore());
        }
        return this;
    }

    public static boolean throwsInstantiationException(Class<?> type, Class<?> classClass) {
        return type.isPrimitive() || type.isArray() || type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type == classClass;
    }

    public static boolean throwsInstantiationException(ResolvedJavaType type, MetaAccessProvider metaAccess) {
        return type.isPrimitive() || type.isArray() || type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.equals(metaAccess.lookupJavaType(Class.class));
    }
}
