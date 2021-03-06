/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtolabs.rundeck.server.plugins.builder

import com.dtolabs.rundeck.core.logging.ExecutionFileStorageException
import com.dtolabs.rundeck.core.logging.ExecutionFileStorageOptions
import com.dtolabs.rundeck.core.plugins.configuration.Configurable
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException
import com.dtolabs.rundeck.core.plugins.configuration.Describable
import com.dtolabs.rundeck.core.plugins.configuration.Description
import com.dtolabs.rundeck.plugins.logging.ExecutionFileStoragePlugin
import org.apache.log4j.Logger

/**
 * ExecutionFileStoragePlugin implementation using closures defined in plugin DSL
 */
class ScriptExecutionFileStoragePlugin
        implements ExecutionFileStoragePlugin, Describable, Configurable, ExecutionFileStorageOptions {
    static Logger logger = Logger.getLogger(ScriptExecutionFileStoragePlugin)
    Description description
    protected Map<String, Closure> handlers
    Map configuration
    Map<String, ? extends Object> pluginContext
    boolean storeSupported
    boolean partialStoreSupported
    boolean retrieveSupported
    boolean partialRetrieveSupported

    ScriptExecutionFileStoragePlugin(Map<String, Closure> handlers, Description description) {
        this.handlers = handlers
        this.description = description
        this.storeSupported = true
        this.retrieveSupported = true
    }

    @Override
    void configure(Properties configuration) throws ConfigurationException {
        this.configuration = new HashMap(configuration)
    }


    @Override
    void initialize(Map<String, ? extends Object> context) {
        this.pluginContext = context
        this.storeSupported = handlers['store'] ? true : false
        this.partialStoreSupported = handlers['partialStore'] ? true : false
        this.partialRetrieveSupported = handlers['partialAvailable'] && handlers['partialRetrieve'] ? true : false
        this.retrieveSupported = (handlers['available'] != null && handlers['retrieve'] != null)
    }

    boolean isAvailable(String filetype) throws ExecutionFileStorageException {

        if(!retrieveSupported){
            throw new IllegalStateException("retrieve/available is not supported")
        }
        logger.debug("isAvailable(${filetype}) ${pluginContext}")
        def closure = handlers.available
        def binding = [
                configuration: configuration,
                context      : pluginContext + (filetype ? [filetype: filetype] : [:])
        ]
        def result = null
        if (closure.getMaximumNumberOfParameters() == 3) {
            def Closure newclos = closure.clone()
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            newclos.delegate = binding
            try {
                result = newclos.call(filetype, binding.context, binding.configuration)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else if (closure.getMaximumNumberOfParameters() == 2) {
            def Closure newclos = closure.clone()
            newclos.delegate = binding
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            try {
                result = newclos.call(filetype, binding.context)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else {
            throw new RuntimeException(
                    "ScriptExecutionFileStoragePlugin: 'available' closure signature invalid for plugin ${description.name}, cannot open"
            )
        }
        return result ? true : false
    }

    boolean isPartialAvailable(String filetype) throws ExecutionFileStorageException {

        if (!partialRetrieveSupported) {
            throw new IllegalStateException("partialRetrieve/partialAvailable is not supported")
        }
        logger.debug("isAvailable(${filetype}) ${pluginContext}")
        return execAvailableClosure(filetype)
    }

    private boolean execAvailableClosure(String filetype, String action) {
        def closure = handlers[action]
        def binding = [
                configuration: configuration,
                context      : pluginContext + (filetype ? [filetype: filetype] : [:])
        ]
        def result = null
        if (closure.getMaximumNumberOfParameters() == 3) {
            def Closure newclos = closure.clone()
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            newclos.delegate = binding
            try {
                result = newclos.call(filetype, binding.context, binding.configuration)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else if (closure.getMaximumNumberOfParameters() == 2) {
            def Closure newclos = closure.clone()
            newclos.delegate = binding
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            try {
                result = newclos.call(filetype, binding.context)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else {
            throw new RuntimeException(
                    "ScriptExecutionFileStoragePlugin: '${action}' closure signature invalid for plugin " +
                            "${description.name}, cannot open"
            )
        }
        return result ? true : false
    }

    boolean store(String filetype, InputStream stream, long length, Date lastModified)
            throws IOException, ExecutionFileStorageException
    {
        if(!storeSupported){
            throw new IllegalStateException("store is not supported")
        }
        logger.debug("store($filetype) ${pluginContext}")
        def binding = [
                configuration: configuration,
                context      : pluginContext + (filetype ? [filetype: filetype] : [:]),
                stream       : stream,
                length       : length,
                lastModified : lastModified
        ]
        return execStoreClosure(binding, filetype, 'store')
    }

    boolean partialStore(String filetype, InputStream stream, long length, Date lastModified)
            throws IOException, ExecutionFileStorageException
    {
        if (!partialStoreSupported) {
            throw new IllegalStateException("partialStore is not supported")
        }
        logger.debug("partialStore($filetype) ${pluginContext}")
        def binding = [
                configuration: configuration,
                context      : pluginContext + (filetype ? [filetype: filetype] : [:]),
                stream       : stream,
                length       : length,
                lastModified : lastModified
        ]
        return execStoreClosure(binding, filetype, 'partialStore')
    }

    private Object execStoreClosure(
            LinkedHashMap<String, Object> binding,
            String filetype,
            String action
    )
    {
        def closure = handlers[action]

        if (closure.getMaximumNumberOfParameters() == 4) {
            def Closure newclos = closure.clone()
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            newclos.delegate = binding
            try {
                return newclos.call(filetype, binding.context, binding.configuration, binding.stream)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else if (closure.getMaximumNumberOfParameters() == 3) {
            def Closure newclos = closure.clone()
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            newclos.delegate = binding
            try {
                return newclos.call(filetype, binding.context, binding.stream)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else if (closure.getMaximumNumberOfParameters() == 2) {
            def Closure newclos = closure.clone()
            newclos.delegate = binding
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            try {
                return newclos.call(filetype, binding.stream)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else {
            throw new RuntimeException(
                    "ScriptExecutionFileStoragePlugin: '$action' closure signature invalid for plugin " +
                            "${description.name}, cannot open"
            )
        }
    }

    @Override
    boolean retrieve(String filetype, OutputStream stream) throws IOException, ExecutionFileStorageException {

        if(!retrieveSupported){
            throw new IllegalStateException("retrieve/available is not supported")
        }
        return execRetrieveClosure(filetype, stream, 'retrieve')
    }

    @Override
    boolean partialRetrieve(String filetype, OutputStream stream) throws IOException, ExecutionFileStorageException {

        if (!partialRetrieveSupported) {
            throw new IllegalStateException("partialRetrieve/partialAvailable is not supported")
        }
        return execRetrieveClosure(filetype, stream, 'partialRetrieve')
    }

    private Object execRetrieveClosure(String filetype, OutputStream stream, String action) {
        logger.debug("$action($filetype) ${pluginContext}")
        def closure = handlers[action]
        def binding = [
                configuration: configuration,
                context      : pluginContext + (filetype ? [filetype: filetype] : [:]),
                stream       : stream,
        ]
        if (closure.getMaximumNumberOfParameters() == 4) {
            def Closure newclos = closure.clone()
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            newclos.delegate = binding
            try {
                return newclos.call(filetype, binding.context, binding.configuration, binding.stream)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else if (closure.getMaximumNumberOfParameters() == 3) {
            def Closure newclos = closure.clone()
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            newclos.delegate = binding
            try {
                return newclos.call(filetype, binding.context, binding.stream)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else if (closure.getMaximumNumberOfParameters() == 2) {
            def Closure newclos = closure.clone()
            newclos.delegate = binding
            newclos.resolveStrategy = Closure.DELEGATE_ONLY
            try {
                return newclos.call(filetype, binding.stream)
            } catch (Exception e) {
                throw new ExecutionFileStorageException(e.getMessage(), e)
            }
        } else {
            throw new RuntimeException(
                    "ScriptExecutionFileStoragePlugin: '$action' closure signature invalid for plugin " +
                            "${description.name}, cannot open"
            )
        }
    }


    public static boolean validAvailableClosure(Closure closure) {
        if (closure.getMaximumNumberOfParameters() == 3) {
            return closure.parameterTypes[0] == String &&
                    closure.parameterTypes[1] ==
                    Map &&
                    closure.parameterTypes[2] ==
                    Map
        } else if (closure.getMaximumNumberOfParameters() == 2) {
            return closure.parameterTypes[0] == String && closure.parameterTypes[1] == Map
        }
        return false
    }

    public static boolean validStoreClosure(Closure closure) {
        if (closure.getMaximumNumberOfParameters() == 4) {
            return closure.parameterTypes[0] == String &&
                    closure.parameterTypes[1] ==
                    Map &&
                    closure.parameterTypes[2] ==
                    Map &&
                    closure.parameterTypes[3] ==
                    InputStream
        } else if (closure.getMaximumNumberOfParameters() == 3) {
            return closure.parameterTypes[0] == String &&
                    closure.parameterTypes[1] ==
                    Map &&
                    closure.parameterTypes[2] ==
                    InputStream
        } else if (closure.getMaximumNumberOfParameters() == 2) {
            return closure.parameterTypes[0] == String && closure.parameterTypes[1] == Map
        }
        return false
    }

    public static boolean validRetrieveClosure(Closure closure) {
        if (closure.getMaximumNumberOfParameters() == 4) {
            return closure.parameterTypes[0] == String &&
                    closure.parameterTypes[1] ==
                    Map &&
                    closure.parameterTypes[2] ==
                    Map &&
                    closure.parameterTypes[3] ==
                    OutputStream
        } else if (closure.getMaximumNumberOfParameters() == 3) {
            return closure.parameterTypes[0] == String &&
                    closure.parameterTypes[1] ==
                    Map &&
                    closure.parameterTypes[2] ==
                    OutputStream
        } else if (closure.getMaximumNumberOfParameters() == 2) {
            return closure.parameterTypes[0] == String && closure.parameterTypes[1] == Map
        }
        return false
    }

    @Override
    public java.lang.String toString() {
        return "ScriptExecutionFileStoragePlugin{" +
                "description=" + description +
                ", configuration=" + configuration +
                ", pluginContext=" + pluginContext +
                '}';
    }
}
