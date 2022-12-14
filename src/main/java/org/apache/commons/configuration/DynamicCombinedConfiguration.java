/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.configuration;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.configuration.event.ConfigurationErrorListener;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.apache.commons.configuration.interpol.ConfigurationInterpolator;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.apache.commons.configuration.tree.ExpressionEngine;
import org.apache.commons.configuration.tree.NodeCombiner;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * DynamicCombinedConfiguration allows a set of CombinedConfigurations to be used. Each CombinedConfiguration
 * is referenced by a key that is dynamically constructed from a key pattern on each call. The key pattern
 * will be resolved using the configured ConfigurationInterpolator.
 * @since 1.6
 * @author <a
 * href="http://commons.apache.org/configuration/team-list.html">Commons
 * Configuration team</a>
 * @version $Id: DynamicCombinedConfiguration.java 1534064 2013-10-21 08:44:33Z henning $
 */
public class DynamicCombinedConfiguration extends CombinedConfiguration
{
    /**
     * Prevent recursion while resolving unprefixed properties.
     */
    private static ThreadLocal<Boolean> recursive = new ThreadLocal<Boolean>()
    {
        @Override
        protected synchronized Boolean initialValue()
        {
            return Boolean.FALSE;
        }
    };

    /** The CombinedConfigurations */
    private final ConcurrentMap<String, CombinedConfiguration> configs =
            new ConcurrentHashMap<String, CombinedConfiguration>();

    /** Stores a list with the contained configurations. */
    private List<ConfigData> configurations = new ArrayList<ConfigData>();

    /** Stores a map with the named configurations. */
    private Map<String, AbstractConfiguration> namedConfigurations =
            new HashMap<String, AbstractConfiguration>();

    /** The key pattern for the CombinedConfiguration map */
    private String keyPattern;

    /** Stores the combiner. */
    private NodeCombiner nodeCombiner;

    /** The name of the logger to use for each CombinedConfiguration */
    private String loggerName = DynamicCombinedConfiguration.class.getName();

    /** The object for handling variable substitution in key patterns. */
    private StrSubstitutor localSubst = new StrSubstitutor(new ConfigurationInterpolator());

    /**
     * Creates a new instance of {@code DynamicCombinedConfiguration} and
     * initializes the combiner to be used.
     *
     * @param comb the node combiner (can be <b>null</b>, then a union combiner
     * is used as default)
     */
    public DynamicCombinedConfiguration(NodeCombiner comb)
    {
        super();
        setNodeCombiner(comb);
        setIgnoreReloadExceptions(false);
        setLogger(LogFactory.getLog(DynamicCombinedConfiguration.class));
    }

    /**
     * Creates a new instance of {@code DynamicCombinedConfiguration} that uses
     * a union combiner.
     *
     * @see org.apache.commons.configuration.tree.UnionCombiner
     */
    public DynamicCombinedConfiguration()
    {
        super();
        setIgnoreReloadExceptions(false);
        setLogger(LogFactory.getLog(DynamicCombinedConfiguration.class));
    }

    public void setKeyPattern(String pattern)
    {
        this.keyPattern = pattern;
    }

    public String getKeyPattern()
    {
        return this.keyPattern;
    }

    /**
     * Set the name of the Logger to use on each CombinedConfiguration.
     * @param name The Logger name.
     */
    public void setLoggerName(String name)
    {
        this.loggerName = name;
    }

    /**
     * Returns the node combiner that is used for creating the combined node
     * structure.
     *
     * @return the node combiner
     */
    @Override
    public NodeCombiner getNodeCombiner()
    {
        return nodeCombiner;
    }

