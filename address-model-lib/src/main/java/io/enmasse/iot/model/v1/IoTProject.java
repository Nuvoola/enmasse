/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.model.v1;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        inline = @Inline(
                type = Doneable.class,
                prefix = "Doneable",
                value = "done"
                )
        )
@ApiVersion("v1alpha1")
@CustomResource(group = "iot.enmasse.io")
public class IoTProject extends AbstractHasMetadata<IoTProject> {

    private static final long serialVersionUID = 1L;

    public static final CustomResourceDefinition CRD;

    static {
        CRD = CustomResources.fromClass(IoTProject.class);
    }

    private IoTProjectSpec spec;

    public void setSpec(final IoTProjectSpec spec) {
        this.spec = spec;
    }

    public IoTProjectSpec getSpec() {
        return this.spec;
    }

}
