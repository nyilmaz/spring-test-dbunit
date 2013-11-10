/*
 * Copyright 2010 the original author or authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.springtestdbunit.annotation;

import com.github.springtestdbunit.DbUnitTestExecutionListener;

import java.lang.annotation.*;

/**
 * Test annotation which indicates how to put a database into a know state before tests are run. This annotation can be
 * placed on a class or on methods. When placed on a class the setup is applied before each test methods is executed.
 * 
 * @see DatabaseTearDown
 * @see ExpectedDatabase
 * @see DbUnitConfiguration
 * @see DbUnitTestExecutionListener
 * 
 * @author Phillip Webb
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface DatabaseSetup {

   DatabaseConnectionSetup[] connections();

}
