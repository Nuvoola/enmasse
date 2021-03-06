/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.admin.model.v1;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

public class AdminCrd {

    public static void registerCustomCrds() {
        KubernetesDeserializer.registerCustomKind("admin.enmasse.io/v1alpha1", AddressSpacePlan.ADDRESS_SPACE_PLAN, AddressSpacePlan.class);
        KubernetesDeserializer.registerCustomKind("admin.enmasse.io/v1alpha1", AddressPlan.ADDRESS_PLAN, AddressPlan.class);
        KubernetesDeserializer.registerCustomKind("admin.enmasse.io/v1alpha1", StandardInfraConfig.STANDARD_INFRA_CONFIG, StandardInfraConfig.class);
        KubernetesDeserializer.registerCustomKind("admin.enmasse.io/v1alpha1", BrokeredInfraConfig.BROKERED_INFRA_CONFIG, BrokeredInfraConfig.class);
    }

    public static CustomResourceDefinition addressspaceplans() {
        return createCrd(AddressSpacePlan.ADDRESS_SPACE_PLAN);
    }

    public static CustomResourceDefinition addressplans() {
        return createCrd(AddressPlan.ADDRESS_PLAN);
    }

    public static CustomResourceDefinition brokeredinfraconfigs() {
        return createCrd(BrokeredInfraConfig.BROKERED_INFRA_CONFIG);
    }

    public static CustomResourceDefinition standardinfraconfigs() {
        return createCrd(StandardInfraConfig.STANDARD_INFRA_CONFIG);
    }

    private static CustomResourceDefinition createCrd(String kind) {
        String singular = kind.toLowerCase();
        String listKind = kind + "List";
        String plural = singular + "s";
        return new CustomResourceDefinitionBuilder()
                .editOrNewMetadata()
                .withName(plural + ".admin.enmassse.io")
                .addToLabels("app", "enmasse")
                .endMetadata()
                .editOrNewSpec()
                .withGroup("admin.enmasse.io")
                .withVersion("v1alpha1")
                .withScope("Namespaced")
                .editOrNewNames()
                .withKind(kind)
                .withListKind(listKind)
                .withPlural(plural)
                .withSingular(singular)
                .endNames()
                .endSpec()
                .build();
    }
}
