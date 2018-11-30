/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.model.v1;

public class DownstreamStrategy {

    private ExternalDownstreamStrategy externalStrategy;

    public ExternalDownstreamStrategy getExternalStrategy() {
        return this.externalStrategy;
    }

    public void setExternalStrategy(final ExternalDownstreamStrategy externalStrategy) {
        this.externalStrategy = externalStrategy;
    }

}
