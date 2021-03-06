/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller;

import io.enmasse.address.model.*;
import io.enmasse.admin.model.v1.BrokeredInfraConfig;
import io.enmasse.admin.model.v1.InfraConfig;
import io.enmasse.admin.model.v1.StandardInfraConfig;
import io.enmasse.config.AnnotationKeys;
import io.enmasse.config.LabelKeys;
import io.enmasse.controller.common.AuthenticationServiceResolverFactory;
import io.enmasse.controller.common.Kubernetes;
import io.enmasse.controller.common.TemplateParameter;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.client.ParameterValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;

public class TemplateInfraResourceFactory implements InfraResourceFactory {
    private static final Logger log = LoggerFactory.getLogger(TemplateInfraResourceFactory.class.getName());
    private static final String KC_IDP_HINT_NONE = "none";
    private static final String KC_IDP_HINT_OPENSHIFT = "openshift-v3";

    private final Kubernetes kubernetes;
    private final AuthenticationServiceResolverFactory authResolverFactory;
    private final boolean openShift;

    public TemplateInfraResourceFactory(Kubernetes kubernetes, AuthenticationServiceResolverFactory authResolverFactory, boolean openShift) {
        this.kubernetes = kubernetes;
        this.authResolverFactory = authResolverFactory;
        this.openShift = openShift;
    }

