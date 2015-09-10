zBikes test kit
===============

Running
-------

Download an assembled jar from [here](https://github.com/zuhlke-days-2015/zBikes-test/releases/latest) and then in the same folder run:

```
  java -jar zBikes-test-assembly-*.jar
```

Running repeatedly
------------------

There are two options! The first one runs the test every 10 seconds:

```
brew install watch
cd zBikes-test
watch -c -t -n 10 java -jar zBikes-test-assembly-*.jar
```

The second runs the tests whenever a file in the implementation folder changes:

```
brew install fswatch
cd zBikes-test
fswatch -0 -or $ZBIKES_IMPL_FOLDER | xargs -0 -n 1 sh -c  "clear; java -jar zBikes-test-assembly-*.jar; "
```
