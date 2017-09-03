---
layout: post
title: Get rid of presenter
---

How to split platform depended views from domain logic and be able to
unit-test them separately? There are few ways to do it, one of them is
<a href="https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93presenter" target="_blank">MVP (Model-View-Presenter)</a>
pattern. It give us many advantages in Android system but has one major drawback in OOP world - a presenter.

## Why to split?
You probably know that it's not so easy to unit-test
<a href="https://developer.android.com/reference/android/content/Context.html" target="_blank">context</a>
dependend stuff like `Activity`, `View` etc. because this classes is a part of Android framework and 
they are loaded in runtime on device, each device may have different implementation of same classes. To test it you have to write
<a href="https://developer.android.com/training/testing/unit-testing/instrumented-unit-tests.html" target="_blank">Instrumentation tests</a>
and launch it on real device or emulator, another option is to use a framework that simulates an Android-SDK e.g. a
<a href="http://robolectric.org/">Robolectric framework</a>.
This kind of tests is slow enough to run it on every build,
therefore it's a good practies to keep our model independed of Android context to be able to write plain java
<a href="http://junit.org/junit4/">JUnit</a> tests for it.<br/>

## Why MVP?
Why do I choose this particular pattern? Why not
<a href="https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel" target="_blank">MVVM</a>, or
<a href="https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller" target="_blank">MVC</a>?<br/>
First of all (as I say before) I want to have an ability to test each part of my app independently. Second important criteria
that I want to see in my app design is a <a href="https://en.wikipedia.org/wiki/Loose_coupling" target="_blank">loose coupling</a>.
This principles are reachable in MVP and MVVM patterns. MVC is off the menu - controller is a weak part here,
we need to test it as an Android component (with instrumentation test), not only view.<br/>
MVVM is better in case of unit testing, but I don't actually like this view-model part - it shares the state to
pass data and events through self and it's not appears as good object.<br/>
MVP is the single who passed a test. As for me this pattern has one 
<a href="http://www.yegor256.com/2015/03/09/objects-end-with-er.html" target="_blank">big problem</a>
called "presenter" - it should present data
from model in view and react to user interactions.
but I think we can get rid of it and save all unit-testing advantages.


## View & Model Services
MVP intend's us to abstract away from view or model implementations and propagates interface usage instead of
concrete view or model classes. 
So if we want to get rid of presenter we should put view and model at **one level** and think about them
as two **independent services** - "view service" and "model service". So presenter get down to **communication level** and
his only responsobility would be to deliver messages from view to model and vice versa.
In this design the only way to communicate between services is to send messages conformed to public protocol
and react to them when receiving.
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

## Service messages
In Android world we have to pay attention in which thread code is executing. We can touch widget/controls only on UI-thread
and we should execute IO operations in background threads to keep user interaction responsive. Usually presenter
takes care of it. Here it will be handled by communication object too *(I'm not sure that it's right design,
but I tested this approach and didn't found any problems related to threads)*.<br/>
Our services can't directly access each other, the only way to communicate
is to send and receive messages. Each message will be called on specific thread.
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
*I'm writing it with RxJava-2 library to save a lot of time, but it can be implemented without it.*<br/>
Now our view and model are independent services. Our model is responsible for consuming packets for `Model` protocol
and at the same time it's a packet source for `View` protocol. Similar for view. In rx-java terms we can define model
as a `Cosumer` for model packets and a `Source` for view packets:
```java
class OurModel implements
  ObservableSource<Packet<View>>,
  Consumer<Packet<Model>> {
}

class OurView extends android.widget.View implements
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
Now our connection logic and service protocols are independent also. We can design our service as a single object:
```java
/**
 * Model as a service.
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

or split connection logic and protocol logic into different classes:
```java
/**
 * Model service.
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

## Instead of presenter
As described previously presenter now have to do only one thing - sending messages from view to model and from model to view.
I'd rename it to `Wire`.
This wire can always been connected to service (read as: encapsulates model-service)
and be able to provide connection to view (I'll describe why later).
I implemented it with rx also:
```java
class Wire {

  private final CompositeDisposable subscriptions =
    new CompositeDisposable();
  
  private final Service<Model, View> modelService;

  public Wire(Service<Model, View> modelService) {
    this.modelService = modelService;
  }

  public void plugIn(Service<View, Model> viewService) {
    subscriptions.add(
      Observable.wrap(modelService)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(viewService)
    );
    subscriptions.add(
      Observable.wrap(viewService)
        .observeOn(Schedulers.io())
        .subscribe(modelService)
    );
  }

  public void unplug() {
    subscriptions.clear();
  }
}
```


## Connect to framework classes
All we know about tricky view lifecycle. When we create a part of user interface and
show it with help of framework, our view have to pass many stages before it will be fully prepared for presenting.<br/>
*I would call 'A View' all user inteface stuff like activity, fragment, view or
whatever you use to interract with a user. It's not so important in terms of MVP.*<br/>
So we can't just put a view as a presenter dependency, we need to setup a presenter later from one of view's 
lifecycler callback. Also we can't put a presenter as a view dependency because view can be inflated via xml layout and system `LayoutInflater` will instantiate our view through reflection. I know this looks dirty but it's a single path to connect them together.<br/>
So our draft will look like this:
```java
class OurView extends android.view.View
  implements Service<Model, View>,
  View {

  private Wire wire;

  public OurView(Context ctx) {
    super(ctx);
  }

  public OurView connected(Wire wire) {
    this.wire = wire;
    return this;
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
    setContentView(
      new OurView(this).connected(
        new Wire(
          new Model()
        )
      )
    );
  }
}
```
Now our view service will be connected to model service when view attached to window and
disconnected when detached from it - connection depends on view lifecycle.

## Tests!
The result of work. View and model are independent now. Model can be tested with plain
junit tests. For view tests I prefer Robolectric.

View's test:
```java
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 25, application = TestApp.class)
public final class ViewTest {

    @Test
    public void renderNameTest() {
        final TestActivity activity = Robolectric.setupActivity(TestActivity.class);
        final View view = new View(activity);
        view.connected(new WireStub<>());
        activity.setContentView(view);
        final String firstName = "First";
        final String lastName = "Last";
        view.render(new FullName(firstName, lastName));
        MatcherAssert.assertThat(
            "First name wasn't displayed correctly",
            EditText.class.cast(activity.findViewById(R.id.edit_first_name)).getText().toString(),
            Matchers.equalTo(firstName)
        );
        MatcherAssert.assertThat(
            "Last name wasn't displayed correctly",
            EditText.class.cast(activity.findViewById(R.id.edit_last_name)).getText().toString(),
            Matchers.equalTo(lastName)
        );
    }
}
```

and model test:
```java
public final class ModelTest {
  
  @Test
  public void changeNameTest() {
    final FakeStore store = new FakeStore();
    final Model model = new Model(store);
    model.change(new FullName("First", "Last"));
    MatcherAssert.assertThat(
      "Name wasn't saved correctly",
      store.check("/person/name/[./first/text() = 'First' and ./last/text() = 'Last']"),
      Matchers.is(true)
    );
  }
}
```


## --
I've never used this approach in any big projects, it's just an idea that I'm implementing in some little pet projects.
Also I'm still thinking about good names for this objects, maybe I'll rename this services, packets and wires into something more self-explanatory.<br/>
So if you have any feedback with corrections, ideas or critique please write a comment below.
