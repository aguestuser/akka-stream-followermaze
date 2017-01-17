# Austin Guest's Code Challenge

The below notes assumes that $BASE_DIR is the file in which this README is located. 

## Dependencies

* jdk v. 1.8.x
* scala v. 2.12.x
* sbt v. 0.13.x

## Run instructions

Open two terminal windows.
 
In the first, start the application:

```
$ cd $BASE_DIR
$ sbt run
```

In the second, start the verification script:

```
$ cd $BASE_DIR/bin
$ ./followermaze.sh
```

## Tests

To run the tests, run:
 
```
$ cd $BASE_DIR
$ sbt test
```

To see the coverage report, open `$BASE_DIR/index.html` in a browser.

## Design Notes

* parser combinators for codec
* akka-streams for transport layer
* actors for thread-safety
* purely functional state machine for ease-of-reasoning 
