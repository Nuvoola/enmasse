/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.iot.model.v1;

public class IoTProjectSpec {

    private DownstreamStrategy downstreamStrategy;

    public DownstreamStrategy getDownstreamStrategy() {
        return this.downstreamStrategy;
    }

    public void setDownstreamStrategy(final DownstreamStrategy downstreamStrategy) {
        this.downstreamStrategy = downstreamStrategy;
    }

}
