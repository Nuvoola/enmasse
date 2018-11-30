/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.iot.model.v1;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;

public final class CustomResources {
    private CustomResources() {
    }

    public static CustomResourceDefinition fromClass(final Class<?> clazz) {

        final String kind = clazz.getSimpleName();
        final CustomResource customResource = clazz.getAnnotation(CustomResource.class);
        final String apiVersion = clazz.getAnnotation(ApiVersion.class).value();

        // get singular, default to variation of "kind"

        String singular = Optional.ofNullable(clazz.getAnnotation(CustomResource.Singular.class))
                .map(CustomResource.Singular::value)
                .orElse(kind.toLowerCase());

        // get plural, default to none

        String plural = Optional.ofNullable(clazz.getAnnotation(CustomResource.Plural.class))
                .map(CustomResource.Plural::value)
                .orElse(null);

        // if no explicit plural is specified, and the singular is not set to "none"

        if (plural == null && !singular.isEmpty()) {
            // then derive the plural from the singular
            plural = singular + "s";
        }

        // if the plural is set to "none"

        if (plural != null && plural.isEmpty()) {
            // then set it to null
            plural = null;
        }

        // if the plural is still null

        if (plural == null) {
            // set it to a variation of "kind"
            plural = kind.toLowerCase() + "s";
        }

        // if the singular is set to "none"
        
        if (singular != null && singular.isEmpty()) {
            // set it to null
            singular = null;
        }

        return new CustomResourceDefinitionBuilder()
                .withApiVersion("apiextensions.k8s.io/v1beta1")

                .withNewMetadata()
                .withName(plural + "." + customResource.group())
                .endMetadata()

                .withNewSpec()
                .withGroup(customResource.group())
                .withVersion(apiVersion)
                .withScope(customResource.scope().name())

                .withNewNames()
                .withKind(kind)
                .withShortNames(customResource.shortNames())
                .withPlural(plural)
                .withSingular(singular)
                .endNames()

                .endSpec()

                .build();
    }
}
