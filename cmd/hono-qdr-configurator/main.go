/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package main

import (
    "flag"
    "k8s.io/client-go/rest"
    "os"

    iot "github.com/enmasseproject/enmasse/pkg/client/clientset/versioned"
    "github.com/enmasseproject/enmasse/pkg/signals"

    "k8s.io/client-go/kubernetes"
    "k8s.io/klog"

    "time"

    informers "github.com/enmasseproject/enmasse/pkg/client/informers/externalversions"
)

var (
    masterURL  string
    kubeconfig string
)

func main() {
    flag.Parse()

    // init log system
    klog.SetOutput(os.Stdout)

    // install signal handler for graceful shutdown, or hard exit
    stopCh := signals.InstallSignalHandler()

    klog.Infof("Starting up...")

    cfg, err := rest.InClusterConfig()
    if err != nil {
        klog.Fatalf("Error getting in-cluster config: %v", err.Error())
    }

    kubeClient, err := kubernetes.NewForConfig(cfg)
    if err != nil {
        klog.Fatalf("Error building kubernetes client: %v", err.Error())
    }

    iotClient, err := iot.NewForConfig(cfg)
    if err != nil {
        klog.Fatalf("Error building IoT project client: %v", err.Error())
    }

    iotInformerFactory := informers.NewSharedInformerFactory(iotClient, time.Second*30)

    configurator := NewConfigurator(
        kubeClient, iotClient,
        iotInformerFactory.Iot().V1alpha1().IoTProjects(),
    )

    iotInformerFactory.Start(stopCh)

    if err = configurator.Run(2, stopCh); err != nil {
        klog.Fatalf("Failed running configurator: %v", err.Error())
    }
}
