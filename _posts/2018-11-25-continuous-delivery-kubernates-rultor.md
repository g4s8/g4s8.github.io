---
layout: post
title: Continuous Delivery in Kubernetes with Rultor
---

Previously, I was speaking about
[Docker build automation]({{ site.baseurl }}/docker-with-rultor)
with [Rultor](http://www.rultor.com/), where I explained how to
build docker-images and push them to registry after release approve.
Now I'll show how to deploy these images to staging environment of
[Kubernetes](https://kubernetes.io/) cluster with one comment
on [GitHub](https://github.com/) ticket.

## The goal

We need to build Docker image from source code and deploy it to staging
environment with one comment on GitHub ticket.

From [previous post]({{ site.baseurl }}/docker-with-rultor) we've learned
how to build and push image to Docker registry on release. So now I'll explain
how to deploy these images.

I prefer to keep Kubernetes config as `yaml` files in GitHub repository
and apply them with [kube-applier](https://github.com/box/kube-applier),
so the goal of release automation is:
 1. Setup Kubernetes to automatically apply configuration from GitHub
repository, when any file changed
 2. Setup Rultor to update Kubernetes configuration on release with fresh
Docker images

So what Rultor should do for `release` command?
 1. Build Docker image and push it to registry
 2. Pull configuration repository
 3. Edit configuration of staging deployment with new container version
 4. Commit updated configuration, push it back

At the same time, Kubernetes service should monitor for updates in
configuration repository (I'm using GitHub webhooks), synchronize it
and apply all files.

Here is an overall schema of deployment process:

![scheme]({{ site.baseurl }}/images/kubescheme.png)

When architect in repository asks to make a release,
Rultor builds Docker image, push it to registry and updates
the deployment in config repository.
Kubernetes detects these changes from webhook, pulls config repository
and updates the deployment by downloading new image from registry.

## Setup kube-applier

From the [README.md](https://github.com/box/kube-applier):

> kube-applier is a service that enables continuous deployment of Kubernetes
> objects by applying declarative configuration files from a Git repository to a Kubernetes cluster.

Before starting, we need to create new repository for `yaml` configuration files if not exist.

The role of kube-applier in this system is to read configuration files from
shared volume and apply them using Kubernetes API.

Here is the workflow:
![kube-applier]({{ site.baseurl }}/images/kube-applier.png)

Rultor edits configuration file of the deployment during release
and pushes these changes to configuration repository.
It produces the webhook which triggers public Kubernetes endpoint
(`POST /sync` on this image). Ingress controller proxies this hook
to applier service which has a pod with two containers and one shared volume:
 1. Sync container, which pulls files from configuration repository to
shared volume on each http call
 2. Applier container, which detects changes in configuration files
on shared volume and apply updates using Kubernetes API

As sync container I'm using very primitive Go server, called
[gsync](https://github.com/g4s8/gsync), anything that this server does
is just pull files from GitHub repository to mounted volume.

Kube-applier periodically checks this volume and apply new changes.

I'm not showing how to do it, because it's fully documented by kube-applier.

Also, it's necessary to configure binding-role for kube-applier,
because applier should be able to use Kubernetes API and
create, delete and update resources in different namespaces.
See this [SO answer](https://stackoverflow.com/a/50921129/1723695)
for details.

## Rultor setup

Rultor uses `.rultor.yml` as configuration file, see
[full documentation](http://doc.rultor.com/reference.html) for details.

We've already configured docker building with Rultor.
Usually I'm moving it to separate `Makefile` and just calling
`command sudo make VERSION=$tag` from Rultor.

To automatically update configuration repository, we need to create new GitHub
user with write permissions.

It's important to securely store the credentials, I'm using 
[rultor-remote](https://github.com/yegor256/rultor-remote) tool
(see documentation for details).

To pass the password to `git` command, we can use
git credentials helpers. First create a script to
return the password:
```sh
#!/bin/sh
exec echo "<deployer-password>"
```
Encrypt it with rultor-remote and add encrypted file to repo.

Then decrypt it in Rultor's container using `decrypt` section in `.rultor.yml`:
```yml
decrypt:
    password.sh: "repo/password.sh.asc"
```
and use it as git password helper: `export GIT_ASKPASS=/tmp/password.sh`,
so now we're able to clone config repo to Rultor's image.

After that, we need to update container version in deployment. It can be done with `sed`
tool:
```sh
set -ie \
  "s/your.registry.com\/image-name:[0-9\.]*/your.registry.com\/image-name:${tag}/g" \
  ./path/to/deployment.yaml
```
This command will replace container image version with `$tag` Rultor variable which means
current release version.

So full release script may look like this:
```yaml
release:
  script: |
    command sudo make VERSION=$tag
    cp ../password.sh /tmp/password.sh
    chmod +x /tmp/password.sh
    export GITHUB_USERNAME="<replace with username>"
    git config --global user.email "<replace with email>"
    git config --global user.name "${GITHUB_USERNAME}"
    export GIT_ASKPASS=/tmp/password.sh
    cd /tmp
    git clone "https://${GITHUB_USERNAME}@github.com/name/repo"
    cd repo
    set -ie \
      "s/your.registry.com\/image-name:[0-9\.]*/your.registry.com\/image-name:${tag}/g" \
      ./path/to/deployment.yaml
    git add ./path/to/deploymeny.yaml
    git commit -m "your-service-release-${tag}"
    git push origin master
```

Don't forget to replace placeholders with real values.

To test it, just make changes, commit them and ask Rultor to make a release
in GitHub ticket:
```
@rultor realse, tag=`1.0`
```
and see what happens.
