/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ReflectionTest {
    @Test
    public void testResolveClassNameBase() {
          assertEquals("org.linqs.psl.util.Reflection", Reflection.resolveClassName("Reflection"));
          assertEquals("org.linqs.psl.util.Reflection", Reflection.resolveClassName("org.linqs.psl.util.Reflection"));

          assertNull("org.linqs.psl.util.Reflection", Reflection.resolveClassName("eflection"));
          assertNull("org.linqs.psl.util.Reflection", Reflection.resolveClassName("abs"));

          assertEquals("org.linqs.psl.reasoner.admm.ADMMReasoner", Reflection.resolveClassName("ADMMReasoner"));
    }
}