    /**
     * Sets the node combiner. This object will be used when the combined node
     * structure is to be constructed. It must not be <b>null</b>, otherwise an
     * {@code IllegalArgumentException} exception is thrown. Changing the
     * node combiner causes an invalidation of this combined configuration, so
     * that the new combiner immediately takes effect.
     *
     * @param nodeCombiner the node combiner
     */
    @Override
    public void setNodeCombiner(NodeCombiner nodeCombiner)
    {
        if (nodeCombiner == null)
        {
            throw new IllegalArgumentException(
                    "Node combiner must not be null!");
        }
        this.nodeCombiner = nodeCombiner;
        invalidateAll();
    }
    /**
     * Adds a new configuration to this combined configuration. It is possible
     * (but not mandatory) to give the new configuration a name. This name must
     * be unique, otherwise a {@code ConfigurationRuntimeException} will
     * be thrown. With the optional {@code at} argument you can specify
     * where in the resulting node structure the content of the added
     * configuration should appear. This is a string that uses dots as property
     * delimiters (independent on the current expression engine). For instance
     * if you pass in the string {@code "database.tables"},
     * all properties of the added configuration will occur in this branch.
     *
     * @param config the configuration to add (must not be <b>null</b>)
     * @param name the name of this configuration (can be <b>null</b>)
     * @param at the position of this configuration in the combined tree (can be
     * <b>null</b>)
     */
    @Override
    public void addConfiguration(AbstractConfiguration config, String name,
            String at)
    {
        ConfigData cd = new ConfigData(config, name, at);
        configurations.add(cd);
        if (name != null)
        {
            namedConfigurations.put(name, config);
        }
    }
       /**
     * Returns the number of configurations that are contained in this combined
     * configuration.
     *
     * @return the number of contained configurations
     */
    @Override
    public int getNumberOfConfigurations()
    {
        return configurations.size();
    }

    /**
     * Returns the configuration at the specified index. The contained
     * configurations are numbered in the order they were added to this combined
     * configuration. The index of the first configuration is 0.
     *
     * @param index the index
     * @return the configuration at this index
     */
    @Override
    public Configuration getConfiguration(int index)
    {
        ConfigData cd = configurations.get(index);
        return cd.getConfiguration();
    }

    /**
     * Returns the configuration with the given name. This can be <b>null</b>
     * if no such configuration exists.
     *
     * @param name the name of the configuration
     * @return the configuration with this name
     */
    @Override
    public Configuration getConfiguration(String name)
    {
        return namedConfigurations.get(name);
    }

    /**
     * Returns a set with the names of all configurations contained in this
     * combined configuration. Of course here are only these configurations
     * listed, for which a name was specified when they were added.
     *
     * @return a set with the names of the contained configurations (never
     * <b>null</b>)
     */
    @Override
    public Set<String> getConfigurationNames()
    {
        return namedConfigurations.keySet();
    }

    /**
     * Removes the configuration with the specified name.
     *
     * @param name the name of the configuration to be removed
     * @return the removed configuration (<b>null</b> if this configuration
     * was not found)
     */
    @Override
    public Configuration removeConfiguration(String name)
    {
        Configuration conf = getConfiguration(name);
        if (conf != null)
        {
            removeConfiguration(conf);
        }
        return conf;
    }

    /**
     * Removes the specified configuration from this combined configuration.
     *
     * @param config the configuration to be removed
     * @return a flag whether this configuration was found and could be removed
     */
    @Override
    public boolean removeConfiguration(Configuration config)
    {
        for (int index = 0; index < getNumberOfConfigurations(); index++)
        {
            if (configurations.get(index).getConfiguration() == config)
            {
                removeConfigurationAt(index);

            }
        }

        return super.removeConfiguration(config);
    }

    /**
     * Removes the configuration at the specified index.
     *
     * @param index the index
     * @return the removed configuration
     */
    @Override
    public Configuration removeConfigurationAt(int index)
    {
        ConfigData cd = configurations.remove(index);
        if (cd.getName() != null)
        {
            namedConfigurations.remove(cd.getName());
        }
        return super.removeConfigurationAt(index);
    }
    /**
     * Returns the configuration root node of this combined configuration. This
     * method will construct a combined node structure using the current node
     * combiner if necessary.
     *
     * @return the combined root node
     */
    @Override
    public ConfigurationNode getRootNode()
    {
        return getCurrentConfig().getRootNode();
    }

    @Override
    public void setRootNode(ConfigurationNode rootNode)
    {
        if (configs != null)
        {
            this.getCurrentConfig().setRootNode(rootNode);
        }
        else
        {
            super.setRootNode(rootNode);
        }
    }

    @Override
    public void addProperty(String key, Object value)
    {
        this.getCurrentConfig().addProperty(key, value);
    }

    @Override
    public void clear()
    {
        if (configs != null)
        {
            this.getCurrentConfig().clear();
        }
    }

    @Override
    public void clearProperty(String key)
    {
        this.getCurrentConfig().clearProperty(key);
    }

    @Override
    public boolean containsKey(String key)
    {
        return this.getCurrentConfig().containsKey(key);
    }

