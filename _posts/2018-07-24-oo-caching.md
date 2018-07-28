---
layout: post
title: Object oriented caching in Java
---

We have a lot of caching libraries for Java,
just take a look at [guava](https://github.com/google/guava) or
[apache-commons JCS](https://commons.apache.org/proper/commons-jcs/). But I don't really like
them because of procedural caching approach, this is why I started
[new caching library](https://github.com/g4s8/cactoos-cache)
for OO code, here I'll try to explain main ideas.

> "There are only two hard things in Computer Science: cache invalidation and naming things." (Phil Karlton)

First of all, just look at existing popular cache libraries:
```java
// Guava cache
LoadingCache<Key, Value> cache = CacheBuilder.newBuilder()
  .maximumSize(42)
  .expireAfterWrite(1, TimeUnit.MINUTES)
  .removalListener(listener)
  .build(
    new CacheLoader<Key, Value>() {
      public Value load(Key key) throws AnyException {
        return loadSomeValue(key);
      }
    }
  );

Value val = cache.get(key);

cache.put(key, val);
```
and one more:
```java
// Apache JCS
public class Image implements Serializable {
  public String name;
  public Bitmap bitmap;
}

CacheAccess<String, Image> cache = JCS.getInstance("images");
Image image = new Image();
image.name = name;
image.bitmap = bitmap;
cache.put(name, image);
```
They uses cache as a storage for data structures and the maintenance of
such code is like a hell. Just imagine, if you need to add synchronization to all cache
clients, then add logging, or change cache logic. In all these cases you need to
find all consumers of static instance, inject additional field for synchronization to all
classes and cross fingers that nobody else will use this cache without that locking object.

## How it should be
As for me, caching should be implemented as a decorator for expensive operation.
If we want to save the result of downloading/computation/payed-service-request or
something else, the most object oriented way is to decorate this call and save the result.
The only problem here is that we need to generalize expensive resource access as
an interface to be able to write generic caching decorators for any resource.
The most primitive implementation may look like this:
```java
interface Resource<T> {
  T get();
}

class CachedResource<T> implements Resource<T> {
  private T cached;
  private final Resource<T> origin;

  @Override
  T get() {
    if (cached == null) {
      cached = origin.get();
    }
    return cached;
  }
}
```

## Cactoos
I decided to use [Cactoos](https://github.com/yegor256/cactoos) library for it, because
it already has generic resource access interfaces, e.g. `Func`, `Scalar` etc. Also it was
designed to be extended by writing decorators. So if you have some `Func` implementation, you can wrap it
with your own cached `Func`:
```java
new Cached(this::image);
```
By the way, it already has some caching `Func`s, they are called *sticky* here
(you can find more about it in this blog post:
[lazy-loading-caching-sticky-cactoos](https://www.yegor256.com/2017/10/17/lazy-loading-caching-sticky-cactoos.html)):
`StickyFunc`, `StickyScalar`, but it is very simple and not really suitable for many real cases.
This `Sticky*` implementations just keeps caching value in a map and doesn't pay attention to available
memory in JVM or last access time or many other things which should be taken into account when
you decide what kind of cache to use.

So I created a caching extension for Cactoos based on these primitives:
[cactoos-cache](https://github.com/g4s8/cactoos-cache).
It has different cache implementations, so you may use these decorators for particular cases.

For example, if you're caching big images and you want to clear cache when JVM requires
more memory, you can use a cache based on
[`SoftReference`](https://docs.oracle.com/javase/7/docs/api/java/lang/ref/SoftReference.html)s: reference lifetime is configured by `-XX:SoftRefLRUPolicyMSPerMB` JVM option, which means how many milliseconds
can live soft-reference for each free megabyte in heap, default value is 1000.<br/>
Example:
```java
Func<String, Bitmap> images = new SoftFunc<>(name -> image(name));
assert images.apply("kittens") == images.apply("kittens"); // same reference
```
This code will keep downloaded image in memory while Java heap has free space,
but this image can be garbage-collected when JVM requires more memory to allocate
new object.

Another case is to keep object in memory while another object is alive.
It can be implemented with
[`WeakReference`](https://docs.oracle.com/javase/7/docs/api/java/lang/ref/WeakReference.html)s:
weak reference will not be garbage collected while any strong references to the same memory exists<br/>
Example:
```java
Func<File, Bytes> files = new WeakFunc<>(file -> bytes(file));
```
This code will keep file's content while file reference exist,
it can be lost when `file` argument is not referenced anymore.

I'm going to add more cache implementations, like LRU cache and cache
based on expiration time, but main idea is to implement them all as
decorators for Cactoos.

## Benefits

I think the benefits are obvious here: cache is not a data access object here,
but decorator for an interface. We can easily decorate this cache with synchronization,
logging, or whatever you want:
```java
Func<String, Image> images = new SyncFunc<>(new SoftFunc<>(name -> image(name)));
```

also we can use it with dependency-injection and replace implementation for tests:
```java
class RotatedImage implements Image {
  private final Func<String, Image> source;
  private final String name;

  RotatedImage(Func<String, Image> source, String name) {
    this.source = source;
    this.name = name;
  }

  @Override
  public void render(Canvas cvs) {
    RotatedImage.rotate(this.source.apply(this.name)).render(cvs);
  }
  
  private static Image rotate(Image src) {
    // apply rotation
  }
}

/**
 * Unit test.
 */
class RotatedImageTest {
  @Test
  public void canRotate() {
    Canvas canvs = new FakeCanvas();
    // RotatedImage will use fake implementation for tests here
    new RotatedImage(new FakeImages(), "image").render(canvas);
    // assert canvas ...
  }
}

/**
 * Application code.
 */
class RotatedImages implements Images {

  private final Func<String, Image> source;

  public RotatedImages(NetworkImages network) {
    this.source = new SoftFunc<>(name -> network.download(name));
  }

  @Override
  public Image image(String name) {
    // RotatedImage will use cache here
    return new RotatedImage(this.source, name);
  }
}
```

Here `RotatedImages` class will use cache based on soft-references
in application code, but unit test will use fake images without caching.

