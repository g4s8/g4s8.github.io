---
layout: post
title: Get rid of presenter
---

How to split platfor-depended view logic from domain logic and
unit-test them separately? There are few ways to do it, one of them is
<a href="https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93presenter" target="_blank">MVP (Model-View-Presenter)</a>
pattern. It give us many advantages in android system but has one major drawback in OOP world - a presenter.

## Why?
Why do I choose this particular pattern? Why not
<a href="https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel" target="_blank">MVVM</a>, or
<a href="https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller" target="_blank">MVC</a>?<br/>
First of all I want to have an ability to test each part of my app independently.
You probably know that it's not so easy to unit-test
<a href="https://developer.android.com/reference/android/content/Context.html" target="_blank">Android context</a>
dependend stuff like `Activity`, `View` etc. You have to write
<a href="https://developer.android.com/training/testing/unit-testing/instrumented-unit-tests.html" target="_blank">Instrumentation tests</a>
and launch it on real device or emulator, another option is to use a framework that simulates an Android-SDK e.g. a
<a href="http://robolectric.org/">Robolectric framework</a>.
Moreover this kind of tests is slow enough to run it on every build.
Therefore it's a good practies to keep our model independed of android context to be able to write plain java
<a href="http://junit.org/junit4/">JUnit</a> tests for it.<br/>
This principles are reachable in MVP and MVVM patterns. MVC is off the menu - controller is a weak part here,
we need to test it as an android component (with instrumentation test), not only view.<br/>
MVVM is better in case of unit testing, but I don't actually like this view-model part - it shares the state to
pass data and events through self.<br/>
MVP is the single who passed a test. This pattern has one big problem called presenter but I think we can get rid of if
and save all unit-testing advantages.


## Services
MVP intend's to abstract away from view or model implementations and propagates interface usage instead of
concrete view or model classes. 
So if we want to get rid of presenter we should put view and model at **one level** and think think about them
as two **independent services** - "view service" and "model service". So presenter get down to **communication level** and
his only responsobility would be to deliver messages from view service to model service and vice versa.
In this design the only way to communicate between services is to send messages conformed to public protocol
and receive them to process.
To simplify this connection we can define public protocols and write them as java interfaces.<br/>
E.g. if we show some person info we can make such kind of protocols:
```java
interface View {
  void render(Person person);
}

interface Model {
  void change(Name name);
}
```

## Messages instead of direct method calls
This services can't directly access each other, the only way to communicate
is to send and receive messages.
Lets define generic messages for this services. I'd call them packets here
```java
interface Packet<T> {
  void apply(T protocol);
}
```
if model want to ask a view to show a person it can send this packet:
```java
class PktShow implements Packet<View> {
  
  private final Person person;
  
  public PktShow(Person person) {
    this.person = person;
  }

  @Override
  public void apply(View protocol) {
    protocol.render(person);
  }
}
```
and if user edited person name a view can ask a model to change it with this message:
```java
class PktChange implements Packet<Model> {
  
  private final Name name;

  public PktChange(Name name) {
    this.name = name;
  }

  @Override
  public void apply(Model protocol) {
    protocol.change(name);
  }
}
```
so we've just declared messages as atomic unit of services communication.

## Reactive communications
Now our view and model are independent services. Our model is responsible for consuming packets for `Model` protocol
and at the same time it's a packet source for `View` protocol. Similar for view. In rx-java terms we can define model
as a `Cosumer` for model packets and a `Source` for view packets:
```java
class OurModel implements
  ObservableSource<Packet<View>>,
  Consumer<Packet<Model>> {
}

class OurView extends View implements
  ObservableSource<Packet<Model>>,
  Consumer<Packet<View>> {
}
```

Let's call them as `Service<In, Out>`:
```java
interface Service<In, Out> extends
  ObservableSource<Packet<Out>>,
  Consumer<Packet<In>> {
}
```
Now our connection logic and service protocols are independent also. We can design our service as a single object
or split connection logic and protocol logic into different classes:
```java
/**
 * Model is a service.
 */
class OurModel implements
  Service<Model, View>,
  Model {

  @Override
  public void accept(Packet<Model> packet) {
    packet.apply(this);
  }
}
```

```java
/**
 * Model socket.
 */
class ModelService implements Service<Model, View> {

  Model model;

  @Override
  public void apply(Packet<Model> packet) {
    packet.apply(model);
  }
}

/**
 * Model.
 */
class OurModel implements Model {
}
```

## Presenter
As described previously presenter now have to do only one thing - sending messages from view to model and from model to view.
I'd rename it to `Ether`.
This ether can always been connected to service (read as: encapsulates model-service)
and be able to provide connection to view.
*I'm writing it with RxJava-2 library to save a lot of time, but it can be implemented without it.*
```java
class Wire {

  private final CompositeDisposable subscriptions = new CompositeDisposable();
  
  private final Socket<Model, View> modelSocket;

  public Wire(Socket<Model, View> modelSocket) {
    this.modelSocket = modelSocket;
  }

  public void plugIn(Socket<View, Model> socket) {
    subscriptions.add(
      Observable.wrap(modelSocket).subscribe(socket)
    );
    subscriptions.add(
      Observable.wrap(socket).subscribe(modelSocket)
    );
  }

  public void unplug() {
    subscriptions.clear();
  }
}
```
Furthermore we can run our model-service on one of thread-pool threads and view-service
only on main thread with a few lines of code:
```java
Observable.wrap(modelSocket)
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(socket)
```

## Connect to framework classes
All we know about tricky view lifecycle. When we create a part of user interface and
show it with help of framework, our view have to pass many stages before it will be fully prepared for presenting.<br/>
*I would call 'A View' all user inteface stuff like activity, fragment, view or
whatever you use to interract with a user to simplify this post and because it's not so important in terms of MVP.*<br/>
So we can't just put a view as a presenter dependency, we need to setup a presenter later from one of view's 
lifecycler callback. Also we can't put a presenter as a view dependency because view can be inflated via xml layout and system `LayoutInflater` will instantiate our view through reflection. I know this looks dirty but it's a single path to connect them together.<br/>
//TODO: rename wire
So our draft will look like this:
```java
class OurView extends android.view.View
  implements Socket<Model, View>,
  View {

  private Wire wire;

  public OurView(Context ctx) {
    super(ctx);
  }

  public void connect(Wire wire) {
    this.wire = wire;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    this.wire.plugIn(this);
  }

  @Override
  protected void onDetachFromWindow() {
    super.onDetachFromWindow();
    this.wire.unplug();
  }

  @Override
  public void render(Person person) {
    //TODO: render
  }
}
```
and an `Activity`:
```java
class OurActivity extends Activity {

  @Override
  public void onCreate(Bundle savedState) {
    super.onCreate(savedState);
    final OurView view = new OurView(this);
    view.connect(new Wire(new Model()));
    setContentView(view);
  }
}
```
