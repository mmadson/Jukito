/**
 * Copyright 2013 ArcBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.jukito;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.internal.Errors;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link Statement} invoking a method with parameters by filling-in these
 * parameters with injected instances.
 */
class InjectedStatement extends Statement {

    private final FrameworkMethod method;
    private final Object test;
    private final Injector injector;

    public InjectedStatement(FrameworkMethod method, Object test, Injector injector) {
        this.method = method;
        this.test = test;
        this.injector = injector;
    }

    @Override
    public void evaluate() throws Throwable {
        Method javaMethod = method.getMethod();
        Injector methodInjector = injector;

        UseModules useModules = javaMethod.getAnnotation(UseModules.class);
        if (useModules != null) {
            Class<? extends Module>[] moduleClasses = useModules.value();
            final boolean autoBindMocks = useModules.autoBindMocks();
            final Module[] modules = new Module[moduleClasses.length];
            for (int i = 0; i < modules.length; i++) {
                modules[i] = moduleClasses[i].newInstance();
            }
            TestModule jukitoModule;
            if (autoBindMocks) {
                jukitoModule = new JukitoModule() {
                    @Override
                    protected void configureTest() {
                        for (Module m : modules) {
                            install(m);
                        }
                    }
                };
            } else {
                jukitoModule = new TestModule() {
                    @Override
                    protected void configureTest() {
                        for (Module m : modules) {
                            install(m);
                        }
                    }
                };
            }
            methodInjector = Guice.createInjector(jukitoModule);
        }

        Errors errors = new Errors(javaMethod);
        List<Key<?>> keys = GuiceUtils.getMethodKeys(javaMethod, errors);
        errors.throwConfigurationExceptionIfErrorsExist();

        Iterator<Binding<?>> bindingIter;
        if (InjectedFrameworkMethod.class.isAssignableFrom(method.getClass())) {
            bindingIter = ((InjectedFrameworkMethod) method).getBindingsToUseForParameters().iterator();
        } else {
            bindingIter = new ArrayList<Binding<?>>().iterator();
        }

        List<Object> injectedParameters = new ArrayList<Object>();
        for (Key<?> key : keys) {
            if (!All.class.equals(key.getAnnotationType())) {
                injectedParameters.add(methodInjector.getInstance(key));
            } else {
                if (!bindingIter.hasNext()) {
                    throw new AssertionError("Expected more bindings to fill @All parameters.");
                }
                injectedParameters.add(methodInjector.getInstance(bindingIter.next().getKey()));
            }
        }

        method.invokeExplosively(test, injectedParameters.toArray());
    }
}
