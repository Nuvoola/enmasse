local common = import "common.jsonnet";
{
  common_service(name, type, annotations)::
  {
    "apiVersion": "v1",
    "kind": "Service",
    "metadata": {
      "name": name,
      "labels": {
        "app": "enmasse"
      },
      "annotations": annotations
    },
    "spec": {
      "ports": [
        {
          "name": "https",
          "port": 443,
          "protocol": "TCP",
          "targetPort": "https"
        }
      ],
      "selector": {
        "name": "address-controller"
      },
      "type": type
    }
  },
  
  internal_service::
    self.common_service("address-controller", "ClusterIP", {}),

  external_service::
    self.common_service("restapi-external", "LoadBalancer", {}),

  deployment(image, template_config, ca_secret, cert_secret, environment, enable_rbac)::
    {
      "apiVersion": "extensions/v1beta1",
      "kind": "Deployment",
      "metadata": {
        "labels": {
          "name": "address-controller",
          "app": "enmasse",
          "environment": environment
        },
        "name": "address-controller"
      },
      "spec": {
        "replicas": 1,
        "template": {
          "metadata": {
            "labels": {
              "name": "address-controller",
              "app": "enmasse",
              "environment": environment
            }
          },

          "spec": {
            "serviceAccount": "enmasse-service-account",
            "containers": [
              {
                "image": image,
                "name": "address-controller",
                "env": [
                  common.env("CA_DIR", "/ca-cert"),
                  common.env("ENABLE_RBAC", enable_rbac),
                  common.env("ENVIRONMENT", environment)
                ],
                "volumeMounts": [
                  common.volume_mount("ca-cert", "/ca-cert", true),
                  common.volume_mount("address-controller-cert", "/address-controller-cert", true),
                ] + if template_config != "" then [ common.volume_mount("templates", "/enmasse-templates") ] else [],
                "resources": {
                    "requests": {
                        "memory": "512Mi",
                    },
                    "limits": {
                        "memory": "512Mi",
                    }
                },
                "ports": [
                  common.container_port("https", 8081),
                  common.container_port("http", 8080)
                ],
               "livenessProbe": common.http_probe("https", "/apis/enmasse.io/v1/health", "HTTPS", 30),
                "readinessProbe": common.http_probe("https", "/apis/enmasse.io/v1/health", "HTTPS", 30),
              }
            ],
            "volumes": [
              common.secret_volume("ca-cert", ca_secret),
              common.secret_volume("address-controller-cert", cert_secret)
            ] + if template_config != "" then [ common.configmap_volume("templates", template_config) ] else [],
          }
        }
      }
    }
}
