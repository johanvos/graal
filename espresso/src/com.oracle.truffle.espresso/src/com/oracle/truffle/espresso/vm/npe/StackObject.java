/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.vm.npe;

import java.util.Objects;

final class StackObject {
    static final int UNKNOWN_BCI = -1;
    static final StackObject UNKNOWN_CONFLICT = new StackObject(UNKNOWN_BCI, StackType.CONFLICT);
    static final StackObject UNKNOWN_OBJECT = new StackObject(UNKNOWN_BCI, StackType.OBJECT);

    private final int originBci;
    private final StackType type;

    private StackObject(int originBci, StackType type) {
        assert type != StackType.VOID;
        this.originBci = originBci;
        this.type = type;
    }

    static StackObject create(int bci, StackType type) {
        if (bci == UNKNOWN_BCI) {
            if (type == StackType.CONFLICT) {
                return UNKNOWN_CONFLICT;
            }
            if (type == StackType.OBJECT) {
                return UNKNOWN_OBJECT;
            }
        }
        return new StackObject(bci, type);
    }

    static StackObject merge(StackObject o1, StackObject o2) {
        StackObject reuseCandidate = null;
        int mergeBci;
        StackType mergeType;

        if (o1.type() != o2.type()) {
            if (((o1.type() == StackType.OBJECT) && (o2.type() == StackType.ARRAY))) {
                mergeType = StackType.OBJECT;
                reuseCandidate = o1;
            } else if (((o1.type() == StackType.ARRAY) && (o2.type() == StackType.OBJECT))) {
                mergeType = StackType.OBJECT;
                reuseCandidate = o2;
            } else {
                return UNKNOWN_CONFLICT;
            }
        } else {
            mergeType = o1.type();
            reuseCandidate = o1;
        }

        if (o1.originBci() == o2.originBci()) {
            mergeBci = o1.originBci();
        } else {
            mergeBci = UNKNOWN_BCI;
            reuseCandidate = null;
        }

        if (reuseCandidate != null) {
            return reuseCandidate;
        }
        return create(mergeBci, mergeType);
    }

    public int originBci() {
        return originBci;
    }

    public boolean hasBci() {
        return originBci != UNKNOWN_BCI;
    }

    public StackType type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (StackObject) obj;
        return this.originBci() == that.originBci() &&
                        Objects.equals(this.type(), that.type());
    }

    @Override
    public int hashCode() {
        return Objects.hash(originBci(), type());
    }

    @Override
    public String toString() {
        return "StackObject[" +
                        "originBci=" + originBci() + ", " +
                        "type=" + type() + ']';
    }

}