    @Override
    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue)
    {
        return this.getCurrentConfig().getBigDecimal(key, defaultValue);
    }

    @Override
    public BigDecimal getBigDecimal(String key)
    {
        return this.getCurrentConfig().getBigDecimal(key);
    }

    @Override
    public BigInteger getBigInteger(String key, BigInteger defaultValue)
    {
        return this.getCurrentConfig().getBigInteger(key, defaultValue);
    }

    @Override
    public BigInteger getBigInteger(String key)
    {
        return this.getCurrentConfig().getBigInteger(key);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue)
    {
        return this.getCurrentConfig().getBoolean(key, defaultValue);
    }

    @Override
    public Boolean getBoolean(String key, Boolean defaultValue)
    {
        return this.getCurrentConfig().getBoolean(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key)
    {
        return this.getCurrentConfig().getBoolean(key);
    }

    @Override
    public byte getByte(String key, byte defaultValue)
    {
        return this.getCurrentConfig().getByte(key, defaultValue);
    }

    @Override
    public Byte getByte(String key, Byte defaultValue)
    {
        return this.getCurrentConfig().getByte(key, defaultValue);
    }

    @Override
    public byte getByte(String key)
    {
        return this.getCurrentConfig().getByte(key);
    }

    @Override
    public double getDouble(String key, double defaultValue)
    {
        return this.getCurrentConfig().getDouble(key, defaultValue);
    }

    @Override
    public Double getDouble(String key, Double defaultValue)
    {
        return this.getCurrentConfig().getDouble(key, defaultValue);
    }

    @Override
    public double getDouble(String key)
    {
        return this.getCurrentConfig().getDouble(key);
    }

    @Override
    public float getFloat(String key, float defaultValue)
    {
        return this.getCurrentConfig().getFloat(key, defaultValue);
    }

    @Override
    public Float getFloat(String key, Float defaultValue)
    {
        return this.getCurrentConfig().getFloat(key, defaultValue);
    }

    @Override
    public float getFloat(String key)
    {
        return this.getCurrentConfig().getFloat(key);
    }

    @Override
    public int getInt(String key, int defaultValue)
    {
        return this.getCurrentConfig().getInt(key, defaultValue);
    }

    @Override
    public int getInt(String key)
    {
        return this.getCurrentConfig().getInt(key);
    }

    @Override
    public Integer getInteger(String key, Integer defaultValue)
    {
        return this.getCurrentConfig().getInteger(key, defaultValue);
    }

    @Override
    public Iterator<String> getKeys()
    {
        return this.getCurrentConfig().getKeys();
    }

    @Override
    public Iterator<String> getKeys(String prefix)
    {
        return this.getCurrentConfig().getKeys(prefix);
    }

    @Override
    public List<Object> getList(String key, List<?> defaultValue)
    {
        return this.getCurrentConfig().getList(key, defaultValue);
    }

    @Override
    public List<Object> getList(String key)
    {
        return this.getCurrentConfig().getList(key);
    }

    @Override
    public long getLong(String key, long defaultValue)
    {
        return this.getCurrentConfig().getLong(key, defaultValue);
    }

    @Override
    public Long getLong(String key, Long defaultValue)
    {
        return this.getCurrentConfig().getLong(key, defaultValue);
    }

    @Override
    public long getLong(String key)
    {
        return this.getCurrentConfig().getLong(key);
    }

    @Override
    public Properties getProperties(String key)
    {
        return this.getCurrentConfig().getProperties(key);
    }

    @Override
    public Object getProperty(String key)
    {
        return this.getCurrentConfig().getProperty(key);
    }

    @Override
    public short getShort(String key, short defaultValue)
    {
        return this.getCurrentConfig().getShort(key, defaultValue);
    }

    @Override
    public Short getShort(String key, Short defaultValue)
    {
        return this.getCurrentConfig().getShort(key, defaultValue);
    }

    @Override
    public short getShort(String key)
    {
        return this.getCurrentConfig().getShort(key);
    }

    @Override
    public String getString(String key, String defaultValue)
    {
        return this.getCurrentConfig().getString(key, defaultValue);
    }

    @Override
    public String getString(String key)
    {
        return this.getCurrentConfig().getString(key);
    }

    @Override
    public String[] getStringArray(String key)
    {
        return this.getCurrentConfig().getStringArray(key);
    }

    @Override
    public boolean isEmpty()
    {
        return this.getCurrentConfig().isEmpty();
    }

    @Override
    public void setProperty(String key, Object value)
    {
        if (configs != null)
        {
            this.getCurrentConfig().setProperty(key, value);
        }
    }

    @Override
    public Configuration subset(String prefix)
    {
        return this.getCurrentConfig().subset(prefix);
    }

    @Override
    public Node getRoot()
    {
        return this.getCurrentConfig().getRoot();
    }

    @Override
    public void setRoot(Node node)
    {
        if (configs != null)
        {
            this.getCurrentConfig().setRoot(node);
        }
        else
        {
            super.setRoot(node);
        }
    }

    @Override
    public ExpressionEngine getExpressionEngine()
    {
        return super.getExpressionEngine();
    }

    @Override
    public void setExpressionEngine(ExpressionEngine expressionEngine)
    {
        super.setExpressionEngine(expressionEngine);
    }

    @Override
    public void addNodes(String key, Collection<? extends ConfigurationNode> nodes)
    {
        this.getCurrentConfig().addNodes(key, nodes);
    }

    @Override
    public SubnodeConfiguration configurationAt(String key, boolean supportUpdates)
    {
        return this.getCurrentConfig().configurationAt(key, supportUpdates);
    }

    @Override
    public SubnodeConfiguration configurationAt(String key)
    {
        return this.getCurrentConfig().configurationAt(key);
    }

    @Override
    public List<HierarchicalConfiguration> configurationsAt(String key)
    {
        return this.getCurrentConfig().configurationsAt(key);
    }

    @Override
    public void clearTree(String key)
    {
        this.getCurrentConfig().clearTree(key);
    }

    @Override
    public int getMaxIndex(String key)
    {
        return this.getCurrentConfig().getMaxIndex(key);
    }

    @Override
    public Configuration interpolatedConfiguration()
    {
        return this.getCurrentConfig().interpolatedConfiguration();
    }


    /**
     * Returns the configuration source, in which the specified key is defined.
     * This method will determine the configuration node that is identified by
     * the given key. The following constellations are possible:
     * <ul>
     * <li>If no node object is found for this key, <b>null</b> is returned.</li>
     * <li>If the key maps to multiple nodes belonging to different
     * configuration sources, a {@code IllegalArgumentException} is
     * thrown (in this case no unique source can be determined).</li>
     * <li>If exactly one node is found for the key, the (child) configuration
     * object, to which the node belongs is determined and returned.</li>
     * <li>For keys that have been added directly to this combined
     * configuration and that do not belong to the namespaces defined by
     * existing child configurations this configuration will be returned.</li>
     * </ul>
     *
     * @param key the key of a configuration property
     * @return the configuration, to which this property belongs or <b>null</b>
     * if the key cannot be resolved
     * @throws IllegalArgumentException if the key maps to multiple properties
     * and the source cannot be determined, or if the key is <b>null</b>
     */
    @Override
    public Configuration getSource(String key)
    {
        if (key == null)
        {
            throw new IllegalArgumentException("Key must not be null!");
        }
        return getCurrentConfig().getSource(key);
    }

    @Override
    public void addConfigurationListener(ConfigurationListener l)
    {
        super.addConfigurationListener(l);

        for (CombinedConfiguration cc : configs.values())
        {
            cc.addConfigurationListener(l);
        }
    }

    @Override
    public boolean removeConfigurationListener(ConfigurationListener l)
    {
        for (CombinedConfiguration cc : configs.values())
        {
            cc.removeConfigurationListener(l);
        }
        return super.removeConfigurationListener(l);
    }

    @Override
    public Collection<ConfigurationListener> getConfigurationListeners()
    {
        return super.getConfigurationListeners();
    }

    @Override
    public void clearConfigurationListeners()
    {
        for (CombinedConfiguration cc : configs.values())
        {
            cc.clearConfigurationListeners();
        }
        super.clearConfigurationListeners();
    }

    @Override
    public void addErrorListener(ConfigurationErrorListener l)
    {
        for (CombinedConfiguration cc : configs.values())
        {
            cc.addErrorListener(l);
        }
        super.addErrorListener(l);
    }

    @Override
    public boolean removeErrorListener(ConfigurationErrorListener l)
    {
        for (CombinedConfiguration cc : configs.values())
        {
            cc.removeErrorListener(l);
        }
        return super.removeErrorListener(l);
    }

    @Override
    public void clearErrorListeners()
    {
        for (CombinedConfiguration cc : configs.values())
        {
            cc.clearErrorListeners();
        }
        super.clearErrorListeners();
    }

    @Override
    public Collection<ConfigurationErrorListener> getErrorListeners()
    {
        return super.getErrorListeners();
    }

    /**
     * Returns a copy of this object. This implementation performs a deep clone,
     * i.e. all contained configurations will be cloned, too. For this to work,
     * all contained configurations must be cloneable. Registered event
     * listeners won't be cloned. The clone will use the same node combiner than
     * the original.
     *
     * @return the copied object
     */
    @Override
    public Object clone()
    {
        return super.clone();
    }

    /**
     * Invalidates the current combined configuration. This means that the next time a
     * property is accessed the combined node structure must be re-constructed.
     * Invalidation of a combined configuration also means that an event of type
     * {@code EVENT_COMBINED_INVALIDATE} is fired. Note that while other
     * events most times appear twice (once before and once after an update),
     * this event is only fired once (after update).
     */
    @Override
    public void invalidate()
    {
        getCurrentConfig().invalidate();
    }

    public void invalidateAll()
    {
        if (configs == null)
        {
            return;
        }
        for (CombinedConfiguration cc : configs.values())
        {
            cc.invalidate();
        }
    }

    /*
     * Don't allow resolveContainerStore to be called recursively.
     * @param key The key to resolve.
     * @return The value of the key.
     */
    @Override
    protected Object resolveContainerStore(String key)
    {
        if (recursive.get().booleanValue())
        {
            return null;
        }
        recursive.set(Boolean.TRUE);
        try
        {
            return super.resolveContainerStore(key);
        }
        finally
        {
            recursive.set(Boolean.FALSE);
        }
    }

    private CombinedConfiguration getCurrentConfig()
    {
        String key = localSubst.replace(keyPattern);
        CombinedConfiguration config = configs.get(key);
        // The double-checked works here due to the Thread guarantees of ConcurrentMap.
        if (config == null)
        {
            synchronized (configs)
            {
                config = configs.get(key);
                if (config == null)
                {
                    config = new CombinedConfiguration(getNodeCombiner());
                    if (loggerName != null)
                    {
                        Log log = LogFactory.getLog(loggerName);
                        if (log != null)
                        {
                            config.setLogger(log);
                        }
                    }
                    config.setIgnoreReloadExceptions(isIgnoreReloadExceptions());
                    config.setExpressionEngine(this.getExpressionEngine());
                    config.setDelimiterParsingDisabled(isDelimiterParsingDisabled());
                    config.setConversionExpressionEngine(getConversionExpressionEngine());
                    config.setListDelimiter(getListDelimiter());
                    for (ConfigurationErrorListener listener : getErrorListeners())
                    {
                        config.addErrorListener(listener);
                    }
                    for (ConfigurationListener listener : getConfigurationListeners())
                    {
                        config.addConfigurationListener(listener);
                    }
                    config.setForceReloadCheck(isForceReloadCheck());
                    for (ConfigData data : configurations)
                    {
                        config.addConfiguration(data.getConfiguration(), data.getName(), data.getAt());
                    }
                    configs.put(key, config);
                }
            }
        }
        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("Returning config for " + key + ": " + config);
        }
        return config;
    }

    /**
     * Internal class that identifies each Configuration.
     */
    static class ConfigData
    {
        /** Stores a reference to the configuration. */
        private AbstractConfiguration configuration;

        /** Stores the name under which the configuration is stored. */
        private String name;

        /** Stores the at string.*/
        private String at;

        /**
         * Creates a new instance of {@code ConfigData} and initializes
         * it.
         *
         * @param config the configuration
         * @param n the name
         * @param at the at position
         */
        public ConfigData(AbstractConfiguration config, String n, String at)
        {
            configuration = config;
            name = n;
            this.at = at;
        }

        /**
         * Returns the stored configuration.
         *
         * @return the configuration
         */
        public AbstractConfiguration getConfiguration()
        {
            return configuration;
        }

        /**
         * Returns the configuration's name.
         *
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        /**
         * Returns the at position of this configuration.
         *
         * @return the at position
         */
        public String getAt()
        {
            return at;
        }

    }
}
