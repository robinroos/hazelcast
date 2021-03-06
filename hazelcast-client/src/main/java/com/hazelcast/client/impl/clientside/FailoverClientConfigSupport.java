/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.client.impl.clientside;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientFailoverConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.client.config.XmlClientConfigLocator;
import com.hazelcast.client.config.XmlClientFailoverConfigBuilder;
import com.hazelcast.client.config.YamlClientConfigBuilder;
import com.hazelcast.client.config.YamlClientConfigLocator;
import com.hazelcast.config.InvalidConfigurationException;

import java.util.List;


/**
 * Static methods to resolve and validate multiple client configs for blue green feature
 */
public final class FailoverClientConfigSupport {

    private FailoverClientConfigSupport() {
    }

    /**
     * If clientFailoverConfig passed null, then we try to load config via system property.
     * If still could no create HazelcastException is thrown.
     *
     * @param clientFailoverConfig provided via HazelcastClient.newHazelcastClient(ClientFailoverConfig config)
     * @return resolvedConfigs
     * @throws InvalidConfigurationException when given config is not valid
     */
    public static ClientFailoverConfig resolveClientConfig(ClientFailoverConfig clientFailoverConfig) {
        if (clientFailoverConfig == null) {
            XmlClientFailoverConfigBuilder configBuilder = new XmlClientFailoverConfigBuilder();
            clientFailoverConfig = configBuilder.build();
        }
        checkValidAlternative(clientFailoverConfig.getClientConfigs());
        return clientFailoverConfig;
    }

    /**
     * Returns a ClientFailoverConfig with single client config. If clientConfig is null,
     * We create it via XmlClientConfigBuilder().build()
     *
     * @param config provided via HazelcastClient.newHazelcastClient(ClientConfig config)
     * @return resolvedConfigs
     * @throws InvalidConfigurationException when given config is not valid
     */
    public static ClientFailoverConfig resolveClientConfig(ClientConfig config) {
        if (config == null) {
            config = createDefaultClientConfig();
        }
        ClientFailoverConfig resolvedConfig = new ClientFailoverConfig();
        resolvedConfig.addClientConfig(config);
        resolvedConfig.setTryCount(1);
        return resolvedConfig;
    }

    private static ClientConfig createDefaultClientConfig() {
        ClientConfig config;
        XmlClientConfigLocator xmlConfigLocator = new XmlClientConfigLocator();
        YamlClientConfigLocator yamlConfigLocator = new YamlClientConfigLocator();

        if (xmlConfigLocator.locateFromSystemProperty()) {
            // 1. Try loading XML config if provided in system property
            config = new XmlClientConfigBuilder(xmlConfigLocator).build();

        } else if (yamlConfigLocator.locateFromSystemProperty()) {
            // 2. Try loading YAML config if provided in system property
            config = new YamlClientConfigBuilder(yamlConfigLocator).build();

        } else if (xmlConfigLocator.locateInWorkDirOrOnClasspath()) {
            // 3. Try loading XML config from the working directory or from the classpath
            config = new XmlClientConfigBuilder(xmlConfigLocator).build();

        } else if (yamlConfigLocator.locateInWorkDirOrOnClasspath()) {
            // 4. Try loading YAML config from the working directory or from the classpath
            config = new YamlClientConfigBuilder(yamlConfigLocator).build();

        } else {
            // 5. Loading the default XML configuration file
            xmlConfigLocator.locateDefault();
            config = new XmlClientConfigBuilder(xmlConfigLocator).build();
        }
        return config;
    }

    /**
     * Creates ClientFailoverConfig is created which is equivalent of single client config.
     *
     * used with HazelcastClient.newHazelcastClient()
     *
     * @return resolved configs
     */
    public static ClientFailoverConfig resolveClientConfig() {
        ClientFailoverConfig resolvedConfig = new ClientFailoverConfig();

        ClientConfig config = createDefaultClientConfig();
        resolvedConfig.addClientConfig(config);
        resolvedConfig.setTryCount(1);
        return resolvedConfig;
    }

    /**
     * For a client to be valid alternative, all configurations should be equal except
     * GroupConfig
     * SecurityConfig
     * Discovery related parts of NetworkConfig
     * Credentials related configs
     *
     * @param alternativeClientConfigs to check if they are valid alternative for a single client two switch between clusters
     * @throws InvalidConfigurationException when given configs are not valid
     */
    private static void checkValidAlternative(List<ClientConfig> alternativeClientConfigs) {
        if (alternativeClientConfigs.isEmpty()) {
            throw new InvalidConfigurationException("ClientFailoverConfig should have at least one client config.");
        }
        ClientConfig mainConfig = alternativeClientConfigs.get(0);
        for (ClientConfig alternativeClientConfig : alternativeClientConfigs.subList(1, alternativeClientConfigs.size())) {
            checkValidAlternative(mainConfig, alternativeClientConfig);
        }

    }

    private static void throwInvalidConfigurationException(String rootClusterName, String clusterName, String configName) {
        throw new InvalidConfigurationException("Alternative config with cluster name " + clusterName
                + " has a different config than the initial config with cluster name " + rootClusterName + " for " + configName);
    }

    /**
     * @return false when both objects are null or they are equal, true otherwise
     */
    private static boolean notEqual(Object l, Object r) {
        return l != null ? !l.equals(r) : r != null;
    }

