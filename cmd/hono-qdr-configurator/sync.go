/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package main

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	enmassev1 "github.com/enmasseproject/enmasse/pkg/apis/enmasse/v1alpha1"
	"github.com/enmasseproject/enmasse/pkg/apis/iot/v1alpha1"
	"github.com/enmasseproject/enmasse/pkg/qdr"
	"github.com/enmasseproject/enmasse/pkg/util"
	"go.uber.org/multierr"
	"io/ioutil"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/klog"
	"os"
	"path"
	"reflect"
	"strconv"
	"strings"
)

func (c *Configurator) syncResource(currentPointer interface{}, resource interface{}) (bool, error) {
	return c.syncResourceWithCreator(currentPointer, resource, toMapStringString)
}

func (c *Configurator) syncResourceWithCreator(currentPointer interface{}, resource interface{}, creator func(interface{}) (map[string]string, error)) (bool, error) {

	r, ok := resource.(qdr.RouterResource)

	if !ok {
		return false, fmt.Errorf("requested resource must implement 'qdr.RouterResource'")
	}

	found, err := c.manage.ReadAsObject(r, currentPointer)
	if err != nil {
		return false, err
	}

	klog.V(4).Infof("Found: %v", found)
	klog.V(3).Infof("Current: %v", currentPointer)
	klog.V(3).Infof("Request: %v", resource)

	if found {
		equals := reflect.DeepEqual(currentPointer, resource)
		klog.V(2).Infof("Resource equals: %v", equals)
		if equals {
			return false, nil
		}
	}

	if found {
		err = c.manage.Delete(r)
		if err != nil {
			return false, err
		}
	}

	m, err := creator(resource)
	if err != nil {
		return false, err
	}

	_, err = c.manage.Create(r, m)

	return true, err

}

func (c *Configurator) syncLinkRoute(route qdr.LinkRoute) (bool, error) {
	return c.syncResource(&qdr.LinkRoute{}, &route)
}

func (c *Configurator) syncConnector(connector qdr.Connector) (bool, error) {
	return c.syncResource(&qdr.Connector{}, &connector)
}

func fileExists(file string) bool {
	if _, err := os.Stat(file); err != nil {
		return false
	}
	return true
}

func certFilePrefix(object metav1.Object) string {
	return object.GetNamespace() + "." + object.GetName() + "-"
}

func (c *Configurator) certificatePath(object metav1.Object, certificate []byte) string {

	if certificate == nil || len(certificate) == 0 {
		return ""
	}

	// TODO: don't put all certs into a single folder

	checksum := fmt.Sprintf("%x", sha256.Sum256(certificate))
	name := certFilePrefix(object) + checksum + "-cert.crt"
	return path.Join(c.ephermalCertBase, name)

}

func (c *Configurator) deleteCertificatesForProject(object metav1.Object) error {
	prefix := certFilePrefix(object)

	// TODO: don't put all certs into a single folder

	files, err := ioutil.ReadDir(c.ephermalCertBase)
	if err != nil {
		return err
	}

	klog.Infof("Cleaning up certificates for: %v", object)

	for _, f := range files {
		klog.V(2).Infof("Checking file: %v", f)
		if strings.HasPrefix(f.Name(), prefix) {
			klog.Infof("Deleting file: %v", f)
			err = multierr.Append(err, os.Remove(f.Name()))
		}
	}

	return err
}

func resourceName(object metav1.Object, name string) string {

	result := name + "-" + object.GetNamespace() + "-" + object.GetName()

	// NOTE: qdrouterd cannot properly handle "." and "/" in resource names

	return strings.
		NewReplacer(".", "-", "/", "-").
		Replace(result)
}

func namedResource(object metav1.Object, name string) qdr.NamedResource {
	return qdr.NamedResource{
		Name: resourceName(object, name),
	}
}

func addressName(object metav1.Object, prefix string) string {
	return prefix + "/" + object.GetNamespace() + "." + object.GetName()
}

// Convert an object to a map of string/string
func toMapStringString(v interface{}) (map[string]string, error) {

	out, err := json.Marshal(v)
	if err != nil {
		return nil, err
	}

	var f interface{}

	err = json.Unmarshal(out, &f)
	if err != nil {
		return nil, err
	}

	s := f.(map[string]interface{})

	result := map[string]string{}

	for k, v := range s {
		switch t := v.(type) {
		case string:
			result[k] = t
		}
	}

	return result, nil
}

func (c *Configurator) syncSslProfile(object metav1.Object, certificate []byte) (bool, error) {

	hasCert := certificate != nil && len(certificate) > 0
	if hasCert && c.ephermalCertBase == "" {
		return false, fmt.Errorf("unable to configure custom certificate, emphermal base directory is not configured")
	}

	certFile := c.certificatePath(object, certificate)
	klog.V(2).Infof("Certificate path: %v", certFile)

	if !hasCert && c.ephermalCertBase != "" {

		// TODO: improve performance, we iterate over all custom certs at this point

		// delete all certificates for this project
		if err := c.deleteCertificatesForProject(object); err != nil {
			return false, err
		}

	} else if hasCert && !fileExists(certFile) {

		// delete all certificates for this project
		if err := c.deleteCertificatesForProject(object); err != nil {
			return false, err
		}

		// cert file currently does not exists, write to file system
		if err := ioutil.WriteFile(certFile, certificate, 0777); err != nil {
			return false, err
		}
	}

	// sync with qdr

	return c.syncResource(&qdr.SslProfile{}, &qdr.SslProfile{
		NamedResource:   namedResource(object, "sslProfile"),
		CertificatePath: certFile,
	})
}

