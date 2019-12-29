/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.extension.defaultassignments;

import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.util.Tristate;

import java.util.List;
import java.util.stream.Collectors;

class AssignmentRule {
    private final AssignmentExpression hasTrueExpression;
    private final AssignmentExpression hasFalseExpression;
    private final AssignmentExpression lacksExpression;

    private final List<Node> toGive;
    private final List<Node> toTake;
    private final String setPrimaryGroup;

    AssignmentRule(String hasTrueExpression, String hasFalseExpression, String lacksExpression, List<String> toGive, List<String> toTake, String setPrimaryGroup) {
        this.hasTrueExpression = hasTrueExpression == null ? null : new AssignmentExpression(hasTrueExpression);
        this.hasFalseExpression = hasFalseExpression == null ? null : new AssignmentExpression(hasFalseExpression);
        this.lacksExpression = lacksExpression == null ? null : new AssignmentExpression(lacksExpression);
        this.toGive = toGive.stream().map(s -> LegacyNodeFactory.fromLegacyString(s)).collect(Collectors.toList());
        this.toTake = toTake.stream().map(s -> LegacyNodeFactory.fromLegacyString(s)).collect(Collectors.toList());
        this.setPrimaryGroup = setPrimaryGroup;
    }

    private boolean evalExpression(User user, AssignmentExpression expression, Tristate tristate) {
        if (expression != null) {
            try {
                boolean b = expression.eval(user, tristate);
                if (!b) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public boolean apply(User user) {
        if (!evalExpression(user, this.hasTrueExpression, Tristate.TRUE)) {
            return false;
        }

        if (!evalExpression(user, this.hasFalseExpression, Tristate.FALSE)) {
            return false;
        }

        if (!evalExpression(user, this.lacksExpression, Tristate.UNDEFINED)) {
            return false;
        }

        // The holder meets all of the requirements of this rule.
        for (Node n : this.toTake) {
            user.data().remove(n);
        }

        for (Node n : this.toGive) {
            user.data().add(n);
        }

        if (this.setPrimaryGroup != null) {
            user.setPrimaryGroup(this.setPrimaryGroup);
        }

        return true;
    }

    @Override
    public String toString() {
        return "AssignmentRule(" +
                "hasTrueExpression=" + this.hasTrueExpression + ", " +
                "hasFalseExpression=" + this.hasFalseExpression + ", " +
                "lacksExpression=" + this.lacksExpression + ", " +
                "toGive=" + this.toGive + ", " +
                "toTake=" + this.toTake + ", " +
                "setPrimaryGroup=" + this.setPrimaryGroup + ")";
    }
}