    @SuppressWarnings({"checkstyle:cyclomaticcomplexity", "checkstyle:npathcomplexity", "checkstyle:methodlength"})
    private static void checkValidAlternative(ClientConfig mainConfig, ClientConfig alternativeConfig) {
        String mainClusterName = mainConfig.getGroupConfig().getName();
        String alterNativeClusterName = alternativeConfig.getGroupConfig().getName();

        checkValidAlternativeForNetwork(mainConfig, alternativeConfig);

        if (mainConfig.getExecutorPoolSize() != alternativeConfig.getExecutorPoolSize()) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "executorPoolSize");
        }
        if (notEqual(mainConfig.getProperties(), alternativeConfig.getProperties())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "properties");
        }
        if (notEqual(mainConfig.getLoadBalancer(), alternativeConfig.getLoadBalancer())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "loadBalancer");
        }
        if (notEqual(mainConfig.getListenerConfigs(), alternativeConfig.getListenerConfigs())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "listeners");
        }
        if (notEqual(mainConfig.getInstanceName(), alternativeConfig.getInstanceName())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "instanceName");
        }
        if (notEqual(mainConfig.getConfigPatternMatcher(), alternativeConfig.getConfigPatternMatcher())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "configPatternMatcher");
        }
        if (notEqual(mainConfig.getNearCacheConfigMap(), alternativeConfig.getNearCacheConfigMap())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "nearCache");
        }
        if (notEqual(mainConfig.getReliableTopicConfigMap(), alternativeConfig.getReliableTopicConfigMap())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "reliableTopic");
        }
        if (notEqual(mainConfig.getQueryCacheConfigs(), alternativeConfig.getQueryCacheConfigs())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "queryCacheConfigs");
        }
        if (notEqual(mainConfig.getSerializationConfig(), alternativeConfig.getSerializationConfig())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "serializationConfig");
        }
        if (notEqual(mainConfig.getNativeMemoryConfig(), alternativeConfig.getNativeMemoryConfig())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "nativeMemory");
        }
        if (notEqual(mainConfig.getProxyFactoryConfigs(), alternativeConfig.getProxyFactoryConfigs())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "proxyFactory");
        }
        if (notEqual(mainConfig.getManagedContext(), alternativeConfig.getManagedContext())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "managedContext");
        }
        if (notEqual(mainConfig.getClassLoader(), alternativeConfig.getClassLoader())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "classLoader");
        }
        if (notEqual(mainConfig.getLicenseKey(), alternativeConfig.getLicenseKey())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "licenseKey");
        }
        if (notEqual(mainConfig.getConnectionStrategyConfig(), alternativeConfig.getConnectionStrategyConfig())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "connectionStrategy");
        }
        if (notEqual(mainConfig.getUserCodeDeploymentConfig(), alternativeConfig.getUserCodeDeploymentConfig())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "userCodeDeployment");
        }
        if (notEqual(mainConfig.getFlakeIdGeneratorConfigMap(), alternativeConfig.getFlakeIdGeneratorConfigMap())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "flakeIdGenerator");
        }
        if (notEqual(mainConfig.getLabels(), alternativeConfig.getLabels())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "labels");
        }
        if (notEqual(mainConfig.getUserContext(), alternativeConfig.getUserContext())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "userContext");
        }
    }

    @SuppressWarnings({"checkstyle:cyclomaticcomplexity", "checkstyle:npathcomplexity", "checkstyle:methodlength"})
    private static void checkValidAlternativeForNetwork(ClientConfig mainConfig, ClientConfig alternativeConfig) {
        String mainClusterName = mainConfig.getGroupConfig().getName();
        String alterNativeClusterName = alternativeConfig.getGroupConfig().getName();

        ClientNetworkConfig mainNetworkConfig = mainConfig.getNetworkConfig();
        ClientNetworkConfig alternativeNetworkConfig = alternativeConfig.getNetworkConfig();

        if (mainNetworkConfig == null && alternativeNetworkConfig == null) {
            return;
        }

        if (mainNetworkConfig == null || alternativeNetworkConfig == null) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network");
        }

        if (mainNetworkConfig.isSmartRouting() != alternativeNetworkConfig.isSmartRouting()) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:smartRouting");
        }
        if (mainNetworkConfig.isRedoOperation() != alternativeNetworkConfig.isRedoOperation()) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:redoOperation");
        }
        if (mainNetworkConfig.getConnectionTimeout() != alternativeNetworkConfig.getConnectionTimeout()) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:connectionTimeout");
        }
        if (mainNetworkConfig.getConnectionAttemptLimit() != alternativeNetworkConfig.getConnectionAttemptLimit()) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:connectionAttemptLimit");
        }
        if (mainNetworkConfig.getConnectionAttemptPeriod() != alternativeNetworkConfig.getConnectionAttemptPeriod()) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:connectionAttemptPeriod");
        }
        if (notEqual(mainNetworkConfig.getSocketOptions(), alternativeNetworkConfig.getSocketOptions())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:socketOptions");
        }
        if (notEqual(mainNetworkConfig.getOutboundPortDefinitions(), alternativeNetworkConfig.getOutboundPortDefinitions())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:outboundPortDefinitions");
        }
        if (notEqual(mainNetworkConfig.getOutboundPorts(), alternativeNetworkConfig.getOutboundPorts())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:smartRouting");
        }
        if (notEqual(mainNetworkConfig.getClientIcmpPingConfig(), alternativeNetworkConfig.getClientIcmpPingConfig())) {
            throwInvalidConfigurationException(mainClusterName, alterNativeClusterName, "network:clientIcmp");
        }
    }
}
