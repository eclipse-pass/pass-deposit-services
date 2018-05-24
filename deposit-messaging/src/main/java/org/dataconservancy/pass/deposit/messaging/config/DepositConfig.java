/*
 * Copyright 2018 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.abdera.parser.Parser;
import org.apache.abdera.parser.stax.FOMParserFactory;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.commons.httpclient.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.dataconservancy.nihms.assembler.nihmsnative.NihmsAssembler;
import org.dataconservancy.nihms.builder.InvalidModel;
import org.dataconservancy.nihms.builder.fs.FcrepoModelBuilder;
import org.dataconservancy.nihms.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.nihms.transport.ftp.FtpTransport;
import org.dataconservancy.pass.client.PassClientDefault;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.deposit.assembler.dspace.mets.DspaceMetsAssembler;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.model.InMemoryMapRegistry;
import org.dataconservancy.pass.deposit.messaging.model.FcrepoRepositoriesSource;
import org.dataconservancy.pass.deposit.messaging.policy.DirtyDepositPolicy;
import org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomFeedStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.RepositoryCopyStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.AtomFeedStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatusMapper;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2Transport;
import org.dataconservancy.pass.model.Policy;
import org.dataconservancy.pass.model.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Base64.getEncoder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Configuration
public class DepositConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DepositConfig.class);

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    @Value("${pass.fedora.user}")
    private String fedoraUser;

    @Value("${pass.fedora.password}")
    private String fedoraPass;

    @Value("${pass.fedora.baseurl}")
    private String fedoraBaseUrl;

    @Value("${pass.elasticsearch.url}")
    private String esUrl;

    @Value("${pass.elasticsearch.limit}")
    private int esLimit;

    @Value("${pass.deposit.transport.configuration}")
    private Resource transportResource;

    @Value("${pass.deposit.workers.concurrency}")
    private int depositWorkersConcurrency;

    @Value("${pass.deposit.http.agent}")
    private String passHttpAgent;

    @Bean
    public PassClientDefault passClient() {

        // PassClientDefault can't be injected with configuration; requires system properties be set.
        // If a system property is already set, allow it to override what is resolved by the Spring environment.
        if (!System.getProperties().containsKey("pass.fedora.user")) {
            System.setProperty("pass.fedora.user", fedoraUser);
        }

        if (!System.getProperties().containsKey("pass.fedora.password")) {
            System.setProperty("pass.fedora.password", fedoraPass);
        }

        if (!System.getProperties().containsKey("pass.fedora.baseurl")) {
            System.setProperty("pass.fedora.baseurl", fedoraBaseUrl);
        }

        if (!System.getProperties().containsKey("pass.elasticsearch.url")) {
            System.setProperty("pass.elasticsearch.url", esUrl);
        }

        if (!System.getProperties().containsKey("pass.elasticsearch.limit")) {
            System.setProperty("pass.elasticsearch.limit", String.valueOf(esLimit));
        }

        if (!System.getProperties().containsKey("http.agent")) {
            System.setProperty("http.agent", passHttpAgent);
        }

        return new PassClientDefault();
    }

    @Bean
    public PassJsonAdapterBasic passJsonAdapter() {
        return new PassJsonAdapterBasic();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public FcrepoModelBuilder modelBuilder() {
        return new FcrepoModelBuilder();
    }

    @Bean
    public OkHttpClient fedoraClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        String builderName = builder.getClass().getSimpleName();
        String builderHashcode = toHexString(identityHashCode(builder.getClass()));

        if (fedoraUser != null) {
            LOG.trace(">>>> {}:{} adding Authorization interceptor", builderName, builderHashcode);
            builder.addInterceptor((chain) -> {
                Request request = chain.request();
                Request.Builder reqBuilder = request.newBuilder();
                byte[] bytes = String.format("%s:%s", fedoraUser, fedoraPass).getBytes();
                return chain.proceed(reqBuilder
                        .addHeader("Authorization",
                                "Basic " + getEncoder().encodeToString(bytes)).build());
            });
        }

        LOG.trace(">>>> {}:{} adding Accept interceptor", builderName, builderHashcode);
        builder.addInterceptor((chain) -> {
            Request request = chain.request();
            Request.Builder reqBuilder = request.newBuilder();
            return chain.proceed(reqBuilder
                    .addHeader("Accept", "application/ld+json").build());
        });

        if (LOG.isDebugEnabled()) {
            LOG.trace(">>>> {}:{} adding Logging interceptor", builderName, builderHashcode);
            HttpLoggingInterceptor httpLogger = new HttpLoggingInterceptor(LOG::debug);
            builder.addInterceptor(httpLogger);
        }

        OkHttpClient client = builder.build();
        LOG.trace(">>>> {}:{} built OkHttpClient {}:{}", builderName, builderHashcode,
                client.getClass().getSimpleName(), toHexString(identityHashCode(client.getClass())));

        return client;
    }

    @Bean
    public Registry<Packager> packagerRegistry(Map<String, Packager> packagers) {
        return new InMemoryMapRegistry<>(packagers);
    }

    @Bean
    public Registry<Repository> repositoryRegistry(FcrepoRepositoriesSource fcrepoRepositoriesSource) {
        return new InMemoryMapRegistry<>(
                fcrepoRepositoriesSource.repositories().stream()
                        .collect(Collectors.toMap(repo -> repo.getId().toString(), repo -> repo)));
    }

    @Bean
    public Map<String, Packager> packagers(DspaceMetsAssembler dspaceAssembler, Sword2Transport swordTransport,
                                           NihmsAssembler nihmsAssembler, FtpTransport ftpTransport,
                                           Map<String, Map<String, String>> transportRegistries) {
        Map<String, Packager> packagers = new HashMap<>();
        // TODO: transport registries looked up by hard-coded strings.  Need a more reliable way of discovering repositories, the packagers for those repositories, and their configuration
        packagers.put("JScholarship",
                new Packager("JScholarship", dspaceAssembler, swordTransport, transportRegistries.get("js")));
        packagers.put("PubMed Central",
                new Packager("PubMed Central", nihmsAssembler, ftpTransport, transportRegistries.get("nihms")));
        return packagers;
    }

    @Bean
    public DocumentBuilderFactory dbf() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf;
    }

    @Bean
    public Map<String, Map<String, String>> transportRegistries() {
        Properties properties = new Properties();
        try {
            properties.load(transportResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Error loading properties from " + transportResource + ": " + e.getMessage(), e);
        }

        Map<String, Map<String, String>> registries = new HashMap<>();

        properties.stringPropertyNames().forEach(key -> {
            String[] keyParts = key.split("\\.");
            if (keyParts.length < 3 || !"transport".equals(keyParts[0])) {
                return;
            }

            String repoName = keyParts[1];
            Map<String, String> registryMap = registries.getOrDefault(repoName, new HashMap<>());

            String registryKey = Arrays.stream(keyParts, 2, keyParts.length).collect(Collectors.joining("."));
            String registryValue = properties.getProperty(key);
            LOG.debug(">>>> key: '{}' -> registryKey: '{}', registryValue: '{}'", key, registryKey, registryValue);
            registryMap.put(registryKey, registryValue);
            registries.putIfAbsent(repoName, registryMap);
        });

        return registries;
    }

    @Bean
    public ThreadPoolTaskExecutor depositWorkers() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(depositWorkersConcurrency);
        executor.setQueueCapacity(10);
        executor.setRejectedExecutionHandler((rejectedTask, exe) ->
                // TODO update deposit status
                LOG.warn(">>>> Task {}@{} rejected, will be retried later.",
                        rejectedTask.getClass().getSimpleName(), toHexString(identityHashCode(rejectedTask))));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("Deposit-Worker-");
        ThreadFactory tf = r -> {
            // TODO update deposit status
            Thread t = new Thread(r);
            t.setName("Deposit-Worker-" + THREAD_COUNTER.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, throwable) ->
                    LOG.warn(">>>> {} aborted with the following exception: {}",
                            thread, throwable.getMessage(), throwable));
            return t;
        };
        executor.setThreadFactory(tf);
        return executor;
    }

    @Bean
    public AbderaClient abderaClient(Map<String, Map<String, String>> transportRegistries) {
        AbderaClient ac = new AbderaClient();

        Map<String, String> jscholarship = transportRegistries.get("js");

        if (jscholarship == null) {
            return ac;
        }

        if (jscholarship.containsKey("deposit.transport.authmode") && jscholarship.get("deposit.transport" +
                ".authmode").equals("userpass")) {

            Credentials creds = new org.apache.commons.httpclient.UsernamePasswordCredentials(
                    jscholarship.get("deposit.transport.username"),
                    jscholarship.get("deposit.transport.password")
            );

            try {
                ac.addCredentials(null, null, null, creds);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return ac;
    }

    @Bean
    public AtomFeedStatusMapper swordv2DspaceStatusMapper(@Value("${pass.deposit.status.mapping}")
                                                               Resource depositMappingResource,
                                                          ObjectMapper objectMapper,
                                                          AtomFeedStatusParser atomFeedStatusParser) {
        try {
            return new AtomFeedStatusMapper(objectMapper.readTree(depositMappingResource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException("Error reading deposit status map resource " + depositMappingResource + ": " +
                    e.getMessage(), e);
        }
    }

    @Bean
    public RepositoryCopyStatusMapper repoCopyv2StatusMapper(@Value("${pass.deposit.status.mapping}")
                                                                       Resource depositMappingResource,
                                                             ObjectMapper objectMapper) {
        try {
            return new RepositoryCopyStatusMapper(objectMapper.readTree(depositMappingResource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException("Error reading deposit status map resource " + depositMappingResource + ": " +
                    e.getMessage(), e);
        }
    }

    @Bean
    public SwordDspaceDepositStatusMapper swordDspaceDepositStatusMapper(@Value("${pass.deposit.status.mapping}")
                                                                                     Resource depositMappingResource,
                                                                         ObjectMapper objectMapper) {
        try {
            return new SwordDspaceDepositStatusMapper(objectMapper.readTree(depositMappingResource.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException("Error reading deposit status map resource " + depositMappingResource + ": " +
                    e.getMessage(), e);
        }
    }

    @Bean
    DirtyDepositPolicy dirtyDepositPolicy() {
        return new DirtyDepositPolicy();
    }

    @Bean
    Parser abderaParser() {
        return new FOMParserFactory().getParser();
    }

}