    private void prepareParameters(InfraConfig infraConfig,
                                   AddressSpace addressSpace,
                                   Map<String, String> parameters) {
        AuthenticationService authService = addressSpace.getAuthenticationService();
        AuthenticationServiceResolver authResolver = authResolverFactory.getResolver(authService.getType());

        Optional<String> kcIdpHint = getKcIdpHint(infraConfig, addressSpace, authService.getType());

        String infraUuid = addressSpace.getAnnotation(AnnotationKeys.INFRA_UUID);
        parameters.put(TemplateParameter.INFRA_NAMESPACE, kubernetes.getNamespace());
        parameters.put(TemplateParameter.ADDRESS_SPACE, addressSpace.getName());
        parameters.put(TemplateParameter.INFRA_UUID, infraUuid);
        parameters.put(TemplateParameter.ADDRESS_SPACE_NAMESPACE, addressSpace.getNamespace());
        parameters.put(TemplateParameter.AUTHENTICATION_SERVICE_HOST, authResolver.getHost(authService));
        parameters.put(TemplateParameter.AUTHENTICATION_SERVICE_PORT, String.valueOf(authResolver.getPort(authService)));
        parameters.put(TemplateParameter.ADDRESS_SPACE_ADMIN_SA, KubeUtil.getAddressSpaceSaName(addressSpace));
        parameters.put(TemplateParameter.ADDRESS_SPACE_PLAN, addressSpace.getPlan());
        parameters.put(TemplateParameter.AUTHENTICATION_SERVICE_KC_IDP_HINT, kcIdpHint.orElse(""));

        String encodedCaCert = authResolver.getCaSecretName(authService)
                .map(secretName ->
                    kubernetes.getSecret(secretName).map(secret ->
                            secret.getData().get("tls.crt"))
                            .orElseThrow(() -> new IllegalArgumentException("Unable to decode secret " + secretName)))
                .orElseGet(() -> {
                    try {
                        return Base64.getEncoder().encodeToString(Files.readAllBytes(new File("/etc/ssl/certs/ca-bundle.crt").toPath()));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        parameters.put(TemplateParameter.AUTHENTICATION_SERVICE_CA_CERT, encodedCaCert);
        authResolver.getClientSecretName(authService).ifPresent(secret -> parameters.put(TemplateParameter.AUTHENTICATION_SERVICE_CLIENT_SECRET, secret));
        parameters.put(TemplateParameter.AUTHENTICATION_SERVICE_SASL_INIT_HOST, authResolver.getSaslInitHost(addressSpace, authService));
        authResolver.getOAuthURL(authService).ifPresent(url -> parameters.put(TemplateParameter.AUTHENTICATION_SERVICE_OAUTH_URL, url));

        Map<String, CertSpec> serviceCertMapping = new HashMap<>();
        for (EndpointSpec endpoint : addressSpace.getEndpoints()) {
                endpoint.getCertSpec().ifPresent(cert -> {
                    serviceCertMapping.put(endpoint.getService(), cert);
            });
        }
        parameters.put(TemplateParameter.MESSAGING_SECRET, serviceCertMapping.get("messaging").getSecretName());
        parameters.put(TemplateParameter.CONSOLE_SECRET, serviceCertMapping.get("console").getSecretName());
    }

    private Optional<String> getKcIdpHint(final InfraConfig infraConfig,
                                final AddressSpace addressSpace,
                                final AuthenticationServiceType authenticationServiceType) {

        String kcIdpHint = null;
        if (this.openShift && authenticationServiceType == AuthenticationServiceType.STANDARD) {
            kcIdpHint = KC_IDP_HINT_OPENSHIFT;
        }

        kcIdpHint = getAnnotation(infraConfig.getMetadata().getAnnotations(), AnnotationKeys.KC_IDP_HINT, kcIdpHint);

        if  (addressSpace.getAnnotation(AnnotationKeys.KC_IDP_HINT) != null) {
            kcIdpHint = addressSpace.getAnnotation(AnnotationKeys.KC_IDP_HINT);
        }
        return KC_IDP_HINT_NONE.equals(kcIdpHint) ? Optional.empty() : Optional.ofNullable(kcIdpHint);
    }

    private void prepareMqttParameters(AddressSpace addressSpace, Map<String, String> parameters) {
        String infraUuid = addressSpace.getAnnotation(AnnotationKeys.INFRA_UUID);
        parameters.put(TemplateParameter.ADDRESS_SPACE, addressSpace.getName());
        parameters.put(TemplateParameter.INFRA_UUID, infraUuid);
        Map<String, CertSpec> serviceCertMapping = new HashMap<>();
        for (EndpointSpec endpoint : addressSpace.getEndpoints()) {
            endpoint.getCertSpec().ifPresent(cert -> {
                serviceCertMapping.put(endpoint.getService(), cert);
            });
        }
        parameters.put(TemplateParameter.MQTT_SECRET, serviceCertMapping.get("mqtt").getSecretName());
    }

    private List<HasMetadata> createStandardInfraMqtt(AddressSpace addressSpace, String templateName) {
        Map<String, String> parameters = new HashMap<>();
        prepareMqttParameters(addressSpace, parameters);

        List<ParameterValue> parameterValues = new ArrayList<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            parameterValues.add(new ParameterValue(entry.getKey(), entry.getValue()));
        }
        return new ArrayList<>(kubernetes.processTemplate(templateName, parameterValues.toArray(new ParameterValue[0])).getItems());
    }


    private List<HasMetadata> createStandardInfra(AddressSpace addressSpace, StandardInfraConfig standardInfraConfig) {

        Map<String, String> parameters = new HashMap<>();

        prepareParameters(standardInfraConfig, addressSpace, parameters);

        parameters.put(TemplateParameter.BROKER_MEMORY_LIMIT, standardInfraConfig.getSpec().getBroker().getResources().getMemory());
        parameters.put(TemplateParameter.BROKER_ADDRESS_FULL_POLICY, standardInfraConfig.getSpec().getBroker().getAddressFullPolicy());
        parameters.put(TemplateParameter.ADMIN_MEMORY_LIMIT, standardInfraConfig.getSpec().getAdmin().getResources().getMemory());
        parameters.put(TemplateParameter.ROUTER_MEMORY_LIMIT, standardInfraConfig.getSpec().getRouter().getResources().getMemory());
        parameters.put(TemplateParameter.ROUTER_LINK_CAPACITY, String.valueOf(standardInfraConfig.getSpec().getRouter().getLinkCapacity()));
        parameters.put(TemplateParameter.STANDARD_INFRA_CONFIG_NAME, standardInfraConfig.getMetadata().getName());

        List<ParameterValue> parameterValues = new ArrayList<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            parameterValues.add(new ParameterValue(entry.getKey(), entry.getValue()));
        }

        Map<String, String> infraAnnotations = standardInfraConfig.getMetadata().getAnnotations();
        String templateName = getAnnotation(infraAnnotations, AnnotationKeys.TEMPLATE_NAME, "standard-space-infra");
        List<HasMetadata> items = new ArrayList<>(kubernetes.processTemplate(templateName, parameterValues.toArray(new ParameterValue[0])).getItems());
        // Workaround since parameterized integer fields cannot be loaded locally by fabric8 kubernetes-client
        for (HasMetadata item : items) {
            if (item instanceof StatefulSet && "qdrouterd".equals(item.getMetadata().getLabels().get(LabelKeys.NAME))) {
                StatefulSet router = (StatefulSet) item;
                router.getSpec().setReplicas(standardInfraConfig.getSpec().getRouter().getMinReplicas());
            }
        }
        if (Boolean.parseBoolean(getAnnotation(infraAnnotations, AnnotationKeys.WITH_MQTT, "false"))) {
            String mqttTemplateName = getAnnotation(infraAnnotations, AnnotationKeys.MQTT_TEMPLATE_NAME, "standard-space-infra-mqtt");
            items.addAll(createStandardInfraMqtt(addressSpace, mqttTemplateName));
        }
        return items;
    }


    private String getAnnotation(Map<String, String> annotations, String key, String defaultValue) {
        return Optional.ofNullable(annotations)
                .flatMap(m -> Optional.ofNullable(m.get(key)))
                .orElse(defaultValue);
    }

    private List<HasMetadata> createBrokeredInfra(AddressSpace addressSpace, BrokeredInfraConfig brokeredInfraConfig) {
        Map<String, String> parameters = new HashMap<>();

        prepareParameters(brokeredInfraConfig, addressSpace, parameters);

        List<ParameterValue> parameterValues = new ArrayList<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            parameterValues.add(new ParameterValue(entry.getKey(), entry.getValue()));
        }

        String templateName = getAnnotation(brokeredInfraConfig.getMetadata().getAnnotations(), AnnotationKeys.TEMPLATE_NAME, "brokered-space-infra");
        return kubernetes.processTemplate(templateName, parameterValues.toArray(new ParameterValue[0])).getItems();
    }

    @Override
    public List<HasMetadata> createInfraResources(AddressSpace addressSpace, InfraConfig infraConfig) {
        if ("standard".equals(addressSpace.getType())) {
            return createStandardInfra(addressSpace, (StandardInfraConfig) infraConfig);
        } else if ("brokered".equals(addressSpace.getType())) {
            return createBrokeredInfra(addressSpace, (BrokeredInfraConfig) infraConfig);
        } else {
            throw new IllegalArgumentException("Unknown address space type " + addressSpace.getType());
        }
    }
}
