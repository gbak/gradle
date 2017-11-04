/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.binarycompatibility.rules

import me.champeau.gradle.japicmp.report.ViolationCheckContext
import spock.lang.Specification
import javassist.ClassPool
import japicmp.model.JApiClass


abstract class AbstractContextAwareRuleSpecification extends Specification {
    ViolationCheckContext context = new ViolationCheckContext() {
        Map userData = [seenApiChanges: [] as Set]

        String getClassName() { return null }

        Map<String, ?> getUserData() { return userData }

        Object getUserData(String key) {
            return userData[key]
        }

        void putUserData(String key, Object value) {
            userData[key] = value
        }
    }

    ClassPool instanceScopedPool = new ClassPool()

    JApiClass apiClass = Stub(JApiClass)

    def setup() {
        instanceScopedPool.appendSystemPath()
    }

    boolean noViolation(def rule){
        rule.maybeViolation(apiClass) == null
    }
}
