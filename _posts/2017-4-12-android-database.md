---
layout: post
title: Database
---


It's an open secret that almost every Android application
stores something in a database. Nearly always it is [SQLite](https://www.sqlite.org/).<br/>
In my practice I've tried many ways to work with sqlite in Android:
it was some [ORM and active-record](https://www.sitepoint.com/5-best-android-orms/) libraries,
[ContentProvider's](https://developer.android.com/reference/android/content/ContentProvider.html),
even some wrappers for native `.so` libraries.
But with none of them I was satisfied.

## What's wrong with them?
The main reason why I don't tolerate [ORM](https://en.wikipedia.org/wiki/Object-relational_mapping)
is that it brings some dirt into app architecture: it makes me think in data domain instead of
object domain.
Also it's hard to maintain in future: object behaviour changes cause database schema changes and vice versa.<br/>
I know that I'm not alone, many people took a stand against ORM:
- [Ted Neward](http://blogs.tedneward.com/post/the-vietnam-of-computer-science/)
- [Yegor Bugayenko](http://www.yegor256.com/2014/12/01/orm-offensive-anti-pattern.html)
- [Jeff Atwood](https://blog.codinghorror.com/object-relational-mapping-is-the-vietnam-of-computer-science/)

`ContentProvider` with `CursorLoader` looks better, but it brings raw data directly to UI layer that is not really good.

It's important to remember that sqlite database is nothing more than just a data store,
this is not an object store and there is not always possible to map table columns to object state.

## Android framework API
Android framework provides us with a database layer API.<br/>
Let's look at the [official implementation guide](https://developer.android.com/training/basics/data-storage/databases.html):
that is not a code that I'd like to see in my applications.
Just look at this:
> You may find it helpful to create a companion class, known as a contract class<br/>
> ...<br/>
> A contract class is a container for constants that define names for URIs, tables, and columns. The contract class allows you to use the same constants across all the other classes in the same package.

It's a public class inflated with public [constants](http://www.yegor256.com/2015/07/06/public-static-literals.html) only;
a class without state and behaviour.

Later they suggest implementing a _helper_ class to maintain a database.
I'd prefer to avoid [this kind of classes](http://www.yegor256.com/2015/03/09/objects-end-with-er.html),
but it's a necessary evil -
this class is an
[entry point](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper.html#getReadableDatabase%28%29)
for android
[database object](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html).

To get something from database via Android framework you should query a cursor:

```java
final SQLiteDatabase db = sqliteHelper.getReadableDatabase();
final Cursor cursor = db.query(
  distinct,
  table,
  columns,
  selection,
  selectionArgs,
  groupBy,
  having,
  orderBy,
  limit
);
```
Now it's more interesting.
Method `query` returns us
[`Cursor`](https://developer.android.com/reference/android/database/Cursor.html)
object.

**Pros and cons:**<br/>
`Cursor` is a good abstraction for database record in my opinion.<br/>
Also it can be decorated with a built-in class
[`CursorWrapper`](https://developer.android.com/reference/android/database/CursorWrapper.html).

On the other hand `query` method is
[overcomplicated](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#query%28boolean,%20java.lang.String,%20java.lang.String[],%20java.lang.String,%20java.lang.String[],%20java.lang.String,%20java.lang.String,%20java.lang.String,%20java.lang.String%29):
we have to pass loads of arguments to build a query.<br/>
Besides if we don't need to group results or set a limit we are passing
`null`-s as arguments - this is not developer-friendly.<br/>
Common query may look like:
```java
db.query(
  false,
  "people",
  new String[]{"id", "name"},
  null,
  null,
  null,
  null,
  null,
  null
);
```
In summary: 
 - we have to use `SQLiteOpenHelper`
 - `Cursor` object is OK
 - Everything else can be object oriented

## How do I see it
Let's try to implement a really simple object with a state, fetched from a database<br/>
E.g. a person with a name:
```java
public interface Person {

  long id();

  String name();
}
```

If I need to access person by id I'd like to see this code:
```java
final Person person = new PersonById(database, id);
```
To find people by name I'd use this:
```java
final List<Person> people = new PeopleWithName(
  database,
  name
);
```
And so on.

I prefer to use a new object for a new need.
For me it's looks much better than _repository pattern_ because of semantics:
repository is an aggregation of procedures to fetch data from the source
when `PersonById` is just a person with specified id.<br/>
Also it's not clear what is behavior of repository is.
Repository behaves like a data-labourer: it can save data, fetch by id, delete if needed, update rows in table,
fetch collection of data, ordered by name, group by name and ~~repair the primus~~ many other things.
Honestly, I think that main reason why repository pattern is so popular is that it's easy to deal with ORM frameworks via repository,
like a mediator between objects and data structures.

## Implementation

First of all we need to describe a person for a `Cursor`:

```java
final class CursorPerson implements Person {

  private final long id;
  private final String name;

  CursorPerson(final Cursor cur) {
    this.id = cur.getInt(cur.getColumnIndex("id"));
    this.name = cur.getString(cur.getColumnIndex("name"));
  }

  public int id() {
    return this.id;
  }

  public String name() {
    return this.name;
  }
}
```

Now we have a `Cursor` adapter, what's next?<br/>
It's time to make a query to database and fetch person's state from cursor
with this adapter.<br/>
Our `PersonById` can be implemented as decorator for person:

```java
public interface Person {

  ...

  /**
   * Default decorator.
   */
  abstract class Wrap implements Person {

    private final Person origin;

    protected Wrap(final Person origin) {
      this.origin = origin;
    }

    public final long id() {
      return this.origin.id();
    }

    public final String name() {
      return this.origin.name();
    }
  }
}

final class PersonById extends Person.Wrap {
  PersonById(
    final SQLiteDatabase db
    final long id
  ) {
    super(
      new CursorPerson(
        db.query(
          // Many args
        )
      )
    );
  }
}
```

Now it's almost good. The only problem with this class is `db.query()` method: 
we need to pass unused arguments and copy-paste duplicated code (like table, columns) over similar objects. 
To solve this issue I've started new project:
[QueryLite](https://github.com/g4s8/QueryLite).
It makes all query work more simple and fluent.
Also it allows me to encapsulate sqlite tables and queries with decorators.

Sharing public constants (like table name, columns)
[is not a good idea](http://www.yegor256.com/2015/07/06/public-static-literals.html)
if we want to go OO way,
so let's define few objects to encapsulate them and compose more complex objects later:

```java
final class PeopleTable extends Table.Wrap {

  private static final String NAME = "people";

  PeopleTable(SQLiteDatabse db) {
    super(
      new TableSqlite(
        db,
        PeopleTable.NAME
      )
    );
  }
}

final class PeopleQuery extends Query.Wrap {
  PeopleQuery(SQLiteDatabse db) {
    super(
      new Select(
        "id",
        "name"
      ).from(
        new PeopleTable(db)
      )
    );
  }
}
```

And finally make `PersonById` as decorator for `CursorPerson`:

```java
public final class PersonById extends Person.Wrap {
  public PersonById(SQLiteDatabase db, long id) {
    super(
      new CursorPerson(
        new CursorQuery(
          new PeopleQuery(db)
            .where("id = ?", id)
        )
      )
    );
  }
}
```

And similar for `PeopleWithName`:

```java
public final class PeopleWithName extends ListWrap<Person> {
  public PeopleWithName(SQLiteDatabase db, String name) {
    super(
      new ListIterable(
        new CursorIterable(
          new CursorQuery(
            new PeopleQuery(db)
              .where(
                "name LIKE ?",
                String.format("%%%s%%", name)
              )
          ),
          CursorPerson::new
        )
      )
    );
  }
}
```

`ListWrap` is a `List` decorator here, <br/>
`ListIterable` is a `List` with `Iterable` as source, <br/>
`CursorIterable` - an `Iterable` with `Cursor` as source, and mapping as second argument.

## Conclusion
This way we make objects with encapsulated state fetched from database as
composition of decorators. It's easy to maintain and refactor them:<br/>
 - `Person` object and `person` table are not interrelated:
 now they can be modified individually.
 - If `Person` object is changed or database schema is changed 
 we don't need to modify `PersonById` and `PeopleWithName`, only `CursorPerson`.
 - It's very easy to change data fetching behaviour. 
 E.g. if we want to make our `PersonById` lazy-loaded 
 we can make `LazyCursorPerson` that will access cursor data in methods instead of constructor.
 - You should have no difficulty in changing object composition:
 e.g. to make our lazy-loaded `PersonById` cacheable we can wrap in with caching decorator:
 ```java
 super(
      new CachedPerson(
        new LazyCursorPerson(
          new CursorQuery(db)
            .where("id = ?", id)
        )
      )
    );
 ```

