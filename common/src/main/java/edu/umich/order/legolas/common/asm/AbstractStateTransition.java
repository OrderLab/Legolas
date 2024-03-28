/*
 *  @author Haoze Wu <haoze@jhu.edu>
 *
 *  The Legolas Project
 *
 *  Copyright (c) 2024, University of Michigan, EECS, OrderLab.
 *      All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.umich.order.legolas.common.asm;

import java.util.Objects;

/**
 * Representing a transition between two abstract states
 * TODO: implement transition of the enhanced version state
 * TODO: to be used
 */
public class AbstractStateTransition {
    final AbstractState previousStateId;
    final AbstractState currentStateId;

    public AbstractStateTransition(AbstractState previousStateId,
            AbstractState currentStateId) {
        this.previousStateId = previousStateId;
        this.currentStateId = currentStateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractStateTransition that = (AbstractStateTransition) o;
        return Objects.equals(previousStateId, that.previousStateId) &&
                Objects.equals(currentStateId, that.currentStateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(previousStateId, currentStateId);
    }

    @Override
    public String toString() {
        return "AbstractStateTransition{" +
                "previousStateId=" + previousStateId +
                ", currentStateId=" + currentStateId +
                '}';
    }
}
