/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.model.v1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMeta;

public abstract class AbstractList<T extends HasMetadata> implements KubernetesResource<T>, KubernetesResourceList<T>, Iterable<T> {

    private static final long serialVersionUID = 1L;

    private List<T> items = new ArrayList<>();

    private ListMeta metadata;

    public AbstractList() {
    }

    public AbstractList(final Collection<? extends T> c) {
        setItems(c);
    }

    public void setItems(final Collection<? extends T> items) {
        this.items = new ArrayList<>(items);
    }

    public List<T> getItems() {
        return this.items;
    }

    @Override
    public Iterator<T> iterator() {
        return this.items.iterator();
    }

    public void setMetadata(final ListMeta metadata) {
        this.metadata = metadata;
    }

    @Override
    public ListMeta getMetadata() {
        return this.metadata;
    }

}