func (c *Configurator) syncProjectProvidedDownstream(project *v1alpha1.IoTProject) (bool, error) {

	strategy := project.Spec.DownstreamStrategy.ProvidedDownstreamStrategy

	addressSpace, err := c.addressSpaceLister.AddressSpaces(strategy.Namespace).Get(strategy.AddressSpaceName)

	if err != nil {
		return false, err
	}

	if !addressSpace.Status.IsReady {
		return false, fmt.Errorf("addressSpace is not ready yet")
	}

	for _, status := range addressSpace.Status.EndpointStatus {

		if status.Name == strategy.EndpointName {

			var host string
			var ports []enmassev1.Port

			switch strategy.EndpointMode {
			case v1alpha1.Service:
				host = status.ServiceHost
				ports = status.ServicePorts
			case v1alpha1.External:
				host = status.ExternalHost
				ports = status.ExternalPorts
			}

			for _, port := range ports {
				if port.Name == strategy.PortName {
					return c.syncProject(project, host, port.Port, strategy.Credentials, false, nil)
				}
			}

		}

	}

	return false, fmt.Errorf("unable to find namespace/addressspace/port/type combination %v/%v/%v/%v", strategy.Namespace, strategy.AddressSpaceName, strategy.PortName, strategy.EndpointMode)
}

func (c *Configurator) syncProjectExternalDownstream(project *v1alpha1.IoTProject) (bool, error) {

	strategy := project.Spec.DownstreamStrategy.ExternalDownstreamStrategy

	return c.syncProject(project, strategy.Host, strategy.Port, strategy.Credentials, strategy.TLS, strategy.Certificate)

}

func (c *Configurator) syncProject(project metav1.Object, host string, port uint16, credentials v1alpha1.Credentials, tls bool, certificate []byte) (bool, error) {

	connectorName := resourceName(project, "connector")
	sslProfileName := ""

	klog.V(2).Infof("Create project: %v", project)

	m := util.MultiTool{}

	if tls {
		m.Run(func() (b bool, e error) {
			return c.syncSslProfile(project, certificate)
		})
		sslProfileName = resourceName(project, "sslProfile")
	}

	m.Run(func() (b bool, e error) {
		return c.syncConnector(qdr.Connector{
			NamedResource: qdr.NamedResource{Name: connectorName},
			Host:          host,
			Port:          strconv.Itoa(int(port)),
			Role:          "route-container",
			SASLUsername:  credentials.Username,
			SASLPassword:  credentials.Password,
			SSLProfile:    sslProfileName,
		})
	})

	m.Run(func() (b bool, e error) {
		return c.syncLinkRoute(qdr.LinkRoute{
			NamedResource: namedResource(project, "linkRoute/t"),
			Direction:     "in",
			Pattern:       addressName(project, "telemetry") + "/#",
			Connection:    connectorName,
		})
	})

	m.Run(func() (b bool, e error) {
		return c.syncLinkRoute(qdr.LinkRoute{
			NamedResource: namedResource(project, "linkRoute/e"),
			Direction:     "in",
			Pattern:       addressName(project, "event") + "/#",
			Connection:    connectorName,
		})
	})

	m.Run(func() (b bool, e error) {
		return c.syncLinkRoute(qdr.LinkRoute{
			NamedResource: namedResource(project, "linkRoute/c_i"),
			Direction:     "in",
			Pattern:       addressName(project, "control") + "/#",
			Connection:    connectorName,
		})
	})

	m.Run(func() (b bool, e error) {
		return c.syncLinkRoute(qdr.LinkRoute{
			NamedResource: namedResource(project, "linkRoute/c_o"),
			Direction:     "out",
			Pattern:       addressName(project, "control") + "/#",
			Connection:    connectorName,
		})
	})

	return m.Return()
}

func (c *Configurator) deleteProject(object metav1.Object) error {

	klog.Infof("Delete project: %v", object)

	m := util.MultiTool{}

	for _, tag := range []string{"t", "e", "c_i", "c_o"} {
		m.Run(func() (b bool, e error) {
			return true, c.manage.Delete(qdr.NamedLinkRoute(resourceName(object, "linkRoute/"+tag)))
		})
	}

	m.Run(func() (b bool, e error) {
		return true, c.manage.Delete(qdr.NamedConnector(resourceName(object, "connector")))
	})

	m.Run(func() (b bool, e error) {
		return true, c.manage.Delete(qdr.NamedSslProfile(resourceName(object, "sslProfile")))
	})

	m.Run(func() (b bool, e error) {
		return true, c.deleteCertificatesForProject(object)
	})

	return m.Error
}
