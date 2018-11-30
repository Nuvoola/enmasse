/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.model.v1;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;

public abstract class AbstractHasMetadata<T> implements HasMetadata {

    private static final long serialVersionUID = 1L;

    private ObjectMeta metadata;

    private final String kind = this.getClass().getSimpleName();
    private final String apiVersion = this.getClass().getAnnotation(ApiVersion.class).value();

    @Override
    public ObjectMeta getMetadata() {
        return this.metadata;
    }

    @Override
    public void setMetadata(final ObjectMeta metadata) {
        this.metadata = metadata;
    }

    @Override
    public String getKind() {
        return this.kind;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(String version) {
        throw new UnsupportedOperationException();
    }

}
