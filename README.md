# manifail

[![Build Status](https://travis-ci.org/tulos/manifail.png?branch=master)](https://travis-ci.org/tulos/manifail)

Handle failures and manage retries without callbacks!

The semantics are modeled closely to
[Failsafe](https://github.com/jhalterman/failsafe), which has a Clojure wrapper
called [Diehard](https://github.com/sunng87/diehard).

Manifail differs in that it tries to be composable and not rely on callbacks
for the failure handling logic. The drawback is that it's less precise and
carries more overhead than Failsafe. Also, circuit breakers are currently not
supported.

## Disclaimer

Use Manifail if you're feeling adventurous and/or like to write your code in a
direct callback-less style! Otherwise please consider using Failsafe directly
or through Diehard.

## Usage

`[tulos/manifail "0.4.0"]`

First, require the namespaces:

```clojure
(refer-clojure :exclude '(delay))
(require '[manifold.deferred :as d])
(require '[manifold.executor :as ex])
(use 'manifail)
```

### Retry building blocks

To create a retriable execution with Manifail you have to use four parts
working in concert:

1. A retry policy - sequence of milliseconds representing the delay of retries
2. A piece of code potentially having the `retry`/`abort`/`reset` markers
3. An executor to run the code and retry attempts on
4. `with-retry` macro or `with-retry*` function wrapping the code to be retried

It looks like this:

```clojure
(def unreliable-service-executor (Executors/newFixedThreadPool 1))

(ex/with-executor unreliable-service-executor
  (let [retry-delays [10 50 100]]
    (with-retries retry-delays
      (try (let [result (unreliable-service)]
             (when (:error result)
               (retry! result)))
             result)
           (catch UnrecoverableException e
             (abort! e))
           (catch SessionExpiredException e
             (authenticate!)
             (reset! retry-delays)))
```

The `with-retries` block above will return a deferred of its result. This
deferred will get fulfilled when either the original call or one of the three
retries completes. The execution will happen on the specified
`unreliable-service-executor`.

Given the above code, a retry will happen if:

* An exception is thrown which is not an `UnrecoverableException`
* The `result` has an `:error` key

and the resulting deferred will be completed when:

* A non `:error` response is returned
* An `UnrecoverableException` happens and the execution is aborted. In this
  case a `manifail.Aborted` exception is thrown with the cause set to `e`
* A `manifail.RetriesExceeded` exception is thrown when there are no more retry
  attempts.

In case a `SessionExpiredException` is thrown the whole execution cycle will
begin anew with the supplied sequence of `retry-delays`. In the example above
it's the same sequence as the one provided to `with-retries` originally.

### Retry policies

The simplest possible retry logic that will perform 3 retries with delays of
50, 100 and 150 ms:

```clojure
(with-retries [50 100 150]
  (unreliable-service))
```

The following code will retry the call 5 times with 50 ms delay:

```clojure
(with-retries (delay (retries 5) 50)
  (unreliable-service))
```

same, but with a backoff factor of 2.0:

```clojure
(with-retries (delay (retries 5) 50 {:backoff-factor 2.0})
  (unreliable-service))
```

with a random jitter of 25 ms:

```clojure
(with-retries (delay (retries 5) 50 {:backoff-factor 2.0, :jitter-ms 25})
  (unreliable-service))
```

with a random jitter scaling the delay up to 1/2:

```clojure
(with-retries (delay (retries 5) 50 {:backoff-factor 2.0, :jitter-factor 0.5})
  (unreliable-service))
```

with a total call and retry duration limited to 500 ms:

```clojure
(with-retries (-> (retries 5)
                  (delay 50 {:backoff-factor 2.0, :jitter-factor 0.5})
                  (limit-duration 500))
  (unreliable-service))
```

unlimited equally spaced out retries with duration limited to 500 ms:

```clojure
(with-retries (-> (forever) (delay 50) (limit-duration 500))
  (unreliable-service))
```

### Retry context

There are several dynamic bindings available in the retry block:

* `*retry-count*` - the current retry count
* `*elapsed-ms*` - milliseconds elapsed since entering the retry block
* `*last-result*` - last result/exception that caused the retry

### A complete example

Below I tried to map callbacks available in Failsafe retry policy to the code
that you could write in order to get equivalent behaviour.  We assume that
`call-some-service` blocks until completed:

```clojure
(->
  (with-retries (delay (retries 5) 50) ;; retry on any exception by default
    (println "-- on before execution")
    (when (> *retry-count* 0)
      (println "-- on before retry"))
    (try (let [result (call-some-service)]
           (when (> result 5) ;; abort condition
             (println "-- on failed attempt 1")
             (println "-- on before abort")
             (abort! result)) ;; set the value of Abort to `result`
           (when (= result ::bad) ;; retry condition
             (println "-- on failed attempt 2")
             (retry!))
           result)
         (catch OkException _ :ok) ;; do not retry on this exception
         (catch UnrecoverableException e
           (abort! e)) ;; set the cause of Abort to `e`
         (catch RecoverableException _
           (do-some-recovery)
           (reset! (delay (retries 5) 50)))
         (catch Throwable e
           (when-not (marker? e)
             (println "-- on failed attempt any"))
           (throw e))))
  (d/chain #(println "-- on complete" %))
  (d/catch manifail.Aborted #(println "-- on after abort" (unwrap %))
  (d/catch manifail.RetriesExceeded #(println "-- on retries exceeded" (unwrap %))
  (d/catch #(println "-- on failure" %)))
```

`call-some-service` might also be asynchronous and return something derefable.
In this case the code within `with-retries` can `chain` the logic:

```clojure
(with-retries (delay (retries 5) 50)
  (-> (call-some-service)
      (d/chain (fn [result] ...))))
```

## License

Copyright © 2016 Tulos Capital

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
