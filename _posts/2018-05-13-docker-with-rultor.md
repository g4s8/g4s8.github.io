---
layout: post
title: Release Docker with Rultor
---

[Rultor](http://rultor.com) is a great bot to automate development life-cycle:
just post a comment in a GitHub ticket and your project will be
released. Also it can merge pull-requests and deploy code to production
but now I want to speak about how to release a
[Docker](https://docs.docker.com/) image.

## Build, tag, push
Usually, when you are releasing new docker image
you have to pull GitHub repository, build an image,
create a tag for new build, upload it to your registry
and add a tag to repository.
With Rultor you can make it with one comment in GitHub:
```
@rultor release, tag=`0.1`
```
It can be configured to make all these steps automatically.
When Rultor released this image you may use it as you want:
start locally with `docker run`, run tests against this image
or deploy to production server:
![Workflow]({{ site.baseurl }}/images/docker-with-rultor.png)

## Docker in Docker
Rultor runs each build
[in Docker container](http://doc.rultor.com/docker.html)
it has a lot of benefits, but when we are building Docker images in Rultor
we should be careful.<br/>
Running Docker in Docker
[may be dangerous](https://jpetazzo.github.io/2015/09/03/do-not-use-docker-in-docker-for-ci/)
because of security issues, filesystem issues,
docker cache will be missed in child container and it can produce
data corruption in some cases.
So instead of running `docker build|tag|push` commands in new Docker daemon
we can invoked it on Rultor's host from release-script.
It's a new [feature in Rultor](https://github.com/yegor256/rultor/issues/1257),
now it shares Docker daemon socket via [bind-mounting](https://docs.docker.com/storage/bind-mounts/) it to container:
```
docker run -v /var/run/docker.sock:/var/run/docker.sock
```
so we can access Docker daemon in rultor-script with `docker` client tool without starting
Docker daemon. It also has some [security issues](https://github.com/yegor256/rultor/issues/1259),
but it can be fixed by using [docker-socket-proxy](https://github.com/Tecnativa/docker-socket-proxy)
instead of daemon unix-socket.

## Example

Let say you have maven java project and you want to pack it
as docker image. First of all you have to write a `Dockerfile`
(*You can find example project here: [samples/docker-with-rultor](https://github.com/g4s8/g4s8.github.io/tree/master/samples/docker-with-rultor)*):
```
# Build stage
FROM g4s8/alpine:jdk-8 as build
# Version argument (should be passed by Rultor)
ARG version="1.0-SNAPSHOT"
WORKDIR /build
# Copying project
COPY pom.xml ./pom.xml
COPY src ./src
# Update project vesion to $version argument and build a jar
RUN mvn versions:set -DnewVersion=${version} install -Pdocker

# Run stage
FROM g4s8/alpine:jre-8
WORKDIR /app
# Copy build from build-stage layer
COPY --from=build /build/target/app.jar /app/app.jar
COPY --from=build /build/target/deps /app/deps
# Run main class
ENTRYPOINT ["java"]
EXPOSE 80
CMD ["-cp", "app.jar:deps/*", "com.sample.App", "--port=80"]
```
This Dockerfile has two [stages](https://docs.docker.com/develop/develop-images/multistage-build/):
build and run.
Build stage copy sources from repository and compile them
to jar file with dependencies. The result of build stage is
`taget/app.jar` and `target/deps/`.<br/>
Run stage just starts main class of `app.jar`.

Then you have to write release script for Rultor in `.rultor.yml`:
```
docker:
  image: "g4s8/rultor:0.5" # Alpine image with docker
env:
  MAVEN_OPTS: "-XX:MaxPermSize=256m -Xmx1g"
release:
  script:
    - 'mvn install -Pqulice -B --quiet'
    - 'command sudo docker build --tag="your-registry.com/example:$tag" --build-arg="version=$tag" .'
    - 'command sudo docker push "your-registry.com/example:$tag"'
    - 'command sudo docker image rm "your-registry.com/example:$tag"'
```
First of all we run tests (with [Qulice](https://github.com/teamed/qulice)) to skip them in image build phase.
Then we build an image, here we are using `$tag` environment as tag name
(`--tag="your-registry.com/example:$tag"`) and as argument (`--build-arg="version=$tag"`)
to pass it to build via `ARG version="1.0-SNAPSHOT"`.
It's important to use `command sudo` here instead of `sudo` because Rultor
has an alias for `sudo`: `sudo='sudo -i'` which change current directory for executing command
to user's home, where we need to run it in current directory.<br/>
Then we push an image to our registry with `docker push` and finally
we remove an image from Rultor to cleanup.
That's all. Now we have to add Rultor as a collaborator to repository and post
a comment in a ticket:
```
@rultor release, tag=`0.1`
```
Also don't forget to change [Docker registry](https://docs.docker.com/registry/) in `.rultor.yml`
to your own instead of placeholder (your-registry.com).

All done, now you can run your image to verify:
```
docker run -d -p 8080:80 your-registry/example:0.1
curl localhost:8080
```
if you see `OK` curl output then your image is working fine.

`:wq`

