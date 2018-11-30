/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package v1alpha1

import (
    metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// +genclient
// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

type AddressSpace struct {
    metav1.TypeMeta   `json:",inline"`
    metav1.ObjectMeta `json:"metadata,omitempty"'`

    Spec   AddressSpaceSpec   `json:"spec"`
    Status AddressSpaceStatus `json:"status"`
}

type AddressSpaceSpec struct {
    Plan string `json:"plan"`
    Type string `json:"type"`

    Ednpoints []EndpointSpec `json:"endpoints"`
}

type EndpointSpec struct {
    Name    string `json:"name"`
    Service string `json:"service"`
}

type AddressSpaceStatus struct {
    IsReady bool `json:"isReady"`

    EndpointStatus []EndpointStatus `json:"endpointStatuses"`
}

type EndpointStatus struct {
    Name        string `json:"name"`
    Certificate []byte `json:"cert"`

    ServiceHost  string `json:"serviceHost"`
    ServicePorts []Port `json:"servicePorts"`

    ExternalHost  string `json:"externalHost"`
    ExternalPorts []Port `json:"externalPorts"`
}

type Port struct {
    Name string
    Port uint16
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

type AddressSpaceList struct {
    metav1.TypeMeta `json:",inline"`
    metav1.ListMeta `json:"metadata,omitempty"'`

    Items []AddressSpace `json:"items"`
}
